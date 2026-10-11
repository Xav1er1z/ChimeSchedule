package com.example.courseschedule.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.courseschedule.R
import com.example.courseschedule.data.ScheduleRepository
import com.example.courseschedule.data.coursesForDay
import com.example.courseschedule.data.weekOf
import java.time.LocalDate

/** 次日课程提醒：每天晚上定时推送明天的课程安排 */
class NextDayReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repo = ScheduleRepository(context)
        val data = repo.data.value
        val tomorrow = LocalDate.now().plusDays(1)
        val week = weekOf(tomorrow, data.settings.semesterStartDate, data.settings.totalWeeks)
        val courses = coursesForDay(data.courses, tomorrow.dayOfWeek.value - 1, week)

        val channelId = "next_day_reminder"
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "次日课程提醒", NotificationManager.IMPORTANCE_DEFAULT),
        )

        val text = if (courses.isEmpty()) "明天没有课 🎉"
        else courses.joinToString("  ·  ") { it.name }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("明天课程（${tomorrow.monthValue}月${tomorrow.dayOfMonth}日）")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(9001, notification)
        } catch (_: SecurityException) {
        }

        // 重新调度下一次
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        ReminderScheduler.scheduleNextDayReminder(context, alarmManager, data)
    }
}
