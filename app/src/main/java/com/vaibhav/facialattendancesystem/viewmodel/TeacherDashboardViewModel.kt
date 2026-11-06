package com.vaibhav.facialattendancesystem.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaibhav.facialattendancesystem.data.AppDatabase
import com.vaibhav.facialattendancesystem.data.CloudSyncManager
import com.vaibhav.facialattendancesystem.data.Clazz
import com.vaibhav.facialattendancesystem.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the TeacherDashboard screen.
 * Owns class creation, deletion, sync, and upload logic.
 */
class TeacherDashboardViewModel(
    private val db: AppDatabase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun startInitialSync(teacherId: String) {
        if (teacherId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            CloudSyncManager.syncTeacherAllClassesAndRosters(teacherId, db, sessionManager)
            _isSyncing.value = false
        }
    }

    fun syncOnResume(teacherId: String) {
        if (teacherId.isBlank() || _isSyncing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            CloudSyncManager.syncTeacherAllClassesAndRosters(teacherId, db, sessionManager)
        }
    }

    fun refreshCloud(
        teacherId: String,
        onResult: (synced: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            val pendingSessions = db.attendanceDao().getUnsyncedSessions()
            for (session in pendingSessions) {
                val records = db.attendanceDao().getRecordsForSession(session.sessionId)
                val success = CloudSyncManager.uploadAttendanceSession(session, records, teacherId)
                if (success) {
                    db.attendanceDao().markSessionSynced(session.sessionId)
                }
            }
            val (synced, msg) = CloudSyncManager.syncTeacherAllClassesAndRosters(teacherId, db, sessionManager)
            _isSyncing.value = false
            withContext(Dispatchers.Main) { onResult(synced, msg) }
        }
    }

    fun createClass(
        teacherId: String,
        className: String,
        subject: String,
        semester: Int,
        section: String,
        classCode: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val newClass = Clazz(
                teacherId = teacherId,
                className = className,
                subject = subject,
                semester = semester,
                section = section,
                classCode = classCode
            )
            db.clazzDao().insertClass(newClass)
            CloudSyncManager.uploadNewClass(newClass)
            withContext(Dispatchers.Main) { onResult(true, "Class created: $className ($classCode)") }
        }
    }

    fun deleteClass(
        classId: String,
        teacherId: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, msg) = CloudSyncManager.deleteClass(classId, teacherId, db, sessionManager)
            withContext(Dispatchers.Main) { onResult(ok, msg) }
        }
    }
}
