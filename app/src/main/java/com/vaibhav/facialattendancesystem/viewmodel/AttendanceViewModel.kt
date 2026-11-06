package com.vaibhav.facialattendancesystem.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaibhav.facialattendancesystem.data.AppDatabase
import com.vaibhav.facialattendancesystem.data.CloudSyncManager
import com.vaibhav.facialattendancesystem.data.Student
import com.vaibhav.facialattendancesystem.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for ClassroomAttendance and AttendanceHistory screens.
 * Owns roster loading, session sync, record toggle, and CSV export state.
 */
class AttendanceViewModel(
    private val db: AppDatabase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _isLoadingRoster = MutableStateFlow(true)
    val isLoadingRoster: StateFlow<Boolean> = _isLoadingRoster.asStateFlow()

    private val _enrolledStudents = MutableStateFlow<List<Student>>(emptyList())
    val enrolledStudents: StateFlow<List<Student>> = _enrolledStudents.asStateFlow()

    // Pairs of (Student, list of their FloatArray embeddings)
    private val _embeddingsData = MutableStateFlow<List<Pair<Student, List<FloatArray>>>>(emptyList())
    val embeddingsData: StateFlow<List<Pair<Student, List<FloatArray>>>> = _embeddingsData.asStateFlow()

    private val _isSyncingSessions = MutableStateFlow(false)
    val isSyncingSessions: StateFlow<Boolean> = _isSyncingSessions.asStateFlow()

    fun loadRosterAndEmbeddings(classId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoadingRoster.value = true }
            try {
                CloudSyncManager.syncClassRosterForTeacher(classId, db)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val converters = com.vaibhav.facialattendancesystem.data.VectorTypeConverters()
            val enrolledStudentIds = db.classEnrollmentDao().getApprovedStudentIds(classId)
            val students = if (enrolledStudentIds.isNotEmpty()) {
                db.studentDao().getStudentsByIds(enrolledStudentIds)
            } else {
                emptyList()
            }

            val list = mutableListOf<Pair<Student, List<FloatArray>>>()
            for (s in students) {
                val embs = db.faceEmbeddingDao().getEmbeddingsForStudent(s.studentId)
                    .filter { it.pipelineVersion >= 2 }
                    .mapNotNull { converters.byteArrayToFloatArray(it.embeddingVector) }
                if (embs.isNotEmpty()) {
                    list.add(s to embs)
                }
            }

            withContext(Dispatchers.Main) {
                _enrolledStudents.value = students
                _embeddingsData.value = list
                _isLoadingRoster.value = false
            }
        }
    }

    fun syncSessions(classId: String, onResult: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isSyncingSessions.value = true }
            val (ok, msg) = CloudSyncManager.syncClassAttendanceSessionsForTeacher(classId, db)
            withContext(Dispatchers.Main) {
                _isSyncingSessions.value = false
                onResult(ok, msg)
            }
        }
    }

    fun toggleAttendanceRecord(
        sessionId: String,
        studentId: String,
        isPresent: Boolean,
        onResult: (success: Boolean) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = CloudSyncManager.updateSingleAttendanceRecord(sessionId, studentId, isPresent)
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }
}
