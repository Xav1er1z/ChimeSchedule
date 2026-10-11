package com.example.courseschedule.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 负责把课程与设置保存为本地 JSON 文件（纯本地，不联网）。
 * 数据通过 StateFlow 暴露，UI 自动刷新。
 */
class ScheduleRepository(context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file: File = File(context.filesDir, "schedule_data.json")

    private val _data = MutableStateFlow(load())
    val data: StateFlow<ScheduleData> = _data.asStateFlow()

    private fun load(): ScheduleData {
        return try {
            if (file.exists()) {
                normalize(gson.fromJson(file.readText(), ScheduleData::class.java) ?: ScheduleData())
            } else {
                ScheduleData()
            }
        } catch (e: Exception) {
            ScheduleData()
        }
    }

    /** 兼容旧版本数据：把缺失或非法的新字段回填为默认值 */
    private fun normalize(data: ScheduleData): ScheduleData {
        val s = data.settings
        val fixed = s.copy(
            currentWeek = if (s.currentWeek <= 0) 1 else s.currentWeek,
            totalWeeks = if (s.totalWeeks <= 0) 20 else s.totalWeeks,
            themeMode = s.themeMode ?: ThemeMode.SYSTEM,
            accentColorArgb = if (s.accentColorArgb == 0L) 0xFF007AFFL else s.accentColorArgb,
            backgroundMode = s.backgroundMode ?: BackgroundMode.GRADIENT,
            backgroundColorArgb = if (s.backgroundColorArgb == 0L) 0xFFF2F2F7L else s.backgroundColorArgb,
            courseCardCornerDp = if (s.courseCardCornerDp <= 0) 10 else s.courseCardCornerDp,
            courseCardFontSp = if (s.courseCardFontSp <= 0) 13 else s.courseCardFontSp,
            courseCardOpacity = if (s.courseCardOpacity <= 0) 100 else s.courseCardOpacity,
            sections = if (s.sections.isNullOrEmpty()) ScheduleSettings.defaultSections() else s.sections,
        )
        return data.copy(settings = fixed)
    }

    private fun save(data: ScheduleData) {
        try {
            file.writeText(gson.toJson(data))
        } catch (_: Exception) {
            // 保存失败不崩溃
        }
    }

    private fun update(transform: (ScheduleData) -> ScheduleData): ScheduleData {
        val newData = transform(_data.value)
        _data.value = newData
        save(newData)
        return newData
    }

    fun addCourse(course: Course): ScheduleData =
        update { it.copy(courses = it.courses + course) }

    fun updateCourse(course: Course): ScheduleData =
        update { d -> d.copy(courses = d.courses.map { if (it.id == course.id) course else it }) }

    fun deleteCourse(id: String): ScheduleData =
        update { d -> d.copy(courses = d.courses.filterNot { it.id == id }) }

    fun replaceCourses(courses: List<Course>): ScheduleData =
        update { it.copy(courses = courses) }

    fun updateSettings(settings: ScheduleSettings): ScheduleData =
        update { it.copy(settings = settings) }

    fun exportJson(): String = gson.toJson(_data.value)

    fun importJson(json: String): Boolean {
        return try {
            val parsed = gson.fromJson(json, ScheduleData::class.java) ?: return false
            _data.value = parsed
            save(parsed)
            true
        } catch (e: Exception) {
            false
        }
    }
}
