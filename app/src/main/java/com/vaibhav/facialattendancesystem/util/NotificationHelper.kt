package com.vaibhav.facialattendancesystem.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vaibhav.facialattendancesystem.MainActivity
import com.vaibhav.facialattendancesystem.R

object NotificationHelper {
    private const val CHANNEL_ID = "attendance_alerts"
    private const val CHANNEL_NAME = "Attendance Alerts"
    private const val CHANNEL_DESC = "Notifications for class attendance status"

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showAttendanceNotification(
        context: Context,
        title: String,
        message: String,
        classId: String? = null,
        className: String? = null,
        notificationId: Int = (System.currentTimeMillis() % 100000).toInt()
    ) {
        try {
            initChannel(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (!classId.isNullOrBlank()) {
                    putExtra("target_class_id", classId)
                    putExtra("target_class_name", className ?: "Class")
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_attendance)
                .setColor(0xFF00B4D8.toInt())
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun schedulePhoto2Reminder(
        context: Context,
        classId: String,
        className: String,
        delayMinutes: Long = 40
    ) {
        try {
            val workData = androidx.work.workDataOf(
                "class_id" to classId,
                "class_name" to className
            )
            val reminderWork = androidx.work.OneTimeWorkRequestBuilder<Photo2ReminderWorker>()
                .setInitialDelay(delayMinutes, java.util.concurrent.TimeUnit.MINUTES)
                .setInputData(workData)
                .addTag("photo2_reminder_$classId")
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "photo2_reminder_$classId",
                androidx.work.ExistingWorkPolicy.REPLACE,
                reminderWork
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelPhoto2Reminder(context: Context, classId: String) {
        try {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("photo2_reminder_$classId")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class Photo2ReminderWorker(
    appContext: Context,
    workerParams: androidx.work.WorkerParameters
) : androidx.work.CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val classId = inputData.getString("class_id") ?: return Result.success()
        val className = inputData.getString("class_name") ?: "Class"
        NotificationHelper.showAttendanceNotification(
            context = applicationContext,
            title = "Photo 2 Reminder 📸",
            message = "Time to capture Photo 2 to finalize attendance for $className.",
            classId = classId,
            className = className
        )
        return Result.success()
    }
}
