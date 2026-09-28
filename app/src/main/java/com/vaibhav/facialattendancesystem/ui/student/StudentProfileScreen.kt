package com.vaibhav.facialattendancesystem.ui.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.vaibhav.facialattendancesystem.data.CloudSyncManager
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.vaibhav.facialattendancesystem.ui.components.ProfileImageHelper
import com.vaibhav.facialattendancesystem.data.Student
import com.vaibhav.facialattendancesystem.ui.components.StatusPill
import com.vaibhav.facialattendancesystem.ui.components.adaptiveBorderColor
import com.vaibhav.facialattendancesystem.ui.components.adaptiveDividerColor
import com.vaibhav.facialattendancesystem.ui.theme.*

@Composable
fun StudentProfileScreen(
    student: Student,
    joinedClassesCount: Int,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onUpdateProfile: (name: String, rollNo: Int, section: String) -> Unit,
    onReEnrollFace: () -> Unit,
    onLogout: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var currentFullName by remember(student.fullName) { mutableStateOf(student.fullName) }
    var currentRollNo by remember(student.rollNumber) { mutableStateOf(student.rollNumber) }
    var currentSection by remember(student.classSection) { mutableStateOf(student.classSection ?: "") }

    var showEditDialog by remember { mutableStateOf(false) }
    var showPhotoDialog by remember { mutableStateOf(false) }
    val avatarVer = ProfileImageHelper.avatarVersion
    var profileBitmap by remember(student.studentId, avatarVer) {
        mutableStateOf<Bitmap?>(ProfileImageHelper.loadProfileBitmap(context, student.studentId))
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(student.studentId) {
        if (student.studentId.isNotBlank()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                CloudSyncManager.purgeAutoFaceAvatarsOnce(context)
            }
            val localBmp = ProfileImageHelper.loadProfileBitmap(context, student.studentId)
            if (localBmp != null) {
                profileBitmap = localBmp
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    CloudSyncManager.uploadProfileAvatar(context, student.studentId)
                }
            } else {
                val downloaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    CloudSyncManager.downloadAndCacheProfileAvatar(context, student.studentId)
                }
                profileBitmap = if (downloaded) ProfileImageHelper.loadProfileBitmap(context, student.studentId) else null
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = ProfileImageHelper.saveProfileImageFromUri(context, student.studentId, uri)
            if (saved) {
                profileBitmap = ProfileImageHelper.loadProfileBitmap(context, student.studentId)
                // Upload to cloud so teacher can see it in the class roster
                scope.launch { CloudSyncManager.uploadProfileAvatar(context, student.studentId) }
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
                text = "My Profile",
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
            val initials = ProfileImageHelper.getInitials(currentFullName, "S")

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
                            text = initials.ifEmpty { "S" },
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

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = currentFullName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Roll No. $currentRollNo · ${if (currentSection.isNotBlank()) currentSection else "CSE"}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Face status pill
            val isEnrolled = student.enrollmentStatus == 1
            StatusPill(
                text = if (isEnrolled) "🟢  Face Profile Active" else "🟡  Face Profile Pending",
                color = if (isEnrolled) SuccessGreen else WarningAmber
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        val borderColor = adaptiveBorderColor()
        val dividerColor = adaptiveDividerColor()

        // ── Stat cards ───────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(
                Triple("$joinedClassesCount", "Classes", PrimaryCyan),
                Triple("6 Angles", "Profile", SuccessGreen)
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
                        Text(value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = color)
                        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    "Email"   to student.email,
                    "Section" to (if (currentSection.isNotBlank()) currentSection else "N/A")
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

        Spacer(modifier = Modifier.height(20.dp))

        // ── Settings card ─────────────────────────────────────────
        var showLogoutDialog by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Dark/Light toggle
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

                HorizontalDivider(color = adaptiveDividerColor(), thickness = 1.dp)

                // Edit profile
                OutlinedButton(
                    onClick = { showEditDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("EDIT PROFILE DETAILS", fontWeight = FontWeight.Bold, color = PrimaryCyan)
                }

                // Re-enroll face
                Button(
                    onClick = onReEnrollFace,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("RE-ENROLL 6-ANGLE FACE PROFILE", fontWeight = FontWeight.Bold, color = Color.White)
                }

                // Manual Wireless App Update
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isUpdatingWirelessly) "Updating ($wirelessUpdatePct%)..." else "Update",
                        fontWeight = FontWeight.Bold,
                        color = PrimaryCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Log out button
        OutlinedButton(
            onClick = { showLogoutDialog = true },
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRose.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Log out", color = ErrorRose, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        // Logout confirm dialog
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
    } // end Column

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
                            ProfileImageHelper.deleteProfileImage(context, student.studentId)
                            profileBitmap = null
                            scope.launch { CloudSyncManager.deleteProfileAvatar(context, student.studentId) }
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

    if (showEditDialog) {
        EditStudentProfileDialog(
            student = student,
            currentName = currentFullName,
            currentRoll = currentRollNo,
            currentSec = currentSection,
            onDismiss = { showEditDialog = false },
            onSave = { name, rollNo, sec ->
                currentFullName = name
                currentRollNo = rollNo
                currentSection = sec
                onUpdateProfile(name, rollNo, sec)
                showEditDialog = false
            }
        )
    }
}

@Composable
fun EditStudentProfileDialog(
    student: Student,
    currentName: String,
    currentRoll: Int,
    currentSec: String,
    onDismiss: () -> Unit,
    onSave: (name: String, rollNo: Int, section: String) -> Unit
) {
    var nameInput by remember(currentName) { mutableStateOf(currentName) }
    var rollNoInput by remember(currentRoll) { mutableStateOf(currentRoll.toString()) }
    var sectionInput by remember(currentSec) { mutableStateOf(currentSec) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Edit Profile", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = PrimaryCyan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Full Name") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryCyan),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rollNoInput,
                    onValueChange = { rollNoInput = it },
                    label = { Text("Roll Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryCyan),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sectionInput,
                    onValueChange = { sectionInput = it },
                    label = { Text("Section") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryCyan),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rollNo = rollNoInput.toIntOrNull() ?: currentRoll
                    onSave(nameInput.trim(), rollNo, sectionInput.trim())
                },
                enabled = nameInput.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
            ) {
                Text("SAVE", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
