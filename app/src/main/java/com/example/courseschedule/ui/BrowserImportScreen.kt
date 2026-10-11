package com.example.courseschedule.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.courseschedule.adapter.SchoolAdapter
import com.example.courseschedule.data.Course
import com.example.courseschedule.data.WeekType
import com.example.courseschedule.ui.theme.courseColorArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 内置浏览器：用于打开教务系统网页、登录，然后运行适配脚本抓取课表。
 * 与拾光课程表的机制一致。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserImportScreen(
    adapter: SchoolAdapter,
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(adapter.importUrl) }
    var currentUrl by remember { mutableStateOf(adapter.importUrl) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var statusText by remember {
        mutableStateOf(
            if (adapter.importUrl.isBlank()) "请在上面填写你学校的教务系统网址"
            else "请先登录教务系统，并进入课表页面",
        )
    }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var importedCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }

    // 供 UI 更新的日志回调
    val appendLog: (String) -> Unit = { msg ->
        logLines = (logLines + msg).takeLast(6)
        statusText = msg
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            adapter.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                // 地址栏
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        label = { Text("教务系统网址") },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            webView?.loadUrl(normalizeUrl(url))
                            statusText = "正在打开…"
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("打开") }
                }
                if (loading) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        bottomBar = {
            Column(Modifier.padding(12.dp)) {
                if (importedCourses.isNotEmpty()) {
                    Text(
                        "已抓取 ${importedCourses.size} 门课程",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.replaceCourses(viewModel.data.value.courses + importedCourses)
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("保存到课表", fontWeight = FontWeight.SemiBold) }
                } else {
                    Button(
                        onClick = {
                            val wv = webView
                            if (wv == null) {
                                statusText = "浏览器未就绪"
                            } else {
                                statusText = "正在执行适配脚本…"
                                wv.evaluateJavascript("""
                                    (function(){
                                      try {
                                        if (typeof runImportFlow === 'function') { runImportFlow(); }
                                        else if (typeof getSchedule === 'function') { getSchedule({}); }
                                        else {
                                          if (window.shiguangBridge) {
                                            window.shiguangBridge.showToast('该脚本缺少 runImportFlow 入口');
                                          }
                                        }
                                      } catch (e) {
                                        if (window.shiguangBridge) {
                                          window.shiguangBridge.showToast('脚本执行失败：' + e.message);
                                        }
                                      }
                                    })();
                                """.trimIndent(), null)
                            }
                        },
                        enabled = adapter.script.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            if (adapter.script.isBlank()) "该适配器暂无脚本" else "执行导入",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (logLines.isNotEmpty()) logLines.last()
                        else "提示：先登录并打开课表页面，再点「执行导入」",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (logLines.isNotEmpty()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White),
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                currentUrl = url.orEmpty()
                                this@apply.post {
                                    statusText = "页面已加载，可登录后点「执行导入」"
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                                loading = newProgress < 100
                            }
                        }

                        addJavascriptInterface(Bridge(scope, appendLog) { courses ->
                            importedCourses = courses
                            appendLog("已抓取 ${courses.size} 门课程，点下方按钮保存")
                        }, "androidBridge")

                        // 注入桥接对象 + jQuery（若页面没有）
                        injectBridges(this)

                        webView = this
                        // 没有预置网址时先加载一个空白页，让用户填地址
                        if (url.isBlank()) {
                            loadDataWithBaseURL(
                                null,
                                "<html><body style='font-family:sans-serif;padding:24px;color:#666'>" +
                                    "<h3>请在上方填写教务系统网址</h3>" +
                                    "<p>例如：<br>jwgl.xxx.edu.cn<br>http://jw.xxx.edu.cn/</p>" +
                                    "<p>填好后点「打开」，登录并进入课表页面，再点底部「执行导入」。</p>" +
                                    "</body></html>",
                                "text/html",
                                "UTF-8",
                                null,
                            )
                            loading = false
                        } else {
                            loadUrl(normalizeUrl(url))
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 注入桥接对象（兼容拾光脚本接口） */
private fun injectBridges(webView: WebView) {
    // 把 androidBridge 包装成 shiguangBridge / shiguangBridgePromise
    val bridgeJs = """
        (function() {
          if (window.shiguangBridge) return;
          window.shiguangBridge = {
            showToast: function(msg) { try { androidBridge.toast(String(msg)); } catch(e){} },
            notifyTaskCompletion: function() { try { androidBridge.done(); } catch(e){} }
          };
          window.shiguangBridgePromise = {
            showAlert: function(title, msg, btn) {
              try { androidBridge.toast(title + ': ' + msg); } catch(e){}
              return Promise.resolve(true);
            },
            saveImportedCourses: function(json) {
              try { androidBridge.saveCourses(String(json)); } catch(e){}
              return Promise.resolve(true);
            }
          };
          window.androidBridgePromise = window.shiguangBridgePromise;
        })();
    """.trimIndent()
    webView.evaluateJavascript(bridgeJs, null)
}

private fun normalizeUrl(input: String): String {
    val t = input.trim()
    return when {
        t.isBlank() -> "about:blank"
        t.startsWith("http://") || t.startsWith("https://") -> t
        else -> "https://$t"
    }
}

/** JS 桥接实现 */
private class Bridge(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val onLog: (String) -> Unit,
    private val onCourses: (List<Course>) -> Unit,
) {
    @JavascriptInterface
    fun toast(msg: String) {
        println("AdapterJS: $msg")
        scope.launch(Dispatchers.Main) { onLog(msg) }
    }

    @JavascriptInterface
    fun done() {
        println("AdapterJS: 任务完成")
    }

    @JavascriptInterface
    fun saveCourses(json: String) {
        val courses = parseCourses(json)
        scope.launch(Dispatchers.Main) { onCourses(courses) }
    }

    private fun parseCourses(json: String): List<Course> {
        return try {
            val trimmed = json.trim()
            val array = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("courses") ?: JSONArray()
                }
                else -> JSONArray()
            }
            val out = mutableListOf<Course>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                if (name.isBlank()) continue
                val day = parseDay(o.opt("day"))
                val start = parseSection(o.opt("startSection"), o.opt("startNode"))
                val end = parseSection(o.opt("endSection"), o.opt("endNode")).coerceAtLeast(start)
                out.add(
                    Course(
                        name = name,
                        teacher = o.optString("teacher"),
                        location = o.optString("position").ifBlank { o.optString("room") },
                        dayOfWeek = day.coerceIn(0, 6),
                        startSection = start.coerceIn(1, 20),
                        endSection = end.coerceIn(1, 20),
                        weekType = parseWeekType(o.opt("weeks")),
                        customWeeks = parseWeeks(o.opt("weeks")),
                        colorArgb = courseColorArgb[out.size % courseColorArgb.size],
                    ),
                )
            }
            out
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private fun parseDay(value: Any?): Int = when (value) {
        is Number -> value.toInt() - if (value.toInt() >= 1) 0 else 0
        is String -> {
            val s = value.trim()
            s.toIntOrNull()?.let { return it - 1 }
            val names = listOf("一", "二", "三", "四", "五", "六", "日", "天")
            val idx = names.indexOfFirst { s.contains(it) }
            if (idx >= 0) idx % 7 else 0
        }
        else -> 0
    }

    private fun parseSection(a: Any?, b: Any?): Int {
        (a as? Number)?.let { return it.toInt() }
        (b as? Number)?.let { return it.toInt() }
        (a as? String)?.toIntOrNull()?.let { return it }
        return 1
    }

    private fun parseWeekType(value: Any?): WeekType {
        val s = value?.toString().orEmpty()
        return when {
            s.contains("单") -> WeekType.ODD
            s.contains("双") -> WeekType.EVEN
            else -> WeekType.ALL
        }
    }

    private fun parseWeeks(value: Any?): List<Int> {
        return try {
            when (value) {
                is JSONArray -> (0 until value.length()).mapNotNull { value.optInt(it) }.filter { it in 1..30 }
                is List<*> -> value.mapNotNull { (it as? Number)?.toInt() }.filter { it in 1..30 }
                is String -> {
                    val weeks = LinkedHashSet<Int>()
                    Regex("""(\d+)\s*[-~到]\s*(\d+)""").findAll(value).forEach { m ->
                        val a = m.groupValues[1].toIntOrNull() ?: return@forEach
                        val b = m.groupValues[2].toIntOrNull() ?: return@forEach
                        for (w in minOf(a, b)..maxOf(a, b)) if (w in 1..30) weeks.add(w)
                    }
                    if (weeks.isEmpty()) {
                        Regex("""\d+""").findAll(value).forEach { m ->
                            m.value.toIntOrNull()?.let { if (it in 1..30) weeks.add(it) }
                        }
                    }
                    weeks.sorted()
                }
                else -> emptyList()
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }
}
