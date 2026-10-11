package com.example.courseschedule.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** 根据开学日期计算今天是第几周 */
fun currentWeekOf(semesterStartDate: String, totalWeeks: Int): Int {
    return try {
        if (semesterStartDate.isBlank()) return 1
        val start = LocalDate.parse(semesterStartDate)
        val today = LocalDate.now()
        val diff = ChronoUnit.DAYS.between(start, today)
        if (diff < 0) 1 else ((diff / 7).toInt() + 1).coerceIn(1, totalWeeks)
    } catch (e: Exception) {
        1
    }
}

/** 某日期属于第几周 */
fun weekOf(date: LocalDate, semesterStartDate: String, totalWeeks: Int): Int {
    return try {
        if (semesterStartDate.isBlank()) return 1
        val start = LocalDate.parse(semesterStartDate)
        val diff = ChronoUnit.DAYS.between(start, date)
        if (diff < 0) 1 else ((diff / 7).toInt() + 1).coerceIn(1, totalWeeks)
    } catch (e: Exception) {
        1
    }
}

/** 今天星期几：0=周一 … 6=周日 */
fun todayDayOfWeek(): Int = LocalDate.now().dayOfWeek.value - 1

/** 某天、某周要上的课（按开始节次排序） */
fun coursesForDay(courses: List<Course>, dayOfWeek: Int, week: Int): List<Course> =
    courses.filter { it.dayOfWeek == dayOfWeek && it.occursInWeek(week) }
        .sortedBy { it.startSection }

/**
 * 合并后的显示块：同一天、课程名相同、老师相同、且节次相邻（或重叠）的多条课程，
 * 合并成一个连续的大块。
 */
data class CourseBlock(
    val name: String,
    val teacher: String,
    val location: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val colorArgb: Long,
    val ids: List<String>,
) {
    val primaryId: String get() = ids.first()
    fun contains(id: String?): Boolean = id != null && ids.contains(id)
}

/** 把某周的课程合并成显示块（同名同老师且相邻的课连成一块） */
fun mergeCourses(courses: List<Course>, week: Int): List<CourseBlock> {
    return courses
        .filter { it.occursInWeek(week) }
        .groupBy { Triple(it.dayOfWeek, it.name.trim(), it.teacher.trim()) }
        .flatMap { (key, list) ->
            val (day, name, teacher) = key
            val sorted = list.sortedBy { it.startSection }
            val blocks = mutableListOf<CourseBlock>()

            var segStart = sorted.first().startSection
            var segEnd = sorted.first().endSection
            var segIds = mutableListOf(sorted.first().id)
            var segColor = sorted.first().colorArgb
            var segLoc = sorted.first().location

            fun flush() {
                blocks.add(CourseBlock(name, teacher, segLoc, day, segStart, segEnd, segColor, segIds.toList()))
            }

            for (c in sorted.drop(1)) {
                if (c.startSection <= segEnd + 1) {
                    // 相邻或重叠，合并
                    segEnd = maxOf(segEnd, c.endSection)
                    segIds.add(c.id)
                    if (segLoc.isBlank()) segLoc = c.location
                } else {
                    flush()
                    segStart = c.startSection
                    segEnd = c.endSection
                    segIds = mutableListOf(c.id)
                    segColor = c.colorArgb
                    segLoc = c.location
                }
            }
            flush()
            blocks
        }
        .sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))
}

fun parseLocalTime(s: String): LocalTime? = try {
    LocalTime.parse(s.trim())
} catch (e: Exception) {
    null
}

/** 计算某周的 7 天日期（未设置开学日期则全为 null） */
fun weekDates(semesterStartDate: String, week: Int): List<LocalDate?> {
    val start = try {
        LocalDate.parse(semesterStartDate)
    } catch (e: Exception) {
        return List(7) { null }
    }
    return (0..6).map { start.plusDays((week - 1) * 7L + it) }
}

/** 判断某天是否为休息日（含调休覆盖） */
fun isRestDay(
    dayOfWeek: Int,
    date: LocalDate?,
    restDays: List<Int>,
    restOverrides: Set<String>,
    workOverrides: Set<String>,
): Boolean {
    val ds = date?.toString()
    if (ds != null && workOverrides.contains(ds)) return false
    if (ds != null && restOverrides.contains(ds)) return true
    return restDays.contains(dayOfWeek)
}

/** 课程进行状态 */
data class ScheduleNow(
    /** 正在上的课，课间为 null */
    val current: Course?,
    /** 下一节课，全天课结束为 null */
    val next: Course?,
    /** 倒计时目标时刻（下一节开始 或 当前课结束） */
    val targetTime: LocalTime?,
    /** 倒计时说明文字 */
    val statusText: String,
) {
    val hasContent: Boolean get() = current != null || next != null
}

/** 计算当前时间点的上课状态 */
fun computeScheduleNow(
    courses: List<Course>,
    sections: List<SectionTime>,
    week: Int,
    now: LocalTime = LocalTime.now(),
): ScheduleNow {
    val todays = coursesForDay(courses, todayDayOfWeek(), week)
    if (todays.isEmpty()) return ScheduleNow(null, null, null, "今天没有课")

    var current: Course? = null
    var next: Course? = null

    for (c in todays) {
        val st = sections.getOrNull(c.startSection - 1)?.start?.let(::parseLocalTime)
        val et = sections.getOrNull(c.endSection - 1)?.end?.let(::parseLocalTime)
        if (st == null || et == null) continue
        when {
            !now.isBefore(st) && now.isBefore(et) -> current = c
            now.isBefore(st) -> { next = c; break }
            else -> {}
        }
    }

    return when {
        current != null -> {
            val end = sections.getOrNull(current.endSection - 1)?.end?.let(::parseLocalTime)
            ScheduleNow(current, null, end, "第${current.startSection}节进行中 · ${current.name}")
        }
        next != null -> {
            val start = sections.getOrNull(next.startSection - 1)?.start?.let(::parseLocalTime)
            ScheduleNow(null, next, start, "下一节 ${next.name} ${sections.getOrNull(next.startSection - 1)?.start.orEmpty()}")
        }
        else -> ScheduleNow(null, null, null, "今天课程已结束")
    }
}
