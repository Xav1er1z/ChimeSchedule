package com.example.courseschedule.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseschedule.data.Course
import com.example.courseschedule.data.ScheduleData
import com.example.courseschedule.data.ScheduleRepository
import com.example.courseschedule.data.ScheduleSettings
import com.example.courseschedule.notification.ReminderScheduler
import com.example.courseschedule.widget.ScheduleWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ScheduleRepository(app)
    val data: StateFlow<ScheduleData> = repository.data

    private fun rescheduleReminders() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            ReminderScheduler.rescheduleAll(context, repository.data.value)
        }
        viewModelScope.launch(Dispatchers.Main) {
            ScheduleWidgetProvider.updateAll(context)
        }
    }

    fun addCourse(course: Course) {
        repository.addCourse(course)
        rescheduleReminders()
    }

    fun updateCourse(course: Course) {
        repository.updateCourse(course)
        rescheduleReminders()
    }

    fun deleteCourse(id: String) {
        repository.deleteCourse(id)
        rescheduleReminders()
    }

    fun replaceCourses(courses: List<Course>) {
        repository.replaceCourses(courses)
        rescheduleReminders()
    }

    fun updateSettings(settings: ScheduleSettings) {
        repository.updateSettings(settings)
        rescheduleReminders()
    }

    fun setCurrentWeek(week: Int) {
        updateSettings(repository.data.value.settings.copy(currentWeek = week))
    }

    fun exportJson(): String = repository.exportJson()

    fun importJson(json: String): Boolean {
        val ok = repository.importJson(json)
        if (ok) rescheduleReminders()
        return ok
    }
}
