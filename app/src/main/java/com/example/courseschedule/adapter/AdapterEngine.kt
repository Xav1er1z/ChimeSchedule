package com.example.courseschedule.adapter

import android.content.Context
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.example.courseschedule.data.Course
import com.example.courseschedule.data.WeekType
import com.example.courseschedule.ui.theme.courseColorArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

/** 脚本运行结果 */
data class AdapterResult(
    val courses: List<Course> = emptyList(),
    val logs: List<String> = emptyList(),
    val error: String? = null,
    /** 脚本返回的课表配置（作息时间等） */
    val timeTable: List<Triple<Int, String, String>> = emptyList(),
    val semesterStart: String = "",
) {
    val isSuccess: Boolean get() = error == null && courses.isNotEmpty()
}

/**
 * 适配脚本运行引擎：在 QuickJS 中执行教务适配脚本，
 * 通过 http 绑定让脚本抓取教务系统数据，最终收集课程返回。
 */
class AdapterEngine(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** 运行适配脚本 */
    suspend fun run(
        adapter: SchoolAdapter,
        username: String,
        password: String,
        extra: Map<String, String> = emptyMap(),
    ): AdapterResult = withContext(Dispatchers.Default) {
        val logs = mutableListOf<String>()
        var collected: List<Course>? = null
        var semesterStart = ""
        val timeTable = mutableListOf<Triple<Int, String, String>>()
        var runtimeError: String? = null
        val cookieStore = mutableMapOf<String, String>()

        fun log(msg: String) {
            if (logs.size < 300) logs.add(msg)
        }
        val logSink: (String) -> Unit = { msg -> log(msg) }

        try {
            val quickJs = QuickJs.create(Dispatchers.Default)
            try {
                quickJs.define("__native") {
                    function("log") { args ->
                        log(args.joinToString(" ") { it?.toString() ?: "null" })
                    }
                    function("done") { args ->
                        val arr = args.firstOrNull() as? List<*>
                        collected = parseCourses(arr ?: emptyList<Any?>())
                        null
                    }
                    function("setTimeTable") { args ->
                        val arr = args.firstOrNull() as? List<*> ?: return@function null
                        arr.forEachIndexed { i, item ->
                            val map = item as? Map<*, *> ?: return@forEachIndexed
                            val node = (map["node"] as? Number)?.toInt() ?: (i + 1)
                            val start = map["start"]?.toString().orEmpty()
                            val end = map["end"]?.toString().orEmpty()
                            if (start.isNotBlank()) timeTable.add(Triple(node, start, end))
                        }
                        null
                    }
                    function("setSemesterStart") { args ->
                        semesterStart = args.firstOrNull()?.toString().orEmpty()
                        null
                    }
                }

                quickJs.define("http") {
                    asyncFunction("get") { args ->
                        val url = args.getOrNull(0)?.toString() ?: return@asyncFunction "{}"
                        val opts = args.getOrNull(1) as? Map<*, *>
                        doRequest("GET", url, null, opts, cookieStore, logSink)
                    }
                    asyncFunction("post") { args ->
                        val url = args.getOrNull(0)?.toString() ?: return@asyncFunction "{}"
                        val body = args.getOrNull(1)?.toString() ?: ""
                        val opts = args.getOrNull(2) as? Map<*, *>
                        doRequest("POST", url, body, opts, cookieStore, logSink)
                    }
                    asyncFunction("postForm") { args ->
                        val url = args.getOrNull(0)?.toString() ?: return@asyncFunction "{}"
                        val params = args.getOrNull(1) as? Map<*, *>
                        val opts = args.getOrNull(2) as? Map<*, *>
                        val form = FormBody.Builder().apply {
                            params?.forEach { (k, v) ->
                                add(k.toString(), v?.toString() ?: "")
                            }
                        }.build()
                        doRequestWithBody("POST", url, form, opts, cookieStore, logSink)
                    }
                }

                quickJs.define("utils") {
                    function<String, String>("md5") { md5(it) }
                    function<String, String>("base64Encode") {
                        Base64.getEncoder().encodeToString(it.toByteArray())
                    }
                    function<String, String>("base64Decode") {
                        String(Base64.getDecoder().decode(it))
                    }
                    function<String, String>("sha1") { sha1(it) }
                }

                // 便捷全局函数
                val prelude = """
                    var console = { log: function() { __native.log(Array.prototype.slice.call(arguments).join(' ')); } };
                    function log() { __native.log(Array.prototype.slice.call(arguments).join(' ')); }
                    function done(courses) { __native.done(courses); }
                    function setTimeTable(t) { __native.setTimeTable(t); }
                    function setSemesterStart(d) { __native.setSemesterStart(d); }
                    var md5 = utils.md5, sha1 = utils.sha1;
                    var base64 = { encode: utils.base64Encode, decode: utils.base64Decode };
                """.trimIndent()

                // 执行 prelude + 脚本
                quickJs.evaluate<Any?>(prelude + "\n" + adapter.script)

                // 调用入口函数
                val extraJson = extra.entries.joinToString(",") { (k, v) ->
                    "\"${escapeJs(k)}\": \"${escapeJs(v)}\""
                }
                val call = """
                    (async function() {
                      try {
                        if (typeof getSchedule === 'function') {
                          await getSchedule({
                            username: "${escapeJs(username)}",
                            password: "${escapeJs(password)}",
                            extra: { $extraJson }
                          });
                        } else {
                          __native.log('错误：脚本缺少 getSchedule(params) 入口函数');
                        }
                      } catch (e) {
                        __native.log('脚本异常：' + (e && e.message ? e.message : e));
                      }
                    })()
                """.trimIndent()
                quickJs.evaluate<Any?>(call)
            } finally {
                quickJs.close()
            }
        } catch (e: Throwable) {
            runtimeError = e.message ?: e.toString()
            log("运行失败：$runtimeError")
        }

        val courses = collected
        when {
            runtimeError != null ->
                AdapterResult(courses = emptyList(), logs = logs, error = runtimeError, timeTable = timeTable, semesterStart = semesterStart)
            courses == null ->
                AdapterResult(courses = emptyList(), logs = logs, error = "脚本未返回课程（请检查账号密码或脚本是否适配）", timeTable = timeTable, semesterStart = semesterStart)
            courses.isEmpty() ->
                AdapterResult(courses = emptyList(), logs = logs, error = "导入结果为空，可能是账号密码错误或课表为空", timeTable = timeTable, semesterStart = semesterStart)
            else ->
                AdapterResult(courses = courses, logs = logs, timeTable = timeTable, semesterStart = semesterStart)
        }
    }

    // ---------- HTTP ----------

    private suspend fun doRequest(
        method: String,
        url: String,
        body: String?,
        opts: Map<*, *>?,
        cookieStore: MutableMap<String, String>,
        log: (String) -> Unit,
    ): String {
        val reqBody = body?.toRequestBody(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
        )
        return doRequestWithBody(method, url, reqBody, opts, cookieStore, log)
    }

    private suspend fun doRequestWithBody(
        method: String,
        url: String,
        body: okhttp3.RequestBody?,
        opts: Map<*, *>?,
        cookieStore: MutableMap<String, String>,
        log: (String) -> Unit,
    ): String {
        return try {
            val builder = Request.Builder().url(url)
            @Suppress("UNCHECKED_CAST")
            val headers = opts?.get("headers") as? Map<*, *>
            headers?.forEach { (k, v) -> builder.header(k.toString(), v?.toString() ?: "") }

            // 带上已保存的 cookie
            if (cookieStore.isNotEmpty()) {
                builder.header("Cookie", cookieStore.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
            if (!headers?.keys?.any { it.toString().equals("User-Agent", true) }!!) {
                builder.header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36",
                )
            }

            when (method) {
                "GET" -> builder.get()
                "POST" -> builder.post(body ?: "".toRequestBody(null))
            }

            val response = withContext(Dispatchers.IO) { client.newCall(builder.build()).execute() }
            response.use { resp ->
                // 保存 cookie
                resp.headers("Set-Cookie").forEach { cookie ->
                    val pair = cookie.split(";").firstOrNull()?.trim() ?: return@forEach
                    val idx = pair.indexOf('=')
                    if (idx > 0) cookieStore[pair.substring(0, idx)] = pair.substring(idx + 1)
                }
                val text = resp.body?.string() ?: ""
                log("${method} $url -> ${resp.code} (${text.length} 字符)")

                // 返回 JSON 字符串，脚本里 JSON.parse 使用
                val escaped = text
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                """{"status":${resp.code},"body":"$escaped"}"""
            }
        } catch (e: Throwable) {
            log("请求失败 $url: ${e.message}")
            """{"status":0,"body":"","error":"${escapeJs(e.message ?: "网络错误")}"}"""
        }
    }

    // ---------- 课程解析 ----------

    private fun parseCourses(list: List<*>): List<Course> {
        val out = mutableListOf<Course>()
        list.forEachIndexed { index, item ->
            val map = item as? Map<*, *> ?: return@forEachIndexed
            val name = map["name"]?.toString()?.trim().orEmpty()
            if (name.isBlank()) return@forEachIndexed

            val teacher = map["teacher"]?.toString().orEmpty()
            val room = (map["room"] ?: map["location"])?.toString().orEmpty()
            val day = parseDay(map["day"])
            val start = (map["startNode"] as? Number)?.toInt() ?: 1
            val step = (map["step"] as? Number)?.toInt() ?: 1
            val weeks = parseWeeks(map["weeks"])

            out.add(
                Course(
                    name = name,
                    teacher = teacher,
                    location = room,
                    dayOfWeek = day.coerceIn(0, 6),
                    startSection = start.coerceIn(1, 20),
                    endSection = (start + step - 1).coerceIn(start.coerceIn(1, 20), 20),
                    weekType = weeks.first,
                    customWeeks = weeks.second,
                    colorArgb = courseColorArgb[index % courseColorArgb.size],
                ),
            )
        }
        return out
    }

    private fun parseDay(value: Any?): Int {
        when (value) {
            is Number -> return value.toInt()
            is String -> {
                val s = value.trim()
                s.toIntOrNull()?.let { return it - 1 }
                val names = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                val idx = names.indexOfFirst { s.contains(it.replace("周", "")) }
                if (idx >= 0) return idx
                val en = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
                val e = en.indexOfFirst { s.lowercase().startsWith(it) }
                if (e >= 0) return e
            }
        }
        return 0
    }

    private fun parseWeeks(value: Any?): Pair<WeekType, List<Int>> {
        when (value) {
            is List<*> -> {
                val weeks = value.mapNotNull { (it as? Number)?.toInt() }.filter { it in 1..30 }
                return if (weeks.isEmpty()) WeekType.ALL to emptyList()
                else WeekType.CUSTOM to weeks.sorted()
            }
            is String -> {
                val s = value.trim()
                if (s.contains("单")) return WeekType.ODD to emptyList()
                if (s.contains("双")) return WeekType.EVEN to emptyList()
                val weeks = LinkedHashSet<Int>()
                Regex("""(\d+)\s*[-~到]\s*(\d+)""").findAll(s).forEach { m ->
                    val a = m.groupValues[1].toIntOrNull() ?: return@forEach
                    val b = m.groupValues[2].toIntOrNull() ?: return@forEach
                    for (w in minOf(a, b)..maxOf(a, b)) if (w in 1..30) weeks.add(w)
                }
                if (weeks.isEmpty()) {
                    Regex("""\d+""").findAll(s).forEach { m ->
                        m.value.toIntOrNull()?.let { if (it in 1..30) weeks.add(it) }
                    }
                }
                return if (weeks.isEmpty()) WeekType.ALL to emptyList()
                else WeekType.CUSTOM to weeks.sorted()
            }
            is Number -> {
                return when (value.toInt()) {
                    1 -> WeekType.ODD to emptyList()
                    2 -> WeekType.EVEN to emptyList()
                    else -> WeekType.ALL to emptyList()
                }
            }
        }
        return WeekType.ALL to emptyList()
    }

    private fun escapeJs(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
