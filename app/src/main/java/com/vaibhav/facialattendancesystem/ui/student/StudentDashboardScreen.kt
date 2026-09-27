package com.vaibhav.facialattendancesystem.ui.student

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.vaibhav.facialattendancesystem.ui.components.ProfileImageHelper
import com.vaibhav.facialattendancesystem.data.AttendanceFeedItem
import com.vaibhav.facialattendancesystem.data.AttendanceRecord
import com.vaibhav.facialattendancesystem.data.Clazz
import com.vaibhav.facialattendancesystem.data.Student
import com.vaibhav.facialattendancesystem.ui.components.AttendanceProgressBar
import com.vaibhav.facialattendancesystem.ui.components.StatusPill
import com.vaibhav.facialattendancesystem.ui.components.adaptiveBorderColor
import com.vaibhav.facialattendancesystem.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import com.vaibhav.facialattendancesystem.data.CloudSyncManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDashboardScreen(
    student: Student,
    joinedClasses: List<Clazz>,
    attendanceRecords: List<AttendanceRecord>,
    attendanceFeed: List<AttendanceFeedItem> = emptyList(),
    teacherUserIds: Map<String, String> = emptyMap(), // classId -> teacher's userId (for avatar)
    isSyncing: Boolean = false,
    onRefreshCloud: () -> Unit = {},
    onJoinClassByCode: (String) -> Unit,
    onOpenClassAttendance: (classId: String, className: String) -> Unit = { _, _ -> },
    onReEnrollFace: () -> Unit,
    onOpenProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var profileBitmap by remember { mutableStateOf<Bitmap?>(ProfileImageHelper.loadProfileBitmap(context, student.studentId)) }

    DisposableEffect(lifecycleOwner, student.studentId) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                profileBitmap = ProfileImageHelper.loadProfileBitmap(context, student.studentId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showJoinDialog by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val displayStudentName = student.fullName.split(" ")
        .firstOrNull()
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        ?: "Student"

    val initials = student.fullName.split(" ")
        .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
        .ifEmpty { "S" }

    val borderColor = adaptiveBorderColor()

    // Overall semester attendance %
    val totalPresent = attendanceFeed.count { it.markedPresent == 1 }
    val totalSessions = attendanceFeed.size
    val semesterPct = if (totalSessions > 0) (totalPresent.toFloat() / totalSessions * 100).toInt() else 0
    val semesterFraction = if (totalSessions > 0) totalPresent.toFloat() / totalSessions else 1f

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(270.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                StudentSidebarContent(
                    name = student.fullName,
                    rollNumber = student.rollNumber,
                    initials = initials,
                    profileBitmap = profileBitmap,
                    borderColor = borderColor,
                    onDashboard = { scope.launch { drawerState.close() } },
                    onProfile = { scope.launch { drawerState.close() }; onOpenProfile() },
                    onLogout = { scope.launch { drawerState.close() }; onLogout() }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showJoinDialog = true },
                    containerColor = PrimaryCyan,
                    contentColor = Color.White,
                    text = { Text("＋ Join Class", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    icon = {}
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                // ── Top Bar ───────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                        }
                        Text(
                            text = "Dashboard",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusPill(
                            text = if (isSyncing) "🔄 Syncing..." else "☁ Synced",
                            color = if (isSyncing) PrimaryCyan else SuccessGreen,
                            modifier = Modifier.clickable { onRefreshCloud() }
                        )
                        if (profileBitmap != null) {
                            Image(
                                bitmap = profileBitmap!!.asImageBitmap(),
                                contentDescription = "Profile Picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, PrimaryCyan, CircleShape)
                                    .clickable { onOpenProfile() }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(PrimaryCyan, PrimaryCyan.copy(alpha = 0.6f))))
                                    .clickable { onOpenProfile() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                PullToRefreshBox(
                    isRefreshing = isSyncing,
                    onRefresh = onRefreshCloud,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                    // ── Semester Attendance Ring Card ─────────────────
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                // Conic-gradient attendance ring
                                AttendanceRing(fraction = semesterFraction, pct = semesterPct)

                                Column {
                                    Text(
                                        text = when {
                                            semesterFraction >= 0.75f -> "Attendance — good standing"
                                            semesterFraction >= 0.50f -> "Attendance — needs attention"
                                            else -> "Attendance — low — take action"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Semester overall",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    // Face enrollment status
                                    val isEnrolled = student.enrollmentStatus == 1
                                    Spacer(modifier = Modifier.height(6.dp))
                                    StatusPill(
                                        text = if (isEnrolled) "✓ Face enrolled" else "⚠ Enroll face",
                                        color = if (isEnrolled) SuccessGreen else WarningAmber
                                    )
                                }
                            }
                        }
                    }

                    // ── Section Header ────────────────────────────────
                    item {
                        Text(
                            text = "My enrolled classes",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // ── Class Cards ───────────────────────────────────
                    if (joinedClasses.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "You haven't joined any classes yet.\nTap '＋ Join Class' below and enter your teacher's code.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    } else {
                        items(joinedClasses, key = { it.classId }) { clazz ->
                            val classFeed = attendanceFeed.filter { it.classId == clazz.classId }
                            val total = classFeed.size
                            val presentCount = classFeed.count { it.markedPresent == 1 }
                            val pctFloat = if (total > 0) presentCount.toFloat() / total else 1f
                            val pctInt = (pctFloat * 100).toInt()
                            val progressColor = when {
                                pctFloat >= 0.75f -> SuccessGreen
                                pctFloat >= 0.50f -> WarningAmber
                                else -> ErrorRose
                            }

                            // Teacher avatar: download once and cache locally
                            val teacherUserId = teacherUserIds[clazz.classId]
                            val teacherAvatarBitmap = remember(teacherUserId) {
                                mutableStateOf<Bitmap?>(
                                    teacherUserId?.let { ProfileImageHelper.loadProfileBitmap(context, it) }
                                )
                            }
                            LaunchedEffect(teacherUserId) {
                                if (teacherUserId != null && teacherAvatarBitmap.value == null) {
                                    val downloaded = CloudSyncManager.downloadAndCacheProfileAvatar(context, teacherUserId)
                                    if (downloaded) {
                                        teacherAvatarBitmap.value = ProfileImageHelper.loadProfileBitmap(context, teacherUserId)
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                                    .clickable { onOpenClassAttendance(clazz.classId, clazz.className) }
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Teacher avatar pill (visible once downloaded)
                                        val bmp = teacherAvatarBitmap.value
                                        if (bmp != null) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "Teacher",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .border(1.dp, PrimaryCyan.copy(alpha = 0.5f), CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(7.dp))
                                        }
                                        Text(
                                            text = clazz.className,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = clazz.classCode,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${clazz.subject} · Sec ${clazz.section}",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                                    )
                                    // Attendance fraction
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (total > 0) "$presentCount/$total lectures attended" else "No lectures held yet",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "View History ›",
                                            fontSize = 11.sp,
                                            color = PrimaryCyan,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { pctFloat },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = progressColor,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        strokeCap = StrokeCap.Round
                                    )
                                    // Recent sessions (last 2)
                                    if (classFeed.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        classFeed.sortedByDescending { it.sessionDate }.take(2).forEach { feedItem ->
                                            val isP = feedItem.markedPresent == 1
                                            val sc = if (isP) SuccessGreen else ErrorRose
                                            val ts = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(feedItem.sessionDate))
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(ts, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    text = if (isP) "✓ Present" else "✕ Absent",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = sc
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
        }
    }

    if (showJoinDialog) {
        JoinClassDialog(
            onDismiss = { showJoinDialog = false },
            onJoin = { code ->
                onJoinClassByCode(code.trim().uppercase())
                showJoinDialog = false
            }
        )
    }
}

// ── Circular attendance ring ──────────────────────────────────────

@Composable
fun AttendanceRing(fraction: Float, pct: Int) {
    val ringColor = when {
        fraction >= 0.75f -> SuccessGreen
        fraction >= 0.50f -> WarningAmber
        else -> ErrorRose
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor  = MaterialTheme.colorScheme.onSurface

    Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
            // Track
            drawArc(color = trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
            // Fill
            drawArc(color = ringColor, startAngle = -90f, sweepAngle = 360f * fraction.coerceIn(0f, 1f), useCenter = false, style = stroke)
        }
        Text("$pct%", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

// ── Sidebar content ───────────────────────────────────────────────

@Composable
fun StudentSidebarContent(
    name: String,
    rollNumber: Int,
    initials: String,
    profileBitmap: Bitmap? = null,
    borderColor: Color,
    onDashboard: () -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit
) {
    Column(modifier = Modifier.fillMaxHeight().padding(16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))

        // User header
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (profileBitmap != null) {
                Image(
                    bitmap = profileBitmap.asImageBitmap(),
                    contentDescription = "Profile Picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(2.dp, PrimaryCyan, CircleShape)
                        .clickable { onProfile() }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(PrimaryCyan, PrimaryCyan.copy(alpha = 0.6f))))
                        .clickable { onProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Roll $rollNumber", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text("Student", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = borderColor)
        Spacer(modifier = Modifier.height(8.dp))

        DrawerItem(icon = "🏠", label = "Dashboard", onClick = onDashboard)
        DrawerItem(icon = "👤", label = "Profile & biometrics", onClick = onProfile)

        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(color = borderColor)
        Spacer(modifier = Modifier.height(8.dp))

        DrawerItem(icon = "🚪", label = "Log out", onClick = onLogout, textColor = ErrorRose)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun DrawerItem(
    icon: String,
    label: String,
    onClick: () -> Unit,
    textColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(icon, fontSize = 16.sp)
        Text(
            label,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (textColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else textColor
        )
    }
}

// ── Join Class Dialog ─────────────────────────────────────────────

@Composable
fun JoinClassDialog(
    onDismiss: () -> Unit,
    onJoin: (code: String) -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Join a class",
                fontWeight = FontWeight.Bold,
                color = PrimaryCyan,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter the 6-character join code provided by your teacher (e.g. ML-4821):",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Class Code") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(code) },
                enabled = code.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
            ) {
                Text("Join class", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
