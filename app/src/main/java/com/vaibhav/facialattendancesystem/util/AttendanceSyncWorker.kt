package com.vaibhav.facialattendancesystem.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vaibhav.facialattendancesystem.data.AppDatabase
import com.vaibhav.facialattendancesystem.data.CloudSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker to automatically sync attendance updates and publish notifications.
 */
class AttendanceSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val sessionManager = SessionManager(applicationContext)
            val session = sessionManager.getSession()

            if (session != null) {
                if (session.userRole == "STUDENT") {
                    // Check cloud for newly published attendance and alert student
                    CloudSyncManager.syncStudentClassesAndEnrollments(session.userId, db, sessionManager)
                    CloudSyncManager.syncStudentAttendance(session.userId, db, applicationContext, notify = true)
                } else if (session.userRole == "TEACHER") {
                    // Sync pending offline sessions
                    val unsyncedSessions = db.attendanceDao().getUnsyncedSessions()
                    for (s in unsyncedSessions) {
                        val records = db.attendanceDao().getRecordsForSession(s.sessionId)
                        val success = CloudSyncManager.uploadAttendanceSession(s, records, session.userId)
                        if (success) {
                            db.attendanceDao().markSessionSynced(s.sessionId)
                        }
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
