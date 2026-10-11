package com.example.courseschedule.import

import com.example.courseschedule.data.Course
import com.example.courseschedule.data.WeekType
import com.example.courseschedule.data.mergeCourses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证导入解析逻辑是否正确（纯 JVM 测试，不需要设备） */
class ImporterTest {

    @Test
    fun `文本导入 能识别常见写法`() {
        val text = """
            高等数学 张三 周一 1-2节 教学楼A101
            大学英语 李四 周二 3-4节 1-16周 B202
            体育 王五 周三 5-6节 单周 操场
            物理 赵六 周四 7-8节 双周 实验楼C301
        """.trimIndent()

        val result = TextImporter.parse(text)

        println("=== 识别到 ${result.courses.size} 门课 ===")
        result.courses.forEach {
            println("  ${it.name} | 老师=${it.teacher} | 地点=${it.location} | 周${it.dayOfWeek + 1} | ${it.startSection}-${it.endSection}节 | ${it.weekType}")
        }
        result.warnings.forEach { println("  警告: $it") }

        assertEquals("应识别 4 门课", 4, result.courses.size)

        val math = result.courses[0]
        assertEquals("高等数学", math.name)
        assertEquals("张三", math.teacher)
        assertEquals("教学楼A101", math.location)
        assertEquals(0, math.dayOfWeek)
        assertEquals(1, math.startSection)
        assertEquals(2, math.endSection)

        val pe = result.courses[2]
        assertEquals("体育", pe.name)
        assertEquals(2, pe.dayOfWeek)
        assertEquals(WeekType.ODD, pe.weekType)

        val physics = result.courses[3]
        assertEquals(WeekType.EVEN, physics.weekType)
    }

    @Test
    fun `文本导入 支持多种星期写法`() {
        val text = """
            语文 星期一 1节
            数学 礼拜二 2节
            English Tuesday 3节
            化学 周日 4节
        """.trimIndent()

        val result = TextImporter.parse(text)
        result.courses.forEach { println("  ${it.name} -> 周${it.dayOfWeek + 1}") }
        assertEquals(4, result.courses.size)
        assertEquals(0, result.courses[0].dayOfWeek)
        assertEquals(1, result.courses[1].dayOfWeek)
        assertEquals(1, result.courses[2].dayOfWeek)
        assertEquals(6, result.courses[3].dayOfWeek)
    }

    @Test
    fun `CSV 导入 正常解析`() {
        val csv = """
            课程名,老师,教室,星期(1-7),开始节,结束节,周次类型,自定义周次
            高等数学,张三,教学楼A101,1,1,2,每周,
            大学英语,李四,教学楼B202,3,3,4,单周,
            体育,王五,操场,5,7,8,自定义,1-8
        """.trimIndent()

        val result = CsvImporter.parse(csv)
        println("=== CSV 识别 ${result.courses.size} 门 ===")
        result.courses.forEach { println("  ${it.name} 周${it.dayOfWeek + 1} ${it.startSection}-${it.endSection} ${it.weekType}") }

        assertEquals(3, result.courses.size)
        assertEquals("高等数学", result.courses[0].name)
        assertEquals("张三", result.courses[0].teacher)
        assertEquals(WeekType.ODD, result.courses[1].weekType)
        assertEquals(WeekType.CUSTOM, result.courses[2].weekType)
        assertTrue(result.courses[2].customWeeks.contains(5))
    }

    @Test
    fun `HTML 导入 解析表格`() {
        val html = """
            <table>
              <tr><td>节次</td><td>周一</td><td>周二</td><td>周三</td></tr>
              <tr><td>1</td><td>高等数学<br>张三<br>1-16周</td><td></td><td>英语</td></tr>
              <tr><td>2</td><td>高等数学<br>张三<br>1-16周</td><td></td><td>英语</td></tr>
            </table>
        """.trimIndent()

        val result = HtmlImporter.parse(html)
        println("=== HTML 识别 ${result.courses.size} 门 ===")
        result.courses.forEach { println("  ${it.name} 周${it.dayOfWeek + 1} ${it.startSection}-${it.endSection}") }
        result.warnings.forEach { println("  警告: $it") }

        assertTrue("应识别到课程", result.courses.isNotEmpty())
        val math = result.courses.firstOrNull { it.name == "高等数学" }
        assertTrue("应识别出高等数学", math != null)
        assertEquals(1, math!!.startSection)
        assertEquals(2, math.endSection)
    }

    @Test
    fun `边界 空输入不会崩溃`() {
        val r1 = TextImporter.parse("")
        val r2 = TextImporter.parse("   \n\n  ")
        val r3 = CsvImporter.parse("")
        val r4 = HtmlImporter.parse("")
        println("空输入结果：${r1.courses.size}, ${r2.courses.size}, ${r3.courses.size}, ${r4.courses.size}")
        assertEquals(0, r1.courses.size)
        assertEquals(0, r2.courses.size)
        assertEquals(0, r3.courses.size)
        assertEquals(0, r4.courses.size)
    }

    @Test
    fun `边界 超范围节次会被约束`() {
        val text = "高等数学 张三 周一 1-99节"
        val result = TextImporter.parse(text)
        result.courses.forEach { println("  ${it.name} ${it.startSection}-${it.endSection}") }
        assertEquals(1, result.courses.size)
        assertTrue("节次应被约束到合理范围", result.courses[0].endSection <= 20)
    }

    @Test
    fun `合并 同课相邻会合并 不同老师不合并`() {
        val courses = listOf(
            Course(name = "高数", teacher = "张三", dayOfWeek = 0, startSection = 1, endSection = 2),
            Course(name = "高数", teacher = "张三", dayOfWeek = 0, startSection = 3, endSection = 4),
            Course(name = "高数", teacher = "李四", dayOfWeek = 0, startSection = 5, endSection = 6),
        )
        val blocks = mergeCourses(courses, week = 1, maxSections = 12)
        println("=== 合并结果 ${blocks.size} 块 ===")
        blocks.forEach { println("  ${it.name}(${it.teacher}) 第${it.startSection}-${it.endSection}节") }

        assertEquals("张三的高数应合并成 1 块，李四单独 1 块", 2, blocks.size)
        val zhang = blocks.first { it.teacher == "张三" }
        assertEquals(1, zhang.startSection)
        assertEquals(4, zhang.endSection)
    }

    @Test
    fun `合并 空课程列表不会崩溃`() {
        val blocks = mergeCourses(emptyList(), week = 1, maxSections = 12)
        assertEquals(0, blocks.size)
    }

    @Test
    fun `合并 非法节次不会崩溃`() {
        val courses = listOf(
            Course(name = "异常课", dayOfWeek = 0, startSection = 0, endSection = 0),
            Course(name = "异常课2", dayOfWeek = 9, startSection = -5, endSection = -1),
            Course(name = "正常课", dayOfWeek = 1, startSection = 1, endSection = 2),
        )
        val blocks = mergeCourses(courses, week = 1, maxSections = 12)
        println("合并结果：${blocks.map { "${it.name} ${it.startSection}-${it.endSection}" }}")
        // 不崩溃即通过，正常课应保留
        assertTrue(blocks.any { it.name == "正常课" })
    }
}
