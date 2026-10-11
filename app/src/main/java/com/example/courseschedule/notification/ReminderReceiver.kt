package com.example.courseschedule.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.courseschedule.R

/** 收到闹钟广播后弹出上课提醒通知 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("course_name") ?: "课程"
        val location = intent.getStringExtra("location") ?: ""
        val sectionTime = intent.getStringExtra("section_time") ?: ""
        val sectionNo = intent.getIntExtra("section_no", 0)

        val channelId = "course_reminder"
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "上课提醒", NotificationManager.IMPORTANCE_HIGH),
        )

        val text = buildString {
            if (sectionTime.isNotBlank()) {
                append("第${sectionNo}节 $sectionTime")
            }
            if (location.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(location)
            }
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText(text.ifBlank { "准备上课" })
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(name.hashCode(), notification)
        } catch (_: SecurityException) {
            // 未授予通知权限时静默忽略
        }
    }
}
