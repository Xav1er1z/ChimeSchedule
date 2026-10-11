package com.example.courseschedule

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.courseschedule.adapter.AdapterEngine
import com.example.courseschedule.adapter.SchoolAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 在设备/模拟器上运行的测试：验证适配脚本引擎真的能执行 JS 并返回课程。
 * 运行方式：gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AdapterEngineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun demoScript_returnsCourses() = runBlocking {
        val engine = AdapterEngine(context)
        val adapter = SchoolAdapter(
            id = "TEST",
            name = "测试脚本",
            script = """
                async function getSchedule(params) {
                  log('账号：' + params.username);
                  done([
                    { name: '高等数学', teacher: '张三', room: 'A101', day: 0, startNode: 1, step: 2, weeks: '1-16' },
                    { name: '大学英语', teacher: '李四', room: 'B202', day: 2, startNode: 3, step: 2, weeks: '单' },
                    { name: '体育', teacher: '王五', room: '操场', day: 4, startNode: 5, step: 2, weeks: [1,2,3] }
                  ]);
                }
            """.trimIndent(),
        )

        val result = engine.run(adapter, "test001", "pw")

        println("=== 日志 ===")
        result.logs.forEach { println("  $it") }
        println("=== 课程 ${result.courses.size} 门 ===")
        result.courses.forEach { println("  ${it.name} 周${it.dayOfWeek + 1} ${it.startSection}-${it.endSection} ${it.weekType}") }
        println("错误：${result.error}")

        assertTrue("应该成功", result.error == null)
        assertEquals(3, result.courses.size)
        assertEquals("高等数学", result.courses[0].name)
        assertEquals("张三", result.courses[0].teacher)
        assertEquals(1, result.courses[0].startSection)
        assertEquals(2, result.courses[0].endSection)
        assertEquals(4, result.courses[2].dayOfWeek)  // 周五
    }

    @Test
    fun scriptCanSetTimeTable() = runBlocking {
        val engine = AdapterEngine(context)
        val adapter = SchoolAdapter(
            id = "TEST2",
            name = "作息测试",
            script = """
                async function getSchedule(params) {
                  setTimeTable([
                    { node: 1, start: '08:00', end: '08:45' },
                    { node: 2, start: '08:55', end: '09:40' }
                  ]);
                  setSemesterStart('2026-09-01');
                  done([{ name: '测试课', day: '周一', startNode: 1, step: 1, weeks: '每周' }]);
                }
            """.trimIndent(),
        )

        val result = engine.run(adapter, "u", "p")
        println("作息：${result.timeTable}")
        println("开学：${result.semesterStart}")

        assertEquals(2, result.timeTable.size)
        assertEquals("2026-09-01", result.semesterStart)
        assertEquals(1, result.courses.size)
    }

    @Test
    fun scriptError_isReported() = runBlocking {
        val engine = AdapterEngine(context)
        val adapter = SchoolAdapter(
            id = "TEST3",
            name = "异常测试",
            script = """
                async function getSchedule(params) {
                  throw new Error('模拟登录失败');
                }
            """.trimIndent(),
        )
        val result = engine.run(adapter, "u", "p")
        println("错误信息：${result.error}")
        println("日志：${result.logs}")
        assertTrue("应捕获脚本异常", result.error != null)
    }

    @Test
    fun builtInScript_loads() {
        val repo = com.example.courseschedule.adapter.AdapterRepository(context)
        val all = repo.all()
        println("内置适配器：${all.map { it.id }}")
        assertTrue("应至少有一个内置适配器", all.isNotEmpty())
    }

    @Test
    fun wakeUpImport_badInputs_neverCrash() {
        // 各种畸形口令都不能让 App 崩溃（真机环境才能正确验证 org.json 行为）
        val badInputs = listOf(
            "", "   ", "abc", "!!!!!!!!!!!!!!!!",
            "a".repeat(1000), "😀😀😀", "\u0000\u0001\u0002",
            "====", "aGVsbG8=",
            "中文口令测试", "{\"fake\":\"json\"}",
        )
        badInputs.forEach { input ->
            try {
                val r = com.example.courseschedule.import.WakeUpImporter.import(input)
                println("输入「${input.take(20)}」-> 课程 ${r.courses.size}，警告 ${r.warnings.firstOrNull()}")
            } catch (e: Throwable) {
                throw AssertionError("输入「${input.take(20)}」导致崩溃：${e.javaClass.simpleName} ${e.message}", e)
            }
        }
    }
}
