package com.vaibhav.facialattendancesystem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.facialattendancesystem.ui.theme.PrimaryCyan
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vaibhav.facialattendancesystem.ui.components.BannerData
import com.vaibhav.facialattendancesystem.ui.components.InAppBanner
import com.vaibhav.facialattendancesystem.ui.components.LocalBannerManager
import com.vaibhav.facialattendancesystem.data.AppDatabase
import com.vaibhav.facialattendancesystem.data.AttendanceRecord
import com.vaibhav.facialattendancesystem.data.AttendanceSession
import com.vaibhav.facialattendancesystem.data.ClassEnrollment
import com.vaibhav.facialattendancesystem.data.Clazz
import com.vaibhav.facialattendancesystem.data.FaceEmbedding
import com.vaibhav.facialattendancesystem.data.Student
import com.vaibhav.facialattendancesystem.data.Teacher
import com.vaibhav.facialattendancesystem.data.User
import com.vaibhav.facialattendancesystem.data.SupabaseAuthManager
import com.vaibhav.facialattendancesystem.data.AuthResult
import com.vaibhav.facialattendancesystem.ui.auth.LoginScreen
import com.vaibhav.facialattendancesystem.ui.auth.SignUpScreen
import com.vaibhav.facialattendancesystem.ui.auth.ForgotPasswordScreen
import com.vaibhav.facialattendancesystem.ui.auth.ResetPasswordScreen
import com.vaibhav.facialattendancesystem.ui.student.StudentDashboardScreen
import com.vaibhav.facialattendancesystem.ui.student.StudentEnrollmentScreen
import com.vaibhav.facialattendancesystem.ui.student.StudentProfileScreen
import com.vaibhav.facialattendancesystem.ui.teacher.AttendanceHistoryScreen
import com.vaibhav.facialattendancesystem.ui.teacher.ClassDetailsScreen
import com.vaibhav.facialattendancesystem.ui.teacher.ClassroomAttendanceScreen
import com.vaibhav.facialattendancesystem.ui.teacher.TeacherDashboardScreen
import com.vaibhav.facialattendancesystem.ui.teacher.TeacherProfileScreen
import com.vaibhav.facialattendancesystem.ui.teacher.exportSessionToCsv
import com.vaibhav.facialattendancesystem.ui.teacher.exportClassAttendanceCsv
import com.vaibhav.facialattendancesystem.ui.teacher.exportAdminCollegeAttendanceCsv
import com.vaibhav.facialattendancesystem.ui.theme.FacialAttendanceSystemTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.net.Uri

private fun encodeRouteParam(param: String): String {
    return try {
        Uri.encode(param)
    } catch (e: Exception) {
        param
    }
}

private fun decodeRouteParam(param: String): String {
    return try {
        Uri.decode(param)
    } catch (e: Exception) {
        param
    }
}

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object SignUp : Screen("signup")
    object ForgotPassword : Screen("forgot_password")
    object ResetPassword : Screen("reset_password?token={token}") {
        fun createRoute(token: String): String =
            "reset_password?token=" + encodeRouteParam(token)
    }
    object StudentEnrollment : Screen("student_enrollment/{studentId}/{studentName}") {
        fun createRoute(studentId: String, studentName: String) =
            "student_enrollment/$studentId/${encodeRouteParam(studentName)}"
    }
    object StudentDashboard : Screen("student_dashboard/{studentId}") {
        fun createRoute(studentId: String) = "student_dashboard/$studentId"
    }
    object StudentProfile : Screen("student_profile/{studentId}") {
        fun createRoute(studentId: String) = "student_profile/$studentId"
    }
    object TeacherDashboard : Screen("teacher_dashboard/{teacherId}/{teacherName}") {
        fun createRoute(teacherId: String, teacherName: String) =
            "teacher_dashboard/$teacherId/${encodeRouteParam(teacherName)}"
    }
    object TeacherProfile : Screen("teacher_profile/{teacherId}/{teacherName}") {
        fun createRoute(teacherId: String, teacherName: String) =
            "teacher_profile/$teacherId/${encodeRouteParam(teacherName)}"
    }
    object ClassDetails : Screen("class_details/{classId}") {
        fun createRoute(classId: String) = "class_details/$classId"
    }
    object ClassroomAttendance : Screen("classroom_attendance/{classId}/{className}") {
        fun createRoute(classId: String, className: String) =
            "classroom_attendance/$classId/${encodeRouteParam(className)}"
    }
    object AttendanceHistory : Screen("attendance_history/{classId}/{className}") {
        fun createRoute(classId: String, className: String) =
            "attendance_history/$classId/${encodeRouteParam(className)}"
    }
    object StudentClassAttendance : Screen("student_class_attendance/{classId}/{className}") {
        fun createRoute(classId: String, className: String) =
            "student_class_attendance/$classId/${encodeRouteParam(className)}"
    }
}

private fun generateClassCode(subject: String): String {
    val prefix = subject.filter { it.isLetter() }.take(2).uppercase().ifBlank { "CS" }
    val randomDigits = (1000..9999).random()
    return "$prefix-$randomDigits"
}

@Composable
fun AppNavigation(
    recoveryToken: String? = null,
    onClearRecoveryToken: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val sessionManager = remember { com.vaibhav.facialattendancesystem.util.SessionManager(context) }

    // Wire SessionManager into CloudSyncManager so every request always has the live token.
    // This is the definitive fix for 401 errors on sync — no more stale/null token races.
    remember(sessionManager, db, context) {
        com.vaibhav.facialattendancesystem.data.CloudSyncManager.initSessionManager(sessionManager, db, context)
    }

    var isDarkTheme by remember { mutableStateOf(sessionManager.isDarkTheme()) }
    var authError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var bannerData by remember { mutableStateOf<BannerData?>(null) }

    val showBanner: (String, Boolean) -> Unit = { message, isError ->
        bannerData = BannerData(message = message, isError = isError, id = System.currentTimeMillis())
    }

    LaunchedEffect(recoveryToken) {
        if (!recoveryToken.isNullOrBlank()) {
            navController.navigate(Screen.ResetPassword.createRoute(recoveryToken)) {
                popUpTo(Screen.Login.route) { inclusive = false }
            }
            onClearRecoveryToken()
        }
    }

    val savedSession = remember { sessionManager.getSession() }
    val savedToken = remember { sessionManager.getAccessToken() }
    LaunchedEffect(savedToken) {
        if (!savedToken.isNullOrBlank()) {
            com.vaibhav.facialattendancesystem.data.CloudSyncManager.setAuthToken(savedToken)
        }
    }

    var availableUpdate by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    LaunchedEffect(Unit) {
        com.vaibhav.facialattendancesystem.data.CloudSyncManager.purgeAutoFaceAvatarsOnce(context)
        if (!sessionManager.isTestDataPurged()) {
            withContext(Dispatchers.IO) {
                try {
                    db.purgeClassesAndAttendanceData(purgeCloud = false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                sessionManager.setTestDataPurged(true)
            }
        }
        val updateInfo = com.vaibhav.facialattendancesystem.data.CloudSyncManager.checkForGitHubUpdate()
        if (updateInfo != null) {
            availableUpdate = updateInfo
        }
    }

    val startDestination = remember {
        when {
            savedSession == null -> Screen.Login.route
            savedSession.userRole == "TEACHER" -> Screen.TeacherDashboard.createRoute(savedSession.userId, savedSession.userName)
            else -> Screen.StudentDashboard.createRoute(savedSession.userId)
        }
    }

    FacialAttendanceSystemTheme(darkTheme = isDarkTheme) {
        CompositionLocalProvider(LocalBannerManager provides showBanner) {
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(navController = navController, startDestination = startDestination) {

            composable(Screen.Login.route) {
                LoginScreen(
                    initialEmail = sessionManager.getSavedEmail(),
                    initialPassword = sessionManager.getSavedPassword(),
                    initialIsTeacher = sessionManager.getSavedIsTeacher(),
                    initialSaveLogin = sessionManager.isSaveLoginEnabled(),
                    onLoginClick = { email, password, isTeacher, saveLogin ->
                        isLoading = true
                        authError = null
                        scope.launch(Dispatchers.IO) {
                            // 1. Authenticate with official Supabase GoTrue Auth
                            val authResult = SupabaseAuthManager.signInWithPassword(email, password)
                            when (authResult) {
                                is AuthResult.Success -> {
                                    val roleUpper = authResult.role.uppercase()
                                    if (isTeacher && roleUpper != "TEACHER") {
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            authError = "This account is a Student account. Please select Student Portal."
                                        }
                                        return@launch
                                    }
                                    if (!isTeacher && roleUpper == "TEACHER") {
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            authError = "This account is a Teacher account. Please select Teacher Portal."
                                        }
                                        return@launch
                                    }

                                    // Save credentials if user opted to save login info
                                    sessionManager.saveLoginCredentials(email, password, isTeacher, saveLogin)

                                    // Set token on CloudSyncManager so all subsequent cloud REST calls authenticate
                                    com.vaibhav.facialattendancesystem.data.CloudSyncManager.setAuthToken(authResult.accessToken)

                                    // Check or insert User record in local Room DB keyed by Supabase UUID
                                    var localUser = db.userDao().getUserByEmail(email)
                                    if (localUser == null) {
                                        localUser = User(
                                            userId = authResult.userId,
                                            email = authResult.email,
                                            passwordHash = password,
                                            userType = roleUpper,
                                            name = authResult.fullName
                                        )
                                        db.userDao().insertUser(localUser)
                                    } else if (localUser.passwordHash != password) {
                                        // Update local cached password if changed remotely
                                        db.userDao().insertUser(localUser.copy(passwordHash = password))
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
                                        sessionManager.saveSession(teacher.teacherId, localUser.name, "TEACHER", localUser.email, authResult.accessToken, authResult.refreshToken)
                                        // Ensure profile exists in Supabase and pull all teacher classes & rosters
                                        scope.launch(Dispatchers.IO) {
                                            com.vaibhav.facialattendancesystem.data.CloudSyncManager.ensureCloudUserProfile(
                                                userId = teacher.teacherId,
                                                email = localUser.email,
                                                fullName = localUser.name,
                                                role = "TEACHER",
                                                rollOrSubject = teacher.subject,
                                                sectionOrDept = teacher.department ?: "CSE"
                                            )
                                            com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncTeacherAllClassesAndRosters(teacher.teacherId, db, sessionManager)
                                        }
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            showBanner("Welcome back, ${localUser.name}!", false)
                                            navController.navigate(Screen.TeacherDashboard.createRoute(teacher.teacherId, localUser.name)) {
                                                popUpTo(0)
                                            }
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

                                        // Restore face embeddings from cloud if student profile was enrolled on cloud
                                        val hasLocalEmbeddings = db.faceEmbeddingDao().getEmbeddingsForStudent(student.studentId).isNotEmpty()
                                        var isFaceEnrolled = student.enrollmentStatus == 1 || hasLocalEmbeddings
                                        if (!isFaceEnrolled) {
                                            val cloudEmbeddingsFound = com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncStudentFaceEmbeddings(student.studentId, db)
                                            if (cloudEmbeddingsFound) {
                                                isFaceEnrolled = true
                                                val updated = student.copy(enrollmentStatus = 1)
                                                db.studentDao().insertStudent(updated)
                                                student = updated
                                            }
                                        }

                                        sessionManager.saveSession(student.studentId, student.fullName, "STUDENT", localUser.email, authResult.accessToken, authResult.refreshToken)

                                        // Ensure profile exists in Supabase and pull enrolled classes & attendance
                                        scope.launch(Dispatchers.IO) {
                                            com.vaibhav.facialattendancesystem.data.CloudSyncManager.ensureCloudUserProfile(
                                                userId = student.studentId,
                                                email = localUser.email,
                                                fullName = student.fullName,
                                                role = "STUDENT",
                                                rollOrSubject = student.rollNumber.toString(),
                                                sectionOrDept = student.classSection ?: ""
                                            )
                                            com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncStudentAttendance(student.studentId, db, context, notify = true)
                                        }

                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            showBanner("Welcome back, ${student.fullName}!", false)
                                            if (isFaceEnrolled) {
                                                navController.navigate(Screen.StudentDashboard.createRoute(student.studentId)) {
                                                    popUpTo(0)
                                                }
                                            } else {
                                                navController.navigate(Screen.StudentEnrollment.createRoute(student.studentId, student.fullName)) {
                                                    popUpTo(0)
                                                }
                                            }
                                        }
                                    }
                                }
                                is AuthResult.EmailNotConfirmed -> {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        authError = authResult.message
                                    }
                                }
                                is AuthResult.Error -> {
                                    // Offline fallback: If network failure, check local Room DB
                                    val isNetworkErr = authResult.message.contains("Network", ignoreCase = true) ||
                                            authResult.message.contains("Unable to resolve host", ignoreCase = true) ||
                                            authResult.message.contains("503", ignoreCase = true)
                                    val localUser = db.userDao().getUserByEmail(email)
                                    if (isNetworkErr && localUser != null && localUser.passwordHash == password) {
                                        sessionManager.saveLoginCredentials(email, password, isTeacher, saveLogin)
                                        if (isTeacher) {
                                            val teacher = db.teacherDao().getTeacherByUserId(localUser.userId)
                                            withContext(Dispatchers.Main) {
                                                isLoading = false
                                                if (teacher != null) {
                                                    sessionManager.saveSession(teacher.teacherId, localUser.name, "TEACHER", localUser.email)
                                                    showBanner("Offline mode: Logged in as ${localUser.name}", false)
                                                    navController.navigate(Screen.TeacherDashboard.createRoute(teacher.teacherId, localUser.name)) {
                                                        popUpTo(0)
                                                    }
                                                } else {
                                                    authError = "Could not find teacher profile."
                                                }
                                            }
                                        } else {
                                            val student = db.studentDao().getStudentByUserId(localUser.userId)
                                            withContext(Dispatchers.Main) {
                                                isLoading = false
                                                if (student != null) {
                                                    sessionManager.saveSession(student.studentId, student.fullName, "STUDENT", localUser.email)
                                                    showBanner("Offline mode: Logged in as ${student.fullName}", false)
                                                    if (student.enrollmentStatus == 1) {
                                                        navController.navigate(Screen.StudentDashboard.createRoute(student.studentId)) { popUpTo(0) }
                                                    } else {
                                                        navController.navigate(Screen.StudentEnrollment.createRoute(student.studentId, student.fullName)) { popUpTo(0) }
                                                    }
                                                } else {
                                                    authError = "Could not find student profile."
                                                }
                                            }
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            authError = authResult.message
                                        }
                                    }
                                }
                                else -> {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                    }
                                }
                            }
                        }
                    },
                    onNavigateToSignUp = {
                        authError = null
                        navController.navigate(Screen.SignUp.route)
                    },
                    onForgotPasswordClick = {
                        authError = null
                        navController.navigate(Screen.ForgotPassword.route)
                    },
                    onResendVerificationClick = { unverifiedEmail ->
                        scope.launch(Dispatchers.IO) {
                            val (success, resMsg) = SupabaseAuthManager.resendVerificationEmail(unverifiedEmail)
                            withContext(Dispatchers.Main) {
                                showBanner(resMsg, !success)
                            }
                        }
                    },
                    errorMessage = authError,
                    isLoading = isLoading
                )
            }

            composable(Screen.SignUp.route) {
                SignUpScreen(
                    onStudentSignUpClick = { name, rollNo, email, pass, section ->
                        isLoading = true
                        authError = null
                        scope.launch(Dispatchers.IO) {
                            val authResult = SupabaseAuthManager.signUp(
                                email = email,
                                password = pass,
                                fullName = name,
                                role = "STUDENT",
                                rollOrSubject = rollNo.toString(),
                                sectionOrDept = section
                            )
                            when (authResult) {
                                is AuthResult.NeedsEmailConfirmation -> {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        showBanner("Verification email sent to $email! Please verify before logging in.", false)
                                        navController.navigate(Screen.Login.route) {
                                            popUpTo(Screen.SignUp.route) { inclusive = true }
                                        }
                                    }
                                }
                                is AuthResult.Success -> {
                                    val newUser = User(userId = authResult.userId, email = email, passwordHash = pass, userType = "STUDENT", name = name)
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
                                    sessionManager.saveLoginCredentials(email, pass, false, true)
                                    com.vaibhav.facialattendancesystem.data.CloudSyncManager.setAuthToken(authResult.accessToken)
                                    com.vaibhav.facialattendancesystem.data.CloudSyncManager.ensureCloudUserProfile(
                                        userId = student.studentId,
                                        email = email,
                                        fullName = name,
                                        role = "STUDENT",
                                        rollOrSubject = rollNo.toString(),
                                        sectionOrDept = section
                                    )

                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        showBanner("Account created! Position face in oval to enroll.", false)
                                        navController.navigate(Screen.StudentEnrollment.createRoute(student.studentId, name))
                                    }
                                }
                                is AuthResult.Error -> {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        authError = authResult.message
                                    }
                                }
                                else -> {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                    }
                                }
                            }
                        }
                    },
                    onTeacherSignUpClick = { name, subject, department, email, pass ->
                        isLoading = true
                        authError = null
                        scope.launch(Dispatchers.IO) {
                            val authResult = SupabaseAuthManager.signUp(
                                email = email,
                                password = pass,
                                fullName = name,
                                role = "TEACHER",
                                rollOrSubject = subject,
                                sectionOrDept = department
                            )
                            when (authResult) {
                                is AuthResult.NeedsEmailConfirmation -> {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        showBanner("Verification email sent to $email! Please verify before logging in.", false)
                                        navController.navigate(Screen.Login.route) {
                                            popUpTo(Screen.SignUp.route) { inclusive = true }
                                        }
                                    }
                                }
                                is AuthResult.Success -> {
                                    val newUser = User(userId = authResult.userId, email = email, passwordHash = pass, userType = "TEACHER", name = name)
                                    db.userDao().insertUser(newUser)
                                    val teacher = Teacher(
                                        teacherId = authResult.userId,
                                        userId = authResult.userId,
                                        subject = subject,
                                        department = department
                                    )
                                    db.teacherDao().insertTeacher(teacher)
                                    sessionManager.saveSession(teacher.teacherId, name, "TEACHER", email, authResult.accessToken, authResult.refreshToken)
                                    sessionManager.saveLoginCredentials(email, pass, true, true)
                                    com.vaibhav.facialattendancesystem.data.CloudSyncManager.setAuthToken(authResult.accessToken)
                                    com.vaibhav.facialattendancesystem.data.CloudSyncManager.ensureCloudUserProfile(
                                        userId = teacher.teacherId,
                                        email = email,
                                        fullName = name,
                                        role = "TEACHER",
                                        rollOrSubject = subject,
                                        sectionOrDept = department
                                    )

                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        showBanner("Teacher account created!", false)
                                        navController.navigate(Screen.TeacherDashboard.createRoute(teacher.teacherId, name))
                                    }
                                }
                                is AuthResult.Error -> {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        authError = authResult.message
                                    }
                                }
                                else -> {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                    }
                                }
                            }
                        }
                    },
                    onNavigateToLogin = {
                        authError = null
                        navController.navigate(Screen.Login.route)
                    },
                    errorMessage = authError,
                    isLoading = isLoading
                )
            }

            composable(Screen.ForgotPassword.route) {
                var forgotLoading by remember { mutableStateOf(false) }
                var forgotMsg by remember { mutableStateOf<String?>(null) }
                var forgotIsError by remember { mutableStateOf(false) }

                ForgotPasswordScreen(
                    onSendResetLink = { email ->
                        forgotLoading = true
                        forgotMsg = null
                        scope.launch(Dispatchers.IO) {
                            val (ok, msg) = SupabaseAuthManager.resetPasswordForEmail(email)
                            withContext(Dispatchers.Main) {
                                forgotLoading = false
                                forgotMsg = msg
                                forgotIsError = !ok
                            }
                        }
                    },
                    onBackToLogin = { navController.navigate(Screen.Login.route) },
                    isLoading = forgotLoading,
                    statusMessage = forgotMsg,
                    isError = forgotIsError
                )
            }

            composable(
                route = Screen.ResetPassword.route,
                arguments = listOf(androidx.navigation.navArgument("token") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                })
            ) { backStackEntry ->
                val rawToken = backStackEntry.arguments?.getString("token") ?: ""
                val decodedToken = try {
                    java.net.URLDecoder.decode(rawToken, "UTF-8")
                } catch (e: Exception) {
                    rawToken
                }

                var resetLoading by remember { mutableStateOf(false) }
                var resetMsg by remember { mutableStateOf<String?>(null) }
                var resetIsError by remember { mutableStateOf(false) }

                ResetPasswordScreen(
                    recoveryToken = decodedToken,
                    onResetPasswordSubmit = { token, newPassword ->
                        resetLoading = true
                        resetMsg = null
                        scope.launch(Dispatchers.IO) {
                            val (ok, msg) = SupabaseAuthManager.updateUserPassword(token, newPassword)
                            withContext(Dispatchers.Main) {
                                resetLoading = false
                                resetMsg = msg
                                resetIsError = !ok
                                if (ok) {
                                    showBanner("Password updated successfully! Please log in with your new password.", false)
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0)
                                    }
                                }
                            }
                        }
                    },
                    onBackToLogin = { navController.navigate(Screen.Login.route) },
                    isLoading = resetLoading,
                    statusMessage = resetMsg,
                    isError = resetIsError
                )
            }

            composable(Screen.StudentEnrollment.route) { backStack ->
                val studentId = backStack.arguments?.getString("studentId") ?: ""
                val studentName = decodeRouteParam(backStack.arguments?.getString("studentName") ?: "Student")

                StudentEnrollmentScreen(
                    studentId = studentId,
                    studentName = studentName,
                    onEnrollmentComplete = { capturedSteps, averagedVector ->
                        scope.launch(Dispatchers.IO) {
                            val converters = com.vaibhav.facialattendancesystem.data.VectorTypeConverters()
                            val embeddings = capturedSteps.map { step ->
                                FaceEmbedding(
                                    studentId = studentId,
                                    embeddingVector = converters.floatArrayToByteArray(step.embedding) ?: ByteArray(0),
                                    sourceAngle = step.stepAngle,
                                    qualityScore = step.qualityScore
                                )
                            }
                            db.faceEmbeddingDao().insertEmbeddings(embeddings)

                            db.faceEmbeddingDao().insertEmbedding(
                                FaceEmbedding(
                                    studentId = studentId,
                                    embeddingVector = converters.floatArrayToByteArray(averagedVector) ?: ByteArray(0),
                                    sourceAngle = "AVERAGED",
                                    qualityScore = 1.0f
                                )
                            )

                            val student = db.studentDao().getStudentById(studentId)
                            val user = student?.let { db.userDao().getUserById(it.userId) }
                            if (student != null) {
                                val updatedStudent = student.copy(enrollmentStatus = 1)
                                db.studentDao().updateStudent(updatedStudent)
                            }

                            // Navigate immediately so student never waits on network upload
                            withContext(Dispatchers.Main) {
                                showBanner("Face profile registered! Syncing with cloud... ✓", false)
                                navController.navigate(Screen.StudentDashboard.createRoute(studentId)) {
                                    popUpTo(0)
                                }
                            }

                            // Sync profile and calibration embeddings to cloud in background (do NOT set face photo as avatar)
                            if (student != null) {
                                val updatedStudent = student.copy(enrollmentStatus = 1)
                                val allVectors = capturedSteps.map { it.embedding } + listOf(averagedVector)
                                com.vaibhav.facialattendancesystem.data.CloudSyncManager.uploadStudentProfile(
                                    student = updatedStudent,
                                    embeddings = allVectors,
                                    password = user?.passwordHash
                                )
                                val angleBitmaps = capturedSteps.map { it.stepAngle to it.bitmap }
                                com.vaibhav.facialattendancesystem.data.CloudSyncManager.uploadCalibrationSamples(studentId, angleBitmaps)
                            }
                        }
                    }
                )
            }

            composable(Screen.StudentDashboard.route) { backStack ->
                val studentId = backStack.arguments?.getString("studentId") ?: ""

                val studentFlow = remember(studentId) { db.studentDao().getStudentByIdFlow(studentId) }
                val student by studentFlow.collectAsState(initial = null)
                val joinedClassesFlow = remember(studentId) { db.clazzDao().getClassesForStudent(studentId) }
                val joinedClasses by joinedClassesFlow.collectAsState(initial = emptyList())

                val attendanceFlow = remember(studentId) { db.attendanceDao().getRecordsForStudent(studentId) }
                val attendanceRecords by attendanceFlow.collectAsState(initial = emptyList())

                val attendanceFeedFlow = remember(studentId) { db.attendanceDao().getAttendanceFeedForStudent(studentId) }
                val attendanceFeed by attendanceFeedFlow.collectAsState(initial = emptyList())

                var isSyncing by remember { mutableStateOf(false) }

                // Initial sync on screen entry + continuous 15s background auto-sync while dashboard is open
                LaunchedEffect(studentId) {
                    if (studentId.isNotBlank()) {
                        isSyncing = true
                        com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncStudentClassesAndEnrollments(studentId, db, sessionManager)
                        com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncStudentAttendance(studentId, db, context)
                        isSyncing = false
                        while (true) {
                            kotlinx.coroutines.delay(15_000L)
                            if (!isSyncing) {
                                com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncStudentClassesAndEnrollments(studentId, db, sessionManager)
                                com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncStudentAttendance(studentId, db, context)
                            }
                        }
                    }
                }

                // Auto-sync every time the screen is RESUMED (e.g. user returns from class detail screen).
                // This keeps data fresh across devices without the user having to manually pull-to-refresh.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, studentId) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME && studentId.isNotBlank() && !isSyncing) {
                            scope.launch(Dispatchers.IO) {
                                com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncStudentClassesAndEnrollments(studentId, db, sessionManager)
                                com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncStudentAttendance(studentId, db, context)
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                student?.let { currentStudent ->
                    // Build classId -> teacher.userId map for avatar downloads
                    // IMPORTANT: getTeacherById is a blocking DAO call — must run on IO thread, never main thread
                    val teacherUserIds by produceState(initialValue = emptyMap<String, String>(), key1 = joinedClasses) {
                        value = withContext(Dispatchers.IO) {
                            joinedClasses.associate { clazz ->
                                val resolvedId = db.teacherDao().getTeacherById(clazz.teacherId)?.userId?.ifBlank { null } ?: clazz.teacherId
                                clazz.classId to resolvedId
                            }.filter { it.value.isNotBlank() }
                        }
                    }
                    StudentDashboardScreen(
                        student = currentStudent,
                        joinedClasses = joinedClasses,
                        attendanceRecords = attendanceRecords,
                        attendanceFeed = attendanceFeed,
                        teacherUserIds = teacherUserIds,
                        isSyncing = isSyncing,
                        onRefreshCloud = {
                            scope.launch(Dispatchers.IO) {
                                isSyncing = true
                                val (cSynced, cMsg) = com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncStudentClassesAndEnrollments(studentId, db, sessionManager)
                                val (synced, msg) = com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncStudentAttendance(studentId, db, context)
                                withContext(Dispatchers.Main) {
                                    isSyncing = false
                                    showBanner(if (synced || cSynced) "✓ Refreshed enrolled classes & attendance" else msg, !synced && !cSynced)
                                }
                            }
                        },
                        onJoinClassByCode = { code ->
                            scope.launch(Dispatchers.IO) {
                                val (success, message) = com.vaibhav.facialattendancesystem.data.CloudSyncManager.joinClassByCode(code, studentId, db, sessionManager)
                                if (success) {
                                    com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncStudentClassesAndEnrollments(studentId, db, sessionManager)
                                    com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncStudentAttendance(studentId, db, context)
                                }
                                withContext(Dispatchers.Main) {
                                    if (success) {
                                        showBanner("✓ Joined $message!", false)
                                    } else {
                                        showBanner(message, true)
                                    }
                                }
                            }
                        },
                        onOpenClassAttendance = { classId, className ->
                            navController.navigate(Screen.StudentClassAttendance.createRoute(classId, className))
                        },

                        onReEnrollFace = {
                            navController.navigate(Screen.StudentEnrollment.createRoute(currentStudent.studentId, currentStudent.fullName))
                        },
                        onOpenProfile = {
                            navController.navigate(Screen.StudentProfile.createRoute(currentStudent.studentId))
                        },
                        onLogout = {
                            sessionManager.clearSession()
                            com.vaibhav.facialattendancesystem.data.CloudSyncManager.setAuthToken(null)
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0)
                            }
                        }
                    )
                }
            }

            composable(Screen.StudentProfile.route) { backStack ->
                val studentId = backStack.arguments?.getString("studentId") ?: ""

                val studentFlow = remember(studentId) { db.studentDao().getStudentByIdFlow(studentId) }
                val student by studentFlow.collectAsState(initial = null)
                val joinedClassesFlow = remember(studentId) { db.clazzDao().getClassesForStudent(studentId) }
                val joinedClasses by joinedClassesFlow.collectAsState(initial = emptyList())

                student?.let { currentStudent ->
                    StudentProfileScreen(
                        student = currentStudent,
                        joinedClassesCount = joinedClasses.size,
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = {
                            isDarkTheme = it
                            sessionManager.setDarkTheme(it)
                        },
                        onUpdateProfile = { name, rollNo, section ->
                            scope.launch(Dispatchers.IO) {
                                val updated = currentStudent.copy(fullName = name, rollNumber = rollNo, classSection = section)
                                db.studentDao().insertStudent(updated)
                                val u = db.userDao().getUserById(currentStudent.userId)
                                if (u != null) {
                                    db.userDao().insertUser(u.copy(name = name))
                                }
                                sessionManager.updateUserName(name)
                                com.vaibhav.facialattendancesystem.data.CloudSyncManager.ensureCloudUserProfile(
                                    userId = currentStudent.studentId,
                                    email = currentStudent.email,
                                    fullName = name,
                                    role = "STUDENT",
                                    rollOrSubject = rollNo.toString(),
                                    sectionOrDept = section
                                )
                                withContext(Dispatchers.Main) {
                                    showBanner("✓ Profile updated!", false)
                                }
                            }
                        },
                        onReEnrollFace = {
                            navController.navigate(Screen.StudentEnrollment.createRoute(currentStudent.studentId, currentStudent.fullName))
                        },
                        onLogout = {
                            sessionManager.clearSession()
                            com.vaibhav.facialattendancesystem.data.CloudSyncManager.setAuthToken(null)
                            navController.navigate(Screen.Login.route) { popUpTo(0) }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(Screen.TeacherDashboard.route) { backStack ->
                val teacherId = backStack.arguments?.getString("teacherId") ?: ""
                val teacherNameParam = decodeRouteParam(backStack.arguments?.getString("teacherName") ?: "Teacher")

                val userFlow = remember(teacherId) { db.userDao().getUserByIdFlow(teacherId) }
                val user by userFlow.collectAsState(initial = null)
                val effectiveTeacherName = user?.name?.ifBlank { null } ?: sessionManager.getSession()?.userName?.ifBlank { null } ?: teacherNameParam

                var isSyncing by remember { mutableStateOf(false) }

                val classesFlow = remember(teacherId) { db.clazzDao().getClassesForTeacher(teacherId) }
                val classes by classesFlow.collectAsState(initial = emptyList())

                val totalStudentsFlow = remember(teacherId) { db.classEnrollmentDao().getTotalStudentsForTeacher(teacherId) }
                val totalStudents by totalStudentsFlow.collectAsState(initial = 0)

                val totalLecturesFlow = remember(teacherId) { db.attendanceDao().getTotalLecturesForTeacher(teacherId) }
                val totalLectures by totalLecturesFlow.collectAsState(initial = 0)

                val allEnrollmentsFlow = remember(teacherId) { db.classEnrollmentDao().getEnrollmentsForTeacherFlow(teacherId) }
                val allEnrollments by allEnrollmentsFlow.collectAsState(initial = emptyList())

                val allSessionsFlow = remember(teacherId) { db.attendanceDao().getSessionsForTeacherFlow(teacherId) }
                val allSessions by allSessionsFlow.collectAsState(initial = emptyList())

                val unsyncedSessionsFlow = remember(teacherId) { db.attendanceDao().getUnsyncedSessionsCountForTeacherFlow(teacherId) }
                val unsyncedSessionsCount by unsyncedSessionsFlow.collectAsState(initial = 0)

                val enrolledCountsMap = remember(classes, allEnrollments) {
                    classes.associate { clazz ->
                        clazz.classId to allEnrollments.count { it.classId == clazz.classId }
                    }
                }
                val lecturesCountsMap = remember(classes, allSessions) {
                    classes.associate { clazz ->
                        clazz.classId to allSessions.count { it.classId == clazz.classId }
                    }
                }

                // Auto-sync teacher classes and rosters when opening dashboard + every 15s in background
                LaunchedEffect(teacherId) {
                    isSyncing = true
                    com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncTeacherAllClassesAndRosters(teacherId, db, sessionManager)
                    isSyncing = false
                    while (true) {
                        kotlinx.coroutines.delay(15_000L)
                        if (!isSyncing && teacherId.isNotBlank()) {
                            com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncTeacherAllClassesAndRosters(teacherId, db, sessionManager)
                        }
                    }
                }

                // Auto-sync every time teacher returns to dashboard (e.g. from class details).
                // Keeps pending enrollment requests and roster changes up to date automatically.
                val teacherLifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(teacherLifecycleOwner, teacherId) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME && teacherId.isNotBlank() && !isSyncing) {
                            scope.launch(Dispatchers.IO) {
                                com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncTeacherAllClassesAndRosters(teacherId, db, sessionManager)
                            }
                        }
                    }
                    teacherLifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { teacherLifecycleOwner.lifecycle.removeObserver(observer) }
                }

                TeacherDashboardScreen(
                    teacherId = teacherId,
                    teacherName = effectiveTeacherName,
                    classes = classes,
                    totalStudents = totalStudents,
                    totalLectures = totalLectures,
                    enrolledCountsMap = enrolledCountsMap,
                    lecturesCountsMap = lecturesCountsMap,
                    isSyncing = isSyncing,
                    unsyncedSessionsCount = unsyncedSessionsCount,
                    onRefreshCloud = {
                        scope.launch(Dispatchers.IO) {
                            isSyncing = true
                            // Upload any pending offline sessions first
                            val pendingSessions = db.attendanceDao().getUnsyncedSessions()
                            for (session in pendingSessions) {
                                val records = db.attendanceDao().getRecordsForSession(session.sessionId)
                                val success = com.vaibhav.facialattendancesystem.data.CloudSyncManager.uploadAttendanceSession(session, records, teacherId)
                                if (success) {
                                    db.attendanceDao().markSessionSynced(session.sessionId)
                                }
                            }
                            val (synced, msg) = com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncTeacherAllClassesAndRosters(teacherId, db, sessionManager)
                            withContext(Dispatchers.Main) {
                                isSyncing = false
                                showBanner(msg, !synced)
                            }
                        }
                    },
                    onCreateClass = { className, subject, semester, section ->
                        scope.launch(Dispatchers.IO) {
                            val code = generateClassCode(subject)
                            val newClass = Clazz(
                                teacherId = teacherId,
                                className = className,
                                subject = subject,
                                semester = semester,
                                section = section,
                                classCode = code
                            )
                            db.clazzDao().insertClass(newClass)
                            com.vaibhav.facialattendancesystem.data.CloudSyncManager.uploadNewClass(newClass)
                            withContext(Dispatchers.Main) {
                                showBanner("Class created! Code: $code", false)
                            }
                        }
                    },
                    onMarkAttendance = { classId ->
                        navController.navigate(Screen.ClassroomAttendance.createRoute(classId, classes.find { it.classId == classId }?.className ?: "Class"))
                    },
                    onViewHistory = { classId ->
                        navController.navigate(Screen.AttendanceHistory.createRoute(classId, classes.find { it.classId == classId }?.className ?: "Class"))
                    },
                    onOpenClassDetails = { classId ->
                        navController.navigate(Screen.ClassDetails.createRoute(classId))
                    },
                    onOpenProfile = {
                        navController.navigate(Screen.TeacherProfile.createRoute(teacherId, effectiveTeacherName))
                    },
                    onDeleteClass = { classId ->
                        scope.launch(Dispatchers.IO) {
                            val (success, msg) = com.vaibhav.facialattendancesystem.data.CloudSyncManager.deleteClass(classId, teacherId, db, sessionManager)
                            withContext(Dispatchers.Main) {
                                showBanner(if (success) "✓ Class deleted successfully" else msg, !success)
                            }
                        }
                    },
                    onLogout = {
                        sessionManager.clearSession()
                        com.vaibhav.facialattendancesystem.data.CloudSyncManager.setAuthToken(null)
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0)
                        }
                    }
                )
            }

            composable(Screen.TeacherProfile.route) { backStack ->
                val teacherId = backStack.arguments?.getString("teacherId") ?: ""
                val teacherNameParam = decodeRouteParam(backStack.arguments?.getString("teacherName") ?: "Teacher")

                val teacherFlow = remember(teacherId) { db.teacherDao().getTeacherByIdFlow(teacherId) }
                val teacher by teacherFlow.collectAsState(initial = null)

                val userFlow = remember(teacherId) { db.userDao().getUserByIdFlow(teacherId) }
                val user by userFlow.collectAsState(initial = null)
                val effectiveTeacherName = user?.name?.ifBlank { null } ?: sessionManager.getSession()?.userName?.ifBlank { null } ?: teacherNameParam

                val classesFlow = remember(teacherId) { db.clazzDao().getClassesForTeacher(teacherId) }
                val classes by classesFlow.collectAsState(initial = emptyList())

                TeacherProfileScreen(
                    teacherId = teacherId,
                    teacherName = effectiveTeacherName,
                    teacher = teacher,
                    classesCount = classes.size,
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = {
                        isDarkTheme = it
                        sessionManager.setDarkTheme(it)
                    },
                    onUpdateProfile = { name, dept, subject ->
                        scope.launch(Dispatchers.IO) {
                            val current = db.teacherDao().getTeacherById(teacherId)
                            val updatedTeacher = (current ?: Teacher(teacherId = teacherId, userId = teacherId, subject = subject, department = dept))
                                .copy(department = dept, subject = subject)
                            db.teacherDao().insertTeacher(updatedTeacher)

                            val currentUser = db.userDao().getUserById(teacherId)
                            if (currentUser != null) {
                                db.userDao().insertUser(currentUser.copy(name = name))
                            }
                            sessionManager.updateUserName(name)
                            com.vaibhav.facialattendancesystem.data.CloudSyncManager.ensureCloudUserProfile(
                                userId = teacherId,
                                email = currentUser?.email ?: sessionManager.getSession()?.userEmail ?: "",
                                fullName = name,
                                role = "TEACHER",
                                rollOrSubject = subject,
                                sectionOrDept = dept
                            )
                            withContext(Dispatchers.Main) {
                                showBanner("✓ Profile updated!", false)
                            }
                        }
                    },
                    onPurgeData = {
                        scope.launch(Dispatchers.IO) {
                            val purged = db.purgeClassesAndAttendanceData(purgeCloud = true)
                            withContext(Dispatchers.Main) {
                                if (purged) {
                                    showBanner("✓ Classes & attendance cleared! User profiles & faces are safe.", false)
                                } else {
                                    showBanner("Error clearing records.", true)
                                }
                            }
                        }
                    },
                    onLogout = {
                        sessionManager.clearSession()
                        com.vaibhav.facialattendancesystem.data.CloudSyncManager.setAuthToken(null)
                        navController.navigate(Screen.Login.route) { popUpTo(0) }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ClassDetails.route) { backStack ->
                val classId = backStack.arguments?.getString("classId") ?: ""

                var clazz by remember { mutableStateOf<Clazz?>(null) }
                var enrolledStudents by remember { mutableStateOf<List<Student>>(emptyList()) }
                var isSyncingRoster by remember { mutableStateOf(false) }
                var isLoadingData by remember { mutableStateOf(true) }

                val classRecordsFlow = remember(classId) { db.attendanceDao().getAllRecordsForClass(classId) }
                val classRecords by classRecordsFlow.collectAsState(initial = emptyList())

                val classSessionsFlow = remember(classId) { db.attendanceDao().getSessionsForClass(classId) }
                val classSessions by classSessionsFlow.collectAsState(initial = emptyList())

                val refreshLocal: suspend () -> Unit = {
                    withContext(Dispatchers.IO) {
                        val c = db.clazzDao().getClassById(classId)
                        // Only show APPROVED students in the main roster
                        val enrolledIds = db.classEnrollmentDao().getApprovedStudentIds(classId)
                        val students = if (enrolledIds.isNotEmpty()) db.studentDao().getStudentsByIds(enrolledIds) else emptyList()
                        withContext(Dispatchers.Main) {
                            clazz = c
                            enrolledStudents = students
                            isLoadingData = false
                        }
                    }
                }

                LaunchedEffect(classId) {
                    withContext(Dispatchers.IO) {
                        try {
                            refreshLocal()
                            withContext(Dispatchers.Main) { isSyncingRoster = true }
                            com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncClassRosterForTeacher(classId, db)
                            refreshLocal()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            withContext(Dispatchers.Main) {
                                isSyncingRoster = false
                                isLoadingData = false
                            }
                        }
                    }
                }

                // Live flow of pending join requests for this class
                val pendingEnrollmentsFlow = remember(classId) {
                    db.classEnrollmentDao().getPendingEnrollmentsForClassFlow(classId)
                }
                val pendingEnrollments by pendingEnrollmentsFlow.collectAsState(initial = emptyList())

                // Resolve student objects for pending enrollments
                var pendingStudents by remember { mutableStateOf<List<Student>>(emptyList()) }
                LaunchedEffect(pendingEnrollments) {
                    withContext(Dispatchers.IO) {
                        val ids = pendingEnrollments.map { it.studentId }
                        val students = if (ids.isNotEmpty()) db.studentDao().getStudentsByIds(ids) else emptyList()
                        withContext(Dispatchers.Main) { pendingStudents = students }
                    }
                }

                if (clazz != null) {
                    ClassDetailsScreen(
                        clazz = clazz!!,
                        enrolledStudents = enrolledStudents,
                        pendingStudents = pendingStudents,
                        attendanceRecords = classRecords,
                        totalSessionsCount = classSessions.size,
                        isSyncing = isSyncingRoster,
                        onMarkAttendance = {
                            navController.navigate(Screen.ClassroomAttendance.createRoute(clazz!!.classId, clazz!!.className))
                        },
                        onRefresh = {
                            scope.launch(Dispatchers.IO) {
                                withContext(Dispatchers.Main) { isSyncingRoster = true }
                                com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncClassRosterForTeacher(clazz!!.classId, db)
                                refreshLocal()
                                withContext(Dispatchers.Main) {
                                    isSyncingRoster = false
                                }
                            }
                        },
                        onApproveStudent = { studentId ->
                            scope.launch(Dispatchers.IO) {
                                val (ok, msg) = com.vaibhav.facialattendancesystem.data.CloudSyncManager.approveEnrollment(clazz!!.classId, studentId, db)
                                refreshLocal()
                                withContext(Dispatchers.Main) {
                                    showBanner(if (ok) "✓ Student approved and added to class" else msg, !ok)
                                }
                            }
                        },
                        onRejectStudent = { studentId ->
                            scope.launch(Dispatchers.IO) {
                                val (ok, msg) = com.vaibhav.facialattendancesystem.data.CloudSyncManager.rejectEnrollment(clazz!!.classId, studentId, db)
                                withContext(Dispatchers.Main) {
                                    showBanner(if (ok) "✗ Join request rejected" else msg, !ok)
                                }
                            }
                        },
                        onResetStudentPassword = { studentEmail, studentName ->
                            scope.launch(Dispatchers.IO) {
                                val (ok, msg) = SupabaseAuthManager.resetPasswordForEmail(studentEmail)
                                withContext(Dispatchers.Main) {
                                    showBanner(if (ok) "Password reset link sent to $studentName ($studentEmail)!" else msg, !ok)
                                }
                            }
                        },
                        onRemoveStudent = { studentId ->
                            scope.launch(Dispatchers.IO) {
                                val (ok, msg) = com.vaibhav.facialattendancesystem.data.CloudSyncManager.leaveClass(clazz!!.classId, studentId, db, sessionManager)
                                refreshLocal()
                                withContext(Dispatchers.Main) {
                                    showBanner(if (ok) "✓ Student removed from class" else msg, !ok)
                                }
                            }
                        },
                        onDeleteClass = { targetClassId ->
                            val teacherId = clazz?.teacherId ?: sessionManager.getSession()?.userId ?: ""
                            scope.launch(Dispatchers.IO) {
                                val (success, msg) = com.vaibhav.facialattendancesystem.data.CloudSyncManager.deleteClass(targetClassId, teacherId, db, sessionManager)
                                withContext(Dispatchers.Main) {
                                    showBanner(if (success) "✓ Class deleted successfully" else msg, !success)
                                    if (success) {
                                        navController.popBackStack()
                                    }
                                }
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                } else if (isLoadingData) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .statusBarsPadding(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryCyan)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .statusBarsPadding()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Class details not available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { navController.popBackStack() }) {
                                Text("Back")
                            }
                        }
                    }
                }
            }

            composable(Screen.ClassroomAttendance.route) { backStack ->
                val classId = backStack.arguments?.getString("classId") ?: ""
                val routeClassName = decodeRouteParam(backStack.arguments?.getString("className") ?: "Class")
                var currentClass by remember { mutableStateOf<Clazz?>(null) }
                LaunchedEffect(classId) {
                    currentClass = withContext(Dispatchers.IO) { db.clazzDao().getClassById(classId) }
                }
                val className = currentClass?.className ?: routeClassName

                // Pre-generate sessionId here so RecognitionLog rows share the same ID as
                // the AttendanceSession that is inserted when the teacher confirms attendance.
                val sessionId = remember { java.util.UUID.randomUUID().toString() }

                var enrolledStudents by remember { mutableStateOf<List<Student>>(emptyList()) }
                var allEmbeddingsData by remember { mutableStateOf<List<Pair<Student, List<FloatArray>>>>(emptyList()) }
                var isLoadingRoster by remember { mutableStateOf(true) }

                LaunchedEffect(classId) {
                    withContext(Dispatchers.IO) {
                        try {
                            // Automatically pull any newly enrolled students and their face embeddings from cloud
                            com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncClassRosterForTeacher(classId, db)
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
                                .mapNotNull { converters.byteArrayToFloatArray(it.embeddingVector) }
                            if (embs.isNotEmpty()) {
                                list.add(s to embs)
                            }
                        }

                        withContext(Dispatchers.Main) {
                            enrolledStudents = students
                            allEmbeddingsData = list
                            isLoadingRoster = false
                        }
                    }
                }

                if (isLoadingRoster) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = PrimaryCyan)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Syncing class roster & biometrics...",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = { navController.popBackStack() }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    ClassroomAttendanceScreen(
                        className = className,
                        classId = classId,
                        enrolledStudents = enrolledStudents,
                        allEmbeddings = allEmbeddingsData,
                        sessionId = sessionId,
                        recognitionLogDao = db.recognitionLogDao(),
                        onConfirmAttendance = { p1Path, p2Path, presentStudentIds ->
                        scope.launch(Dispatchers.IO) {
                            try {
                                val session = AttendanceSession(
                                    // Reuse the same sessionId that was passed into the screen
                                    sessionId = sessionId,
                                    classId = classId,
                                    photo1Path = p1Path,
                                    photo1FacesDetected = presentStudentIds.size,
                                    photo2Path = p2Path,
                                    photo2FacesDetected = presentStudentIds.size,
                                    sessionStatus = "COMPLETED"
                                )
                                db.attendanceDao().insertSession(session)

                                val records = enrolledStudents.map { student ->
                                    val isPresent = if (presentStudentIds.contains(student.studentId)) 1 else 0
                                    AttendanceRecord(
                                        sessionId = session.sessionId,
                                        studentId = student.studentId,
                                        photo1Matched = isPresent,
                                        photo2Matched = isPresent,
                                        dualCapturePresent = isPresent,
                                        markedPresent = isPresent
                                    )
                                }
                                db.attendanceDao().insertAttendanceRecords(records)

                                // Show banner immediately after DB save
                                // Show instant feedback to teacher before cloud upload
                                val presentCount = records.count { it.markedPresent == 1 }
                                val absentCount = records.size - presentCount
                                withContext(Dispatchers.Main) {
                                    showBanner("✓ Attendance saved! $presentCount present, $absentCount absent. Syncing...", false)
                                    com.vaibhav.facialattendancesystem.util.NotificationHelper.showAttendanceNotification(
                                        context,
                                        "Attendance Marked: $className",
                                        "✓ $presentCount marked Present, $absentCount Absent"
                                    )
                                    navController.popBackStack()
                                }

                                // Upload to cloud in background (non-blocking)
                                val clazz = db.clazzDao().getClassById(classId)
                                if (clazz != null) {
                                    com.vaibhav.facialattendancesystem.data.CloudSyncManager.uploadNewClass(clazz)
                                }
                                val teacherId = clazz?.teacherId ?: sessionManager.getSession()?.userId
                                val uploaded = com.vaibhav.facialattendancesystem.data.CloudSyncManager.uploadAttendanceSession(session, records, teacherId)
                                if (uploaded) {
                                    db.attendanceDao().markSessionSynced(session.sessionId)
                                }
                            } catch (e: Throwable) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    showBanner("Error saving attendance: ${e.localizedMessage}", true)
                                    navController.popBackStack()
                                }
                            }
                        }
                    },
                    onCancel = { navController.popBackStack() }
                )
            }
        }

            composable(Screen.AttendanceHistory.route) { backStack ->
                val classId = backStack.arguments?.getString("classId") ?: ""
                val routeClassName = decodeRouteParam(backStack.arguments?.getString("className") ?: "Class")
                var currentClass by remember { mutableStateOf<Clazz?>(null) }
                LaunchedEffect(classId) {
                    currentClass = withContext(Dispatchers.IO) { db.clazzDao().getClassById(classId) }
                }
                val className = currentClass?.className ?: routeClassName

                var isSyncingSessions by remember { mutableStateOf(false) }

                val sessionsFlow = remember(classId) { db.attendanceDao().getSessionsForClass(classId) }
                val sessions by sessionsFlow.collectAsState(initial = emptyList())

                val classRecordsFlow = remember(classId) { db.attendanceDao().getAllRecordsForClass(classId) }
                val classRecords by classRecordsFlow.collectAsState(initial = emptyList())

                var enrolledStudentsForExport by remember { mutableStateOf<List<com.vaibhav.facialattendancesystem.data.Student>>(emptyList()) }

                val syncSessions: () -> Unit = {
                    scope.launch(Dispatchers.IO) {
                        withContext(Dispatchers.Main) { isSyncingSessions = true }
                        try {
                            com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncClassRosterForTeacher(classId, db)
                        } catch (e: Exception) { e.printStackTrace() }
                        val enrolledIds = db.classEnrollmentDao().getEnrolledStudentIds(classId)
                        val students = if (enrolledIds.isNotEmpty()) db.studentDao().getStudentsByIds(enrolledIds) else emptyList()
                        val (synced, msg) = com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncClassAttendanceSessionsForTeacher(classId, db)
                        withContext(Dispatchers.Main) {
                            enrolledStudentsForExport = students
                            isSyncingSessions = false
                            if (!synced) {
                                showBanner(msg, true)
                            }
                        }
                    }
                }

                LaunchedEffect(classId) {
                    syncSessions()
                    // Also sync student attendance in case a student navigated here
                    val currentSession = sessionManager.getSession()
                    if (currentSession?.userRole == "STUDENT") {
                        scope.launch(Dispatchers.IO) {
                            com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncStudentAttendance(currentSession.userId, db, context)
                        }
                    }
                }

                var currentClazz by remember { mutableStateOf<com.vaibhav.facialattendancesystem.data.Clazz?>(null) }
                LaunchedEffect(classId) {
                    currentClazz = withContext(Dispatchers.IO) { db.clazzDao().getClassById(classId) }
                }

                AttendanceHistoryScreen(
                    className = currentClazz?.className ?: className,
                    subject = currentClazz?.subject ?: "",
                    section = currentClazz?.section ?: "",
                    semester = currentClazz?.semester ?: 1,
                    teacherName = sessionManager.getSession()?.userName ?: "Faculty",
                    sessions = sessions,
                    allStudents = enrolledStudentsForExport,
                    allRecords = classRecords,
                    isSyncing = isSyncingSessions,
                    onRefresh = syncSessions,
                    onExportCsv = { session ->
                        scope.launch(Dispatchers.IO) {
                            val records = db.attendanceDao().getRecordsForSession(session.sessionId)
                            val students = db.studentDao().getStudentsByIds(records.map { it.studentId })
                            withContext(Dispatchers.Main) {
                                exportSessionToCsv(context, session, records, students) { ok, msg ->
                                    showBanner(msg, !ok)
                                }
                            }
                        }
                    },
                    onExportAdminReport = { startDate, endDate, threshold ->
                        scope.launch(Dispatchers.IO) {
                            var enrolledIds = db.classEnrollmentDao().getEnrolledStudentIds(classId)
                            if (enrolledIds.isEmpty()) {
                                try {
                                    com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncClassRosterForTeacher(classId, db)
                                } catch (e: Exception) { e.printStackTrace() }
                                enrolledIds = db.classEnrollmentDao().getEnrolledStudentIds(classId)
                            }
                            val students = if (enrolledIds.isNotEmpty()) db.studentDao().getStudentsByIds(enrolledIds) else emptyList()
                            val c = db.clazzDao().getClassById(classId)
                            val resolvedName = c?.className ?: className
                            val teacherName = sessionManager.getSession()?.userName ?: "Faculty"
                            withContext(Dispatchers.Main) {
                                exportAdminCollegeAttendanceCsv(
                                    context = context,
                                    className = resolvedName,
                                    subject = c?.subject ?: "",
                                    section = c?.section ?: "",
                                    semester = c?.semester ?: 1,
                                    teacherName = teacherName,
                                    sessions = sessions,
                                    allStudents = students,
                                    allRecords = classRecords,
                                    startDate = startDate,
                                    endDate = endDate,
                                    defaulterThresholdPct = threshold
                                ) { ok, msg ->
                                    showBanner(msg, !ok)
                                }
                            }
                        }
                    },
                    onExportFullReport = {
                        scope.launch(Dispatchers.IO) {
                            var enrolledIds = db.classEnrollmentDao().getEnrolledStudentIds(classId)
                            if (enrolledIds.isEmpty()) {
                                try {
                                    com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncClassRosterForTeacher(classId, db)
                                } catch (e: Exception) { e.printStackTrace() }
                                enrolledIds = db.classEnrollmentDao().getEnrolledStudentIds(classId)
                            }
                            val students = if (enrolledIds.isNotEmpty()) db.studentDao().getStudentsByIds(enrolledIds) else emptyList()
                            val c = db.clazzDao().getClassById(classId)
                            val resolvedName = c?.className ?: className
                            withContext(Dispatchers.Main) {
                                exportClassAttendanceCsv(context, resolvedName, sessions, students, classRecords) { ok, msg ->
                                    showBanner(msg, !ok)
                                }
                            }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.StudentClassAttendance.route) { backStack ->
                val classId = backStack.arguments?.getString("classId") ?: ""
                val routeClassName = decodeRouteParam(backStack.arguments?.getString("className") ?: "Class")

                val currentSession = sessionManager.getSession()
                val studentId = currentSession?.userId ?: ""

                var isSyncing by remember { mutableStateOf(false) }

                var currentClass by remember { mutableStateOf<com.vaibhav.facialattendancesystem.data.Clazz?>(null) }
                LaunchedEffect(classId) {
                    currentClass = withContext(Dispatchers.IO) { db.clazzDao().getClassById(classId) }
                }
                val className = currentClass?.className ?: routeClassName

                val sessionsFlow = remember(classId) { db.attendanceDao().getSessionsForClass(classId) }
                val sessions by sessionsFlow.collectAsState(initial = emptyList())

                val recordsFlow = remember(classId, studentId) { db.attendanceDao().getRecordsForStudentInClass(classId, studentId) }
                val records by recordsFlow.collectAsState(initial = emptyList())

                val syncAttendance: () -> Unit = {
                    scope.launch(Dispatchers.IO) {
                        withContext(Dispatchers.Main) { isSyncing = true }
                        // Quiet background sync without system notification
                        com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncStudentAttendance(studentId, db, context, notify = false)
                        val updated = db.clazzDao().getClassById(classId)
                        withContext(Dispatchers.Main) {
                            currentClass = updated
                            isSyncing = false
                        }
                    }
                }

                LaunchedEffect(classId) {
                    syncAttendance()
                }

                val teacherUserFlow = remember(currentClass?.teacherId) {
                    val tId = currentClass?.teacherId ?: ""
                    db.userDao().getUserByIdFlow(tId)
                }
                val teacherUser by teacherUserFlow.collectAsState(initial = null)

                com.vaibhav.facialattendancesystem.ui.student.StudentClassAttendanceScreen(
                    className = className,
                    subject = currentClass?.subject ?: "",
                    teacherUserId = currentClass?.teacherId ?: "",
                    teacherName = teacherUser?.name ?: "",
                    sessions = sessions,
                    records = records,
                    isSyncing = isSyncing,
                    onRefresh = syncAttendance,
                    onLeaveClass = {
                        scope.launch(Dispatchers.IO) {
                            val (success, msg) = com.vaibhav.facialattendancesystem.data.CloudSyncManager.leaveClass(classId, studentId, db, sessionManager)
                            withContext(Dispatchers.Main) {
                                showBanner(if (success) "Left $className successfully" else msg, !success)
                                if (success) {
                                    navController.popBackStack()
                                }
                            }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        val currentUpdate = availableUpdate
        if (currentUpdate != null) {
            val (latestTag, releaseNotes, downloadUrl) = currentUpdate
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { availableUpdate = null },
                title = {
                    Text("Update Available ($latestTag)", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = if (releaseNotes.length > 300) releaseNotes.take(300) + "..." else releaseNotes,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    androidx.compose.material3.Button(
                        onClick = {
                            availableUpdate = null
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(downloadUrl)
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    ) {
                        Text("Download")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { availableUpdate = null }) {
                        Text("Later")
                    }
                }
            )
        }

        InAppBanner(
            bannerData = bannerData,
            onDismiss = { bannerData = null },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
}
}
