package com.example.courseschedule.adapter

/**
 * 教务系统适配器：描述一个学校的适配脚本。
 *
 * @param id 唯一标识（如 CUST）
 * @param name 学校名称（如 长春理工大学）
 * @param initial 首字母（用于排序）
 * @param script 适配脚本内容（JavaScript，在内置浏览器中运行）
 * @param importUrl 教务系统网址（打开浏览器时的默认地址）
 * @param category 分类（GENERAL_TOOL = 通用教务工具，SCHOOL = 具体学校）
 * @param remark 备注说明
 */
data class SchoolAdapter(
    val id: String,
    val name: String,
    val initial: String = "",
    val script: String = "",
    val importUrl: String = "",
    val category: String = "",
    val remark: String = "",
) {
    val displayName: String get() = if (name.isBlank()) id else name
    val isGeneralTool: Boolean get() = category == "GENERAL_TOOL"
}

/**
 * 脚本 API 规范（写适配脚本时可用以下全局对象/函数）：
 *
 * ── 网络 ──
 *   http.get(url, options?)            GET 请求，返回 { status, headers, body }
 *   http.post(url, body, options?)     POST 请求（body 为字符串或对象）
 *   http.postForm(url, params, opts?)  POST 表单（application/x-www-form-urlencoded）
 *   options 支持: { headers: {}, cookies: true, encoding: "utf-8" }
 *
 * ── 工具 ──
 *   log(...)                  输出日志（可在导入日志中查看）
 *   base64.encode/decode      编解码
 *   md5(str) / sha1(str)      哈希
 *
 * ── 输出 ──
 *   done(courses)  返回课程列表，结束运行
 *      courses: [{ name, teacher, room, day, startNode, step, weeks }]
 *        day: 0-6（0=周一）或 "周一"~"周日"
 *        weeks: [1,2,...] 周次数组，或 "1-16" / "单" / "双"
 *
 * ── 入口 ──
 *   脚本需定义函数  async function getSchedule(params) { ... }
 *   params: { username, password, extra: {...} }  用户输入
 *
 * ── 示例 ──
 *   async function getSchedule(params) {
 *     const login = await http.postForm("https://jw.example.edu/login", {
 *       username: params.username, password: params.password
 *     })
 *     const res = await http.get("https://jw.example.edu/schedule")
 *     const courses = []
 *     // ...解析 res.body（HTML/JSON）填充 courses
 *     done(courses)
 *   }
 */
object AdapterSpec
