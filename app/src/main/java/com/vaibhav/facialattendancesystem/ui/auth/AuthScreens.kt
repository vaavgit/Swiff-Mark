package com.vaibhav.facialattendancesystem.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.facialattendancesystem.ui.components.adaptiveBorderColor
import com.vaibhav.facialattendancesystem.ui.components.isAppDarkTheme
import com.vaibhav.facialattendancesystem.ui.theme.*

// ─────────────────────────────────────────────────────────────────
// Minimalist Eye Icon (Eye Toggle for Passwords)
// ─────────────────────────────────────────────────────────────────

@Composable
fun MinimalistEyeIcon(
    visible: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.6.dp.toPx()

        if (visible) {
            val eyePath = Path().apply {
                moveTo(w * 0.08f, h * 0.5f)
                quadraticBezierTo(w * 0.5f, h * 0.12f, w * 0.92f, h * 0.5f)
                quadraticBezierTo(w * 0.5f, h * 0.88f, w * 0.08f, h * 0.5f)
                close()
            }
            drawPath(path = eyePath, color = tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            drawCircle(color = tint, radius = w * 0.18f, center = Offset(w * 0.5f, h * 0.5f))
        } else {
            val eyePath = Path().apply {
                moveTo(w * 0.08f, h * 0.5f)
                quadraticBezierTo(w * 0.5f, h * 0.12f, w * 0.92f, h * 0.5f)
                quadraticBezierTo(w * 0.5f, h * 0.88f, w * 0.08f, h * 0.5f)
                close()
            }
            drawPath(path = eyePath, color = tint.copy(alpha = 0.50f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            drawCircle(color = tint.copy(alpha = 0.50f), radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.5f))
            drawLine(
                color = tint,
                start = Offset(w * 0.14f, h * 0.14f),
                end = Offset(w * 0.86f, h * 0.86f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Prototype Reusable Components
// ─────────────────────────────────────────────────────────────────

@Composable
fun PrototypeSegmentedTab(
    options: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = adaptiveBorderColor()
    val isDark = isAppDarkTheme()
    val selectedFill = if (isDark) Color.White.copy(alpha = 0.10f) else Color(0x140F172A)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(Color.Transparent)
    ) {
        options.forEachIndexed { index, title ->
            val isSelected = selectedIndex == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (isSelected) selectedFill else Color.Transparent)
                    .clickable { onSelectIndex(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PrototypeFieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val borderColor = adaptiveBorderColor()

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(PrimaryCyan),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                ),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                fontSize = 13.sp
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.weight(1f)
            )
            if (isPassword && onTogglePasswordVisibility != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onTogglePasswordVisibility),
                    contentAlignment = Alignment.Center
                ) {
                    MinimalistEyeIcon(visible = passwordVisible)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Login Screen (Exact Facial Attendance Prototype Layout)
// ─────────────────────────────────────────────────────────────────

@Composable
fun LoginScreen(
    onLoginClick: (email: String, password: String, isTeacher: Boolean, saveLogin: Boolean) -> Unit,
    onNavigateToSignUp: () -> Unit,
    onForgotPasswordClick: () -> Unit = {},
    onResendVerificationClick: (String) -> Unit = {},
    initialEmail: String = "",
    initialPassword: String = "",
    initialIsTeacher: Boolean = false,
    initialSaveLogin: Boolean = false,
    errorMessage: String? = null,
    isLoading: Boolean = false
) {
    var email by remember { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf(initialPassword) }
    var isTeacherSelected by remember { mutableStateOf(initialIsTeacher) }
    var saveLoginInfo by remember { mutableStateOf(initialSaveLogin) }
    var passwordVisible by remember { mutableStateOf(false) }

    val isDark = isAppDarkTheme()
    val isFormValid = !isLoading && email.isNotBlank() && password.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Swiff Mark",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Text(
                text = "AI-Powered Attendance System",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Segmented portal switch
            PrototypeSegmentedTab(
                options = listOf("Student portal", "Teacher portal"),
                selectedIndex = if (isTeacherSelected) 1 else 0,
                onSelectIndex = { isTeacherSelected = (it == 1) },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Email
            PrototypeFieldRow(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                placeholder = "name@school.edu",
                keyboardType = KeyboardType.Email
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Password
            PrototypeFieldRow(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                placeholder = "••••••••",
                isPassword = true,
                passwordVisible = passwordVisible,
                onTogglePasswordVisibility = { passwordVisible = !passwordVisible }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Save login info checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { saveLoginInfo = !saveLoginInfo }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = saveLoginInfo,
                    onCheckedChange = { saveLoginInfo = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = PrimaryCyan,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        checkmarkColor = Color.White
                    ),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save login info",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ErrorRose.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = error,
                            color = ErrorRose,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center
                        )
                        if (error.contains("verified", ignoreCase = true) || error.contains("confirm", ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Resend Verification Email ✉",
                                color = PrimaryCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onResendVerificationClick(email.trim()) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Log in button
            Button(
                onClick = { onLoginClick(email.trim(), password.trim(), isTeacherSelected, saveLoginInfo) },
                enabled = isFormValid,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryCyan,
                    contentColor = Color.White,
                    disabledContainerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                    disabledContentColor = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Log in", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Forgot password?",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryCyan,
                modifier = Modifier.clickable { onForgotPasswordClick() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "New here? Create account",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onNavigateToSignUp() }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Sign Up Screen (Exact Facial Attendance Prototype Layout)
// ─────────────────────────────────────────────────────────────────

@Composable
fun SignUpScreen(
    onStudentSignUpClick: (name: String, rollNo: Int, email: String, pass: String, section: String) -> Unit,
    onTeacherSignUpClick: (name: String, subject: String, department: String, email: String, pass: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    errorMessage: String? = null,
    isLoading: Boolean = false
) {
    var isTeacherRole by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var rollNumberStr by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var classSection by remember { mutableStateOf("B1") }
    var teacherSubject by remember { mutableStateOf("Machine Learning") }
    var teacherDepartment by remember { mutableStateOf("CSE") }
    var hasConsented by remember { mutableStateOf(false) }

    val isDark = isAppDarkTheme()

    val isStudentValid = fullName.isNotBlank() && email.isNotBlank() && password.length >= 8 && rollNumberStr.isNotBlank() && hasConsented
    val isTeacherValid = fullName.isNotBlank() && email.isNotBlank() && password.length >= 8 && hasConsented
    val isFormValid = !isLoading && (if (isTeacherRole) isTeacherValid else isStudentValid)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Create account",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Segmented student / teacher
            PrototypeSegmentedTab(
                options = listOf("Student", "Teacher"),
                selectedIndex = if (isTeacherRole) 1 else 0,
                onSelectIndex = { isTeacherRole = (it == 1) },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Full name
            PrototypeFieldRow(
                value = fullName,
                onValueChange = { fullName = it },
                label = "Full name",
                placeholder = "Arjun Kumar"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Email
            PrototypeFieldRow(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                placeholder = "name@school.edu",
                keyboardType = KeyboardType.Email
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Password
            PrototypeFieldRow(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                placeholder = "••••••••",
                isPassword = true,
                passwordVisible = passwordVisible,
                onTogglePasswordVisibility = { passwordVisible = !passwordVisible }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Role specific fields
            if (!isTeacherRole) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PrototypeFieldRow(
                        value = rollNumberStr,
                        onValueChange = { rollNumberStr = it },
                        label = "Roll number",
                        placeholder = "21BCE1001",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                    PrototypeFieldRow(
                        value = classSection,
                        onValueChange = { classSection = it },
                        label = "Section",
                        placeholder = "B1",
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PrototypeFieldRow(
                        value = teacherSubject,
                        onValueChange = { teacherSubject = it },
                        label = "Subject",
                        placeholder = "Machine Learning",
                        modifier = Modifier.weight(1f)
                    )
                    PrototypeFieldRow(
                        value = teacherDepartment,
                        onValueChange = { teacherDepartment = it },
                        label = "Department",
                        placeholder = "CSE",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Consent checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { hasConsented = !hasConsented }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(
                    checked = hasConsented,
                    onCheckedChange = { hasConsented = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = PrimaryCyan,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        checkmarkColor = Color.White
                    ),
                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I consent to my facial data being processed for attendance recognition",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "A verification link will be sent to your email.",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.fillMaxWidth().padding(start = 26.dp)
            )

            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = ErrorRose.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                    Text(text = error, color = ErrorRose, fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(10.dp))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Create account button
            Button(
                onClick = {
                    if (isTeacherRole) {
                        onTeacherSignUpClick(fullName.trim(), teacherSubject.trim(), teacherDepartment.trim(), email.trim(), password.trim())
                    } else {
                        val rollNo = rollNumberStr.trim().toIntOrNull() ?: 0
                        onStudentSignUpClick(fullName.trim(), rollNo, email.trim(), password.trim(), classSection.trim())
                    }
                },
                enabled = isFormValid,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryCyan,
                    contentColor = Color.White,
                    disabledContainerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                    disabledContentColor = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Create account", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Already have an account? Log in",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onNavigateToLogin() }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Forgot Password Screen (Exact Facial Attendance Prototype Layout)
// ─────────────────────────────────────────────────────────────────

@Composable
fun ForgotPasswordScreen(
    onSendResetLink: (email: String) -> Unit,
    onBackToLogin: () -> Unit,
    isLoading: Boolean = false,
    statusMessage: String? = null,
    isError: Boolean = false
) {
    var email by remember { mutableStateOf("") }
    val isDark = isAppDarkTheme()
    val isFormValid = !isLoading && email.isNotBlank() && email.contains("@")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Reset your password",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Enter your registered email",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            PrototypeFieldRow(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                placeholder = "name@school.edu",
                keyboardType = KeyboardType.Email
            )

            statusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isError) ErrorRose.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = msg,
                        color = if (isError) ErrorRose else SuccessGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = { onSendResetLink(email.trim()) },
                enabled = isFormValid,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryCyan,
                    contentColor = Color.White,
                    disabledContainerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                    disabledContentColor = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Send recovery link", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Back to login",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onBackToLogin() }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Reset Password Screen (Exact Facial Attendance Prototype Layout)
// ─────────────────────────────────────────────────────────────────

@Composable
fun ResetPasswordScreen(
    recoveryToken: String,
    onResetPasswordSubmit: (token: String, newPassword: String) -> Unit,
    onBackToLogin: () -> Unit,
    isLoading: Boolean = false,
    statusMessage: String? = null,
    isError: Boolean = false
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    val isDark = isAppDarkTheme()

    val passwordsMatch = newPassword.isNotEmpty() && newPassword == confirmPassword
    val isFormValid = !isLoading && newPassword.length >= 8 && passwordsMatch

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Set new password",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            PrototypeFieldRow(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = "New password",
                placeholder = "At least 8 characters",
                isPassword = true,
                passwordVisible = newPasswordVisible,
                onTogglePasswordVisibility = { newPasswordVisible = !newPasswordVisible }
            )

            Spacer(modifier = Modifier.height(12.dp))

            PrototypeFieldRow(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = "Confirm password",
                placeholder = "Repeat password",
                isPassword = true,
                passwordVisible = confirmPasswordVisible,
                onTogglePasswordVisibility = { confirmPasswordVisible = !confirmPasswordVisible }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Match hint pill
            val matchHintText = when {
                newPassword.isEmpty() || confirmPassword.isEmpty() -> "Enter both fields"
                !passwordsMatch -> "Passwords do not match"
                newPassword.length < 8 -> "At least 8 characters required"
                else -> "✓ Passwords match"
            }
            val matchHintColor = when {
                passwordsMatch && newPassword.length >= 8 -> SuccessGreen
                !passwordsMatch && confirmPassword.isNotEmpty() -> ErrorRose
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (passwordsMatch && newPassword.length >= 8) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = matchHintText,
                    color = matchHintColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            statusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isError) ErrorRose.copy(alpha = 0.12f) else SuccessGreen.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        color = if (isError) ErrorRose else SuccessGreen,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = { onResetPasswordSubmit(recoveryToken, newPassword.trim()) },
                enabled = isFormValid,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryCyan,
                    contentColor = Color.White,
                    disabledContainerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                    disabledContentColor = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Update password", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Back to login",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onBackToLogin() }
            )
        }
    }
}
