package com.example.courseschedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.courseschedule.data.Course
import com.example.courseschedule.data.WeekType
import com.example.courseschedule.ui.theme.courseColorArgb

private val dayShort = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditSheet(
    course: Course?,
    totalSections: Int,
    totalWeeks: Int,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
    onDelete: (Course) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CourseEditForm(course, totalSections, totalWeeks, onSave, onDelete)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseEditForm(
    course: Course?,
    totalSections: Int,
    totalWeeks: Int,
    onSave: (Course) -> Unit,
    onDelete: (Course) -> Unit,
) {
    var name by remember { mutableStateOf(course?.name ?: "") }
    var teacher by remember { mutableStateOf(course?.teacher ?: "") }
    var location by remember { mutableStateOf(course?.location ?: "") }
    var day by remember { mutableIntStateOf(course?.dayOfWeek ?: 0) }
    var start by remember { mutableIntStateOf((course?.startSection ?: 1).coerceIn(1, totalSections)) }
    var end by remember { mutableIntStateOf((course?.endSection ?: 1).coerceIn(1, totalSections)) }
    var weekType by remember { mutableStateOf(course?.weekType ?: WeekType.ALL) }
    var customWeeks by remember { mutableStateOf((course?.customWeeks ?: emptyList()).toSet()) }
    var color by remember { mutableLongStateOf(course?.colorArgb ?: 0xFF007AFFL) }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            if (course == null) "添加课程" else "编辑课程",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("课程名称 *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = teacher,
            onValueChange = { teacher = it },
            label = { Text("老师") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("教室 / 地点") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("星期")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            dayShort.forEachIndexed { i, label ->
                FilterChip(
                    selected = day == i,
                    onClick = { day = i },
                    label = { Text("周$label") },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("节次（第 ${start}-${end} 节）")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stepper(
                label = "开始",
                value = start,
                onValueChange = {
                    start = it.coerceIn(1, end)
                },
                min = 1,
                max = totalSections,
            )
            Spacer(Modifier.width(24.dp))
            Stepper(
                label = "结束",
                value = end,
                onValueChange = {
                    end = it.coerceIn(start, totalSections)
                },
                min = start,
                max = totalSections,
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("周次")
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            weekTypeOptions.forEach { (type, label) ->
                FilterChip(
                    selected = weekType == type,
                    onClick = { weekType = type },
                    label = { Text(label) },
                )
            }
        }
        if (weekType == WeekType.CUSTOM) {
            Spacer(Modifier.height(8.dp))
            Text(
                "选择具体周次（可多选）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..totalWeeks).forEach { w ->
                    val selected = customWeeks.contains(w)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            customWeeks = if (selected) customWeeks - w else customWeeks + w
                        },
                        label = { Text("$w") },
                        shape = RoundedCornerShape(50),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("颜色")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            courseColorArgb.forEach { argb ->
                val c = Color(argb)
                val selected = color == argb
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(c)
                        .then(
                            if (selected) {
                                Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable { color = argb },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (isLight(c)) Color(0xFF1C1C1E) else Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                val newCourse = Course(
                    id = course?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name.trim(),
                    teacher = teacher.trim(),
                    location = location.trim(),
                    dayOfWeek = day,
                    startSection = start,
                    endSection = end,
                    weekType = weekType,
                    customWeeks = customWeeks.sorted(),
                    colorArgb = color,
                )
                onSave(newCourse)
            },
            enabled = name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text("保存", fontWeight = FontWeight.SemiBold)
        }

        if (course != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { onDelete(course) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("删除课程", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private val weekTypeOptions = listOf(
    WeekType.ALL to "每周",
    WeekType.ODD to "单周",
    WeekType.EVEN to "双周",
    WeekType.CUSTOM to "自定义",
)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (value > min) onValueChange(value - 1) }) {
            Icon(Icons.Filled.Remove, contentDescription = "减少")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$value",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { if (value < max) onValueChange(value + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "增加")
        }
    }
}

private fun isLight(color: Color): Boolean {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return luminance > 0.65f
}
