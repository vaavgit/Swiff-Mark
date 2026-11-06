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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vaibhav.facialattendancesystem.viewmodel.AppViewModelFactory
import com.vaibhav.facialattendancesystem.viewmodel.AuthViewModel
import com.vaibhav.facialattendancesystem.viewmodel.AttendanceViewModel
import com.vaibhav.facialattendancesystem.viewmodel.ClassDetailsViewModel
import com.vaibhav.facialattendancesystem.viewmodel.StudentDashboardViewModel
import com.vaibhav.facialattendancesystem.viewmodel.TeacherDashboardViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.vaibhav.facialattendancesystem.ui.theme.PrimaryCyan
import com.vaibhav.facialattendancesystem.ui.theme.ErrorRose
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll


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
    object SignUp : Screen("signup?isTeacher={isTeacher}") {
        fun createRoute(isTeacher: Boolean = false): String = "signup?isTeacher=$isTeacher"
    }
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
    onClearRecoveryToken: () -> Unit = {},
    targetClass: Pair<String, String>? = null,
    onClearTargetClass: () -> Unit = {}
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

    val handleLogout: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            try {
                db.faceEmbeddingDao().deleteAllEmbeddings()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        sessionManager.clearSession()
        com.vaibhav.facialattendancesystem.data.CloudSyncManager.setAuthToken(null)
        navController.navigate(Screen.Login.route) {
            popUpTo(0)
        }
    }

    var isDarkTheme by remember { mutableStateOf(sessionManager.isDarkTheme()) }
    val authViewModel: AuthViewModel = viewModel(factory = AppViewModelFactory(db, sessionManager))
    val isLoading by authViewModel.isLoading.collectAsStateWithLifecycle()
    val authError by authViewModel.authError.collectAsStateWithLifecycle()
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

    LaunchedEffect(targetClass) {
        if (targetClass != null && savedSession != null) {
            val (classId, className) = targetClass
            if (savedSession.userRole == "STUDENT") {
                navController.navigate(Screen.StudentClassAttendance.createRoute(classId, className))
            } else if (savedSession.userRole == "TEACHER") {
                navController.navigate(Screen.AttendanceHistory.createRoute(classId, className))
            }
            onClearTargetClass()
        }
    }
    val savedToken = remember { sessionManager.getAccessToken() }
    LaunchedEffect(savedToken) {
        if (!savedToken.isNullOrBlank()) {
            com.vaibhav.facialattendancesystem.data.CloudSyncManager.setAuthToken(savedToken)
        }
    }

    var availableUpdate by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var updateDownloadProgress by remember { mutableStateOf(0f) }
    var updateDownloadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        com.vaibhav.facialattendancesystem.data.CloudSyncManager.purgeAutoFaceAvatarsOnce(context)
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
                    initialPassword = "",
                    initialIsTeacher = sessionManager.getSavedIsTeacher(),
                    initialSaveLogin = sessionManager.isSaveLoginEnabled(),
                    onLoginClick = { email, password, isTeacher, saveLogin ->
                        authViewModel.login(
                            email = email,
                            password = password,
                            isTeacher = isTeacher,
                            saveLogin = saveLogin,
                            onNavigateTeacher = { teacherId, name ->
                                navController.navigate(Screen.TeacherDashboard.createRoute(teacherId, name)) {
                                    popUpTo(0)
                                }
                            },
                            onNavigateStudentDashboard = { studentId ->
                                navController.navigate(Screen.StudentDashboard.createRoute(studentId)) {
                                    popUpTo(0)
                                }
                            },
                            onNavigateStudentEnrollment = { studentId, name ->
                                navController.navigate(Screen.StudentEnrollment.createRoute(studentId, name)) {
                                    popUpTo(0)
                                }
                            },
                            onBanner = showBanner
                        )
                    },
                    onNavigateToSignUp = { isTeacher ->
                        authViewModel.clearError()
                        navController.navigate(Screen.SignUp.createRoute(isTeacher))
                    },
                    onForgotPasswordClick = {
                        authViewModel.clearError()
                        navController.navigate(Screen.ForgotPassword.route)
                    },
                    onResendVerificationClick = { unverifiedEmail ->
                        authViewModel.resendVerification(unverifiedEmail) { success, resMsg ->
                            showBanner(resMsg, !success)
                        }
                    },
                    errorMessage = authError,
                    isLoading = isLoading
                )
            }

            composable(
                route = Screen.SignUp.route,
                arguments = listOf(
                    androidx.navigation.navArgument("isTeacher") {
                        type = androidx.navigation.NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val isTeacher = backStackEntry.arguments?.getBoolean("isTeacher") ?: false
                SignUpScreen(
                    initialIsTeacher = isTeacher,
                    onStudentSignUpClick = { name, rollNo, email, pass, section ->
                        authViewModel.signUpStudent(
                            name = name,
                            rollNo = rollNo,
                            email = email,
                            password = pass,
                            section = section,
                            onNavigateToLogin = {
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(Screen.SignUp.route) { inclusive = true }
                                }
                            },
                            onNavigateToEnrollment = { studentId, studentName ->
                                navController.navigate(Screen.StudentEnrollment.createRoute(studentId, studentName))
                            },
                            onBanner = showBanner
                        )
                    },
                    onTeacherSignUpClick = { name, department, email, pass ->
                        authViewModel.signUpTeacher(
                            name = name,
                            department = department,
                            email = email,
                            password = pass,
                            onNavigateToLogin = {
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(Screen.SignUp.route) { inclusive = true }
                                }
                            },
                            onNavigateToTeacherDashboard = { teacherId, teacherName ->
                                navController.navigate(Screen.TeacherDashboard.createRoute(teacherId, teacherName))
                            },
                            onBanner = showBanner
                        )
                    },
                    onNavigateToLogin = {
                        authViewModel.clearError()
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
                        authViewModel.sendResetEmail(email) { ok, msg ->
                            forgotLoading = false
                            forgotMsg = msg
                            forgotIsError = !ok
                        }
                    },
                    onBackToLogin = { navController.navigate(Screen.Login.route) },
                    onManualTokenEntered = { token ->
                        navController.navigate(Screen.ResetPassword.createRoute(token))
                    },
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
                        authViewModel.resetPassword(token, newPassword) { ok, msg ->
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

                val context = LocalContext.current
                val prefs = remember { context.getSharedPreferences("swiffmark_consent", android.content.Context.MODE_PRIVATE) }
                val consentKey = "biometric_consent_$studentId"
                var consentGiven by remember { mutableStateOf(prefs.getBoolean(consentKey, false)) }
                var showConsentDialog by remember { mutableStateOf(!consentGiven) }

                if (showConsentDialog) {
                    AlertDialog(
                        onDismissRequest = { /* Non-dismissable */ },
                        title = {
                            Text(
                                text = "📷 Biometric Data Consent",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Swiff Mark will capture your face to create a unique mathematical face profile for attendance marking.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Your face photo is NOT stored — only an encrypted mathematical embedding is saved.\n" +
                                           "• This data is used exclusively for attendance identification.\n" +
                                           "• You can delete your face profile at any time from Profile settings.\n" +
                                           "• Your data is protected under India's DPDP Act 2023 and our Privacy Policy.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    prefs.edit().putBoolean(consentKey, true).apply()
                                    consentGiven = true
                                    showConsentDialog = false
                                }
                            ) {
                                Text("I Agree & Continue")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    navController.popBackStack()
                                }
                            ) {
                                Text("Decline", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }

                if (consentGiven) {
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
                            } + listOf(
                                FaceEmbedding(
                                    studentId = studentId,
                                    embeddingVector = converters.floatArrayToByteArray(averagedVector) ?: ByteArray(0),
                                    sourceAngle = "AVERAGED",
                                    qualityScore = 1.0f
                                )
                            )
                            // Atomic replace: delete + insert in a single SQLite transaction
                            // so a crash mid-way leaves the student with their old embeddings.
                            db.faceEmbeddingDao().replaceEmbeddingsForStudent(studentId, embeddings)

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

                            // Sync profile and multi-angle embeddings to cloud in background (do NOT upload face photos)
                            if (student != null) {
                                val updatedStudent = student.copy(enrollmentStatus = 1)
                                val allVectorsWithAngles = capturedSteps.map { it.stepAngle to it.embedding } + listOf("FUSED_AVERAGE" to averagedVector)
                                com.vaibhav.facialattendancesystem.data.CloudSyncManager.uploadStudentProfile(
                                    student = updatedStudent,
                                    embeddingsWithAngles = allVectorsWithAngles
                                )
                            }
                        }
                    }
                )
                } // end if (consentGiven)
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

                val studentDashboardViewModel: StudentDashboardViewModel = viewModel(
                    factory = AppViewModelFactory(db, sessionManager)
                )
                val isSyncing by studentDashboardViewModel.isSyncing.collectAsStateWithLifecycle()

                // Kick off the continuous background sync loop — cancelled automatically when VM is cleared.
                LaunchedEffect(studentId) {
                    studentDashboardViewModel.startSyncLoop(studentId)
                }

                // Extra sync on every ON_RESUME (user returns from another screen or background).
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, studentId) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME && studentId.isNotBlank() && !isSyncing) {
                            studentDashboardViewModel.syncOnResume(studentId)
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
                            studentDashboardViewModel.refreshCloud(studentId) { synced, msg ->
                                showBanner(if (synced) "Refreshed enrolled classes & attendance" else msg, !synced)
                            }
                        },
                        onJoinClassByCode = { code ->
                            studentDashboardViewModel.joinClass(code, studentId) { success, message ->
                                if (success) {
                                    showBanner("Joined $message!", false)
                                } else {
                                    showBanner(message, true)
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
                        onLogout = handleLogout
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
                        onDeleteFaceProfile = {
                            scope.launch(Dispatchers.IO) {
                                // 1. Delete locally from Room
                                db.faceEmbeddingDao().deleteEmbeddingsForStudent(currentStudent.studentId)
                                // 2. Reset enrollmentStatus to 0 in local DB
                                db.studentDao().insertStudent(currentStudent.copy(enrollmentStatus = 0))
                                // 3. Delete from Supabase
                                com.vaibhav.facialattendancesystem.data.CloudSyncManager.deleteStudentFaceEmbeddings(
                                    currentStudent.studentId
                                )
                                withContext(Dispatchers.Main) {
                                    showBanner("✓ Face profile deleted.", false)
                                    // Navigate to enrollment so the student can re-enroll immediately
                                    navController.navigate(
                                        Screen.StudentEnrollment.createRoute(currentStudent.studentId, currentStudent.fullName)
                                    )
                                }
                            }
                        },
                        onLogout = handleLogout,
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

                val teacherDashboardViewModel: TeacherDashboardViewModel = viewModel(
                    factory = AppViewModelFactory(db, sessionManager)
                )
                val isSyncing by teacherDashboardViewModel.isSyncing.collectAsStateWithLifecycle()

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
                        clazz.classId to allEnrollments.count { it.classId == clazz.classId && it.enrollmentVerified == 1 }
                    }
                }
                val lecturesCountsMap = remember(classes, allSessions) {
                    classes.associate { clazz ->
                        clazz.classId to allSessions.count { it.classId == clazz.classId }
                    }
                }

                // Kick off the continuous background sync loop — cancelled automatically when VM is cleared.
                LaunchedEffect(teacherId) {
                    teacherDashboardViewModel.startSyncLoop(teacherId)
                }

                // Extra sync on every ON_RESUME (user returns from class details or background).
                val teacherLifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(teacherLifecycleOwner, teacherId) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME && teacherId.isNotBlank() && !isSyncing) {
                            teacherDashboardViewModel.syncOnResume(teacherId)
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
                        teacherDashboardViewModel.refreshCloud(teacherId) { synced, msg ->
                            showBanner(msg, !synced)
                        }
                    },
                    onCreateClass = { className, subject, semester, section ->
                        val code = generateClassCode(subject)
                        teacherDashboardViewModel.createClass(teacherId, className, subject, semester, section, code) { _, msg ->
                            showBanner(msg, false)
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
                        teacherDashboardViewModel.deleteClass(classId, teacherId) { success, msg ->
                            showBanner(if (success) "Class deleted successfully" else msg, !success)
                        }
                    },
                    onLogout = handleLogout
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
                            try {
                                val teacherId = savedSession?.userId ?: ""
                                val classes = db.clazzDao().getClassesListForTeacher(teacherId)
                                for (c in classes) {
                                    com.vaibhav.facialattendancesystem.data.CloudSyncManager.deleteClass(
                                        c.classId,
                                        c.teacherId,
                                        db,
                                        sessionManager
                                    )
                                }
                                val purged = db.purgeClassesAndAttendanceData()
                                withContext(Dispatchers.Main) {
                                    if (purged) {
                                        showBanner("Classes & attendance cleared.", false)
                                    } else {
                                        showBanner("Error clearing records.", true)
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("Navigation", "Error purging records", e)
                                withContext(Dispatchers.Main) {
                                    showBanner("Error clearing records.", true)
                                }
                            }
                        }
                    },
                    onLogout = handleLogout,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ClassDetails.route) { backStack ->
                val classId = backStack.arguments?.getString("classId") ?: ""

                val classDetailsViewModel: ClassDetailsViewModel = viewModel(
                    factory = AppViewModelFactory(db, sessionManager)
                )
                val clazz by classDetailsViewModel.clazz.collectAsStateWithLifecycle()
                val enrolledStudents by classDetailsViewModel.enrolledStudents.collectAsStateWithLifecycle()
                val pendingStudents by classDetailsViewModel.pendingStudents.collectAsStateWithLifecycle()
                val isSyncingRoster by classDetailsViewModel.isSyncingRoster.collectAsStateWithLifecycle()
                val isLoadingData by classDetailsViewModel.isLoadingData.collectAsStateWithLifecycle()

                val classRecordsFlow = remember(classId) { db.attendanceDao().getAllRecordsForClass(classId) }
                val classRecords by classRecordsFlow.collectAsState(initial = emptyList())

                val classSessionsFlow = remember(classId) { db.attendanceDao().getSessionsForClass(classId) }
                val classSessions by classSessionsFlow.collectAsState(initial = emptyList())

                LaunchedEffect(classId) {
                    classDetailsViewModel.loadAndSync(classId)
                }

                // Live flow of pending join requests for this class
                val pendingEnrollmentsFlow = remember(classId) {
                    db.classEnrollmentDao().getPendingEnrollmentsForClassFlow(classId)
                }
                val pendingEnrollments by pendingEnrollmentsFlow.collectAsState(initial = emptyList())

                // Resolve student objects for pending enrollments through ViewModel
                LaunchedEffect(pendingEnrollments) {
                    classDetailsViewModel.refreshPendingStudents(classId, pendingEnrollments.map { it.studentId })
                }

                if (clazz != null) {
                    ClassDetailsScreen(
                        clazz = clazz!!,
                        enrolledStudents = enrolledStudents,
                        pendingStudents = pendingStudents,
                        attendanceRecords = classRecords,
                        totalSessionsCount = classSessions.count { it.sessionStatus == "COMPLETED" },
                        isSyncing = isSyncingRoster,
                        onMarkAttendance = {
                            navController.navigate(Screen.ClassroomAttendance.createRoute(clazz!!.classId, clazz!!.className))
                        },
                        onRefresh = {
                            classDetailsViewModel.refreshRoster(classId)
                        },
                        onApproveStudent = { studentId ->
                            classDetailsViewModel.approveStudent(classId, studentId) { ok, msg ->
                                showBanner(if (ok) "Student approved and added to class" else msg, !ok)
                            }
                        },
                        onRejectStudent = { studentId ->
                            classDetailsViewModel.rejectStudent(classId, studentId) { ok, msg ->
                                showBanner(if (ok) "Join request rejected" else msg, !ok)
                            }
                        },
                        onResetStudentPassword = { studentEmail, studentName ->
                            classDetailsViewModel.resetStudentPassword(studentEmail, studentName) { ok, msg ->
                                showBanner(if (ok) "Password reset link sent to $studentName ($studentEmail)!" else msg, !ok)
                            }
                        },
                        onRemoveStudent = { studentId ->
                            classDetailsViewModel.removeStudent(classId, studentId) { ok, msg ->
                                showBanner(if (ok) "Student removed from class" else msg, !ok)
                            }
                        },
                        onDeleteClass = { targetClassId ->
                            val teacherId = clazz?.teacherId ?: sessionManager.getSession()?.userId ?: ""
                            classDetailsViewModel.deleteClass(targetClassId, teacherId) { success, msg ->
                                showBanner(if (success) "Class deleted successfully" else msg, !success)
                                if (success) {
                                    navController.popBackStack()
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

                val attendanceViewModel: AttendanceViewModel = viewModel(
                    factory = AppViewModelFactory(db, sessionManager)
                )
                val enrolledStudents by attendanceViewModel.enrolledStudents.collectAsStateWithLifecycle()
                val allEmbeddingsData by attendanceViewModel.embeddingsData.collectAsStateWithLifecycle()
                val isLoadingRoster by attendanceViewModel.isLoadingRoster.collectAsStateWithLifecycle()

                LaunchedEffect(classId) {
                    attendanceViewModel.loadRosterAndEmbeddings(classId)
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
                        onConfirmAttendance = { p1Path, p2Path, presentStudentIds, p1Statuses, p2Statuses, p1FaceCount, p2FaceCount ->
                        scope.launch(Dispatchers.IO) {
                            try {
                                val session = AttendanceSession(
                                    // Reuse the same sessionId that was passed into the screen
                                    sessionId = sessionId,
                                    classId = classId,
                                    photo1Path = p1Path,
                                    photo1FacesDetected = p1FaceCount,
                                    photo2Path = p2Path,
                                    photo2FacesDetected = p2FaceCount,
                                    sessionStatus = "COMPLETED"
                                )
                                db.attendanceDao().insertSession(session)

                                val p1Map = p1Statuses.associateBy { it.student.studentId }
                                val p2Map = p2Statuses.associateBy { it.student.studentId }

                                val records = enrolledStudents.map { student ->
                                    val isFinalPresent = if (presentStudentIds.contains(student.studentId)) 1 else 0
                                    val p1 = p1Map[student.studentId]
                                    val p2 = p2Map[student.studentId]
                                    val p1Matched = if (p1?.isMarkedPresent == true) 1 else 0
                                    val p2Matched = if (p2?.isMarkedPresent == true) 1 else 0
                                    val dualPresent = if (p1Matched == 1 && p2Matched == 1) 1 else 0

                                    AttendanceRecord(
                                        sessionId = session.sessionId,
                                        studentId = student.studentId,
                                        photo1MatchConfidence = p1?.matchConfidence,
                                        photo1Matched = p1Matched,
                                        photo2MatchConfidence = p2?.matchConfidence,
                                        photo2Matched = p2Matched,
                                        dualCapturePresent = dualPresent,
                                        markedPresent = isFinalPresent
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

                val attendanceHistoryViewModel: AttendanceViewModel = viewModel(
                    factory = AppViewModelFactory(db, sessionManager)
                )
                val isSyncingSessions by attendanceHistoryViewModel.isSyncingSessions.collectAsStateWithLifecycle()

                val sessionsFlow = remember(classId) { db.attendanceDao().getSessionsForClass(classId) }
                val sessions by sessionsFlow.collectAsState(initial = emptyList())

                val classRecordsFlow = remember(classId) { db.attendanceDao().getAllRecordsForClass(classId) }
                val classRecords by classRecordsFlow.collectAsState(initial = emptyList())

                var enrolledStudentsForExport by remember { mutableStateOf<List<com.vaibhav.facialattendancesystem.data.Student>>(emptyList()) }

                val syncSessions: () -> Unit = {
                    attendanceHistoryViewModel.syncSessions(classId) { synced, msg ->
                        if (!synced) showBanner(msg, true)
                    }
                }

                LaunchedEffect(classId) {
                    withContext(Dispatchers.IO) {
                        val enrolledIds = db.classEnrollmentDao().getEnrolledStudentIds(classId)
                        val students = if (enrolledIds.isNotEmpty()) db.studentDao().getStudentsByIds(enrolledIds) else emptyList()
                        withContext(Dispatchers.Main) { enrolledStudentsForExport = students }
                    }
                    syncSessions()
                    // Also sync student attendance in case a student navigated here
                    val currentSession = sessionManager.getSession()
                    if (currentSession?.userRole == "STUDENT") {
                        scope.launch(Dispatchers.IO) {
                            // notify=false — don't fire system notifications from the history screen
                            com.vaibhav.facialattendancesystem.data.CloudSyncManager.syncStudentAttendance(currentSession.userId, db, context, notify = false)
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
                    onToggleAttendanceRecord = { sessionId, studentId, isPresent ->
                        scope.launch(Dispatchers.IO) {
                            val existing = db.attendanceDao().getRecordsForSession(sessionId).find { it.studentId == studentId }
                            val teacherId = sessionManager.getSession()?.userId ?: "TEACHER"
                            val now = System.currentTimeMillis()
                            val updatedRecord = existing?.copy(
                                markedPresent = if (isPresent) 1 else 0,
                                manualOverride = 1,
                                manuallyOverriddenAt = now,
                                overriddenBy = teacherId,
                                overrideNotes = "Manual override by teacher"
                            ) ?: com.vaibhav.facialattendancesystem.data.AttendanceRecord(
                                sessionId = sessionId,
                                studentId = studentId,
                                markedPresent = if (isPresent) 1 else 0,
                                manualOverride = 1,
                                manuallyOverriddenAt = now,
                                overriddenBy = teacherId,
                                overrideNotes = "Manual override by teacher"
                            )
                            db.attendanceDao().insertAttendanceRecord(updatedRecord)
                            com.vaibhav.facialattendancesystem.data.CloudSyncManager.updateSingleAttendanceRecord(sessionId, studentId, isPresent)
                            withContext(Dispatchers.Main) {
                                val sName = enrolledStudentsForExport.find { it.studentId == studentId }?.fullName ?: "Student"
                                val st = if (isPresent) "Present" else "Absent"
                                showBanner("$sName marked $st", false)
                            }
                        }
                    },
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

                val teacherId = currentClass?.teacherId ?: ""
                val teacherUserFlow = remember(teacherId) {
                    db.userDao().getUserByIdFlow(teacherId)
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
                onDismissRequest = {
                    if (!isDownloadingUpdate) {
                        availableUpdate = null
                        updateDownloadError = null
                    }
                },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text("Update Available ($latestTag)", fontWeight = FontWeight.Bold, color = PrimaryCyan)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (isDownloadingUpdate) {
                            Text(
                                text = "Downloading update... ${(updateDownloadProgress * 100).toInt()}%",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { updateDownloadProgress },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = PrimaryCyan
                            )
                        } else {
                            if (updateDownloadError != null) {
                                Text(
                                    text = updateDownloadError ?: "",
                                    color = ErrorRose,
                                    fontSize = 12.5.sp
                                )
                            }
                            Text(
                                text = if (releaseNotes.length > 300) releaseNotes.take(300) + "..." else releaseNotes,
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.Button(
                        onClick = {
                            if (!isDownloadingUpdate) {
                                isDownloadingUpdate = true
                                updateDownloadProgress = 0f
                                updateDownloadError = null
                                scope.launch(Dispatchers.IO) {
                                    val apkFile = com.vaibhav.facialattendancesystem.data.CloudSyncManager.downloadApk(
                                        context = context,
                                        downloadUrl = downloadUrl,
                                        onProgress = { p -> updateDownloadProgress = p }
                                    )
                                    withContext(Dispatchers.Main) {
                                        isDownloadingUpdate = false
                                        if (apkFile != null) {
                                            val launched = com.vaibhav.facialattendancesystem.data.CloudSyncManager.launchApkInstall(context, apkFile)
                                            if (launched) {
                                                availableUpdate = null
                                            } else {
                                                updateDownloadError = "Could not open installer. Please grant install permission."
                                            }
                                        } else {
                                            updateDownloadError = "Download failed. Please check internet connection."
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isDownloadingUpdate,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                    ) {
                        Text(
                            text = if (isDownloadingUpdate) "DOWNLOADING..." else "UPDATE NOW",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                dismissButton = {
                    if (!isDownloadingUpdate) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                try {
                                    val browserIntent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(downloadUrl)
                                    ).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(browserIntent)
                                } catch (_: Exception) {}
                            }) {
                                Text("BROWSER", color = PrimaryCyan)
                            }
                            TextButton(onClick = {
                                availableUpdate = null
                                updateDownloadError = null
                            }) {
                                Text("LATER", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            )
        }

        InAppBanner(
            bannerData = bannerData,
            onDismiss = { bannerData = null },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
}
}
