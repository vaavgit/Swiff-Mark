package com.vaibhav.facialattendancesystem.ui.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import com.vaibhav.facialattendancesystem.data.CloudSyncManager
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.facialattendancesystem.ui.components.ProfileImageHelper
import com.vaibhav.facialattendancesystem.data.Teacher
import com.vaibhav.facialattendancesystem.ui.components.adaptiveBorderColor
import com.vaibhav.facialattendancesystem.ui.components.adaptiveDividerColor
import com.vaibhav.facialattendancesystem.ui.theme.*

@Composable
fun TeacherProfileScreen(
    teacherId: String = "",
    teacherName: String,
    teacher: Teacher?,
    classesCount: Int,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onUpdateProfile: (name: String, department: String, subject: String) -> Unit = { _, _, _ -> },
    onPurgeData: () -> Unit = {},
    onLogout: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val actualTeacherId = teacherId.ifBlank { teacher?.teacherId ?: "teacher" }
    val borderColor = adaptiveBorderColor()
    val dividerColor = adaptiveDividerColor()

    var currentName by remember(teacherName) { mutableStateOf(teacherName) }
    var currentDept by remember(teacher?.department) { mutableStateOf(teacher?.department ?: "") }
    var currentSubject by remember(teacher?.subject) { mutableStateOf(teacher?.subject ?: "") }

    var showPhotoDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showPurgeDialog by remember { mutableStateOf(false) }
    val avatarVer = ProfileImageHelper.avatarVersion
    var profileBitmap by remember(actualTeacherId, avatarVer) {
        mutableStateOf<Bitmap?>(ProfileImageHelper.loadProfileBitmap(context, actualTeacherId))
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(actualTeacherId) {
        if (actualTeacherId.isNotBlank()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                CloudSyncManager.purgeAutoFaceAvatarsOnce(context)
            }
            val localBmp = ProfileImageHelper.loadProfileBitmap(context, actualTeacherId)
            if (localBmp != null) {
                profileBitmap = localBmp
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    CloudSyncManager.uploadProfileAvatar(context, actualTeacherId)
                }
            } else {
                val downloaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    CloudSyncManager.downloadAndCacheProfileAvatar(context, actualTeacherId)
                }
                profileBitmap = if (downloaded) ProfileImageHelper.loadProfileBitmap(context, actualTeacherId) else null
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = ProfileImageHelper.saveProfileImageFromUri(context, actualTeacherId, uri)
            if (saved) {
                profileBitmap = ProfileImageHelper.loadProfileBitmap(context, actualTeacherId)
                // Upload to cloud so students can see it
                scope.launch { CloudSyncManager.uploadProfileAvatar(context, actualTeacherId) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(top = 20.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ───────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", color = PrimaryCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "Teacher Profile",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(64.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Avatar ────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val initials = ProfileImageHelper.getInitials(currentName, "T")

            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clickable { showPhotoDialog = true },
                contentAlignment = Alignment.Center
            ) {
                if (profileBitmap != null) {
                    Image(
                        bitmap = profileBitmap!!.asImageBitmap(),
                        contentDescription = "Profile Picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .border(3.dp, PrimaryCyan, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(PrimaryCyan, PrimaryCyan.copy(alpha = 0.5f))
                                )
                            )
                            .border(3.dp, PrimaryCyan.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials.ifEmpty { "T" },
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }

                // Pencil edit badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(PrimaryCyan)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✏", fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = currentName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (currentDept.isNotBlank()) currentDept else "Computer Science Department",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Stat cards ───────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(
                Triple("$classesCount", "Classes", PrimaryCyan),
                Triple("Active", "AI Engine", SuccessGreen)
            ).forEach { (value, label, color) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = value,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = color
                        )
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Info rows card ────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(
                    "Department" to (if (currentDept.isNotBlank()) currentDept else "Computer Science"),
                    "Subject"    to (if (currentSubject.isNotBlank()) currentSubject else "Machine Learning")
                ).forEachIndexed { i, (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (i < 1) HorizontalDivider(color = dividerColor, thickness = 1.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Edit Profile Details Button ───────────────────────────
        OutlinedButton(
            onClick = { showEditDialog = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("EDIT PROFILE DETAILS", fontWeight = FontWeight.Bold, color = PrimaryCyan)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Theme toggle card ─────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Dark Theme", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Toggle light / dark mode", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = onThemeToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryCyan,
                        checkedTrackColor = PrimaryCyan.copy(alpha = 0.4f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Reset Test Data / Purge Button ───────────────────────
        OutlinedButton(
            onClick = { showPurgeDialog = true },
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Clear Test Classes & Attendance", color = WarningAmber, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Manual Wireless App Update Button ─────────────────────
        val showBanner = com.vaibhav.facialattendancesystem.ui.components.LocalBannerManager.current
        var isUpdatingWirelessly by remember { mutableStateOf(false) }
        var wirelessUpdatePct by remember { mutableStateOf(0) }
        OutlinedButton(
            enabled = !isUpdatingWirelessly,
            onClick = {
                isUpdatingWirelessly = true
                wirelessUpdatePct = 0
                scope.launch {
                    val release = com.vaibhav.facialattendancesystem.util.AppUpdateDownloader.fetchLatestRelease()
                    if (release == null || release.apkDownloadUrl.isBlank()) {
                        isUpdatingWirelessly = false
                        showBanner("Could not reach GitHub release server.", true)
                    } else {
                        val (ok, msg) = com.vaibhav.facialattendancesystem.util.AppUpdateDownloader.downloadAndInstallApk(
                            context = context,
                            apkUrl = release.apkDownloadUrl,
                            onProgress = { pct -> wirelessUpdatePct = pct }
                        )
                        isUpdatingWirelessly = false
                        showBanner(msg, !ok)
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.8f)),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(
                text = if (isUpdatingWirelessly) "Updating ($wirelessUpdatePct%)..." else "Update",
                color = PrimaryCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        var showLogoutDialog by remember { mutableStateOf(false) }

        OutlinedButton(
            onClick = { showLogoutDialog = true },
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRose.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Log out", color = ErrorRose, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        if (showPurgeDialog) {
            AlertDialog(
                onDismissRequest = { showPurgeDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                title = { Text("Clear Classes & Attendance?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    Text(
                        "This will wipe all existing classes, attendance sessions, and logs so you start fresh.\n\n✓ All registered student profiles, logins, and facial biometrics will remain safe!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPurgeDialog = false
                            onPurgeData()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
                    ) { Text("Confirm Clear", color = Color.Black, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showPurgeDialog = false }, shape = RoundedCornerShape(10.dp)) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                title = { Text("Log out?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    Text(
                        "You'll need to log in again to access your classes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showLogoutDialog = false; onLogout() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRose)
                    ) { Text("Log out", color = Color.White, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showLogoutDialog = false }, shape = RoundedCornerShape(10.dp)) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        if (showPhotoDialog) {
            AlertDialog(
                onDismissRequest = { showPhotoDialog = false },
                title = {
                    Text("Profile Photo", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                },
                text = {
                    Text(
                        "Update your profile picture or revert to your default avatar initials.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPhotoDialog = false
                            photoPickerLauncher.launch("image/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                    ) {
                        Text("CHOOSE PHOTO 🖼", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    if (profileBitmap != null) {
                        TextButton(
                            onClick = {
                                ProfileImageHelper.deleteProfileImage(context, actualTeacherId)
                                profileBitmap = null
                                scope.launch { CloudSyncManager.deleteProfileAvatar(context, actualTeacherId) }
                                showPhotoDialog = false
                            }
                        ) {
                            Text("REMOVE (RESET TO DEFAULT)", color = ErrorRose, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(onClick = { showPhotoDialog = false }) {
                            Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )
        }
    }

    // ── Edit Profile Dialog ───────────────────────────────────────
    if (showEditDialog) {
        EditTeacherProfileDialog(
            teacher = teacher,
            teacherName = currentName,
            onDismiss = { showEditDialog = false },
            onSave = { name, dept, subject ->
                currentName = name
                currentDept = dept
                currentSubject = subject
                onUpdateProfile(name, dept, subject)
                showEditDialog = false
            }
        )
    }
}

@Composable
fun EditTeacherProfileDialog(
    teacher: Teacher?,
    teacherName: String,
    onDismiss: () -> Unit,
    onSave: (name: String, dept: String, subject: String) -> Unit
) {
    var nameInput by remember(teacherName) { mutableStateOf(teacherName) }
    var deptInput by remember(teacher?.department) { mutableStateOf(teacher?.department ?: "") }
    var subjectInput by remember(teacher?.subject) { mutableStateOf(teacher?.subject ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "Edit Profile Details",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = deptInput,
                    onValueChange = { deptInput = it },
                    label = { Text("Department") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subjectInput,
                    onValueChange = { subjectInput = it },
                    label = { Text("Subject") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(nameInput.trim(), deptInput.trim(), subjectInput.trim()) },
                enabled = nameInput.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
            ) {
                Text("Save ✓", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
