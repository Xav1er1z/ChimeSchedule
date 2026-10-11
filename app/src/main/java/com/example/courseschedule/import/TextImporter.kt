package com.example.courseschedule.import

import com.example.courseschedule.data.Course
import com.example.courseschedule.data.WeekType
import com.example.courseschedule.ui.theme.courseColorArgb

/**
 * 智能文本解析：把任意粘贴的课表文字解析成课程。
 * 支持多种常见写法，尽力识别，识别不到的部分会给出提示。
 */
object TextImporter {

    private val dayPatterns = mapOf(
        0 to listOf("周一", "星期一", "礼拜一", "周一", "Monday", "Mon", "1"),
        1 to listOf("周二", "星期二", "礼拜二", "Tuesday", "Tue", "2"),
        2 to listOf("周三", "星期三", "礼拜三", "Wednesday", "Wed", "3"),
        3 to listOf("周四", "星期四", "礼拜四", "Thursday", "Thu", "4"),
        4 to listOf("周五", "星期五", "礼拜五", "Friday", "Fri", "5"),
        5 to listOf("周六", "星期六", "礼拜六", "Saturday", "Sat", "6"),
        6 to listOf("周日", "周天", "星期日", "星期天", "礼拜日", "Sunday", "Sun", "7", "0"),
    )

    /** 节次模式：第1-2节 / 1-2节 / 1~2节 / 第3,4节 */
    private val sectionRange = Regex("""第?\s*(\d{1,2})\s*[-~到,，、]\s*(\d{1,2})\s*节""")
    private val sectionSingle = Regex("""第?\s*(\d{1,2})\s*节""")

    /** 周次模式：1-16周 / 单周 / 双周 / 每周 */
    private val weekRange = Regex("""(\d{1,2})\s*[-~到]\s*(\d{1,2})\s*周""")

    fun parse(text: String): ImportResult {
        val warnings = mutableListOf<String>()
        val courses = mutableListOf<Course>()
        var colorIndex = 0

        // 按行拆开，支持一行一门课，也支持以分号/竖线分隔
        val lines = text.split(Regex("""[\r\n;；]+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return ImportResult(warnings = listOf("没有识别到任何内容，请粘贴课表文字。"))
        }

        for ((index, line) in lines.withIndex()) {
            val course = parseLine(line, colorIndex) ?: run {
                warnings.add("第 ${index + 1} 行没能识别：${line.take(30)}")
                null
            }
            if (course != null) {
                courses.add(course)
                colorIndex++
            }
        }

        if (courses.isEmpty()) {
            warnings.add("没能识别出课程。建议每行写一门课，例如：高等数学 张三 周一 1-2节 A101")
        }
        return ImportResult(courses, warnings)
    }

    private fun parseLine(line: String, colorIndex: Int): Course? {
        // 1. 先提取星期
        var day = -1
        var dayMatchedText = ""
        for ((d, patterns) in dayPatterns) {
            val hit = patterns.firstOrNull { p ->
                // 数字模式要求是独立词，避免把 "1-2节" 里的 1 当星期
                if (p.length == 1 && p[0].isDigit()) {
                    Regex("""(?<![0-9])$p(?![0-9])""").containsMatchIn(line) && !line.contains("节")
                } else {
                    line.contains(p, ignoreCase = true)
                }
            }
            if (hit != null) {
                day = d
                dayMatchedText = hit
                break
            }
        }
        if (day < 0) return null

        // 2. 提取节次
        var startSection = 0
        var endSection = 0
        sectionRange.find(line)?.let { m ->
            startSection = m.groupValues[1].toIntOrNull() ?: 0
            endSection = m.groupValues[2].toIntOrNull() ?: startSection
        }
        if (startSection == 0) {
            sectionSingle.find(line)?.let { m ->
                startSection = m.groupValues[1].toIntOrNull() ?: 0
                endSection = startSection
            }
        }
        if (startSection == 0) {
            // 尝试时间格式 8:00-9:40 转节次（简单映射：按小时估算）
            val timeRange = Regex("""(\d{1,2}):(\d{2})\s*[-~到]\s*(\d{1,2}):(\d{2})""").find(line)
            if (timeRange != null) {
                val h = timeRange.groupValues[1].toIntOrNull() ?: 8
                startSection = estimateSection(h)
                endSection = startSection
            }
        }
        if (startSection == 0) return null

        // 3. 提取周次
        var weekType = WeekType.ALL
        var customWeeks = emptyList<Int>()
        when {
            line.contains("单周") -> weekType = WeekType.ODD
            line.contains("双周") -> weekType = WeekType.EVEN
            else -> {
                val ranges = mutableListOf<IntRange>()
                val weeks = LinkedHashSet<Int>()
                weekRange.findAll(line).forEach { m ->
                    val a = m.groupValues[1].toIntOrNull() ?: return@forEach
                    val b = m.groupValues[2].toIntOrNull() ?: return@forEach
                    val lo = minOf(a, b); val hi = maxOf(a, b)
                    ranges.add(lo..hi); weeks.addAll(lo..hi)
                }
                Regex("""(\d{1,2})\s*[,，、]\s*(\d{1,2})\s*[,，、]?\s*周""").findAll(line).forEach { m ->
                    m.groupValues[1].toIntOrNull()?.let { weeks.add(it) }
                    m.groupValues[2].toIntOrNull()?.let { weeks.add(it) }
                }
                if (weeks.isNotEmpty()) {
                    weekType = WeekType.CUSTOM
                    customWeeks = weeks.sorted()
                }
            }
        }

        // 4. 清理出课程名/老师/地点
        var rest = line
        // 去掉星期词
        rest = rest.replace(dayMatchedText, " ")
        // 去掉节次描述
        rest = sectionRange.replace(rest, " ")
        rest = sectionSingle.replace(rest, " ")
        // 去掉周次描述
        rest = weekRange.replace(rest, " ")
        rest = Regex("""\d{1,2}\s*[-~到]\s*\d{1,2}\s*周""").replace(rest, " ")
        rest = rest.replace(Regex("""(单|双|每)周"""), " ")
        rest = rest.replace(Regex("""\d{1,2}:\d{2}\s*[-~到]\s*\d{1,2}:\d{2}"""), " ")
        rest = rest.replace(Regex("""[第节周\(\)（）\[\]【】,，、;；|]+"""), " ")
        rest = rest.replace(Regex("""\s+"""), " ").trim()

        if (rest.isBlank()) return null
        val parts = rest.split(" ").filter { it.isNotBlank() }
        val name = parts.firstOrNull() ?: return null

        // 剩下部分里找地点和老师
        var teacher = ""
        var location = ""
        for (p in parts.drop(1)) {
            if (isLocation(p)) location = p
            else if (teacher.isBlank()) teacher = p
        }

        // 节次范围约束，避免越界导致课表渲染异常
        val maxSection = 20
        val s = startSection.coerceIn(1, maxSection)
        val e = endSection.coerceIn(s, maxSection)

        return Course(
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = day.coerceIn(0, 6),
            startSection = s,
            endSection = e,
            weekType = weekType,
            customWeeks = customWeeks.filter { it in 1..30 },
            colorArgb = courseColorArgb[colorIndex % courseColorArgb.size],
        )
    }

    private fun estimateSection(hour: Int): Int = when {
        hour < 9 -> 1
        hour < 11 -> 3
        hour < 13 -> 5
        hour < 15 -> 5
        hour < 17 -> 7
        hour < 19 -> 9
        else -> 11
    }

    private fun isLocation(s: String): Boolean =
        Regex("""(楼|室|馆|堂|区|号|教|校|场|中心|实验室|A\d|B\d|C\d|D\d)""").containsMatchIn(s) ||
            Regex("""^[A-Za-z]?\d{2,4}$""").matches(s)
}
