package com.vaibhav.facialattendancesystem.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaibhav.facialattendancesystem.data.AppDatabase
import com.vaibhav.facialattendancesystem.data.Clazz
import com.vaibhav.facialattendancesystem.data.CloudSyncManager
import com.vaibhav.facialattendancesystem.data.Student
import com.vaibhav.facialattendancesystem.data.SupabaseAuthManager
import com.vaibhav.facialattendancesystem.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the ClassDetails screen.
 * Owns roster loading, approval/rejection, student removal, and class deletion.
 */
class ClassDetailsViewModel(
    private val db: AppDatabase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _clazz = MutableStateFlow<Clazz?>(null)
    val clazz: StateFlow<Clazz?> = _clazz.asStateFlow()

    private val _enrolledStudents = MutableStateFlow<List<Student>>(emptyList())
    val enrolledStudents: StateFlow<List<Student>> = _enrolledStudents.asStateFlow()

    private val _pendingStudents = MutableStateFlow<List<Student>>(emptyList())
    val pendingStudents: StateFlow<List<Student>> = _pendingStudents.asStateFlow()

    private val _isSyncingRoster = MutableStateFlow(false)
    val isSyncingRoster: StateFlow<Boolean> = _isSyncingRoster.asStateFlow()

    private val _isLoadingData = MutableStateFlow(true)
    val isLoadingData: StateFlow<Boolean> = _isLoadingData.asStateFlow()

    fun loadAndSync(classId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                refreshLocal(classId)
                withContext(Dispatchers.Main) { _isSyncingRoster.value = true }
                CloudSyncManager.syncClassRosterForTeacher(classId, db)
                refreshLocal(classId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    _isSyncingRoster.value = false
                    _isLoadingData.value = false
                }
            }
        }
    }

    fun refreshRoster(classId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isSyncingRoster.value = true }
            CloudSyncManager.syncClassRosterForTeacher(classId, db)
            refreshLocal(classId)
            withContext(Dispatchers.Main) { _isSyncingRoster.value = false }
        }
    }

    fun approveStudent(
        classId: String,
        studentId: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, msg) = CloudSyncManager.approveEnrollment(classId, studentId, db)
            refreshLocal(classId)
            withContext(Dispatchers.Main) { onResult(ok, msg) }
        }
    }

    fun rejectStudent(
        classId: String,
        studentId: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, msg) = CloudSyncManager.rejectEnrollment(classId, studentId, db)
            withContext(Dispatchers.Main) { onResult(ok, msg) }
        }
    }

    fun removeStudent(
        classId: String,
        studentId: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, msg) = CloudSyncManager.leaveClass(classId, studentId, db, sessionManager)
            refreshLocal(classId)
            withContext(Dispatchers.Main) { onResult(ok, msg) }
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

    fun resetStudentPassword(
        studentEmail: String,
        studentName: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, msg) = SupabaseAuthManager.resetPasswordForEmail(studentEmail)
            withContext(Dispatchers.Main) { onResult(ok, msg) }
        }
    }

    fun refreshPendingStudents(classId: String, pendingEnrollmentIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val students = if (pendingEnrollmentIds.isNotEmpty()) {
                db.studentDao().getStudentsByIds(pendingEnrollmentIds)
            } else {
                emptyList()
            }
            withContext(Dispatchers.Main) { _pendingStudents.value = students }
        }
    }

    private suspend fun refreshLocal(classId: String) {
        val c = db.clazzDao().getClassById(classId)
        val enrolledIds = db.classEnrollmentDao().getApprovedStudentIds(classId)
        val students = if (enrolledIds.isNotEmpty()) db.studentDao().getStudentsByIds(enrolledIds) else emptyList()
        withContext(Dispatchers.Main) {
            _clazz.value = c
            _enrolledStudents.value = students
        }
    }
}
