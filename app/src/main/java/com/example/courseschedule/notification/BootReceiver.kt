package com.example.courseschedule.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.courseschedule.data.ScheduleRepository

/** 开机后重新挂载所有上课提醒 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val repository = ScheduleRepository(context)
        ReminderScheduler.rescheduleAll(context, repository.data.value)
    }
}
