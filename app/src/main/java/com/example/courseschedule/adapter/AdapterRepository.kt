package com.example.courseschedule.adapter

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 适配器仓库管理：
 * - 内置通用示例脚本
 * - 支持从远端 JSON 仓库拉取更多学校适配脚本
 * - 用户自定义脚本保存在本地
 */
class AdapterRepository(private val context: Context) {

    private val gson = Gson()
    private val customFile = File(context.filesDir, "custom_adapters.json")
    private val cacheFile = File(context.filesDir, "remote_adapters.json")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 全部可用适配器（内置 + 远端缓存 + 自定义） */
    fun all(): List<SchoolAdapter> {
        val list = mutableListOf<SchoolAdapter>()
        list.addAll(builtIn())
        list.addAll(loadFrom(cacheFile))
        list.addAll(loadFrom(customFile))
        return list.distinctBy { it.id }
    }

    /** 保存用户自定义脚本 */
    fun saveCustom(adapter: SchoolAdapter) {
        val current = loadFrom(customFile).toMutableList()
        current.removeAll { it.id == adapter.id }
        current.add(adapter)
        customFile.writeText(gson.toJson(current))
    }

    fun deleteCustom(id: String) {
        val current = loadFrom(customFile).filterNot { it.id == id }
        customFile.writeText(gson.toJson(current))
    }

    /**
     * 从远端仓库拉取适配脚本列表。
     * 仓库格式：[{ id, name, initial, script, remark }]
     */
    suspend fun fetchRemote(url: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).get().build()
            val body = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
            val type = object : TypeToken<List<SchoolAdapter>>() {}.type
            val list: List<SchoolAdapter> = gson.fromJson(body, type) ?: emptyList()
            val valid = list.filter { it.id.isNotBlank() && it.script.isNotBlank() }
            cacheFile.writeText(gson.toJson(valid))
            Result.success(valid.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadFrom(file: File): List<SchoolAdapter> {
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<SchoolAdapter>>() {}.type
            gson.fromJson<List<SchoolAdapter>>(file.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun builtIn(): List<SchoolAdapter> = listOf(
        SchoolAdapter(
            id = "DEMO",
            name = "示例适配器（可复制修改）",
            initial = "S",
            remark = "演示脚本 API 用法，可复制后改成你学校的地址",
            script = DEMO_SCRIPT,
        ),
        SchoolAdapter(
            id = "CUSTOM",
            name = "自定义脚本",
            initial = "Z",
            remark = "粘贴你自己的适配脚本",
            script = "",
        ),
    )

    companion object {
        /** 远端适配仓库地址（可指向你自己的 GitHub Raw 链接） */
        const val DEFAULT_REPO_URL =
            "https://raw.githubusercontent.com/Xav1er1z/ChimeSchedule/main/adapters/index.json"

        val DEMO_SCRIPT = """
            // ============================================================
            // 教务适配脚本示例
            // 入口：async function getSchedule(params)
            //   params.username / params.password  用户输入的账号密码
            //   params.extra                       其它自定义参数
            //
            // 可用：
            //   http.get(url, opts)         -> {status, body}
            //   http.post(url, body, opts)  -> {status, body}
            //   http.postForm(url, params)  -> {status, body}
            //   log(...)                    输出日志
            //   md5(str) / sha1(str) / base64.encode/decode
            //   done([...])                 返回课程，结束
            //   setTimeTable([{node:1,start:"08:00",end:"08:45"}, ...])  可选
            //   setSemesterStart("2026-09-01")                          可选
            //
            // 课程字段：
            //   name      课程名（必填）
            //   teacher   老师
            //   room      教室
            //   day       星期：0-6（0=周一）或 "周一"
            //   startNode 开始节次（从 1 开始）
            //   step      连续节数
            //   weeks     周次：[1,2,3] 或 "1-16" 或 "单" / "双"
            // ============================================================

            async function getSchedule(params) {
              log('开始导入，账号：' + params.username);

              // 1) 登录（示例地址，请替换成你学校的）
              // var login = await http.postForm('https://jw.example.edu/login', {
              //   username: params.username,
              //   password: params.password
              // });
              // log('登录返回：' + login.status);

              // 2) 请求课表页面
              // var res = await http.get('https://jw.example.edu/schedule');
              // log('课表页面长度：' + res.body.length);

              // 3) 解析（这里用 JSON 举例，实际可能是 HTML，需要自己写解析）
              // var data = JSON.parse(res.body);
              // var list = data.courses.map(function (c) {
              //   return {
              //     name: c.name,
              //     teacher: c.teacher,
              //     room: c.room,
              //     day: c.day,          // 0-6
              //     startNode: c.start,  // 从 1 开始
              //     step: c.step,
              //     weeks: c.weeks       // "1-16" 或 "单"
              //   };
              // });

              // 演示：返回两门示例课程
              log('这是示例脚本，请替换成你学校的登录与解析逻辑');
              done([
                { name: '高等数学', teacher: '张三', room: '教学楼A101', day: 0, startNode: 1, step: 2, weeks: '1-16' },
                { name: '大学英语', teacher: '李四', room: '教学楼B202', day: 2, startNode: 3, step: 2, weeks: '单' }
              ]);
            }
        """.trimIndent()
    }
}
