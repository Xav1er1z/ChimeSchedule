package com.example.courseschedule.import

import com.example.courseschedule.data.Course
import com.example.courseschedule.data.WeekType
import com.example.courseschedule.ui.theme.courseColorArgb
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** 导入结果 */
data class ImportResult(
    val courses: List<Course> = emptyList(),
    val warnings: List<String> = emptyList(),
)

// ---------------------------------------------------------------------------
// CSV 导入：课程名,老师,教室,星期(1-7),开始节,结束节,周次类型,自定义周次
// ---------------------------------------------------------------------------
object CsvImporter {

    fun parse(text: String): ImportResult {
        val courses = mutableListOf<Course>()
        val warnings = mutableListOf<String>()
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        for ((i, line) in lines.withIndex()) {
            val fields = splitCsvLine(line)
            // 跳过表头
            if (i == 0 && (fields.firstOrNull() == "课程名" || fields.firstOrNull() == "课程名称")) continue

            if (fields.size < 5) {
                warnings.add("第 ${i + 1} 行字段不足，已跳过：$line")
                continue
            }
            try {
                val name = fields[0].trim()
                val teacher = fields.getOrElse(1) { "" }.trim()
                val location = fields.getOrElse(2) { "" }.trim()
                val day = (fields[3].trim().toIntOrNull() ?: 1) - 1
                val rawStart = fields[4].trim().toIntOrNull() ?: 1
                val rawEnd = fields.getOrElse(5) { fields[4] }.trim().toIntOrNull() ?: rawStart
                val weekType = parseWeekType(fields.getOrElse(6) { "每周" }.trim())
                val weeks = if (weekType == WeekType.CUSTOM) {
                    parseWeekList(fields.getOrElse(7) { "" })
                } else {
                    emptyList()
                }
                if (name.isBlank() || day !in 0..6) {
                    warnings.add("第 ${i + 1} 行内容无法识别，已跳过：$line")
                    continue
                }
                // 节次范围约束，避免越界
                val start = rawStart.coerceIn(1, 20)
                val end = rawEnd.coerceIn(start, 20)
                courses.add(
                    Course(
                        name = name,
                        teacher = teacher,
                        location = location,
                        dayOfWeek = day,
                        startSection = start,
                        endSection = end,
                        weekType = weekType,
                        customWeeks = weeks.filter { it in 1..30 },
                        colorArgb = courseColorArgb[courses.size % courseColorArgb.size],
                    ),
                )
            } catch (e: Exception) {
                warnings.add("第 ${i + 1} 行解析失败，已跳过：$line")
            }
        }
        return ImportResult(courses, warnings)
    }

    private fun parseWeekType(s: String): WeekType = when {
        s.contains("单周") -> WeekType.ODD
        s.contains("双周") -> WeekType.EVEN
        s.contains("自定义") || s.contains(",") || s.contains("-") || s.contains("~") -> WeekType.CUSTOM
        else -> WeekType.ALL
    }

    private fun parseWeekList(s: String): List<Int> {
        val weeks = LinkedHashSet<Int>()
        Regex("""(\d+)\s*[-~到]\s*(\d+)""").findAll(s).forEach { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            for (w in minOf(a, b)..maxOf(a, b)) weeks.add(w)
        }
        if (weeks.isEmpty()) {
            Regex("""\d+""").findAll(s).forEach { m -> weeks.add(m.value.toInt()) }
        }
        return weeks.sorted()
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}

// ---------------------------------------------------------------------------
// HTML 课表导入（解析 <table>，支持 colspan/rowspan）
// ---------------------------------------------------------------------------
object HtmlImporter {

    fun parse(html: String): ImportResult {
        val grid = parseTable(html)
        if (grid.isEmpty()) return ImportResult(warnings = listOf("未在文件中找到表格，请确认是教务系统导出的课表网页。"))
        return coursesFromGrid(grid)
    }

    private fun parseTable(html: String): List<List<String>> {
        val rowPattern = Regex("""<tr[^>]*>(.*?)</tr>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val cellPattern = Regex("""<t[dh]([^>]*)>(.*?)</t[dh]>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        val grid = mutableListOf<MutableList<String>>()
        val occupied = mutableListOf<MutableList<Boolean>>()

        fun ensureRows(upTo: Int) {
            while (grid.size <= upTo) {
                grid.add(mutableListOf())
                occupied.add(mutableListOf())
            }
        }
        fun ensureCols(row: Int, upTo: Int) {
            while (grid[row].size <= upTo) {
                grid[row].add("")
                occupied[row].add(false)
            }
        }

        var rowIdx = 0
        for (tr in rowPattern.findAll(html)) {
            ensureRows(rowIdx)
            var colIdx = 0
            for (cell in cellPattern.findAll(tr.groupValues[1])) {
                ensureCols(rowIdx, colIdx)
                while (colIdx < occupied[rowIdx].size && occupied[rowIdx][colIdx]) {
                    colIdx++
                    ensureCols(rowIdx, colIdx)
                }
                val attrs = cell.groupValues[1]
                val text = stripTags(cell.groupValues[2]).trim()
                val colspan = Regex("""colspan\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE)
                    .find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val rowspan = Regex("""rowspan\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE)
                    .find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                ensureRows(rowIdx + rowspan - 1)
                for (r in rowIdx until rowIdx + rowspan) {
                    ensureCols(r, colIdx + colspan - 1)
                    for (cc in colIdx until colIdx + colspan) {
                        grid[r][cc] = text
                        occupied[r][cc] = true
                    }
                }
                colIdx += colspan
            }
            rowIdx++
        }

        val maxCols = grid.maxOfOrNull { it.size } ?: 0
        return grid.map { row -> (0 until maxCols).map { row.getOrElse(it) { "" } } }
    }
}

// ---------------------------------------------------------------------------
// Excel(.xlsx) 导入（用内置 Zip + XML 解析，无需第三方库）
// ---------------------------------------------------------------------------
object XlsxImporter {

    fun parse(bytes: ByteArray): ImportResult {
        val sharedStrings = readZipEntry(bytes, "xl/sharedStrings.xml")
            ?.let { parseSharedStrings(ByteArrayInputStream(it)) }
            ?: emptyList()
        val sheetNames = listOf("xl/worksheets/sheet1.xml") +
            (2..10).map { "xl/worksheets/sheet$it.xml" }
        val sheetBytes = sheetNames.firstNotNullOfOrNull { readZipEntry(bytes, it) }
            ?: return ImportResult(warnings = listOf("无法读取 Excel 中的工作表。"))

        val cells = mutableMapOf<Pair<Int, Int>, String>()
        parseSheetCells(ByteArrayInputStream(sheetBytes), sharedStrings, cells)

        val maxRow = cells.keys.maxOfOrNull { it.first } ?: 0
        val maxCol = cells.keys.maxOfOrNull { it.second } ?: 0
        val grid = (0..maxRow).map { r -> (0..maxCol).map { c -> cells[Pair(r, c)] ?: "" } }
        return coursesFromGrid(grid)
    }

    private fun readZipEntry(bytes: ByteArray, name: String): ByteArray? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == name) return zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun parseSharedStrings(input: InputStream): List<String> {
        val result = mutableListOf<String>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, null)
        var event = parser.eventType
        var sb = StringBuilder()
        var inSi = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "si") {
                    sb = StringBuilder()
                    inSi = true
                }
                XmlPullParser.TEXT -> if (inSi) sb.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "si") {
                    result.add(sb.toString())
                    inSi = false
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseSheetCells(
        input: InputStream,
        sharedStrings: List<String>,
        cells: MutableMap<Pair<Int, Int>, String>,
    ) {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, null)
        var event = parser.eventType
        var ref: String? = null
        var type: String? = null
        var value: String? = null
        var inV = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "c" -> {
                        ref = parser.getAttributeValue(null, "r")
                        type = parser.getAttributeValue(null, "t")
                        value = null
                        inV = false
                    }
                    "v" -> {
                        value = ""
                        inV = true
                    }
                }
                XmlPullParser.TEXT -> if (inV) value += parser.text
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inV = false
                    "c" -> {
                        val r = ref
                        if (r != null && value != null) {
                            val pos = cellRefToRowCol(r)
                            if (pos != null) {
                                val text = if (type == "s") {
                                    sharedStrings.getOrElse(value.toIntOrNull() ?: 0) { "" }
                                } else {
                                    value ?: ""
                                }
                                cells[pos] = text
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
    }

    private fun cellRefToRowCol(ref: String): Pair<Int, Int>? {
        val m = Regex("""([A-Z]+)(\d+)""").find(ref) ?: return null
        var col = 0
        for (ch in m.groupValues[1]) col = col * 26 + (ch - 'A' + 1)
        return Pair(m.groupValues[2].toInt() - 1, col - 1)
    }
}

// ---------------------------------------------------------------------------
// 共用：从二维网格提取课程（供 HTML / Excel 使用）
// ---------------------------------------------------------------------------
internal fun coursesFromGrid(rows: List<List<String>>): ImportResult {
    if (rows.isEmpty()) return ImportResult(warnings = listOf("表格为空。"))

    val headerIdx = rows.indexOfFirst { row ->
        row.count { it.contains("周一") || it.contains("周二") || it.contains("星期三") || it.contains("星期") } >= 2
    }.let { if (it >= 0) it else 0 }
    val dataStart = headerIdx + 1

    val courses = mutableListOf<Course>()
    var colorIndex = 0
    val warnings = mutableListOf<String>()

    val maxCols = rows.maxOfOrNull { it.size } ?: 0
    for (c in 1 until maxCols) {
        val day = c - 1
        if (day > 6) continue
        var r = dataStart
        while (r < rows.size) {
            val text = rows[r].getOrElse(c) { "" }.trim()
            if (text.isBlank() || isSectionOrTimeCell(text)) {
                r++
                continue
            }
            val startSection = r - dataStart + 1
            var end = r
            while (end + 1 < rows.size && rows[end + 1].getOrElse(c) { "" }.trim() == text) end++
            val span = end - r + 1
            val course = parseCourseCellText(
                text = text,
                day = day,
                startSection = startSection,
                span = span,
                colorArgb = courseColorArgb[colorIndex % courseColorArgb.size],
            )
            if (course != null) {
                courses.add(course)
                colorIndex++
            }
            r = end + 1
        }
    }

    if (courses.isEmpty()) warnings.add("没有解析到课程，请确认表格格式（第一行为星期表头，第一列为节次/时间）。")
    return ImportResult(courses, warnings)
}

/** 节次/时间单元格（不含课程内容），例如 "1"、"08:00"、"第一大节" */
private fun isSectionOrTimeCell(text: String): Boolean {
    if (text.matches(Regex("""\d{1,2}:\d{2}""")) ) return true
    if (text.matches(Regex("""\d{1,2}"""))) return true
    if (text.contains("大节") || text.contains("第") && text.contains("节") && text.length <= 6) return true
    return false
}

/** 从单个课程单元格文本中解析课程信息 */
internal fun parseCourseCellText(
    text: String,
    day: Int,
    startSection: Int,
    span: Int,
    colorArgb: Long,
): Course? {
    val lines = text.split(Regex("""[\r\n]+""")).map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null

    val name = lines.first().replace(Regex("""\s+"""), " ")
    var teacher = ""
    var location = ""
    var weekType = WeekType.ALL
    var weeks = emptyList<Int>()

    for (line in lines.drop(1)) {
        val weekParsed = parseWeeks(line)
        if (weekParsed.first != WeekType.ALL || weekParsed.second.isNotEmpty() || line.contains("周")) {
            weekType = weekParsed.first
            weeks = weekParsed.second
            continue
        }
        if (isLocationLine(line)) {
            location = line
            continue
        }
        if (line.contains("老师")) {
            teacher = line.replace("老师", "").trim()
            continue
        }
        if (teacher.isBlank() && line.length <= 4) {
            teacher = line
        }
    }

    // 节次范围约束
    val s = startSection.coerceIn(1, 20)
    val e = (startSection + span - 1).coerceIn(s, 20)

    return Course(
        name = name,
        teacher = teacher,
        location = location,
        dayOfWeek = day.coerceIn(0, 6),
        startSection = s,
        endSection = e,
        weekType = weekType,
        customWeeks = weeks.filter { it in 1..30 },
        colorArgb = colorArgb,
    )
}

/** 解析周次描述，例如 "1-16周"、"单周"、"1-8,10-16周" */
private fun parseWeeks(line: String): Pair<WeekType, List<Int>> {
    val t = line.trim()
    if (t.contains("单周")) return WeekType.ODD to emptyList()
    if (t.contains("双周")) return WeekType.EVEN to emptyList()
    if (t.contains("每周") || t.contains("全周")) return WeekType.ALL to emptyList()
    if (!t.any { it.isDigit() }) return WeekType.ALL to emptyList()

    val weeks = LinkedHashSet<Int>()
    val ranges = mutableListOf<IntRange>()
    Regex("""(\d+)\s*[-~到]\s*(\d+)""").findAll(t).forEach { m ->
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        val lo = minOf(a, b)
        val hi = maxOf(a, b)
        ranges.add(lo..hi)
        weeks.addAll(lo..hi)
    }
    Regex("""\d+""").findAll(t).forEach { m ->
        val n = m.value.toInt()
        if (ranges.none { n in it }) weeks.add(n)
    }
    return if (weeks.isEmpty()) WeekType.ALL to emptyList() else WeekType.CUSTOM to weeks.sorted()
}

private fun isLocationLine(line: String): Boolean =
    Regex("""(楼|室|馆|堂|区|号|教|校)""").containsMatchIn(line) ||
        Regex("""[A-Za-z]\d{2,}""").containsMatchIn(line)

private fun stripTags(html: String): String {
    return html
        .replace(Regex("""<[^>]*>"""), "\n")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
}
