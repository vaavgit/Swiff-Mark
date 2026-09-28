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

            CloudSyncManager.initSessionManager(sessionManager, db, applicationContext)

            if (session != null) {
                if (session.userRole == "STUDENT") {
                    // 1. Ensure student profile & embeddings are uploaded to cloud if created offline
                    val localStudent = db.studentDao().getStudentById(session.userId)
                    if (localStudent != null) {
                        val converters = com.vaibhav.facialattendancesystem.data.VectorTypeConverters()
                        val localEmbs = db.faceEmbeddingDao().getEmbeddingsForStudent(session.userId)
                            .mapNotNull { converters.byteArrayToFloatArray(it.embeddingVector) }
                        if (localEmbs.isNotEmpty()) {
                            CloudSyncManager.uploadStudentProfile(localStudent, localEmbs)
                        } else {
                            CloudSyncManager.ensureCloudUserProfile(
                                userId = session.userId,
                                email = localStudent.email,
                                fullName = localStudent.fullName,
                                role = "STUDENT",
                                rollOrSubject = localStudent.rollNumber.toString(),
                                sectionOrDept = localStudent.classSection ?: ""
                            )
                        }
                    }

                    // 2. Sync enrolled classes, teacher profiles/avatars, and attendance records
                    CloudSyncManager.syncStudentClassesAndEnrollments(session.userId, db, sessionManager)
                    CloudSyncManager.syncStudentAttendance(session.userId, db, applicationContext, notify = true)
                } else if (session.userRole == "TEACHER") {
                    // 1. Sync pending offline sessions
                    val unsyncedSessions = db.attendanceDao().getUnsyncedSessions()
                    for (s in unsyncedSessions) {
                        val records = db.attendanceDao().getRecordsForSession(s.sessionId)
                        val success = CloudSyncManager.uploadAttendanceSession(s, records, session.userId)
                        if (success) {
                            db.attendanceDao().markSessionSynced(s.sessionId)
                        }
                    }

                    // 2. Sync all teacher classes, rosters, student avatars, and session history
                    CloudSyncManager.syncTeacherAllClassesAndRosters(session.userId, db, sessionManager)
                }
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
