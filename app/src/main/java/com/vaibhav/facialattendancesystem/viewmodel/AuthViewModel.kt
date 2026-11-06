package com.vaibhav.facialattendancesystem.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaibhav.facialattendancesystem.data.AppDatabase
import com.vaibhav.facialattendancesystem.data.AuthResult
import com.vaibhav.facialattendancesystem.data.CloudSyncManager
import com.vaibhav.facialattendancesystem.data.Student
import com.vaibhav.facialattendancesystem.data.SupabaseAuthManager
import com.vaibhav.facialattendancesystem.data.Teacher
import com.vaibhav.facialattendancesystem.data.User
import com.vaibhav.facialattendancesystem.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for authentication screens: Login, SignUp, ForgotPassword, ResetPassword.
 * Extracted from Navigation.kt to separate business logic from UI/navigation.
 */
class AuthViewModel(
    private val db: AppDatabase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    fun clearError() {
        _authError.value = null
    }

    /**
     * Authenticates the user against Supabase Auth and sets up the local Room session.
     * Calls [onNavigateTeacher] or [onNavigateStudent] on the main thread when login succeeds.
     */
    fun login(
        email: String,
        password: String,
        isTeacher: Boolean,
        saveLogin: Boolean,
        onNavigateTeacher: (teacherId: String, name: String) -> Unit,
        onNavigateStudentDashboard: (studentId: String) -> Unit,
        onNavigateStudentEnrollment: (studentId: String, name: String) -> Unit,
        onBanner: (message: String, isError: Boolean) -> Unit
    ) {
        _isLoading.value = true
        _authError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val authResult = SupabaseAuthManager.signInWithPassword(email, password)
            when (authResult) {
                is AuthResult.Success -> {
                    val roleUpper = authResult.role.uppercase()
                    if (isTeacher && roleUpper != "TEACHER") {
                        withContext(Dispatchers.Main) {
                            _isLoading.value = false
                            _authError.value = "This account is a Student account. Please select Student Portal."
                        }
                        return@launch
                    }
                    if (!isTeacher && roleUpper == "TEACHER") {
                        withContext(Dispatchers.Main) {
                            _isLoading.value = false
                            _authError.value = "This account is a Teacher account. Please select Teacher Portal."
                        }
                        return@launch
                    }

                    sessionManager.saveLoginCredentials(email, isTeacher, saveLogin)
                    CloudSyncManager.setAuthToken(authResult.accessToken)

                    var localUser = db.userDao().getUserByEmail(email)
                    if (localUser == null) {
                        localUser = User(
                            userId = authResult.userId,
                            email = authResult.email,
                            passwordHash = "",
                            userType = roleUpper,
                            name = authResult.fullName
                        )
                        db.userDao().insertUser(localUser)
                    }

                    if (isTeacher) {
                        var teacher = db.teacherDao().getTeacherByUserId(localUser.userId)
                        if (teacher == null) {
                            teacher = Teacher(
                                teacherId = localUser.userId,
                                userId = localUser.userId,
                                subject = authResult.rollOrSubject.ifBlank { "Subject" },
                                department = authResult.sectionOrDept.ifBlank { "Dept" }
                            )
                            db.teacherDao().insertTeacher(teacher)
                        }
                        sessionManager.saveSession(
                            teacher.teacherId, localUser.name, "TEACHER",
                            localUser.email, authResult.accessToken, authResult.refreshToken
                        )
                        viewModelScope.launch(Dispatchers.IO) {
                            CloudSyncManager.ensureCloudUserProfile(
                                userId = teacher.teacherId,
                                email = localUser.email,
                                fullName = localUser.name,
                                role = "TEACHER",
                                rollOrSubject = teacher.subject,
                                sectionOrDept = teacher.department ?: ""
                            )
                            CloudSyncManager.syncTeacherAllClassesAndRosters(teacher.teacherId, db, sessionManager)
                        }
                        withContext(Dispatchers.Main) {
                            _isLoading.value = false
                            onBanner("Welcome back, ${localUser.name}!", false)
                            onNavigateTeacher(teacher.teacherId, localUser.name)
                        }
                    } else {
                        var student = db.studentDao().getStudentByUserId(localUser.userId)
                        if (student == null) {
                            student = Student(
                                studentId = localUser.userId,
                                userId = localUser.userId,
                                rollNumber = authResult.rollOrSubject.toIntOrNull() ?: 0,
                                fullName = authResult.fullName,
                                email = authResult.email,
                                classSection = authResult.sectionOrDept.ifBlank { null }
                            )
                            db.studentDao().insertStudent(student)
                        }

                        val hasLocalEmbeddings = db.faceEmbeddingDao().getEmbeddingsForStudent(student.studentId).isNotEmpty()
                        var isFaceEnrolled = student.enrollmentStatus == 1 || hasLocalEmbeddings
                        if (!isFaceEnrolled) {
                            val cloudEmbeddingsFound = CloudSyncManager.syncStudentFaceEmbeddings(student.studentId, db)
                            if (cloudEmbeddingsFound) {
                                isFaceEnrolled = true
                                val updated = student.copy(enrollmentStatus = 1)
                                db.studentDao().insertStudent(updated)
                                student = updated
                            }
                        }

                        sessionManager.saveSession(
                            student.studentId, student.fullName, "STUDENT",
                            localUser.email, authResult.accessToken, authResult.refreshToken
                        )

                        viewModelScope.launch(Dispatchers.IO) {
                            CloudSyncManager.ensureCloudUserProfile(
                                userId = student.studentId,
                                email = localUser.email,
                                fullName = student.fullName,
                                role = "STUDENT",
                                rollOrSubject = student.rollNumber.toString(),
                                sectionOrDept = student.classSection ?: ""
                            )
                            CloudSyncManager.syncStudentClassesAndEnrollments(student.studentId, db, sessionManager)
                            CloudSyncManager.syncStudentAttendance(student.studentId, db, null, notify = false)
                        }

                        val finalStudent = student
                        val finalEnrolled = isFaceEnrolled
                        withContext(Dispatchers.Main) {
                            _isLoading.value = false
                            onBanner("Welcome back, ${finalStudent.fullName}!", false)
                            if (finalEnrolled) {
                                onNavigateStudentDashboard(finalStudent.studentId)
                            } else {
                                onNavigateStudentEnrollment(finalStudent.studentId, finalStudent.fullName)
                            }
                        }
                    }
                }
                is AuthResult.EmailNotConfirmed -> {
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        _authError.value = authResult.message
                    }
                }
                is AuthResult.Error -> {
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        _authError.value = authResult.message
                    }
                }
                else -> {
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                    }
                }
            }
        }
    }

    fun signUpStudent(
        name: String,
        rollNo: Int,
        email: String,
        password: String,
        section: String,
        onNavigateToLogin: () -> Unit,
        onNavigateToEnrollment: (studentId: String, name: String) -> Unit,
        onBanner: (message: String, isError: Boolean) -> Unit
    ) {
        _isLoading.value = true
        _authError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val authResult = SupabaseAuthManager.signUp(
                email = email,
                password = password,
                fullName = name,
                role = "STUDENT",
                rollOrSubject = rollNo.toString(),
                sectionOrDept = section
            )
            when (authResult) {
                is AuthResult.NeedsEmailConfirmation -> {
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        onBanner("Verification email sent to $email! Please verify before logging in.", false)
                        onNavigateToLogin()
                    }
                }
                is AuthResult.Success -> {
                    val newUser = User(userId = authResult.userId, email = email, passwordHash = "", userType = "STUDENT", name = name)
                    db.userDao().insertUser(newUser)
                    val student = Student(
                        studentId = authResult.userId,
                        userId = authResult.userId,
                        rollNumber = rollNo,
                        fullName = name,
                        email = email,
                        classSection = section
                    )
                    db.studentDao().insertStudent(student)
                    sessionManager.saveSession(student.studentId, name, "STUDENT", email, authResult.accessToken, authResult.refreshToken)
                    sessionManager.saveLoginCredentials(email, false, true)
                    CloudSyncManager.setAuthToken(authResult.accessToken)
                    CloudSyncManager.ensureCloudUserProfile(
                        userId = student.studentId,
                        email = email,
                        fullName = name,
                        role = "STUDENT",
                        rollOrSubject = rollNo.toString(),
                        sectionOrDept = section
                    )
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        onBanner("Account created! Position face in oval to enroll.", false)
                        onNavigateToEnrollment(student.studentId, name)
                    }
                }
                is AuthResult.Error -> {
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        _authError.value = authResult.message
                    }
                }
                else -> {
                    withContext(Dispatchers.Main) { _isLoading.value = false }
                }
            }
        }
    }

    fun signUpTeacher(
        name: String,
        department: String,
        email: String,
        password: String,
        onNavigateToLogin: () -> Unit,
        onNavigateToTeacherDashboard: (teacherId: String, name: String) -> Unit,
        onBanner: (message: String, isError: Boolean) -> Unit
    ) {
        _isLoading.value = true
        _authError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val authResult = SupabaseAuthManager.signUp(
                email = email,
                password = password,
                fullName = name,
                role = "TEACHER",
                rollOrSubject = "Faculty",
                sectionOrDept = department
            )
            when (authResult) {
                is AuthResult.NeedsEmailConfirmation -> {
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        onBanner("Verification email sent to $email! Please verify before logging in.", false)
                        onNavigateToLogin()
                    }
                }
                is AuthResult.Success -> {
                    val newUser = User(userId = authResult.userId, email = email, passwordHash = "", userType = "TEACHER", name = name)
                    db.userDao().insertUser(newUser)
                    val teacher = Teacher(
                        teacherId = authResult.userId,
                        userId = authResult.userId,
                        subject = "",
                        department = department
                    )
                    db.teacherDao().insertTeacher(teacher)
                    sessionManager.saveSession(teacher.teacherId, name, "TEACHER", email, authResult.accessToken, authResult.refreshToken)
                    sessionManager.saveLoginCredentials(email, true, true)
                    CloudSyncManager.setAuthToken(authResult.accessToken)
                    CloudSyncManager.ensureCloudUserProfile(
                        userId = teacher.teacherId,
                        email = email,
                        fullName = name,
                        role = "TEACHER",
                        rollOrSubject = "Faculty",
                        sectionOrDept = department
                    )
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        onBanner("Teacher account created!", false)
                        onNavigateToTeacherDashboard(teacher.teacherId, name)
                    }
                }
                is AuthResult.Error -> {
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        _authError.value = authResult.message
                    }
                }
                else -> {
                    withContext(Dispatchers.Main) { _isLoading.value = false }
                }
            }
        }
    }

    fun sendResetEmail(
        email: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, msg) = SupabaseAuthManager.resetPasswordForEmail(email)
            withContext(Dispatchers.Main) { onResult(ok, msg) }
        }
    }

    fun resetPassword(
        token: String,
        newPassword: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, msg) = SupabaseAuthManager.updateUserPassword(token, newPassword)
            withContext(Dispatchers.Main) { onResult(ok, msg) }
        }
    }

    fun resendVerification(
        email: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, msg) = SupabaseAuthManager.resendVerificationEmail(email)
            withContext(Dispatchers.Main) { onResult(ok, msg) }
        }
    }
}
