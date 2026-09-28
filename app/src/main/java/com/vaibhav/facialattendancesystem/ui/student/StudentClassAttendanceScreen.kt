package com.vaibhav.facialattendancesystem.ui.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.facialattendancesystem.data.AttendanceRecord
import com.vaibhav.facialattendancesystem.data.AttendanceSession
import com.vaibhav.facialattendancesystem.ui.components.StatusPill
import com.vaibhav.facialattendancesystem.ui.components.adaptiveBorderColor
import com.vaibhav.facialattendancesystem.ui.theme.ErrorRose
import com.vaibhav.facialattendancesystem.ui.theme.PrimaryCyan
import com.vaibhav.facialattendancesystem.ui.theme.SuccessGreen
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.size
import com.vaibhav.facialattendancesystem.ui.components.ProfileImageHelper
import com.vaibhav.facialattendancesystem.data.CloudSyncManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated attendance feed screen for students inside an enrolled class.
 * Shows:
 *  - Leave Class option with confirmation dialog
 *  - Overall class attendance percentage and counts
 *  - Live feed of all lectures with Day, Date, Time, and PRESENT / ABSENT status
 *  - No CSV export (teacher-only feature)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentClassAttendanceScreen(
    className: String,
    subject: String = "",
    teacherUserId: String = "",
    teacherName: String = "",
    sessions: List<AttendanceSession>,
    records: List<AttendanceRecord>,
    isSyncing: Boolean = false,
    onRefresh: () -> Unit = {},
    onLeaveClass: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dayDateFormat = remember { SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val borderColor = adaptiveBorderColor()

    val avatarVer = ProfileImageHelper.avatarVersion
    val teacherAvatarBitmap = remember(teacherUserId, avatarVer) {
        mutableStateOf<Bitmap?>(if (teacherUserId.isNotBlank()) ProfileImageHelper.loadProfileBitmap(context, teacherUserId) else null)
    }
    LaunchedEffect(teacherUserId) {
        if (teacherUserId.isNotBlank() && teacherAvatarBitmap.value == null) {
            val downloaded = CloudSyncManager.downloadAndCacheProfileAvatar(context, teacherUserId)
            if (downloaded) {
                teacherAvatarBitmap.value = ProfileImageHelper.loadProfileBitmap(context, teacherUserId)
            }
        }
    }

    var showLeaveDialog by remember { mutableStateOf(false) }

    // Map sessions to attendance record for fast lookup
    val recordsBySession = remember(records) {
        records.associateBy { it.sessionId }
    }

    val totalConducted = sessions.size
    val attendedCount = records.count { it.markedPresent == 1 }
    val pct = if (totalConducted > 0) (attendedCount * 100) / totalConducted else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("‹ Back", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            ) {
                Text(
                    text = className,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
                // Teacher avatar & name / subject row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    val bmp = teacherAvatarBitmap.value
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(PrimaryCyan.copy(alpha = 0.2f))
                            .border(0.8.dp, PrimaryCyan.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Teacher",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Text(
                                text = ProfileImageHelper.getInitials(teacherName.ifBlank { "Teacher" }, "T"),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryCyan
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    val subtitleText = when {
                        teacherName.isNotBlank() && subject.isNotBlank() -> "$subject · $teacherName"
                        teacherName.isNotBlank() -> "Faculty: $teacherName"
                        else -> subject
                    }
                    if (subtitleText.isNotBlank()) {
                        Text(
                            text = subtitleText,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            // Small Leave Class button (matching logout button style)
            OutlinedButton(
                onClick = { showLeaveDialog = true },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ErrorRose
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRose.copy(alpha = 0.5f))
            ) {
                Text("Leave", color = ErrorRose, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Summary Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "YOUR ATTENDANCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$attendedCount / $totalConducted Lectures",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                StatusPill(
                    text = if (totalConducted > 0) "$pct% Attendance" else "No Lectures",
                    color = if (totalConducted == 0) MaterialTheme.colorScheme.onSurfaceVariant else if (pct >= 75) SuccessGreen else ErrorRose
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "LIVE ATTENDANCE FEED",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Pull to refresh attendance feed
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f)
        ) {
            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSyncing) "Syncing attendance feed..." else "No lectures conducted for this class yet.\n(Pull down to refresh)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sessions) { session ->
                        val record = recordsBySession[session.sessionId]
                        val isPresent = record?.markedPresent == 1
                        val sessionDate = Date(session.sessionDate)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dayDateFormat.format(sessionDate),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Time: ${timeFormat.format(sessionDate)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                StatusPill(
                                    text = if (isPresent) "PRESENT" else "ABSENT",
                                    color = if (isPresent) SuccessGreen else ErrorRose
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Leave Class Confirmation Dialog
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = {
                Text("Leave $className?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to leave this class? You will be unenrolled and will need the class code to join again.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveDialog = false
                        onLeaveClass()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRose)
                ) {
                    Text("Leave Class", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
