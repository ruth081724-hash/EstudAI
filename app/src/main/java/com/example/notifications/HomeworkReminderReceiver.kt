package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class HomeworkReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(HomeworkReminderScheduler.EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(HomeworkReminderScheduler.EXTRA_TASK_TITLE) ?: "Tarea pendiente"
        val subject = intent.getStringExtra(HomeworkReminderScheduler.EXTRA_TASK_SUBJECT) ?: "Estudio"
        HomeworkReminderScheduler.showNotificationNow(
            context = context,
            taskId = taskId,
            title = title,
            subject = subject
        )
    }
}
