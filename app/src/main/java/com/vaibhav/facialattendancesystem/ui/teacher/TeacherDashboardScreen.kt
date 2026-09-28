package com.vaibhav.facialattendancesystem.ui.teacher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.vaibhav.facialattendancesystem.ui.components.LocalBannerManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.vaibhav.facialattendancesystem.ui.components.ProfileImageHelper
import com.vaibhav.facialattendancesystem.data.Clazz
import com.vaibhav.facialattendancesystem.ui.components.StatusPill
import com.vaibhav.facialattendancesystem.ui.components.adaptiveBorderColor
import com.vaibhav.facialattendancesystem.ui.student.DrawerItem
import com.vaibhav.facialattendancesystem.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherDashboardScreen(
    teacherId: String = "",
    teacherName: String,
    classes: List<Clazz>,
    totalStudents: Int = 0,
    totalLectures: Int = 0,
    enrolledCountsMap: Map<String, Int> = emptyMap(),
    lecturesCountsMap: Map<String, Int> = emptyMap(),
    isSyncing: Boolean = false,
    unsyncedSessionsCount: Int = 0,
    onRefreshCloud: () -> Unit = {},
    onCreateClass: (className: String, subject: String, semester: Int, section: String) -> Unit,
    onMarkAttendance: (classId: String) -> Unit,
    onViewHistory: (classId: String) -> Unit,
    onOpenClassDetails: (classId: String) -> Unit,
    onOpenProfile: () -> Unit,
    onDeleteClass: (classId: String) -> Unit = {},
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var profileBitmap by remember(teacherId) { mutableStateOf<Bitmap?>(ProfileImageHelper.loadProfileBitmap(context, teacherId)) }

    LaunchedEffect(teacherId) {
        if (teacherId.isNotBlank()) {
            val localBmp = ProfileImageHelper.loadProfileBitmap(context, teacherId)
            if (localBmp != null) {
                profileBitmap = localBmp
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.vaibhav.facialattendancesystem.data.CloudSyncManager.uploadProfileAvatar(context, teacherId)
                }
            } else {
                val downloaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.vaibhav.facialattendancesystem.data.CloudSyncManager.downloadAndCacheProfileAvatar(context, teacherId)
                }
                if (downloaded) {
                    profileBitmap = ProfileImageHelper.loadProfileBitmap(context, teacherId)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, teacherId) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                profileBitmap = ProfileImageHelper.loadProfileBitmap(context, teacherId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val displayTeacherName = teacherName.split(" ")
        .firstOrNull()
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        ?: "Teacher"

    val initials = teacherName.split(" ")
        .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
        .ifEmpty { "T" }

    val borderColor = adaptiveBorderColor()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(270.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                TeacherSidebarContent(
                    name = teacherName,
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
                    onClick = { showCreateDialog = true },
                    containerColor = PrimaryCyan,
                    contentColor = Color.White,
                    text = { Text("＋ Create class", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val (syncText, syncColor) = when {
                            isSyncing -> "🔄 Syncing..." to PrimaryCyan
                            unsyncedSessionsCount > 0 -> "⏳ $unsyncedSessionsCount Pending" to WarningAmber
                            else -> "☁ Synced" to SuccessGreen
                        }
                        StatusPill(
                            text = syncText,
                            color = syncColor,
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

                Spacer(modifier = Modifier.height(14.dp))

                // ── 3-Stat Grid ───────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("${classes.size}", "Classes", PrimaryCyan),
                        Triple("$totalStudents", "Students", SuccessGreen),
                        Triple("$totalLectures", "Lectures", WarningAmber)
                    ).forEach { (value, label, color) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
                                Text(label, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "My classes",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                PullToRefreshBox(
                    isRefreshing = isSyncing,
                    onRefresh = onRefreshCloud,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    if (classes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No classes yet.\nTap '＋ Create class' to get started and share the code with students.\n(Pull down to refresh)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(classes, key = { it.classId }) { clazz ->
                                TeacherClassCard(
                                    clazz = clazz,
                                    enrolledCount = enrolledCountsMap[clazz.classId] ?: 0,
                                    lectureCount = lecturesCountsMap[clazz.classId] ?: 0,
                                    onMarkAttendance = { onMarkAttendance(clazz.classId) },
                                    onViewHistory = { onViewHistory(clazz.classId) },
                                    onOpenDetails = { onOpenClassDetails(clazz.classId) },
                                    onDelete = { onDeleteClass(clazz.classId) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateClassDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, subj, sem, sec ->
                onCreateClass(name, subj, sem, sec)
                showCreateDialog = false
            }
        )
    }
}

// ── Teacher Sidebar Content ───────────────────────────────────────

@Composable
fun TeacherSidebarContent(
    name: String,
    initials: String,
    profileBitmap: Bitmap? = null,
    borderColor: Color,
    onDashboard: () -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit
) {
    Column(modifier = Modifier.fillMaxHeight().padding(16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))

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
                Text("Teacher", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = PrimaryCyan.copy(alpha = 0.15f),
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text("Teacher", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = borderColor)
        Spacer(modifier = Modifier.height(8.dp))

        DrawerItem(icon = "🏠", label = "Dashboard", onClick = onDashboard)
        DrawerItem(icon = "👤", label = "Profile & settings", onClick = onProfile)

        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(color = borderColor)
        Spacer(modifier = Modifier.height(8.dp))
        DrawerItem(icon = "🚪", label = "Log out", onClick = onLogout, textColor = ErrorRose)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Teacher Class Card ────────────────────────────────────────────

@Composable
fun TeacherClassCard(
    clazz: Clazz,
    enrolledCount: Int = 0,
    lectureCount: Int = 0,
    onMarkAttendance: () -> Unit,
    onViewHistory: () -> Unit,
    onOpenDetails: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val borderColor = adaptiveBorderColor()
    val showBanner = LocalBannerManager.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onOpenDetails() }
            .padding(14.dp)
    ) {
        Column {
            // Class name + code pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = clazz.className,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = clazz.classCode,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Class Code", clazz.classCode))
                            showBanner("✓ Code copied: ${clazz.classCode}", false)
                        }
                    )
                    if (onDelete != null) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.size(28.dp).padding(start = 2.dp)
                        ) {
                            Text("🗑", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Subject · Section
            Text(
                text = "${clazz.subject} · Sec ${clazz.section}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            // Statistics chips: Enrolled students and conducted lectures
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            ) {
                StatusPill(
                    text = if (enrolledCount == 1) "👥 1 Student" else "👥 $enrolledCount Students",
                    color = SuccessGreen
                )
                StatusPill(
                    text = if (lectureCount == 1) "📅 1 Lecture" else "📅 $lectureCount Lectures",
                    color = PrimaryCyan
                )
            }

            // Action buttons: Roster & Details | Mark Attendance 📷
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenDetails,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(40.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("👥 Roster & Details", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }
                Button(
                    onClick = onMarkAttendance,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                    modifier = Modifier.weight(1f).height(40.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("📷 Take Attendance", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = onViewHistory,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(36.dp),
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Text("📊 Attendance History & Reports", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }

    if (showDeleteDialog && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text("Delete Class?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Text("Permanently delete \"${clazz.className}\"? All attendance data and rosters for this class will be removed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRose)
                ) {
                    Text("DELETE", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

// ── Create Class Dialog ───────────────────────────────────────────

@Composable
fun CreateClassDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, subject: String, semester: Int, section: String) -> Unit
) {
    var className by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var semesterStr by remember { mutableStateOf("1") }
    var section by remember { mutableStateOf("A") }

    val previewCode = if (subject.isNotBlank()) {
        val cleanSubj = subject.trim().uppercase().replace(Regex("[^A-Z]"), "").take(2).padEnd(2, 'X')
        "$cleanSubj-####"
    } else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Create new class", fontWeight = FontWeight.Bold, color = PrimaryCyan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = className,
                    onValueChange = { className = it },
                    label = { Text("Subject (e.g. ML fundamentals)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Department (e.g. CSE)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = semesterStr,
                        onValueChange = { semesterStr = it },
                        label = { Text("Sem") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = section,
                        onValueChange = { section = it },
                        label = { Text("Section") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                AnimatedVisibility(visible = previewCode.isNotBlank(), enter = fadeIn(tween(200))) {
                    Surface(shape = RoundedCornerShape(8.dp), color = PrimaryCyan.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Code will look like: $previewCode",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryCyan,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(className, subject, semesterStr.toIntOrNull() ?: 1, section) },
                enabled = className.isNotBlank() && subject.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
            ) { Text("Create & generate code", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}

// Backward-compat alias
@Composable
fun ClassCard(
    clazz: Clazz,
    onMarkAttendance: () -> Unit,
    onViewHistory: () -> Unit,
    onOpenDetails: () -> Unit
) = TeacherClassCard(
    clazz = clazz,
    enrolledCount = 0,
    onMarkAttendance = onMarkAttendance,
    onViewHistory = onViewHistory,
    onOpenDetails = onOpenDetails
)
