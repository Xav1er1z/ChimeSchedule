package com.example.courseschedule.data

import java.util.UUID

/** 周次类型 */
enum class WeekType {
    /** 每周都上 */
    ALL,
    /** 单周 */
    ODD,
    /** 双周 */
    EVEN,
    /** 自定义周次 */
    CUSTOM
}

/**
 * 一门课程。
 * @param dayOfWeek 星期几：0=周一，1=周二，…，6=周日
 * @param startSection 起始节次（从 1 开始）
 * @param endSection 结束节次（含）
 * @param colorArgb 课程颜色，ARGB 格式（如 0xFF007AFF）
 */
data class Course(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int = 0,
    val startSection: Int = 1,
    val endSection: Int = 1,
    val weekType: WeekType = WeekType.ALL,
    val customWeeks: List<Int> = emptyList(),
    val colorArgb: Long = 0xFF007AFFL,
) {
    /** 判断这门课在第 [week] 周是否上课 */
    fun occursInWeek(week: Int): Boolean = when (weekType) {
        WeekType.ALL -> true
        WeekType.ODD -> week % 2 == 1
        WeekType.EVEN -> week % 2 == 0
        WeekType.CUSTOM -> customWeeks.contains(week)
    }
}

/** 一节课的时间（第几节、几点到几点） */
data class SectionTime(
    val section: Int,
    val start: String,
    val end: String,
)

/** 主题模式 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 背景模式 */
enum class BackgroundMode { GRADIENT, COLOR, IMAGE }

/** 全局设置 */
data class ScheduleSettings(
    val currentWeek: Int = 1,
    val totalWeeks: Int = 20,
    /** 学期第一天，格式 yyyy-MM-dd，用于自动计算当前周 */
    val semesterStartDate: String = "",
    val reminderEnabled: Boolean = false,
    val reminderMinutesBefore: Int = 15,
    /** 次日课程提醒 */
    val nextDayReminderEnabled: Boolean = false,
    val nextDayReminderTime: String = "21:00",
    /** 外观 */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColorArgb: Long = 0xFF007AFFL,
    /** 背景 */
    val backgroundMode: BackgroundMode = BackgroundMode.GRADIENT,
    val backgroundColorArgb: Long = 0xFFF2F2F7L,
    val backgroundImagePath: String = "",
    /** 课程格子外观 */
    val courseCardCornerDp: Int = 10,
    val courseCardFontSp: Int = 13,
    val courseCardOpacity: Int = 100,
    val showTimeInCard: Boolean = true,
    val showTeacherInCard: Boolean = false,
    val showLocationInCard: Boolean = true,
    /** 休息日星期（0=周一…6=周日），默认周六周日 */
    val restDays: List<Int> = listOf(5, 6),
    /** 调休放假日期（yyyy-MM-dd，强制休息） */
    val restDayOverrides: List<String> = emptyList(),
    /** 调休补班日期（yyyy-MM-dd，强制上班） */
    val workDayOverrides: List<String> = emptyList(),
    val sections: List<SectionTime> = defaultSections(),
) {
    companion object {
        /** 一套常见的高校作息表（可在「设置」里修改） */
        fun defaultSections(): List<SectionTime> {
            val times = listOf(
                "08:00" to "08:45",
                "08:55" to "09:40",
                "10:00" to "10:45",
                "10:55" to "11:40",
                "14:00" to "14:45",
                "14:55" to "15:40",
                "16:00" to "16:45",
                "16:55" to "17:40",
                "19:00" to "19:45",
                "19:55" to "20:40",
                "20:50" to "21:35",
                "21:45" to "22:30",
            )
            return times.mapIndexed { i, (s, e) -> SectionTime(i + 1, s, e) }
        }
    }
}

/** 一次持久化的全部数据 */
data class ScheduleData(
    val courses: List<Course> = emptyList(),
    val settings: ScheduleSettings = ScheduleSettings(),
)
