package com.example.courseschedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import com.example.courseschedule.adapter.AdapterRepository
import com.example.courseschedule.adapter.SchoolAdapter
import kotlinx.coroutines.launch

@Composable
fun EduImportScreen(
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
    onOpenAdapter: (SchoolAdapter) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AdapterRepository(context) }

    var adapters by remember { mutableStateOf(repo.all()) }
    var query by remember { mutableStateOf("") }
    var updating by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }
    var editingTarget by remember { mutableStateOf<SchoolAdapter?>(null) }
    var editingScript by remember { mutableStateOf("") }

    val filtered = remember(adapters, query) {
        val q = query.trim()
        if (q.isBlank()) adapters
        else adapters.filter {
            it.name.contains(q, true) || it.id.contains(q, true) ||
                it.remark.contains(q, true) || it.initial.equals(q, true)
        }
    }

    val general = filtered.filter { it.isGeneralTool || it.script.isNotBlank() && it.category != "SCHOOL" }
    val schools = filtered.filter { it.category == "SCHOOL" }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(editingTarget?.displayName ?: "编辑脚本") },
            text = {
                Column {
                    Text(
                        "脚本在内置浏览器中运行，可操作网页 DOM。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editingScript,
                        onValueChange = { editingScript = it },
                        label = { Text("JavaScript") },
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = editingTarget
                    val saved = SchoolAdapter(
                        id = if (target == null || target.id == "custom") "custom" else "${target.id}_custom",
                        name = (target?.name ?: "自定义") + "（已修改）",
                        initial = target?.initial.orEmpty(),
                        script = editingScript,
                        importUrl = target?.importUrl.orEmpty(),
                        category = "GENERAL_TOOL",
                        remark = "用户自定义脚本",
                    )
                    repo.saveCustom(saved)
                    adapters = repo.all()
                    showEditor = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) { Text("取消") }
            },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
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
                    Text(
                        "教务系统导入",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("搜索学校名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "选择你学校的教务系统，在内置浏览器里登录后点「执行导入」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            updating = true
                            statusText = "正在更新学校列表…"
                            scope.launch {
                                val r = repo.fetchRemote()
                                adapters = repo.all()
                                updating = false
                                statusText = r.fold(
                                    onSuccess = { "已更新 $it 个适配脚本" },
                                    onFailure = { "更新失败：${it.message ?: "网络问题"}" },
                                )
                            }
                        },
                        enabled = !updating,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text(if (updating) "更新中…" else "更新学校列表") }

                    OutlinedButton(
                        onClick = {
                            editingTarget = adapters.firstOrNull { it.id == "custom" }
                            editingScript = editingTarget?.script.orEmpty()
                            showEditor = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text("写自定义脚本") }
                }
            }

            if (statusText.isNotBlank()) {
                item {
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (general.isNotEmpty()) {
                item {
                    SectionTitle("通用教务系统")
                }
                items(general, key = { "g_${it.id}" }) { adapter ->
                    AdapterRow(adapter) { onOpenAdapter(adapter) }
                }
            }

            if (schools.isNotEmpty()) {
                item {
                    SectionTitle("学校（已适配）")
                }
                items(schools, key = { "s_${it.id}" }) { adapter ->
                    AdapterRow(adapter) { onOpenAdapter(adapter) }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Text(
                        "没有找到「$query」。可以点「更新学校列表」获取更多，或用「粘贴文本导入」。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun AdapterRow(adapter: SchoolAdapter, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
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
}
