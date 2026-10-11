package com.example.courseschedule.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.courseschedule.data.ScheduleData
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 课前提醒调度：用 AlarmManager 在每节课开始前 [reminderMinutesBefore] 分钟触发通知。
 * 已调度的闹钟通过 SharedPreferences 记录 requestCode，便于重新调度前统一取消。
 */
object ReminderScheduler {

    private const val PREFS = "reminder_scheduler"
    private const val KEY_CODES = "scheduled_request_codes"
    private const val NEXT_DAY_CODE = 9001

    fun rescheduleAll(context: Context, data: ScheduleData) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAll(context, alarmManager)
        scheduleNextDayReminder(context, alarmManager, data)

        if (!data.settings.reminderEnabled) return

        val sections = data.settings.sections
        val anchor = parseAnchor(data.settings.semesterStartDate) ?: return
        val minutesBefore = data.settings.reminderMinutesBefore.coerceAtLeast(0)

        val scheduled = mutableSetOf<String>()
        val now = LocalDateTime.now()

        for (course in data.courses) {
            val section = sections.getOrNull(course.startSection - 1) ?: continue
            val startTime = parseTime(section.start) ?: continue

            for (week in 1..data.settings.totalWeeks) {
                if (!course.occursInWeek(week)) continue

                val date = anchor.plusDays(((week - 1) * 7L) + course.dayOfWeek)
                val alarmDateTime = date.atTime(startTime).minusMinutes(minutesBefore.toLong())
                if (!alarmDateTime.isAfter(now)) continue

                val triggerAtMillis = alarmDateTime
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    putExtra("course_name", course.name)
                    putExtra("location", course.location)
                    putExtra("section_time", section.start)
                    putExtra("section_no", course.startSection)
                }
                val requestCode = requestCodeFor(course.id, week)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent,
                    )
                } catch (_: SecurityException) {
                    // 无精确闹钟权限时退化为普通闹钟
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent,
                    )
                }
                scheduled.add(requestCode.toString())
            }
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_CODES, scheduled).apply()
    }

    private fun cancelAll(context: Context, alarmManager: AlarmManager) {
        val codes = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_CODES, emptySet()) ?: emptySet()

        for (code in codes) {
            val requestCode = code.toIntOrNull() ?: continue
            val intent = Intent(context, ReminderReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            pi?.let { alarmManager.cancel(it) }
        }
    }

    private fun requestCodeFor(courseId: String, week: Int): Int =
        (courseId.hashCode() * 31 + week).let { if (it == Int.MIN_VALUE) 0 else it }

    private fun parseAnchor(dateStr: String): LocalDate? {
        return try {
            if (dateStr.isNotBlank()) {
                LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            } else {
                // 未设置开学日期时，以本周一为锚点
                LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTime(time: String): LocalTime? {
        return try {
            LocalTime.parse(time.trim())
        } catch (_: Exception) {
            null
        }
    }

    fun scheduleNextDayReminder(context: Context, alarmManager: AlarmManager, data: ScheduleData) {
        cancelNextDayReminder(context, alarmManager)
        if (!data.settings.nextDayReminderEnabled) return
        val time = parseTime(data.settings.nextDayReminderTime) ?: return
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAtMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(context, NextDayReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            NEXT_DAY_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun cancelNextDayReminder(context: Context, alarmManager: AlarmManager) {
        val intent = Intent(context, NextDayReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            NEXT_DAY_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pi?.let { alarmManager.cancel(it) }
    }
}
