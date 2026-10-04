package com.vaibhav.facialattendancesystem.ui.teacher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.vaibhav.facialattendancesystem.ui.components.LocalBannerManager
import com.vaibhav.facialattendancesystem.ui.components.ProfileImageHelper
import com.vaibhav.facialattendancesystem.data.CloudSyncManager
import kotlinx.coroutines.launch
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.facialattendancesystem.data.AttendanceRecord
import com.vaibhav.facialattendancesystem.data.Clazz
import com.vaibhav.facialattendancesystem.data.Student
import com.vaibhav.facialattendancesystem.ui.components.StatusPill
import com.vaibhav.facialattendancesystem.ui.components.adaptiveBorderColor
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.vaibhav.facialattendancesystem.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailsScreen(
    clazz: Clazz,
    enrolledStudents: List<Student>,
    pendingStudents: List<Student> = emptyList(),
    attendanceRecords: List<AttendanceRecord> = emptyList(),
    totalSessionsCount: Int = 0,
    isSyncing: Boolean = false,
    onMarkAttendance: () -> Unit,
    onRefresh: () -> Unit = {},
    onSyncCloudRoster: () -> Unit = onRefresh,
    onApproveStudent: (studentId: String) -> Unit = {},
    onRejectStudent: (studentId: String) -> Unit = {},
    onResetStudentPassword: (studentEmail: String, studentName: String) -> Unit = { _, _ -> },
    onRemoveStudent: (studentId: String) -> Unit = {},
    onDeleteClass: (classId: String) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val showBanner = LocalBannerManager.current
    var studentToResetPassword by remember { mutableStateOf<Student?>(null) }
    var studentToRemove by remember { mutableStateOf<Student?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterMode by remember { mutableStateOf(0) } // 0: All, 1: Low Attendance (< 75%)
    var showDefaultersDialog by remember { mutableStateOf(false) }

    val defaultersList = remember(enrolledStudents, attendanceRecords, totalSessionsCount) {
        if (totalSessionsCount == 0) emptyList()
        else enrolledStudents.mapNotNull { student ->
            val attended = attendanceRecords.count { it.studentId == student.studentId && it.markedPresent == 1 }
            val pct = ((attended.toFloat() / totalSessionsCount) * 100).toInt()
            if (pct < 75) Triple(student, attended, pct) else null
        }.sortedBy { it.second }
    }

    val filteredStudents = remember(enrolledStudents, searchQuery, selectedFilterMode, attendanceRecords, totalSessionsCount) {
        enrolledStudents.filter { student ->
            val attendedCount = attendanceRecords.count { it.studentId == student.studentId && it.markedPresent == 1 }
            val pct = if (totalSessionsCount > 0) ((attendedCount.toFloat() / totalSessionsCount) * 100).toInt() else 0
            val matchesFilter = when (selectedFilterMode) {
                1 -> totalSessionsCount > 0 && pct < 75
                else -> true
            }
            val matchesQuery = searchQuery.isBlank() ||
                student.fullName.contains(searchQuery, ignoreCase = true) ||
                student.rollNumber.toString().contains(searchQuery) ||
                student.email.contains(searchQuery, ignoreCase = true) ||
                (student.classSection ?: "").contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    // Rotating sync icon animation
    val rotation by rememberInfiniteTransition(label = "sync").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing)),
        label = "syncrot"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.background
            ) {
                Button(
                    onClick = onMarkAttendance,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp)
                ) {
                    Text(
                        text = "MARK ATTENDANCE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
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
                    text = "Class Roster",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorRose)
                ) {
                    Text("Delete", color = ErrorRose, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val borderColor = adaptiveBorderColor()

            Text(
                text = clazz.className,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Sec ${clazz.section} · Semester ${clazz.semester}",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            // ── Code Card (Prototype row) ─────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Code: ${clazz.classCode}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StatusPill(
                        text = "⎘ Copy",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Class Code", clazz.classCode)
                            clipboard.setPrimaryClip(clip)
                            showBanner("✓ Code copied: ${clazz.classCode}", false)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── 3-Stat Grid: Students | Sessions | Defaulters ──────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("${enrolledStudents.size}", "Students", PrimaryCyan) to {},
                    Triple("$totalSessionsCount", "Sessions", SuccessGreen) to {},
                    Triple("${defaultersList.size}", "Defaulters (<75%)", if (defaultersList.isNotEmpty()) ErrorRose else MaterialTheme.colorScheme.onSurfaceVariant) to {
                        if (totalSessionsCount > 0) showDefaultersDialog = true
                    }
                ).forEach { (stat, onClickAction) ->
                    val (value, label, color) = stat
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable { onClickAction() }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
                            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }

            if (totalSessionsCount > 0 && defaultersList.isNotEmpty()) {
                Button(
                    onClick = { showDefaultersDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRose.copy(alpha = 0.9f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).height(38.dp)
                ) {
                    Text("⚠️ View Defaulter List & Notice (${defaultersList.size} < 75%)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            // ── Pending Join Requests ──────────────────────────────────
            if (pendingStudents.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "⏳ Pending Approvals",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WarningAmber
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(WarningAmber.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${pendingStudents.size} request${if (pendingStudents.size > 1) "s" else ""}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarningAmber
                        )
                    }
                }
                pendingStudents.forEach { student ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(WarningAmber.copy(alpha = 0.07f))
                            .border(1.dp, WarningAmber.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Smart avatar for pending student
                            val avatarVer = ProfileImageHelper.avatarVersion
                            val pendingAvatarBitmap = remember(student.studentId, avatarVer) {
                                mutableStateOf<Bitmap?>(ProfileImageHelper.loadProfileBitmap(context, student.studentId))
                            }
                            LaunchedEffect(student.studentId) {
                                if (pendingAvatarBitmap.value == null) {
                                    val downloaded = CloudSyncManager.downloadAndCacheProfileAvatar(context, student.studentId)
                                    if (downloaded) {
                                        pendingAvatarBitmap.value = ProfileImageHelper.loadProfileBitmap(context, student.studentId)
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(WarningAmber.copy(alpha = 0.15f))
                                    .border(1.2.dp, WarningAmber.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val bmp = pendingAvatarBitmap.value
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Profile photo of ${student.fullName}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Text(
                                        text = ProfileImageHelper.getInitials(student.fullName, "S"),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = WarningAmber
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = student.fullName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Roll ${student.rollNumber} · ${student.email}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "✓ Approve",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SuccessGreen.copy(alpha = 0.12f))
                                        .clickable { onApproveStudent(student.studentId) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                                Text(
                                    text = "✕ Reject",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ErrorRose,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ErrorRose.copy(alpha = 0.12f))
                                        .clickable { onRejectStudent(student.studentId) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }

            // ── Roster Header, Search & Filter ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enrolled Students (${filteredStudents.size}${if (filteredStudents.size != enrolledStudents.size) "/${enrolledStudents.size}" else ""})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (defaultersList.isNotEmpty() && totalSessionsCount > 0) {
                    Text(
                        text = "⚠️ Defaulters: ${defaultersList.size}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = ErrorRose,
                        modifier = Modifier.clickable { showDefaultersDialog = true }
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by name, roll no, email...", fontSize = 12.5.sp) },
                leadingIcon = { Text("🔍", fontSize = 13.sp) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Text("✕", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryCyan,
                    unfocusedBorderColor = borderColor,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Quick Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedFilterMode == 0,
                    onClick = { selectedFilterMode = 0 },
                    label = { Text("All (${enrolledStudents.size})", fontSize = 11.5.sp) }
                )
                FilterChip(
                    selected = selectedFilterMode == 1,
                    onClick = { selectedFilterMode = 1 },
                    label = {
                        Text(
                            "⚠️ Low Attendance (< 75%) (${defaultersList.size})",
                            fontSize = 11.5.sp,
                            color = if (selectedFilterMode == 1 || defaultersList.isNotEmpty()) ErrorRose else MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            }

            PullToRefreshBox(
                isRefreshing = isSyncing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f)
            ) {
                if (enrolledStudents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No students enrolled yet.\nShare code '${clazz.classCode}' with your students!\n(Pull down to refresh)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                } else if (filteredStudents.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No students match your search or filter.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.5.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    searchQuery = ""
                                    selectedFilterMode = 0
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Clear Search & Filter", fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredStudents) { student ->
                            val isEnrolled = student.enrollmentStatus == 1
                            val attendedCount = attendanceRecords.count { it.studentId == student.studentId && it.markedPresent == 1 }
                            val pctInt = if (totalSessionsCount > 0) ((attendedCount.toFloat() / totalSessionsCount) * 100).toInt() else 0
                            val progressFloat = if (totalSessionsCount > 0) (attendedCount.toFloat() / totalSessionsCount).coerceIn(0f, 1f) else 0f

                            val attendancePillColor = when {
                                totalSessionsCount == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                                pctInt >= 75 -> SuccessGreen
                                pctInt >= 50 -> WarningAmber
                                else -> ErrorRose
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                            )
                                        )
                                    )
                                    .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                                    .padding(16.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Row 1: Student Identity & Attendance Tag
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // Smart avatar: show profile photo if available, fallback to First+Last initials (VV)
                                            val avatarVerRoster = ProfileImageHelper.avatarVersion
                                            val studentAvatarBitmap = remember(student.studentId, avatarVerRoster) {
                                                mutableStateOf<Bitmap?>(ProfileImageHelper.loadProfileBitmap(context, student.studentId))
                                            }
                                            val scope = rememberCoroutineScope()
                                            LaunchedEffect(student.studentId) {
                                                if (studentAvatarBitmap.value == null) {
                                                    // Try to download from cloud
                                                    val downloaded = CloudSyncManager.downloadAndCacheProfileAvatar(context, student.studentId)
                                                    if (downloaded) {
                                                        studentAvatarBitmap.value = ProfileImageHelper.loadProfileBitmap(context, student.studentId)
                                                    }
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(PrimaryCyan.copy(alpha = 0.15f))
                                                    .border(1.5.dp, PrimaryCyan.copy(alpha = 0.4f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val bmp = studentAvatarBitmap.value
                                                if (bmp != null) {
                                                    Image(
                                                        bitmap = bmp.asImageBitmap(),
                                                        contentDescription = "Profile photo of ${student.fullName}",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                                    )
                                                } else {
                                                    Text(
                                                        text = ProfileImageHelper.getInitials(student.fullName, "S"),
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = PrimaryCyan
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = student.fullName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Roll ${student.rollNumber} · Sec ${student.classSection ?: "A"}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (student.email.isNotBlank()) {
                                                    Text(
                                                        text = "✉ ${student.email}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            StatusPill(
                                                text = if (totalSessionsCount > 0) "$pctInt% Attendance" else "No Lectures",
                                                color = attendancePillColor
                                            )
                                            if (totalSessionsCount > 0) {
                                                Text(
                                                    text = "$attendedCount/$totalSessionsCount",
                                                    fontSize = 10.sp,
                                                    color = attendancePillColor,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = androidx.compose.ui.Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Row 2: Attendance Progress Bar & Count
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (totalSessionsCount > 0) "$attendedCount/$totalSessionsCount classes" else "0 classes conducted so far",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (totalSessionsCount > 0) "$pctInt%" else "—",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = attendancePillColor
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { progressFloat },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = attendancePillColor,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }

                                    // Row 3: Biometrics Status & Teacher Actions
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isEnrolled) SuccessGreen else WarningAmber)
                                            )
                                            Text(
                                                text = if (isEnrolled) "Face Biometrics Active" else "Face Enrollment Pending",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isEnrolled) SuccessGreen else WarningAmber
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Reset Password 🔑",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PrimaryCyan,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(PrimaryCyan.copy(alpha = 0.1f))
                                                    .clickable { studentToResetPassword = student }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            )

                                            Text(
                                                text = "Remove ✕",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ErrorRose,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(ErrorRose.copy(alpha = 0.12f))
                                                    .clickable { studentToRemove = student }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dialog for Teacher-Assisted Password Reset
        studentToResetPassword?.let { student ->
            AlertDialog(
                onDismissRequest = { studentToResetPassword = null },
                title = {
                    Text(
                        text = "Reset Student Password",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = "Send a password recovery email to ${student.fullName} (${student.email})?\n\nThe student will receive a link to set a new password on their device.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onResetStudentPassword(student.email, student.fullName)
                            studentToResetPassword = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                    ) {
                        Text("SEND RESET LINK", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { studentToResetPassword = null }) {
                        Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        // Dialog for Removing Student from Class
        studentToRemove?.let { student ->
            AlertDialog(
                onDismissRequest = { studentToRemove = null },
                title = {
                    Text(
                        text = "Remove Student from Class?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to remove ${student.fullName} (Roll: ${student.rollNumber}) from ${clazz.className}?\n\nThey will be un-enrolled from this class roster.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val sid = student.studentId
                            studentToRemove = null
                            onRemoveStudent(sid)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRose)
                    ) {
                        Text("REMOVE", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { studentToRemove = null }) {
                        Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = {
                    Text(
                        text = "Delete Class?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to permanently delete \"${clazz.className}\"? All associated lectures, attendance records, and student enrollments for this class will be removed.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            onDeleteClass(clazz.classId)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRose)
                    ) {
                        Text("DELETE CLASS", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        if (showDefaultersDialog) {
            DefaulterListDialog(
                clazz = clazz,
                totalSessionsCount = totalSessionsCount,
                defaulters = defaultersList,
                onDismiss = { showDefaultersDialog = false },
                onCopyNotice = { notice ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Defaulters Notice", notice)
                    clipboard.setPrimaryClip(clip)
                    showBanner("✓ Defaulters notice copied to clipboard", false)
                },
                onExportCsv = {
                    exportDefaultersCsv(
                        context = context,
                        className = clazz.className,
                        section = clazz.section,
                        totalSessions = totalSessionsCount,
                        defaulters = defaultersList
                    ) { ok, msg ->
                        showBanner(msg, !ok)
                    }
                }
            )
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaulterListDialog(
    clazz: Clazz,
    totalSessionsCount: Int,
    defaulters: List<Triple<Student, Int, Int>>, // (Student, AttendedCount, PctInt)
    onDismiss: () -> Unit,
    onCopyNotice: (String) -> Unit,
    onExportCsv: () -> Unit
) {
    val borderColor = adaptiveBorderColor()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Attendance Defaulters",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = ErrorRose
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ErrorRose.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "< 75%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ErrorRose
                        )
                    }
                }
                Text(
                    text = "${clazz.className} · $totalSessionsCount total lectures",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (defaulters.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🎉 Outstanding! No students have attendance below 75% across $totalSessionsCount conducted sessions.",
                            fontSize = 13.5.sp,
                            textAlign = TextAlign.Center,
                            color = SuccessGreen,
                            lineHeight = 20.sp
                        )
                    }
                } else {
                    Text(
                        text = "${defaulters.size} student${if (defaulters.size > 1) "s" else ""} currently below 75% attendance criteria:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(defaulters, key = { it.first.studentId }) { (student, attended, pct) ->
                            val needed = (3 * totalSessionsCount - 4 * attended).coerceAtLeast(1)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = student.fullName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Roll ${student.rollNumber} · ${student.email}",
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(ErrorRose.copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "$pct%",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.sp,
                                                color = ErrorRose
                                            )
                                        }
                                    }
                                    LinearProgressIndicator(
                                        progress = { if (totalSessionsCount > 0) (attended.toFloat() / totalSessionsCount).coerceIn(0f, 1f) else 0f },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = ErrorRose,
                                        trackColor = MaterialTheme.colorScheme.surface
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "$attended / $totalSessionsCount attended",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Needs next $needed classes",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WarningAmber
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Notice generation helper
                    val noticeText = remember(defaulters, clazz, totalSessionsCount) {
                        val sb = StringBuilder()
                        sb.append("⚠️ ATTENDANCE DEFAULTERS NOTICE (< 75%)\n")
                        sb.append("Class: ${clazz.className} (Sec ${clazz.section})\n")
                        sb.append("Total Lectures Conducted: $totalSessionsCount\n")
                        sb.append("Defaulter Count: ${defaulters.size}\n\n")
                        sb.append("Students with Attendance Shortage:\n")
                        defaulters.forEachIndexed { idx, (s, att, pct) ->
                            val needed = (3 * totalSessionsCount - 4 * att).coerceAtLeast(1)
                            sb.append("${idx + 1}. [Roll ${s.rollNumber}] ${s.fullName} - $att/$totalSessionsCount ($pct%) | Needs next $needed classes\n")
                        }
                        sb.append("\nPlease meet your faculty/course coordinator immediately.")
                        sb.toString()
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onCopyNotice(noticeText) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("📋 Copy Notice", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Button(
                            onClick = onExportCsv,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("📤 Export CSV", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
            ) {
                Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}
