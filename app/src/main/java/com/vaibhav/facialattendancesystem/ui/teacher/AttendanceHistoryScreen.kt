package com.vaibhav.facialattendancesystem.ui.teacher

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.facialattendancesystem.data.AttendanceRecord
import com.vaibhav.facialattendancesystem.data.AttendanceSession
import com.vaibhav.facialattendancesystem.data.Student
import com.vaibhav.facialattendancesystem.ui.components.StatusPill
import com.vaibhav.facialattendancesystem.ui.components.adaptiveBorderColor
import com.vaibhav.facialattendancesystem.ui.theme.PrimaryCyan
import com.vaibhav.facialattendancesystem.ui.theme.SuccessGreen
import com.vaibhav.facialattendancesystem.ui.theme.WarningAmber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceHistoryScreen(
    className: String,
    subject: String = "",
    section: String = "",
    semester: Int = 1,
    teacherName: String = "",
    sessions: List<AttendanceSession>,
    allStudents: List<Student> = emptyList(),
    allRecords: List<AttendanceRecord> = emptyList(),
    isSyncing: Boolean = false,
    onRefresh: () -> Unit = {},
    onExportCsv: (AttendanceSession) -> Unit,
    onExportAdminReport: ((startDate: Long?, endDate: Long?, threshold: Int) -> Unit)? = null,
    onExportFullReport: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
    val borderColor = adaptiveBorderColor()
    var showAdminExportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Attendance History", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            OutlinedButton(onClick = onBack, shape = RoundedCornerShape(10.dp)) {
                Text("Back", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        Text(className, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))

        // Export College Admin Report button
        if ((onExportAdminReport != null || onExportFullReport != null) && sessions.isNotEmpty()) {
            Button(
                onClick = {
                    if (onExportAdminReport != null) {
                        showAdminExportDialog = true
                    } else {
                        onExportFullReport?.invoke()
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text("📋 Export Admin Report (College Register)", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

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
                        text = if (isSyncing) "Loading attendance sessions from cloud..." else "No attendance sessions recorded yet.\n(Pull down to refresh)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sessions) { session ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dateFormat.format(Date(session.sessionDate)),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp
                                    )
                                    StatusPill(
                                        text = "Completed",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Start: ${session.photo1FacesDetected} detected · End: ${session.photo2FacesDetected ?: 0} detected",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { onExportCsv(session) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Text("Export Single Lecture CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdminExportDialog && onExportAdminReport != null) {
        AdminExportDialog(
            sessions = sessions,
            onDismiss = { showAdminExportDialog = false },
            onExport = { start, end, threshold ->
                showAdminExportDialog = false
                onExportAdminReport(start, end, threshold)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminExportDialog(
    sessions: List<AttendanceSession>,
    onDismiss: () -> Unit,
    onExport: (startDate: Long?, endDate: Long?, threshold: Int) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(0) } // 0: All, 1: This Month, 2: Last 30 Days
    var selectedThreshold by remember { mutableStateOf(75) } // 75%, 60%, 80%

    val calendar = remember { Calendar.getInstance() }
    val (startDate, endDate) = remember(selectedFilter) {
        when (selectedFilter) {
            1 -> {
                // This Month
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                Pair(cal.timeInMillis, System.currentTimeMillis())
            }
            2 -> {
                // Last 30 Days
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -30)
                Pair(cal.timeInMillis, System.currentTimeMillis())
            }
            else -> Pair(null, null)
        }
    }

    val matchingSessionsCount = remember(startDate, endDate, sessions) {
        sessions.count { s ->
            (startDate == null || s.sessionDate >= startDate) &&
            (endDate == null || s.sessionDate <= endDate)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "College Attendance Register Export",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = PrimaryCyan
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Generates an official session-by-session matrix register formatted for college and university administration submission.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Date Range Filter Chips
                Text("Select Period / Date Range:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == 0,
                        onClick = { selectedFilter = 0 },
                        label = { Text("All Time", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == 1,
                        onClick = { selectedFilter = 1 },
                        label = { Text("This Month", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == 2,
                        onClick = { selectedFilter = 2 },
                        label = { Text("Last 30 Days", fontSize = 11.sp) }
                    )
                }

                // Defaulter Threshold Selection
                Text("Attendance Shortage Criteria:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedThreshold == 75,
                        onClick = { selectedThreshold = 75 },
                        label = { Text("75% (Standard)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedThreshold == 60,
                        onClick = { selectedThreshold = 60 },
                        label = { Text("60% (Condoned)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedThreshold == 80,
                        onClick = { selectedThreshold = 80 },
                        label = { Text("80% (Lab/Strict)", fontSize = 11.sp) }
                    )
                }

                // Summary preview box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "Included: $matchingSessionsCount lectures in period",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = PrimaryCyan
                        )
                        Text(
                            text = "Format: Grid Matrix (P/A per lecture) + Total + Shortage Deficit (<$selectedThreshold%) + Signatures",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onExport(startDate, endDate, selectedThreshold) },
                enabled = matchingSessionsCount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Generate Register 📄", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

private fun shareCsvFile(context: Context, file: java.io.File, title: String) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_SUBJECT, title)
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(shareIntent, "Share Attendance Report via")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun exportSessionToCsv(
    context: Context,
    session: AttendanceSession,
    records: List<AttendanceRecord>,
    students: List<Student>,
    onResult: ((Boolean, String) -> Unit)? = null
) {
    try {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = java.io.File(dir, "Attendance_${session.sessionId.take(8)}.csv")
        val writer = java.io.FileWriter(file)

        writer.write("Roll No,Student Name,Email,Photo1 Matched,Photo2 Matched,Dual Present,Final Attendance,Manual Override\n")

        for (record in records) {
            val student = students.find { it.studentId == record.studentId } ?: continue
            val p1 = if (record.photo1Matched == 1) "YES" else "NO"
            val p2 = if (record.photo2Matched == 1) "YES" else "NO"
            val dual = if (record.dualCapturePresent == 1) "YES" else "NO"
            val finalStatus = if (record.markedPresent == 1) "PRESENT" else "ABSENT"
            val override = if (record.manualOverride == 1) "YES" else "NO"

            writer.write("${student.rollNumber},\"${student.fullName}\",\"${student.email}\",$p1,$p2,$dual,$finalStatus,$override\n")
        }

        writer.flush()
        writer.close()

        shareCsvFile(context, file, "Attendance Session Report - ${file.name}")

        val msg = "Exported CSV: ${file.name}"
        if (onResult != null) {
            onResult(true, msg)
        } else {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        val err = "CSV Export Failed: ${e.message}"
        if (onResult != null) {
            onResult(false, err)
        } else {
            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Enhanced College Submission Admin Export.
 * Generates an official session-by-session matrix register formatted for college/university administration.
 */
fun exportAdminCollegeAttendanceCsv(
    context: Context,
    className: String,
    subject: String = "",
    section: String = "",
    semester: Int = 1,
    teacherName: String = "",
    sessions: List<AttendanceSession>,
    allStudents: List<Student>,
    allRecords: List<AttendanceRecord>,
    startDate: Long? = null,
    endDate: Long? = null,
    defaulterThresholdPct: Int = 75,
    onResult: ((Boolean, String) -> Unit)? = null
) {
    try {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val sortedSessions = sessions
            .filter { s ->
                (startDate == null || s.sessionDate >= startDate) &&
                (endDate == null || s.sessionDate <= endDate)
            }
            .sortedBy { it.sessionDate }

        if (sortedSessions.isEmpty()) {
            onResult?.invoke(false, "No lectures found in the selected date range.")
            return
        }

        val dateFileStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val safeName = className.replace("[^a-zA-Z0-9_]".toRegex(), "_")
        val file = java.io.File(dir, "College_Register_${safeName}_$dateFileStr.csv")
        val writer = java.io.FileWriter(file)

        val reportGeneratedTimestamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        val sessionDateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val fullDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        val fromStr = fullDateFormat.format(Date(sortedSessions.first().sessionDate))
        val toStr = fullDateFormat.format(Date(sortedSessions.last().sessionDate))

        // 1. Official College Header
        writer.write("========================================================================================\n")
        writer.write("SWIFF MARK - OFFICIAL COLLEGE ATTENDANCE REGISTER\n")
        writer.write("========================================================================================\n")
        writer.write("Class / Course,\"$className\"\n")
        writer.write("Subject Code / Title,\"${subject.ifBlank { "N/A" }}\"\n")
        writer.write("Department & Section,\"${section.ifBlank { "A" }}\"\n")
        writer.write("Semester,\"Semester $semester\"\n")
        writer.write("Faculty In-Charge,\"${teacherName.ifBlank { "Faculty" }}\"\n")
        writer.write("Attendance Period,\"$fromStr to $toStr\"\n")
        writer.write("Total Lectures Conducted in Period,${sortedSessions.size}\n")
        writer.write("Total Enrolled Students,${allStudents.size}\n")
        writer.write("Report Generated Date,\"$reportGeneratedTimestamp\"\n")
        writer.write("Minimum Required Attendance,$defaulterThresholdPct%\n")
        writer.write("========================================================================================\n\n")

        // 2. Table Column Headers: S.No, Roll No, Student Name, Section, Email, [Date 1], [Date 2]..., Attended, Total, %, Shortage Status
        val headerCols = mutableListOf("S.No.", "Roll No", "Student Name", "Section", "Email")
        for (s in sortedSessions) {
            headerCols.add("\"${sessionDateFormat.format(Date(s.sessionDate))}\"")
        }
        headerCols.add("Attended")
        headerCols.add("Total Lectures")
        headerCols.add("Attendance %")
        headerCols.add("Eligibility (<$defaulterThresholdPct%)")
        writer.write(headerCols.joinToString(",") + "\n")

        // Map records by (sessionId + "_" + studentId)
        val recordMap = allRecords.associateBy { "${it.sessionId}_${it.studentId}" }

        val defaulters = mutableListOf<Triple<Student, Int, Int>>() // student, attended, pct
        var totalAttendedSum = 0

        // 3. Student rows
        allStudents.sortedBy { it.rollNumber }.forEachIndexed { index, student ->
            val row = mutableListOf<String>()
            row.add("${index + 1}")
            row.add("${student.rollNumber}")
            row.add("\"${student.fullName}\"")
            row.add("\"${student.classSection ?: section.ifBlank { "A" }}\"")
            row.add("\"${student.email}\"")

            var attendedCount = 0
            for (s in sortedSessions) {
                val rec = recordMap["${s.sessionId}_${student.studentId}"]
                if (rec != null) {
                    if (rec.markedPresent == 1) {
                        row.add("P")
                        attendedCount++
                    } else {
                        row.add("A")
                    }
                } else {
                    row.add("-")
                }
            }

            totalAttendedSum += attendedCount
            val pct = if (sortedSessions.isNotEmpty()) ((attendedCount.toFloat() / sortedSessions.size) * 100).toInt() else 0
            val isDefaulter = pct < defaulterThresholdPct

            row.add("$attendedCount")
            row.add("${sortedSessions.size}")
            row.add("$pct%")
            row.add(if (isDefaulter) "DEFICIENT (SHORTAGE <$defaulterThresholdPct%)" else "ELIGIBLE")

            if (isDefaulter) {
                defaulters.add(Triple(student, attendedCount, pct))
            }

            writer.write(row.joinToString(",") + "\n")
        }

        // 4. College Statistical Summary & Defaulter Breakdown
        writer.write("\n========================================================================================\n")
        writer.write("ACADEMIC SUMMARY & STATISTICAL ANALYSIS\n")
        writer.write("========================================================================================\n")

        val totalPossibleMarks = allStudents.size * sortedSessions.size
        val classAvg = if (totalPossibleMarks > 0) ((totalAttendedSum.toFloat() / totalPossibleMarks) * 1000).toInt() / 10.0 else 0.0
        val eligibleCount = allStudents.size - defaulters.size
        val eligiblePct = if (allStudents.isNotEmpty()) ((eligibleCount.toFloat() / allStudents.size) * 100).toInt() else 0
        val defaulterPct = if (allStudents.isNotEmpty()) ((defaulters.size.toFloat() / allStudents.size) * 100).toInt() else 0

        writer.write("Class Average Attendance Rate,$classAvg%\n")
        writer.write("Total Attendance Marks Recorded (P),$totalAttendedSum\n")
        writer.write("Total Absent Marks Recorded (A),${totalPossibleMarks - totalAttendedSum}\n")
        writer.write("Students Meeting Criteria (>= $defaulterThresholdPct%),$eligibleCount ($eligiblePct%)\n")
        writer.write("Students with Shortage (< $defaulterThresholdPct%),${defaulters.size} ($defaulterPct%)\n\n")

        if (defaulters.isNotEmpty()) {
            writer.write("LIST OF DEFAULTER STUDENTS (< $defaulterThresholdPct% ATTENDANCE):\n")
            writer.write("Roll No,Student Name,Lectures Attended,Total Lectures,Current %,Shortage by (Lectures to Reach $defaulterThresholdPct%)\n")
            for ((dStudent, dAttended, dPct) in defaulters.sortedBy { it.second }) {
                val targetAttended = kotlin.math.ceil((defaulterThresholdPct * sortedSessions.size) / 100.0).toInt()
                val deficit = (targetAttended - dAttended).coerceAtLeast(1)
                writer.write("${dStudent.rollNumber},\"${dStudent.fullName}\",$dAttended,${sortedSessions.size},$dPct%,Short by $deficit lectures\n")
            }
            writer.write("\n")
        } else {
            writer.write("Outstanding Performance: All students meet the $defaulterThresholdPct% attendance requirement!\n\n")
        }

        writer.write("========================================================================================\n")
        writer.write("Faculty Signature: _______________________      HOD / Dean Verification: _______________________\n")
        writer.write("Date Submitted: _______________________\n")
        writer.write("========================================================================================\n")

        writer.flush()
        writer.close()

        shareCsvFile(context, file, "College Attendance Register - $className ($fromStr to $toStr)")

        val msg = "College Register exported: ${file.name}"
        onResult?.invoke(true, msg)
    } catch (e: Exception) {
        val err = "Export Failed: ${e.localizedMessage ?: e.message}"
        onResult?.invoke(false, err)
    }
}

fun exportClassAttendanceCsv(
    context: Context,
    className: String,
    sessions: List<AttendanceSession>,
    allStudents: List<Student>,
    allRecords: List<AttendanceRecord>,
    onResult: ((Boolean, String) -> Unit)? = null
) {
    exportAdminCollegeAttendanceCsv(
        context = context,
        className = className,
        sessions = sessions,
        allStudents = allStudents,
        allRecords = allRecords,
        onResult = onResult
    )
}


