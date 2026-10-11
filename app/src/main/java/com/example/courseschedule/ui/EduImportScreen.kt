package com.example.courseschedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.courseschedule.adapter.AdapterEngine
import com.example.courseschedule.adapter.AdapterRepository
import com.example.courseschedule.adapter.AdapterResult
import com.example.courseschedule.adapter.SchoolAdapter
import com.example.courseschedule.data.SectionTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EduImportScreen(
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AdapterRepository(context) }
    val engine = remember { AdapterEngine(context) }

    var adapters by remember { mutableStateOf(repo.all()) }
    var selected by remember { mutableStateOf<SchoolAdapter?>(null) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AdapterResult?>(null) }
    var statusText by remember { mutableStateOf("") }
    var showScriptEditor by remember { mutableStateOf(false) }
    var editingScript by remember { mutableStateOf("") }
    var repoUrl by remember { mutableStateOf(AdapterRepository.DEFAULT_REPO_URL) }
    var showRepoDialog by remember { mutableStateOf(false) }

    fun runImport() {
        val adapter = selected ?: return
        running = true
        result = null
        statusText = "正在运行适配脚本…"
        scope.launch {
            val r = withContext(Dispatchers.Default) {
                engine.run(adapter, username.trim(), password)
            }
            result = r
            running = false
            statusText = if (r.isSuccess) "成功获取 ${r.courses.size} 门课程" else (r.error ?: "导入失败")
        }
    }

    if (showScriptEditor) {
        AlertDialog(
            onDismissRequest = { showScriptEditor = false },
            title = { Text("编辑适配脚本") },
            text = {
                Column {
                    Text(
                        "按脚本 API 说明编写（见示例脚本注释）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editingScript,
                        onValueChange = { editingScript = it },
                        label = { Text("JavaScript") },
                        minLines = 8,
                        maxLines = 16,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val adapter = (selected ?: repo.all().first()).copy(
                        id = if (selected?.id == "CUSTOM" || selected == null) "CUSTOM" else selected!!.id + "_custom",
                        name = (selected?.name ?: "自定义") + "（已修改）",
                        script = editingScript,
                    )
                    repo.saveCustom(adapter)
                    adapters = repo.all()
                    selected = adapter
                    showScriptEditor = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showScriptEditor = false }) { Text("取消") }
            },
        )
    }

    if (showRepoDialog) {
        AlertDialog(
            onDismissRequest = { showRepoDialog = false },
            title = { Text("从网络更新适配器") },
            text = {
                Column {
                    Text(
                        "填入适配脚本仓库的 JSON 地址，格式为数组：[{id,name,script}]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = repoUrl,
                        onValueChange = { repoUrl = it },
                        label = { Text("仓库地址") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRepoDialog = false
                    running = true
                    statusText = "正在拉取适配器…"
                    scope.launch {
                        val r = repo.fetchRemote(repoUrl.trim())
                        adapters = repo.all()
                        running = false
                        statusText = r.fold(
                            onSuccess = { "已更新 $it 个适配器" },
                            onFailure = { "更新失败：${it.message}" },
                        )
                    }
                }) { Text("更新") }
            },
            dismissButton = {
                TextButton(onClick = { showRepoDialog = false }) { Text("取消") }
            },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.primary)
                }
                Text("教务导入", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
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
                "选择你学校的适配脚本，输入教务系统账号密码即可导入。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showRepoDialog = true }, shape = RoundedCornerShape(12.dp)) {
                    Text("更新适配器")
                }
                OutlinedButton(
                    onClick = {
                        editingScript = selected?.script ?: AdapterRepository.DEMO_SCRIPT
                        showScriptEditor = true
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("编辑脚本")
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("选择学校", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            adapters.forEach { adapter ->
                val isSel = selected?.id == adapter.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        )
                        .border(
                            1.dp,
                            if (isSel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { selected = adapter }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            adapter.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (adapter.remark.isNotBlank()) {
                            Text(
                                adapter.remark,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (isSel) {
                        Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (selected != null) {
                Spacer(Modifier.height(20.dp))
                Text("教务账号", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("学号 / 用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "账号密码仅在本机使用，用于登录你学校的教务系统，不会上传到其它服务器。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { runImport() },
                    enabled = !running && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(if (running) "导入中…" else "开始导入", fontWeight = FontWeight.SemiBold)
                }
            }

            if (statusText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (result?.isSuccess == true) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }

            result?.let { r ->
                if (r.courses.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "识别到 ${r.courses.size} 门课程：",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(6.dp))
                    r.courses.take(12).forEach { c ->
                        Text(
                            "周${c.dayOfWeek + 1} 第${c.startSection}-${c.endSection}节 · ${c.name}" +
                                (if (c.location.isNotBlank()) " · ${c.location}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (r.courses.size > 12) {
                        Text("… 等共 ${r.courses.size} 门", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                if (r.timeTable.isNotEmpty()) {
                                    val sections = r.timeTable.map { Triple(it.first, it.second, it.third) }
                                        .sortedBy { it.first }
                                        .map { SectionTime(it.first, it.second, it.third) }
                                    val newSettings = viewModel.data.value.settings.copy(
                                        sections = sections,
                                        semesterStartDate = r.semesterStart.ifBlank {
                                            viewModel.data.value.settings.semesterStartDate
                                        },
                                    )
                                    viewModel.updateSettings(newSettings)
                                } else if (r.semesterStart.isNotBlank()) {
                                    val newSettings = viewModel.data.value.settings.copy(
                                        semesterStartDate = r.semesterStart,
                                    )
                                    viewModel.updateSettings(newSettings)
                                }
                                viewModel.replaceCourses(viewModel.data.value.courses + r.courses)
                                onBack()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("合并到课表") }
                        OutlinedButton(
                            onClick = {
                                viewModel.replaceCourses(r.courses)
                                onBack()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("覆盖课表") }
                    }
                }

                if (r.logs.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("运行日志", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(6.dp))
                    SelectionContainer {
                        Text(
                            r.logs.takeLast(40).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
