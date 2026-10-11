package com.example.courseschedule.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.courseschedule.import.CsvImporter
import com.example.courseschedule.import.HtmlImporter
import com.example.courseschedule.import.ImportResult
import com.example.courseschedule.import.TextImporter
import com.example.courseschedule.import.WakeUpImporter
import com.example.courseschedule.import.XlsxImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ImportType { CSV, HTML, XLSX }

@Composable
fun ImportScreen(
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
    onOpenEdu: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<ImportResult?>(null) }
    var importing by remember { mutableStateOf(false) }

    var pendingType by remember { mutableStateOf(ImportType.CSV) }
    var showWakeUpDialog by remember { mutableStateOf(false) }
    var wakeUpCode by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importing = true
            val type = pendingType
            scope.launch {
                val r = withContext(Dispatchers.IO) { parseFile(context, uri, type) }
                result = r
                importing = false
            }
        }
    }

    if (showWakeUpDialog) {
        AlertDialog(
            onDismissRequest = { showWakeUpDialog = false },
            title = { Text("WakeUp 口令导入") },
            text = {
                Column {
                    Text(
                        "在 WakeUp 课程表中点「分享课表」复制口令，粘贴到下面。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = wakeUpCode,
                        onValueChange = { wakeUpCode = it },
                        label = { Text("分享口令") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        val clip = getClipboardText(context)
                        if (clip.isNotBlank()) wakeUpCode = clip
                    }) { Text("从剪贴板粘贴") }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = wakeUpCode.isNotBlank() && !importing,
                    onClick = {
                        showWakeUpDialog = false
                        importing = true
                        val code = wakeUpCode
                        scope.launch {
                            result = try {
                                withContext(Dispatchers.IO) { WakeUpImporter.import(code) }
                            } catch (e: Throwable) {
                                ImportResult(warnings = listOf("导入失败：${e.message ?: e.javaClass.simpleName}"))
                            }
                            importing = false
                        }
                    },
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showWakeUpDialog = false }) { Text("取消") }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    "导入课表",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "选择课表文件，自动解析为课程。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            TutorialCard()

            Spacer(Modifier.height(20.dp))
            ImportButton("教务系统导入", "内置浏览器登录教务，一键抓取课表") {
                onOpenEdu()
            }
            Spacer(Modifier.height(12.dp))
            ImportButton("粘贴文本导入", "从剪贴板粘贴课表文字，一键识别（推荐）") {
                val clip = getClipboardText(context)
                if (clip.isBlank()) {
                    result = ImportResult(warnings = listOf("剪贴板是空的，请先复制课表文字。"))
                } else {
                    importing = true
                    scope.launch {
                        result = try {
                            withContext(Dispatchers.Default) { TextImporter.parse(clip) }
                        } catch (e: Throwable) {
                            ImportResult(warnings = listOf("解析失败：${e.message ?: e.javaClass.simpleName}"))
                        }
                        importing = false
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ImportButton("WakeUp 口令导入", "粘贴 WakeUp 课程表的分享口令") {
                showWakeUpDialog = true
            }
            Spacer(Modifier.height(12.dp))
            ImportButton("导入 CSV 文件", "每行一门课，最稳定") {
                pendingType = ImportType.CSV
                launcher.launch("text/*")
            }
            Spacer(Modifier.height(12.dp))
            ImportButton("导入 HTML 课表", "教务系统导出的网页课表") {
                pendingType = ImportType.HTML
                launcher.launch("text/html")
            }
            Spacer(Modifier.height(12.dp))
            ImportButton("导入 Excel (.xlsx)", "学校下发的 Excel 课表") {
                pendingType = ImportType.XLSX
                launcher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "CSV 格式说明（每行一门课）：\n" +
                    "课程名,老师,教室,星期(1-7),开始节,结束节,周次类型,自定义周次\n\n" +
                    "示例：\n高等数学,张三,教学楼A101,1,1,2,每周,\n" +
                    "大学英语,李四,教学楼B202,3,3,4,单周,\n" +
                    "体育,王五,操场,5,7,8,自定义,1-8,10-16\n\n" +
                    "· 星期：1=周一 … 7=周日\n" +
                    "· 周次类型：每周 / 单周 / 双周 / 自定义\n" +
                    "· 自定义周次：如 1-8,10-16",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (importing) {
                Spacer(Modifier.height(16.dp))
                Text("正在解析…", color = MaterialTheme.colorScheme.primary)
            }

            result?.let { r ->
                Spacer(Modifier.height(20.dp))
                ImportResultCard(
                    result = r,
                    onMerge = { viewModel.replaceCourses(viewModel.data.value.courses + r.courses); onBack() },
                    onReplace = { viewModel.replaceCourses(r.courses); onBack() },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ImportButton(title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = androidx.compose.ui.Alignment.Start,
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImportResultCard(
    result: ImportResult,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            "解析到 ${result.courses.size} 门课程",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (result.warnings.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            result.warnings.take(3).forEach { w ->
                Text(
                    "⚠ $w",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        result.courses.take(10).forEach { c ->
            Text(
                "${dayLabel(c.dayOfWeek)} 第${c.startSection}-${c.endSection}节 · ${c.name}" +
                    (if (c.location.isNotBlank()) " · ${c.location}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (result.courses.size > 10) {
            Text(
                "… 等共 ${result.courses.size} 门",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onMerge,
                enabled = result.courses.isNotEmpty(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("合并到现有课表")
            }
            OutlinedButton(
                onClick = onReplace,
                enabled = result.courses.isNotEmpty(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("覆盖现有课表")
            }
        }
    }
}

@Composable
private fun TutorialCard() {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .clickable { expanded = !expanded }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "教务系统导入教程",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text(
                "1. 登录学校教务系统网页\n" +
                    "2. 找到「我的课表 / 课表查询」页面\n" +
                    "3. 点「打印 / 导出」按钮\n" +
                    "4. 优先导出 Excel/CSV；若只有网页课表，用浏览器「另存为」保存成 .html\n" +
                    "5. 回到本 App，点下方对应按钮选择文件导入",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun dayLabel(day: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrElse(day) { "周${day + 1}" }

private fun readText(context: Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""

/** 读取剪贴板文本 */
private fun getClipboardText(context: Context): String {
    return try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    } catch (e: Exception) {
        ""
    }
}

private fun readBytes(context: Context, uri: Uri): ByteArray =
    context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)

private fun parseFile(context: Context, uri: Uri, type: ImportType): ImportResult {
    return when (type) {
        ImportType.CSV -> CsvImporter.parse(readText(context, uri))
        ImportType.HTML -> HtmlImporter.parse(readText(context, uri))
        ImportType.XLSX -> XlsxImporter.parse(readBytes(context, uri))
    }
}
