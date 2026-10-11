package com.example.courseschedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.courseschedule.MainActivity
import com.example.courseschedule.R
import com.example.courseschedule.data.ScheduleRepository
import com.example.courseschedule.data.coursesForDay
import com.example.courseschedule.data.currentWeekOf
import com.example.courseschedule.data.todayDayOfWeek
import java.time.LocalDate

/** 桌面小组件：展示今日课程（在小米负一屏等任意桌面均可添加） */
class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    companion object {
        private val slotIds = intArrayOf(
            R.id.course1, R.id.course2, R.id.course3,
            R.id.course4, R.id.course5, R.id.course6,
        )

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, id: Int) {
            val repo = ScheduleRepository(context)
            val data = repo.data.value
            val today = LocalDate.now()
            val week = currentWeekOf(data.settings.semesterStartDate, data.settings.totalWeeks)
            val courses = coursesForDay(data.courses, todayDayOfWeek(), week)

            val views = RemoteViews(context.packageName, R.layout.widget_schedule)
            views.setTextViewText(R.id.widget_title, "${today.monthValue}月${today.dayOfMonth}日 · 今日课程")

            for (i in slotIds.indices) {
                if (i < courses.size) {
                    val c = courses[i]
                    val time = data.settings.sections.getOrNull(c.startSection - 1)?.start ?: ""
                    views.setViewVisibility(slotIds[i], View.VISIBLE)
                    views.setTextViewText(slotIds[i], "$time  ${c.name}")
                } else {
                    views.setViewVisibility(slotIds[i], View.GONE)
                }
            }
            if (courses.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setTextViewText(R.id.widget_empty, "今天没有课 🎉")
            } else {
                views.setViewVisibility(R.id.widget_empty, View.GONE)
            }

            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)

            appWidgetManager.updateAppWidget(id, views)
        }

        fun updateAll(context: Context) {
            val am = AppWidgetManager.getInstance(context)
            val ids = am.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))
            for (id in ids) updateWidget(context, am, id)
        }
    }
}
