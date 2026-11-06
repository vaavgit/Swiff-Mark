package com.vaibhav.facialattendancesystem.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaibhav.facialattendancesystem.data.AppDatabase
import com.vaibhav.facialattendancesystem.data.CloudSyncManager
import com.vaibhav.facialattendancesystem.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the StudentDashboard screen.
 * Owns syncing state so the composable stays thin.
 */
class StudentDashboardViewModel(
    private val db: AppDatabase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun joinClass(
        code: String,
        studentId: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val (success, message) = CloudSyncManager.joinClassByCode(code, studentId, db, sessionManager)
            if (success) {
                CloudSyncManager.syncStudentClassesAndEnrollments(studentId, db, sessionManager)
                CloudSyncManager.syncStudentAttendance(studentId, db, null, notify = true)
            }
            withContext(Dispatchers.Main) { onResult(success, message) }
        }
    }

    fun refreshCloud(
        studentId: String,
        onResult: (synced: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            val (cSynced, _) = CloudSyncManager.syncStudentClassesAndEnrollments(studentId, db, sessionManager)
            val (synced, msg) = CloudSyncManager.syncStudentAttendance(studentId, db, null, notify = true)
            _isSyncing.value = false
            withContext(Dispatchers.Main) { onResult(synced || cSynced, msg) }
        }
    }

    /**
     * Runs a continuous sync loop for the student dashboard.
     * First sync fires immediately (notify = false to avoid stale alerts on first load).
     * Subsequent syncs run every 8 seconds with notify = true.
     * The loop is cancelled automatically when the ViewModel is cleared (screen leaves composition).
     */
    fun startSyncLoop(studentId: String) {
        if (studentId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            CloudSyncManager.syncStudentClassesAndEnrollments(studentId, db, sessionManager)
            CloudSyncManager.syncStudentAttendance(studentId, db, null, notify = false)
            _isSyncing.value = false
            while (true) {
                kotlinx.coroutines.delay(8_000L)
                if (!_isSyncing.value) {
                    CloudSyncManager.syncStudentClassesAndEnrollments(studentId, db, sessionManager)
                    CloudSyncManager.syncStudentAttendance(studentId, db, null, notify = true)
                }
            }
        }
    }

    fun syncOnResume(studentId: String) {
        if (studentId.isBlank() || _isSyncing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            CloudSyncManager.syncStudentClassesAndEnrollments(studentId, db, sessionManager)
            CloudSyncManager.syncStudentAttendance(studentId, db, null, notify = true)
        }
    }
}
