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
 * - 内置「通用教务」适配器，开箱即用（无需联网更新）
 * - 支持一键从远端仓库拉取学校列表与脚本
 * - 用户自定义脚本保存在本地
 */
class AdapterRepository(private val context: Context) {

    private val gson = Gson()
    private val customFile = File(context.filesDir, "custom_adapters.json")
    private val cacheFile = File(context.filesDir, "remote_adapters.json")

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** 全部可用适配器（内置 + 远端缓存 + 自定义） */
    fun all(): List<SchoolAdapter> {
        val list = mutableListOf<SchoolAdapter>()
        list.addAll(builtIn())
        list.addAll(loadFrom(cacheFile))
        list.addAll(loadFrom(customFile))
        return list.distinctBy { it.id }
    }

    /** 是否已经拉取过远端学校列表 */
    fun hasRemoteSchools(): Boolean = loadFrom(cacheFile).isNotEmpty()

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
     * 一键更新：从远端仓库拉取全部学校适配脚本。
     * 仓库格式：[{ id, name, initial, script, importUrl, category }]
     */
    suspend fun fetchRemote(url: String = DEFAULT_REPO_URL): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                .get()
                .build()
            val body = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
            val type = object : TypeToken<List<SchoolAdapter>>() {}.type
            val list: List<SchoolAdapter> = gson.fromJson(body, type) ?: emptyList()
            val valid = list.filter { it.id.isNotBlank() && it.script.isNotBlank() }
            if (valid.isEmpty()) throw IllegalStateException("仓库中没有可用的适配脚本")
            cacheFile.writeText(gson.toJson(valid))
            Result.success(valid.size)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun loadFrom(file: File): List<SchoolAdapter> {
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<SchoolAdapter>>() {}.type
            gson.fromJson<List<SchoolAdapter>>(file.readText(), type) ?: emptyList()
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /**
     * 内置适配器：开箱即用，不需要联网更新。
     * 这些都是「通用教务」脚本，输入你自己学校的教务网址即可使用。
     */
    private fun builtIn(): List<SchoolAdapter> = listOf(
        SchoolAdapter(
            id = "zhengfang",
            name = "正方教务系统",
            initial = "Z",
            importUrl = "",
            category = "GENERAL_TOOL",
            remark = "国内高校最常见，网址通常含 jwglxt / jwweb",
            script = ZHENGFANG_SCRIPT,
        ),
        SchoolAdapter(
            id = "qiangzhi",
            name = "强智教务系统",
            initial = "Q",
            importUrl = "",
            category = "GENERAL_TOOL",
            remark = "网址通常含 qzdatasoft / 教务网络管理系统",
            script = QIANGZHI_SCRIPT,
        ),
        SchoolAdapter(
            id = "urp",
            name = "URP 教务系统",
            initial = "U",
            importUrl = "",
            category = "GENERAL_TOOL",
            remark = "老牌教务系统，页面多为框架结构",
            script = URP_SCRIPT,
        ),
        SchoolAdapter(
            id = "custom",
            name = "自定义脚本",
            initial = "Z",
            category = "GENERAL_TOOL",
            remark = "粘贴自己的适配脚本",
            script = "",
        ),
    )

    companion object {
        /**
         * 远端适配仓库地址（JSON 数组）。
         * 首次使用时可点「更新学校列表」从网络获取更多学校的专用适配脚本。
         */
        const val DEFAULT_REPO_URL =
            "https://raw.githubusercontent.com/Xav1er1z/ChimeSchedule/main/adapters/index.json"

        /** 通用提示：写脚本时要知道的桥接接口 */
        private val BRIDGE_HINT = """
            // 脚本运行在内置浏览器中，可用：
            //   document / window / jQuery（网页本身的环境）
            //   window.shiguangBridge.showToast(msg)       提示
            //   window.shiguangBridge.notifyTaskCompletion()
            //   await window.shiguangBridgePromise.saveImportedCourses(JSON.stringify(courses))
            // 课程字段：name / day(0-6) / weeks / teacher / position / startSection / endSection
        """.trimIndent()

        /** 正方教务：课表在 #kbgrid_table_0（表格）或 #kblist_table（列表） */
        val ZHENGFANG_SCRIPT = """
            $BRIDGE_HINT

            function zfParseTable() {
              const out = [];
              const nameClean = /[●★○]/g;

              // 表格视图
              const cells = document.querySelectorAll('#kbgrid_table_0 td, table#kbtable td');
              cells.forEach(function (td) {
                const id = td.getAttribute('id') || '';
                const m = id.match(/^(\d+)-(\d+)$/);
                if (!m) return;
                const day = parseInt(m[1]);
                const node = parseInt(m[2]);
                const blocks = td.querySelectorAll('.timetable_con, .kbcontent');
                blocks.forEach(function (b) {
                  const nameEl = b.querySelector('.title font, .title, font');
                  const name = (nameEl ? nameEl.textContent : '').replace(nameClean, '').trim();
                  if (!name) return;
                  const ps = b.querySelectorAll('p');
                  let teacher = '', room = '', weekText = '';
                  ps.forEach(function (p) {
                    const t = p.textContent.trim();
                    if (/周/.test(t) && /节/.test(t)) weekText = t;
                    else if (/^[\u4e00-\u9fa5]{2,4}$/.test(t) && !teacher) teacher = t;
                    else if (t && !room && t.length < 30) room = t;
                  });
                  const sec = weekText.match(/\((\d+)-(\d+)节\)/);
                  const startSection = sec ? parseInt(sec[1]) : node;
                  const endSection = sec ? parseInt(sec[2]) : node;
                  out.push({ name: name, day: day, weeks: weekText, teacher: teacher,
                             position: room, startSection: startSection, endSection: endSection });
                });
              });
              return out;
            }

            function zfParseList() {
              const out = [];
              const nameClean = /[●★○]/g;
              const tbodies = document.querySelectorAll('#kblist_table tbody');
              tbodies.forEach(function (tbody, dayIdx) {
                if (dayIdx < 1 || dayIdx > 7) return;
                const rows = tbody.querySelectorAll('tr');
                rows.forEach(function (tr) {
                  const tds = tr.querySelectorAll('td');
                  if (tds.length < 2) return;
                  const secText = tds[0].textContent;
                  const nameEl = tds[1].querySelector('.title, font');
                  const name = (nameEl ? nameEl.textContent : '').replace(nameClean, '').trim();
                  if (!name) return;
                  const all = tr.textContent;
                  const sec = secText.match(/(\d+)/);
                  const startSection = sec ? parseInt(sec[1]) : 1;
                  let endSection = startSection;
                  const range = secText.match(/(\d+)\s*[-~]\s*(\d+)/);
                  if (range) endSection = parseInt(range[2]);
                  const roomM = all.match(/上课地点[：:]\s*(\S+)/);
                  const teacherM = all.match(/教师\s*[：:]\s*(\S+)/);
                  out.push({ name: name, day: dayIdx - 1, weeks: all,
                             teacher: teacherM ? teacherM[1] : '',
                             position: roomM ? roomM[1] : '',
                             startSection: startSection, endSection: endSection });
                });
              });
              return out;
            }

            async function runImportFlow() {
              const b = window.shiguangBridge, bp = window.shiguangBridgePromise;
              let courses = zfParseTable();
              if (!courses.length) courses = zfParseList();
              if (!courses.length) {
                b.showToast('没有找到课表，请先登录并打开课表页面再执行导入');
                return;
              }
              await bp.saveImportedCourses(JSON.stringify(courses));
              b.showToast('已获取 ' + courses.length + ' 门课程');
              b.notifyTaskCompletion();
            }
        """.trimIndent()

        /** 强智教务：课表通常在表格里，含「第X-X节」与周次 */
        val QIANGZHI_SCRIPT = """
            $BRIDGE_HINT

            function qzParse() {
              const out = [];
              const tables = document.querySelectorAll('table');
              tables.forEach(function (table) {
                const rows = table.querySelectorAll('tr');
                rows.forEach(function (tr, rowIdx) {
                  const tds = tr.querySelectorAll('td');
                  if (tds.length < 8) return;
                  tds.forEach(function (td, colIdx) {
                    if (colIdx === 0) return;          // 第一列是节次
                    const html = td.innerHTML || '';
                    if (!html.trim()) return;
                    const text = td.innerText || '';
                    const nameM = text.match(/^([^\n（(]+)/);
                    const name = nameM ? nameM[1].trim() : '';
                    if (!name || name.length > 30) return;
                    const roomM = text.match(/([\u4e00-\u9fa5A-Za-z0-9\-]*(?:楼|室|馆|区)[\u4e00-\u9fa5A-Za-z0-9\-]*)/);
                    const teacherM = text.match(/([\u4e00-\u9fa5]{2,4})(?=\s|$)/g);
                    // 节次从行号推算（常见 1-2 节一行）
                    const nodeTd = tds[0].innerText || '';
                    const nums = nodeTd.match(/\d+/g) || [];
                    const startSection = nums.length ? parseInt(nums[0]) : (rowIdx * 2 + 1);
                    const endSection = nums.length > 1 ? parseInt(nums[1]) : startSection + 1;
                    out.push({ name: name, day: colIdx - 1, weeks: text,
                               teacher: teacherM ? teacherM[teacherM.length - 1] : '',
                               position: roomM ? roomM[1] : '',
                               startSection: startSection, endSection: endSection });
                  });
                });
              });
              return out;
            }

            async function runImportFlow() {
              const b = window.shiguangBridge, bp = window.shiguangBridgePromise;
              const courses = qzParse();
              if (!courses.length) {
                b.showToast('没有解析到课程，请确认已登录并停留在课表页面');
                return;
              }
              await bp.saveImportedCourses(JSON.stringify(courses));
              b.showToast('已获取 ' + courses.length + ' 门课程');
              b.notifyTaskCompletion();
            }
        """.trimIndent()

        /** URP 教务：常见 iframe 结构，尽量做通用解析 */
        val URP_SCRIPT = """
            $BRIDGE_HINT

            function urpParse() {
              const out = [];
              // URP 课表常见为 table，行列对应节次与星期
              const tables = document.querySelectorAll('table');
              tables.forEach(function (table) {
                const rows = table.querySelectorAll('tr');
                rows.forEach(function (tr, rowIdx) {
                  const tds = tr.querySelectorAll('td');
                  if (tds.length < 7) return;
                  tds.forEach(function (td, colIdx) {
                    if (colIdx === 0) return;
                    const text = (td.innerText || '').trim();
                    if (!text || text.length < 2) return;
                    const lines = text.split('\n').map(function (s) { return s.trim(); }).filter(Boolean);
                    const name = lines[0] || '';
                    if (!name || name.length > 30) return;
                    const roomM = text.match(/([\u4e00-\u9fa5A-Za-z0-9\-]*(?:楼|室|馆|区)[\u4e00-\u9fa5A-Za-z0-9\-]*)/);
                    const startSection = rowIdx + 1;
                    out.push({ name: name, day: colIdx - 1, weeks: text,
                               teacher: lines.length > 2 ? lines[2] : '',
                               position: roomM ? roomM[1] : (lines[1] || ''),
                               startSection: startSection, endSection: startSection });
                  });
                });
              });
              return out;
            }

            async function runImportFlow() {
              const b = window.shiguangBridge, bp = window.shiguangBridgePromise;
              const courses = urpParse();
              if (!courses.length) {
                b.showToast('没有解析到课程，请确认已登录并停留在课表页面');
                return;
              }
              await bp.saveImportedCourses(JSON.stringify(courses));
              b.showToast('已获取 ' + courses.length + ' 门课程');
              b.notifyTaskCompletion();
            }
        """.trimIndent()
    }
}
