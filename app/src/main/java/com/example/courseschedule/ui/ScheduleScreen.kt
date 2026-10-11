package com.example.courseschedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.courseschedule.data.Course
import com.example.courseschedule.data.CourseBlock
import com.example.courseschedule.data.ScheduleNow
import com.example.courseschedule.data.SectionTime
import com.example.courseschedule.data.computeScheduleNow
import com.example.courseschedule.data.isRestDay
import com.example.courseschedule.data.mergeCourses
import com.example.courseschedule.data.todayDayOfWeek
import com.example.courseschedule.data.weekDates
import com.example.courseschedule.ui.theme.frostedGlass
import com.example.courseschedule.ui.theme.pressableClickable
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

private val dayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private enum class ViewMode { WEEK, DAY }

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onOpenSettings: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val settings = data.settings

    var editingCourse by remember { mutableStateOf<Course?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(ViewMode.WEEK) }

    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalTime.now()
        }
    }
    val scheduleNow = remember(data.courses, settings, now) {
        computeScheduleNow(data.courses, settings.sections, settings.currentWeek, now)
    }
    val mergedBlocks = remember(data.courses, settings.currentWeek, settings.sections.size) {
        mergeCourses(data.courses, settings.currentWeek, settings.sections.size.coerceAtLeast(1))
    }
    val weekDays = remember(settings.semesterStartDate, settings.currentWeek) {
        weekDates(settings.semesterStartDate, settings.currentWeek)
    }
    val restFlags = remember(settings.restDays, settings.restDayOverrides, settings.workDayOverrides, weekDays) {
        (0..6).map { i ->
            isRestDay(
                i,
                weekDays[i],
                settings.restDays,
                settings.restDayOverrides.toSet(),
                settings.workDayOverrides.toSet(),
            )
        }
    }

    if (showAddSheet || editingCourse != null) {
        CourseEditSheet(
            course = editingCourse,
            totalSections = settings.sections.size,
            totalWeeks = settings.totalWeeks,
            onDismiss = {
                showAddSheet = false
                editingCourse = null
            },
            onSave = { course ->
                if (editingCourse == null) viewModel.addCourse(course)
                else viewModel.updateCourse(course)
                showAddSheet = false
                editingCourse = null
            },
            onDelete = { course ->
                viewModel.deleteCourse(course.id)
                editingCourse = null
            },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "添加课程")
                Spacer(Modifier.width(6.dp))
                Text("添加课程", fontWeight = FontWeight.Medium)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "课程表",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenImport) {
                    Icon(Icons.Outlined.FileDownload, "导入课表", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, "设置", tint = MaterialTheme.colorScheme.primary)
                }
            }

            WeekSelector(
                currentWeek = settings.currentWeek,
                totalWeeks = settings.totalWeeks,
                onWeekChange = { viewModel.setCurrentWeek(it) },
            )

            ViewModeToggle(viewMode, onToggle = { viewMode = it })

            CountdownBar(scheduleNow, now)

            if (viewMode == ViewMode.WEEK) {
                ScheduleGrid(
                    blocks = mergedBlocks,
                    restFlags = restFlags,
                    sections = settings.sections,
                    cornerDp = settings.courseCardCornerDp,
                    fontSp = settings.courseCardFontSp,
                    opacity = settings.courseCardOpacity,
                    showTime = settings.showTimeInCard,
                    showTeacher = settings.showTeacherInCard,
                    showLocation = settings.showLocationInCard,
                    currentCourseId = scheduleNow.current?.id,
                    onCourseClick = { block ->
                        data.courses.firstOrNull { it.id == block.primaryId }?.let { editingCourse = it }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                )
            } else {
                DayView(
                    blocks = mergedBlocks.filter { it.dayOfWeek == todayDayOfWeek() },
                    sections = settings.sections,
                    dayOfWeek = todayDayOfWeek(),
                    onCourseClick = { block ->
                        data.courses.firstOrNull { it.id == block.primaryId }?.let { editingCourse = it }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ViewModeToggle(mode: ViewMode, onToggle: (ViewMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TogglePill("周视图", mode == ViewMode.WEEK) { onToggle(ViewMode.WEEK) }
        TogglePill("日视图", mode == ViewMode.DAY) { onToggle(ViewMode.DAY) }
    }
}

@Composable
private fun TogglePill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CountdownBar(scheduleNow: ScheduleNow, now: LocalTime) {
    if (!scheduleNow.hasContent) return
    val target = scheduleNow.targetTime
    val countdown = target?.let { t ->
        val mins = Duration.between(now, t).toMinutes()
        if (mins < 0) null else mins
    }
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp),
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(10.dp))
            Text(
                scheduleNow.statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (countdown != null) {
                Text(
                    if (scheduleNow.current != null) "剩余 ${fmtDuration(countdown)}"
                    else "还有 ${fmtDuration(countdown)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                )
            }
        }
    }
}

private fun fmtDuration(mins: Long): String =
    if (mins < 60) "$mins 分钟" else "${mins / 60} 小时 ${mins % 60} 分"

@Composable
private fun WeekSelector(currentWeek: Int, totalWeeks: Int, onWeekChange: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { if (currentWeek > 1) onWeekChange(currentWeek - 1) }) {
            Icon(Icons.Filled.ChevronLeft, "上一周")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("第 $currentWeek 周", style = MaterialTheme.typography.titleMedium)
            Text("共 $totalWeeks 周", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { if (currentWeek < totalWeeks) onWeekChange(currentWeek + 1) }) {
            Icon(Icons.Filled.ChevronRight, "下一周")
        }
    }
}

@Composable
private fun ScheduleGrid(
    blocks: List<CourseBlock>,
    restFlags: List<Boolean>,
    sections: List<SectionTime>,
    cornerDp: Int,
    fontSp: Int,
    opacity: Int,
    showTime: Boolean,
    showTeacher: Boolean,
    showLocation: Boolean,
    currentCourseId: String?,
    onCourseClick: (CourseBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sections.isEmpty()) return

    val density = LocalDensity.current
    val leftWidth = 56.dp
    val headerHeight = 44.dp
    val rowHeight = 58.dp
    val leftWidthPx = with(density) { leftWidth.toPx() }
    val headerHeightPx = with(density) { headerHeight.toPx() }
    val rowHeightPx = with(density) { rowHeight.toPx() }
    val todayIndex = (LocalDate.now().dayOfWeek.value - 1).coerceIn(0, 6)

    BoxWithConstraints(modifier) {
        val containerWidth = maxWidth
        val cellWidthPx = ((with(density) { containerWidth.toPx() } - leftWidthPx) / 7).toInt()
        val cellWidthDp = with(density) { cellWidthPx.toDp() }
        val totalHeight = headerHeight + rowHeight * sections.size

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.width(containerWidth).height(totalHeight)) {
                // 网格底板：半透明表面（大尺寸滚动区域内不用背景模糊，避免性能与稳定性问题）
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(16.dp),
                        ),
                )

                // 休息日红色底
                for (i in 0..6) {
                    if (restFlags[i]) {
                        Box(
                            Modifier
                                .offset { IntOffset((leftWidthPx + i * cellWidthPx).toInt(), 0) }
                                .size(cellWidthDp, totalHeight)
                                .background(Color(0x0DFF3B30)),
                        )
                    }
                }
                // 今天高亮（休息日则不重复加蓝底）
                if (!restFlags[todayIndex]) {
                    Box(
                        Modifier
                            .offset { IntOffset((leftWidthPx + todayIndex * cellWidthPx).toInt(), 0) }
                            .size(cellWidthDp, totalHeight)
                            .background(Color(0x14007AFF)),
                    )
                }

                dayLabels.forEachIndexed { i, label ->
                    val isRest = restFlags[i]
                    val isToday = i == todayIndex
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            isRest -> Color(0xFFFF3B30)
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .offset { IntOffset((leftWidthPx + i * cellWidthPx).toInt(), 0) }
                            .size(cellWidthDp, headerHeight)
                            .padding(top = 14.dp),
                        textAlign = TextAlign.Center,
                    )
                }

                sections.forEachIndexed { i, section ->
                    Column(
                        Modifier
                            .offset { IntOffset(0, (headerHeightPx + i * rowHeightPx).toInt()) }
                            .size(leftWidth, rowHeight),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("${section.section}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(section.start, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                for (i in 0..sections.size) {
                    Box(
                        Modifier
                            .offset { IntOffset(leftWidthPx.toInt(), (headerHeightPx + i * rowHeightPx).toInt()) }
                            .width(cellWidthDp * 7)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                for (i in 0..6) {
                    Box(
                        Modifier
                            .offset { IntOffset((leftWidthPx + i * cellWidthPx).toInt(), headerHeightPx.toInt()) }
                            .width(1.dp)
                            .height(rowHeight * sections.size)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }

                val corner = cornerDp.dp
                val nameStyle = MaterialTheme.typography.bodySmall.copy(
                    fontSize = fontSp.sp,
                    lineHeight = (fontSp + 4).sp,
                )
                blocks.forEach { block ->
                    val span = (block.endSection - block.startSection + 1).coerceAtLeast(1)
                    val baseColor = Color(block.colorArgb)
                    val textColor = onColor(baseColor)
                    val fillColor = baseColor.copy(alpha = opacity / 100f)
                    val isCurrent = block.contains(currentCourseId)
                    val blockPadding = 2.dp
                    val blockPaddingPx = with(density) { blockPadding.toPx() }
                    val timeText = sections.getOrNull(block.startSection - 1)?.start ?: ""

                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    (leftWidthPx + block.dayOfWeek * cellWidthPx + blockPaddingPx).toInt(),
                                    (headerHeightPx + (block.startSection - 1) * rowHeightPx + blockPaddingPx).toInt(),
                                )
                            }
                            .size(cellWidthDp - blockPadding * 2, rowHeight * span - blockPadding * 2)
                            .clip(RoundedCornerShape(corner))
                            .background(fillColor)
                            .then(
                                if (isCurrent) {
                                    Modifier.border(2.dp, Color.White, RoundedCornerShape(corner))
                                } else {
                                    Modifier
                                },
                            )
                            .pressableClickable { onCourseClick(block) }
                            .padding(6.dp),
                    ) {
                        Column {
                            if (showTime && timeText.isNotBlank()) {
                                Text(
                                    timeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                block.name,
                                style = nameStyle,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                color = textColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (showTeacher && block.teacher.isNotBlank()) {
                                Text(
                                    block.teacher,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (showLocation && block.location.isNotBlank()) {
                                Text(
                                    block.location,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayView(
    blocks: List<CourseBlock>,
    sections: List<SectionTime>,
    dayOfWeek: Int,
    onCourseClick: (CourseBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(
            "${dayLabels[dayOfWeek]}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        if (blocks.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(18.dp),
                    ),
            ) {
                Text(
                    "今天没有课 🎉",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            blocks.forEach { block ->
                CourseRow(block, sections, onClick = { onCourseClick(block) })
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun CourseRow(block: CourseBlock, sections: List<SectionTime>, onClick: () -> Unit) {
    val start = sections.getOrNull(block.startSection - 1)?.start ?: ""
    val end = sections.getOrNull(block.endSection - 1)?.end ?: ""
    val color = Color(block.colorArgb)
    val onBg = MaterialTheme.colorScheme.onBackground

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .pressableClickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(5.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(block.name, style = MaterialTheme.typography.titleMedium, color = onBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            val sub = listOfNotNull(
                block.teacher.ifBlank { null },
                block.location.ifBlank { null },
            ).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("$start", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = onBg)
            Text("第${block.startSection}-${block.endSection}节 · $end", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun onColor(background: Color): Color {
    val luminance = background.luminance()
    return if (luminance > 0.65f) Color(0xFF1C1C1E) else Color.White
}
