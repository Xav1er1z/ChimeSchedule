package com.example.courseschedule.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.courseschedule.data.BackgroundMode
import com.example.courseschedule.data.ScheduleSettings
import com.example.courseschedule.data.SectionTime
import com.example.courseschedule.data.ThemeMode
import com.example.courseschedule.ui.theme.courseColorArgb
import java.io.File

private val backgroundColors = listOf(
    0xFFF2F2F7L, 0xFFFFFFFFL, 0xFFEAF2FFL, 0xFFE8F8EEL, 0xFFFFF3E0L,
    0xFFFDEAECL, 0xFFF3E8FFL, 0xFF000000L, 0xFF1C1C1EL, 0xFF16233AL,
)

@Composable
fun SettingsScreen(
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val settings = data.settings

    var currentWeek by remember { mutableIntStateOf(settings.currentWeek) }
    var totalWeeks by remember { mutableIntStateOf(settings.totalWeeks) }
    var semesterStart by remember { mutableStateOf(settings.semesterStartDate) }
    var sections by remember { mutableStateOf(settings.sections) }
    var reminderEnabled by remember { mutableStateOf(settings.reminderEnabled) }
    var reminderMinutes by remember { mutableIntStateOf(settings.reminderMinutesBefore) }
    var nextDayEnabled by remember { mutableStateOf(settings.nextDayReminderEnabled) }
    var nextDayTime by remember { mutableStateOf(settings.nextDayReminderTime) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var accentArgb by remember { mutableLongStateOf(settings.accentColorArgb) }
    var backgroundMode by remember { mutableStateOf(settings.backgroundMode) }
    var backgroundArgb by remember { mutableLongStateOf(settings.backgroundColorArgb) }
    var backgroundImagePath by remember { mutableStateOf(settings.backgroundImagePath) }
    var cornerDp by remember { mutableIntStateOf(settings.courseCardCornerDp) }
    var fontSp by remember { mutableIntStateOf(settings.courseCardFontSp) }
    var opacity by remember { mutableIntStateOf(settings.courseCardOpacity) }
    var showTime by remember { mutableStateOf(settings.showTimeInCard) }
    var showTeacher by remember { mutableStateOf(settings.showTeacherInCard) }
    var showLocation by remember { mutableStateOf(settings.showLocationInCard) }
    var restDays by remember { mutableStateOf(settings.restDays.toSet()) }
    var restOverrides by remember { mutableStateOf(settings.restDayOverrides) }
    var workOverrides by remember { mutableStateOf(settings.workDayOverrides) }
    var overrideDate by remember { mutableStateOf("") }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 无论是否授权，开关状态已更新 */ }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(viewModel.exportJson().toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "已导出备份", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                val ok = viewModel.importJson(json)
                Toast.makeText(context, if (ok) "已恢复备份" else "恢复失败，文件格式不对", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val dest = File(context.filesDir, "background_${System.currentTimeMillis()}.jpg")
                    dest.outputStream().use { out -> input.copyTo(out) }
                    backgroundImagePath = dest.name
                }
                Toast.makeText(context, "背景图片已设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun save() {
        val newSettings = ScheduleSettings(
            currentWeek = currentWeek.coerceIn(1, totalWeeks),
            totalWeeks = totalWeeks,
            semesterStartDate = semesterStart.trim(),
            reminderEnabled = reminderEnabled,
            reminderMinutesBefore = reminderMinutes.coerceIn(0, 120),
            nextDayReminderEnabled = nextDayEnabled,
            nextDayReminderTime = nextDayTime,
            themeMode = themeMode,
            accentColorArgb = accentArgb,
            backgroundMode = backgroundMode,
            backgroundColorArgb = backgroundArgb,
            backgroundImagePath = backgroundImagePath,
            courseCardCornerDp = cornerDp,
            courseCardFontSp = fontSp,
            courseCardOpacity = opacity,
            showTimeInCard = showTime,
            showTeacherInCard = showTeacher,
            showLocationInCard = showLocation,
            restDays = restDays.sorted(),
            restDayOverrides = restOverrides,
            workDayOverrides = workOverrides,
            sections = sections,
        )
        viewModel.updateSettings(newSettings)
        onBack()
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
                Text("设置", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { save() }) {
                    Icon(Icons.Filled.Check, "保存", tint = MaterialTheme.colorScheme.primary)
                }
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

            SectionTitle("外观")
            Spacer(Modifier.height(12.dp))
            Text("主题模式", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.values().forEach { m ->
                    FilterChip(selected = themeMode == m, onClick = { themeMode = m }, label = { Text(themeModeLabel(m)) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("主题色", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(10.dp))
            ColorRow(courseColorArgb, accentArgb) { accentArgb = it }

            Spacer(Modifier.height(24.dp))
            SectionTitle("背景")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = backgroundMode == BackgroundMode.GRADIENT, onClick = { backgroundMode = BackgroundMode.GRADIENT }, label = { Text("渐变") })
                FilterChip(selected = backgroundMode == BackgroundMode.COLOR, onClick = { backgroundMode = BackgroundMode.COLOR }, label = { Text("纯色") })
                FilterChip(selected = backgroundMode == BackgroundMode.IMAGE, onClick = { backgroundMode = BackgroundMode.IMAGE }, label = { Text("图片") })
            }
            Spacer(Modifier.height(12.dp))
            when (backgroundMode) {
                BackgroundMode.GRADIENT -> Text("渐变背景会跟随主题色", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BackgroundMode.COLOR -> ColorRow(backgroundColors, backgroundArgb) { backgroundArgb = it }
                BackgroundMode.IMAGE -> {
                    OutlinedButton(onClick = {
                        pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, shape = RoundedCornerShape(12.dp)) {
                        Text(if (backgroundImagePath.isBlank()) "从相册选择图片" else "更换图片")
                    }
                    if (backgroundImagePath.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("已设置背景图片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("课程格子外观")
            Spacer(Modifier.height(12.dp))
            SliderSetting("圆角大小", cornerDp, 0, 24, "dp") { cornerDp = it }
            SliderSetting("字体大小", fontSp, 8, 20, "sp") { fontSp = it }
            SliderSetting("透明度", opacity, 50, 100, "%") { opacity = it }
            Spacer(Modifier.height(8.dp))
            SwitchRow("显示上课时间", "在格子顶部显示开始时间", showTime) { showTime = it }
            SwitchRow("显示老师", "在格子中显示授课老师", showTeacher) { showTeacher = it }
            SwitchRow("显示地点/教室", "在格子中显示上课地点", showLocation) { showLocation = it }

            Spacer(Modifier.height(24.dp))
            SectionTitle("学期设置")
            Spacer(Modifier.height(12.dp))
            StepperSetting("当前周", currentWeek, 1, totalWeeks) { currentWeek = it }
            Spacer(Modifier.height(8.dp))
            StepperSetting("总周数", totalWeeks, 1, 30) { totalWeeks = it }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = semesterStart,
                onValueChange = { semesterStart = it },
                label = { Text("学期第一天（yyyy-MM-dd）") },
                placeholder = { Text("例如 2026-09-01") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle("休息日与调休")
            Spacer(Modifier.height(12.dp))
            Text("休息日（红色，默认周六周日）", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, d ->
                    FilterChip(
                        selected = restDays.contains(i),
                        onClick = {
                            restDays = if (restDays.contains(i)) restDays - i else restDays + i
                        },
                        label = { Text("周$d") },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = overrideDate,
                onValueChange = { overrideDate = it },
                label = { Text("调休日期（yyyy-MM-dd）") },
                placeholder = { Text("例如 2026-10-01") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    val d = overrideDate.trim()
                    if (d.isNotBlank() && d !in restOverrides) restOverrides = restOverrides + d
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("设为放假") }
                OutlinedButton(onClick = {
                    val d = overrideDate.trim()
                    if (d.isNotBlank() && d !in workOverrides) workOverrides = workOverrides + d
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("设为补班") }
            }
            if (restOverrides.isNotEmpty() || workOverrides.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                (restOverrides.map { it to "放假" } + workOverrides.map { it to "补班" }).forEach { (d, t) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$d · $t", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            restOverrides = restOverrides - d
                            workOverrides = workOverrides - d
                        }) {
                            Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            ExpandableSection(
                title = "节次时间",
                summary = "共 ${sections.size} 节 · ${sections.firstOrNull()?.start.orEmpty()}–${sections.lastOrNull()?.end.orEmpty()}",
            ) {
                sections.forEachIndexed { i, section ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${section.section}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(28.dp))
                        OutlinedTextField(
                            value = section.start,
                            onValueChange = { v -> sections = sections.mapIndexed { j, s -> if (j == i) s.copy(start = v) else s } },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            label = { Text("开始") },
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = section.end,
                            onValueChange = { v -> sections = sections.mapIndexed { j, s -> if (j == i) s.copy(end = v) else s } },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            label = { Text("结束") },
                        )
                        IconButton(onClick = {
                            sections = sections.filterIndexed { j, _ -> j != i }.mapIndexed { idx, s -> s.copy(section = idx + 1) }
                        }) {
                            Icon(Icons.Filled.Delete, "删除节次", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(onClick = {
                    val next = sections.size + 1
                    sections = sections + SectionTime(next, "08:00", "08:45")
                }, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("添加节次")
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("上课提醒")
            Spacer(Modifier.height(8.dp))
            SwitchRow("课前提醒", "每节课开始前通知你", reminderEnabled) { checked ->
                reminderEnabled = checked
                if (checked && Build.VERSION.SDK_INT >= 33) {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (reminderEnabled) {
                Spacer(Modifier.height(8.dp))
                StepperSetting("提前提醒（分钟）", reminderMinutes, 0, 120) { reminderMinutes = it }
            }
            Spacer(Modifier.height(12.dp))
            SwitchRow("次日课程提醒", "每晚定时推送明天的课程", nextDayEnabled) { nextDayEnabled = it }
            if (nextDayEnabled) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = nextDayTime,
                    onValueChange = { nextDayTime = it },
                    label = { Text("提醒时间（HH:mm）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("数据备份")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("课程表备份.json") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("导出备份")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("恢复备份")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ColorRow(colors: List<Long>, selected: Long, onSelect: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { argb ->
            val c = Color(argb)
            val isSel = selected == argb
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(c)
                    .then(
                        if (isSel) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    .clickable { onSelect(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSel) {
                    Icon(Icons.Filled.Check, null, tint = if (c.luminance() > 0.65f) Color(0xFF1C1C1E) else Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    summary: String,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            if (!expanded) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            content()
        }
    }
}

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun SliderSetting(label: String, value: Int, min: Int, max: Int, unit: String, onValueChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text("$value $unit", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
        )
    }
}

@Composable
private fun StepperSetting(label: String, value: Int, min: Int, max: Int, onValueChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        IconButton(onClick = { if (value > min) onValueChange(value - 1) }) { Icon(Icons.Filled.Remove, "减少") }
        Text("$value", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        IconButton(onClick = { if (value < max) onValueChange(value + 1) }) { Icon(Icons.Filled.Add, "增加") }
    }
}
