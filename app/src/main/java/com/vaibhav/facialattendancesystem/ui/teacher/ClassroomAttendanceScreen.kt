package com.vaibhav.facialattendancesystem.ui.teacher

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vaibhav.facialattendancesystem.ui.components.CameraPermissionFallbackCard
import com.vaibhav.facialattendancesystem.ui.components.openAppSettings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vaibhav.facialattendancesystem.data.RecognitionLog
import com.vaibhav.facialattendancesystem.data.RecognitionLogDao
import com.vaibhav.facialattendancesystem.data.Student
import com.vaibhav.facialattendancesystem.ml.ConfidenceTier
import com.vaibhav.facialattendancesystem.ml.FaceAligner
import com.vaibhav.facialattendancesystem.ml.FaceClassifierHelper
import com.vaibhav.facialattendancesystem.ml.FaceDetectorHelper
import com.vaibhav.facialattendancesystem.ml.FaceMath
import com.vaibhav.facialattendancesystem.ml.RecognitionCandidate
import com.vaibhav.facialattendancesystem.ml.RecognitionResult
import com.vaibhav.facialattendancesystem.ui.components.AttendanceCameraHelper
import com.vaibhav.facialattendancesystem.ui.theme.ErrorRose
import com.vaibhav.facialattendancesystem.ui.theme.PrimaryCyan
import com.vaibhav.facialattendancesystem.ui.theme.SuccessGreen
import com.vaibhav.facialattendancesystem.ui.theme.WarningAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Stage Enum ────────────────────────────────────────────────────────────────

enum class AttendanceCaptureStage {
    TAKE_PHOTO_1,
    REVIEW_PHOTO_1,
    SESSION_SAVED,       // Photo 1 done — teacher can close app and return later for Photo 2
    TAKE_PHOTO_2,
    REVIEW_PHOTO_2,
    FINAL_SUMMARY
}

// ── Per-student tracking (student-centric, not face-centric) ──────────────────

data class StudentDetectionStatus(
    val student: Student,
    val detectedInPhoto: Boolean,        // Was face picked up by ML?
    val confidenceTier: ConfidenceTier,  // AUTO confidence from model
    val matchConfidence: Float,          // Raw cosine similarity (0..1)
    val isMarkedPresent: Boolean         // Mutable toggle (teacher override)
)

// ── Helper: face-centric results → student-centric statuses ──────────────────

private fun buildStudentStatuses(
    recognitionResults: List<RecognitionResult>,
    enrolledStudents: List<Student>
): List<StudentDetectionStatus> {
    // For each student, find the best (highest confidence) recognition result
    val bestConf  = mutableMapOf<String, Float>()
    val bestTier  = mutableMapOf<String, ConfidenceTier>()

    for (result in recognitionResults) {
        val sid  = result.selectedStudentId ?: continue
        val conf = result.topMatch?.confidenceScore ?: 0f
        if ((bestConf[sid] ?: -1f) < conf) {
            bestConf[sid] = conf
            bestTier[sid] = result.confidenceTier
        }
    }

    return enrolledStudents.sortedBy { it.rollNumber }.map { student ->
        val conf     = bestConf[student.studentId] ?: 0f
        val tier     = bestTier[student.studentId] ?: ConfidenceTier.LOW
        val detected = bestConf.containsKey(student.studentId)
        StudentDetectionStatus(
            student         = student,
            detectedInPhoto = detected,
            confidenceTier  = tier,
            matchConfidence = conf,
            // HIGH → auto-present; MEDIUM → present but amber; LOW/absent → false
            isMarkedPresent = detected && (tier == ConfidenceTier.HIGH || tier == ConfidenceTier.MEDIUM)
        )
    }
}

// ── SharedPreferences draft helpers ──────────────────────────────────────────

private fun saveDraft(
    prefs: android.content.SharedPreferences,
    classId: String,
    sessionId: String,
    photo1Path: String,
    statuses: List<StudentDetectionStatus>
) {
    val ids = statuses.filter { it.isMarkedPresent }.joinToString(",") { it.student.studentId }
    prefs.edit()
        .putString("d_${classId}_sid",    sessionId)
        .putString("d_${classId}_p1",     photo1Path)
        .putString("d_${classId}_ids",    ids)
        .putLong  ("d_${classId}_ts",     System.currentTimeMillis())
        .apply()
}

private fun clearDraft(prefs: android.content.SharedPreferences, classId: String) {
    prefs.edit()
        .remove("d_${classId}_sid")
        .remove("d_${classId}_p1")
        .remove("d_${classId}_ids")
        .remove("d_${classId}_ts")
        .apply()
}

// ── Main Screen ───────────────────────────────────────────────────────────────

@Composable
fun ClassroomAttendanceScreen(
    className: String,
    classId: String,
    enrolledStudents: List<Student>,
    allEmbeddings: List<Pair<Student, List<FloatArray>>>,
    sessionId: String,
    recognitionLogDao: RecognitionLogDao,
    onConfirmAttendance: (photo1Path: String, photo2Path: String, presentStudentIds: Set<String>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val prefs   = remember { context.getSharedPreferences("att_drafts", Context.MODE_PRIVATE) }

    // Shared ML helpers (used by processPhoto on full-res bitmap)
    val detectorHelper   = remember { FaceDetectorHelper(context, minDetectionConfidence = 0.55f) }
    val classifierHelper = remember { FaceClassifierHelper(context) }
    DisposableEffect(Unit) { onDispose { detectorHelper.close(); classifierHelper.close() } }

    var stage       by remember { mutableStateOf(AttendanceCaptureStage.TAKE_PHOTO_1) }
    var isProcessing by remember { mutableStateOf(false) }
    var showResume  by remember { mutableStateOf(false) }

    // Photo 1 state
    var photo1Path by remember { mutableStateOf("") }
    val photo1Statuses = remember { mutableStateListOf<StudentDetectionStatus>() }

    // Photo 2 state
    var photo2Path by remember { mutableStateOf("") }
    val photo2Statuses = remember { mutableStateListOf<StudentDetectionStatus>() }

    // Check for a saved draft session on first open
    LaunchedEffect(classId) {
        val sid = prefs.getString("d_${classId}_sid", null)
        val ts  = prefs.getLong("d_${classId}_ts", 0L)
        val ageHours = (System.currentTimeMillis() - ts) / 3_600_000L
        if (sid != null && ageHours < 24) showResume = true
    }

    // Resume dialog
    if (showResume) {
        val ts  = prefs.getLong("d_${classId}_ts", 0L)
        val fmt = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ts))
        val cnt = prefs.getString("d_${classId}_ids", "")
            ?.split(",")?.count { it.isNotBlank() } ?: 0

        AlertDialog(
            onDismissRequest = { showResume = false },
            containerColor   = MaterialTheme.colorScheme.surface,
            title = { Text("Resume Session?", fontWeight = FontWeight.Bold, color = PrimaryCyan) },
            text  = {
                Text(
                    "Photo 1 was saved at $fmt — $cnt students detected.\n\nResume to take Photo 2 now?",
                    fontSize = 14.sp,
                    color    = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p1     = prefs.getString("d_${classId}_p1", "") ?: ""
                        val ids    = prefs.getString("d_${classId}_ids", "")
                            ?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                        photo1Path = p1
                        photo1Statuses.clear()
                        photo1Statuses.addAll(enrolledStudents.sortedBy { it.rollNumber }.map { s ->
                            StudentDetectionStatus(
                                student         = s,
                                detectedInPhoto = ids.contains(s.studentId),
                                confidenceTier  = if (ids.contains(s.studentId)) ConfidenceTier.HIGH else ConfidenceTier.LOW,
                                matchConfidence = if (ids.contains(s.studentId)) 0.45f else 0f,
                                isMarkedPresent = ids.contains(s.studentId)
                            )
                        })
                        showResume = false
                        stage = AttendanceCaptureStage.TAKE_PHOTO_2
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                ) { Text("RESUME →", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { clearDraft(prefs, classId); showResume = false }) {
                    Text("START FRESH", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    when (stage) {

        AttendanceCaptureStage.TAKE_PHOTO_1 -> {
            CapturePhotoStepView(
                title       = "📷 Photo 1 — Start of Class",
                subtitle    = "Point camera at students. Capture room photo.",
                stepNumber  = 1,
                isProcessing = isProcessing,
                onCancel    = onCancel,
                onCaptureBitmap = { bitmap ->
                    isProcessing = true
                    scope.launch(Dispatchers.IO) {
                        // Save bitmap to disk so photo1Path refers to a real file
                        val savedPath = try {
                            val dir = context.getExternalFilesDir(null) ?: context.filesDir
                            val file = java.io.File(dir, "photo_1_${System.currentTimeMillis()}.jpg")
                            java.io.FileOutputStream(file).use { out ->
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                            }
                            file.absolutePath
                        } catch (e: Exception) { "photo_1_${System.currentTimeMillis()}.jpg" }
                        val results = withContext(Dispatchers.Default) {
                            processPhoto(bitmap, detectorHelper, classifierHelper, allEmbeddings, sessionId, "PHOTO_1", recognitionLogDao)
                        }
                        try { bitmap.recycle() } catch (_: Exception) {}
                        val statuses = buildStudentStatuses(results, enrolledStudents)
                        withContext(Dispatchers.Main) {
                            photo1Path = savedPath
                            photo1Statuses.clear()
                            photo1Statuses.addAll(statuses)
                            isProcessing = false
                            stage = AttendanceCaptureStage.REVIEW_PHOTO_1
                        }
                    }
                }
            )
        }

        AttendanceCaptureStage.REVIEW_PHOTO_1 -> {
            StudentRosterReviewView(
                title        = "Photo 1 Review",
                photoLabel   = "Start of Class — tap any student to toggle",
                statuses     = photo1Statuses,
                onToggle     = { idx, isPresent ->
                    photo1Statuses[idx] = photo1Statuses[idx].copy(isMarkedPresent = isPresent)
                },
                proceedLabel = "SAVE & CONTINUE →",
                onProceed    = {
                    saveDraft(prefs, classId, sessionId, photo1Path, photo1Statuses)
                    stage = AttendanceCaptureStage.SESSION_SAVED
                }
            )
        }

        AttendanceCaptureStage.SESSION_SAVED -> {
            SessionSavedView(
                presentCount  = photo1Statuses.count { it.isMarkedPresent },
                totalStudents = enrolledStudents.size,
                onContinueNow = { stage = AttendanceCaptureStage.TAKE_PHOTO_2 },
                onCloseApp    = onCancel
            )
        }

        AttendanceCaptureStage.TAKE_PHOTO_2 -> {
            CapturePhotoStepView(
                title        = "📷 Photo 2 — End of Class",
                subtitle     = "Take another classroom photo now that class is ending.",
                stepNumber   = 2,
                isProcessing = isProcessing,
                onCancel     = { stage = AttendanceCaptureStage.SESSION_SAVED },
                onCaptureBitmap = { bitmap ->
                    isProcessing = true
                    scope.launch(Dispatchers.IO) {
                        // Save bitmap to disk so photo2Path refers to a real file
                        val savedPath = try {
                            val dir = context.getExternalFilesDir(null) ?: context.filesDir
                            val file = java.io.File(dir, "photo_2_${System.currentTimeMillis()}.jpg")
                            java.io.FileOutputStream(file).use { out ->
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                            }
                            file.absolutePath
                        } catch (e: Exception) { "photo_2_${System.currentTimeMillis()}.jpg" }
                        val results = withContext(Dispatchers.Default) {
                            processPhoto(bitmap, detectorHelper, classifierHelper, allEmbeddings, sessionId, "PHOTO_2", recognitionLogDao)
                        }
                        try { bitmap.recycle() } catch (_: Exception) {}
                        val statuses = buildStudentStatuses(results, enrolledStudents)
                        withContext(Dispatchers.Main) {
                            photo2Path = savedPath
                            photo2Statuses.clear()
                            photo2Statuses.addAll(statuses)
                            isProcessing = false
                            stage = AttendanceCaptureStage.REVIEW_PHOTO_2
                        }
                    }
                }
            )
        }

        AttendanceCaptureStage.REVIEW_PHOTO_2 -> {
            StudentRosterReviewView(
                title        = "Photo 2 Review",
                photoLabel   = "End of Class — tap any student to toggle",
                statuses     = photo2Statuses,
                onToggle     = { idx, isPresent ->
                    photo2Statuses[idx] = photo2Statuses[idx].copy(isMarkedPresent = isPresent)
                },
                proceedLabel = "FINALIZE ATTENDANCE →",
                onProceed    = { stage = AttendanceCaptureStage.FINAL_SUMMARY }
            )
        }

        AttendanceCaptureStage.FINAL_SUMMARY -> {
            FinalSummaryView(
                className        = className,
                enrolledStudents = enrolledStudents,
                photo1Statuses   = photo1Statuses,
                photo2Statuses   = photo2Statuses,
                onConfirm        = { finalPresentIds ->
                    clearDraft(prefs, classId)
                    onConfirmAttendance(photo1Path, photo2Path, finalPresentIds)
                },
                onRetake = { stage = AttendanceCaptureStage.TAKE_PHOTO_1 }
            )
        }
    }
}

// ── Capture Step View ─────────────────────────────────────────────────────────

@Composable
fun CapturePhotoStepView(
    title: String,
    subtitle: String,
    stepNumber: Int,
    isProcessing: Boolean,
    onCaptureBitmap: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cameraHelper   = remember { AttendanceCameraHelper(context) }
    var inFlight       by remember { mutableStateOf(false) }
    var liveFaceCount  by remember { mutableStateOf(0) }
    val previewDetector = remember { FaceDetectorHelper(context, minDetectionConfidence = 0.50f) }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            cameraHelper.onFacesUpdated = { faces ->
                liveFaceCount = faces.size
            }
            cameraHelper.bindToLifecycle(lifecycleOwner, previewView, detectorHelper = previewDetector, frontCamera = false)
        }
    }
    DisposableEffect(hasCameraPermission) {
        onDispose {
            if (hasCameraPermission) {
                cameraHelper.release()
                previewDetector.close()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Step badge & Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(PrimaryCyan),
                contentAlignment = Alignment.Center
            ) { Text("$stepNumber", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Viewfinder Box with Live Face Counter Overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(2.dp, PrimaryCyan, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                // Live Faces in View Pill (Top Center)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.70f))
                        .border(
                            1.dp,
                            if (liveFaceCount > 0) SuccessGreen else Color.White.copy(alpha = 0.25f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (liveFaceCount > 0) "👥 $liveFaceCount Face${if (liveFaceCount > 1) "s" else ""} in Frame"
                               else "👥 Point camera at students",
                        color = if (liveFaceCount > 0) SuccessGreen else Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            } else {
                CameraPermissionFallbackCard(
                    title = "Camera Access Required",
                    description = "Swiff Mark needs camera permission to capture classroom photos and recognize student faces.",
                    onRequestPermission = { permissionLauncher.launch(android.Manifest.permission.CAMERA) },
                    onOpenSettings = { openAppSettings(context) }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Action Buttons Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onCancel,
                shape   = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(52.dp)
            ) { Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant) }

            Button(
                onClick = {
                    if (hasCameraPermission && !isProcessing && !inFlight) {
                        inFlight = true
                        cameraHelper.capturePhoto { bitmap ->
                            inFlight = false
                            if (bitmap != null) {
                                onCaptureBitmap(bitmap)
                            }
                        }
                    }
                },
                enabled  = hasCameraPermission && !isProcessing && !inFlight,
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                modifier = Modifier.weight(2f).height(52.dp)
            ) {
                if (isProcessing || inFlight) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
                } else {
                    Text("CAPTURE PHOTO", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ── Student Roster Review View ────────────────────────────────────────────────

@Composable
fun StudentRosterReviewView(
    title: String,
    photoLabel: String,
    statuses: List<StudentDetectionStatus>,
    onToggle: (index: Int, isPresent: Boolean) -> Unit,
    proceedLabel: String,
    onProceed: () -> Unit
) {
    val presentCount = statuses.count { it.isMarkedPresent }
    val uncertainCount = statuses.count { it.detectedInPhoto && it.confidenceTier == ConfidenceTier.MEDIUM && it.isMarkedPresent }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
        Text(photoLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))

        // Quick stats row
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AttChip("✓ Present",   presentCount,           SuccessGreen)
            AttChip("✕ Absent",    statuses.size - presentCount, ErrorRose)
            AttChip("Total",       statuses.size,           PrimaryCyan)
        }

        if (uncertainCount > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "⚠ $uncertainCount medium-confidence matches — verify below",
                fontSize = 12.sp, color = WarningAmber, fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text("Tap any row to toggle Present ↔ Absent", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(statuses) { idx, status ->
                StudentStatusRow(status = status, onToggle = { onToggle(idx, !status.isMarkedPresent) })
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick  = onProceed,
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text(proceedLabel, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
        }
    }
}

// ── Individual Student Row (toggleable) ────────────────────────────────────────

@Composable
fun StudentStatusRow(status: StudentDetectionStatus, onToggle: () -> Unit) {
    val bgColor = when {
        status.isMarkedPresent && status.confidenceTier == ConfidenceTier.HIGH   -> SuccessGreen.copy(alpha = 0.08f)
        status.isMarkedPresent && status.confidenceTier == ConfidenceTier.MEDIUM -> WarningAmber.copy(alpha = 0.10f)
        status.isMarkedPresent                                                    -> PrimaryCyan.copy(alpha = 0.08f)
        else                                                                      -> ErrorRose.copy(alpha = 0.06f)
    }
    val dotColor = when {
        status.isMarkedPresent && status.confidenceTier == ConfidenceTier.HIGH   -> SuccessGreen
        status.isMarkedPresent && status.confidenceTier == ConfidenceTier.MEDIUM -> WarningAmber
        status.isMarkedPresent                                                    -> PrimaryCyan
        else                                                                      -> ErrorRose
    }
    val pct = FaceMath.calibratedConfidencePercent(status.matchConfidence)
    val subLabel = when {
        status.isMarkedPresent && status.confidenceTier == ConfidenceTier.HIGH   -> "✓ Auto-detected · $pct% match"
        status.isMarkedPresent && status.confidenceTier == ConfidenceTier.MEDIUM -> "⚠ Uncertain · $pct% — confirm?"
        status.isMarkedPresent                                                    -> "✓ Manually marked present"
        status.detectedInPhoto                                                    -> "✕ Low confidence · $pct% — marked absent"
        else                                                                      -> "✕ Not detected in photo"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Roll circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = "${status.student.rollNumber}",
                fontSize   = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = dotColor
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(status.student.fullName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(subLabel, fontSize = 11.sp, color = dotColor, fontWeight = FontWeight.SemiBold)
        }

        // Toggle circle
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(dotColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = if (status.isMarkedPresent) "✓" else "✕",
                color      = Color.White,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Summary chip ──────────────────────────────────────────────────────────────

@Composable
fun AttChip(label: String, count: Int, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

// ── Session Saved View ────────────────────────────────────────────────────────

@Composable
fun SessionSavedView(
    presentCount: Int,
    totalStudents: Int,
    onContinueNow: () -> Unit,
    onCloseApp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("✅", fontSize = 60.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Photo 1 Saved!", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = SuccessGreen)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text      = "$presentCount of $totalStudents students detected at class start.",
            fontSize  = 15.sp,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PrimaryCyan.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("What happens next?", fontWeight = FontWeight.Bold, color = PrimaryCyan, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("1. Close this app and teach normally", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("2. Re-open app at end of class", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("3. Tap your class → Resume session", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("4. Capture Photo 2 → app marks attendance", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick  = onContinueNow,
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) { Text("TAKE PHOTO 2 NOW", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp) }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick  = onCloseApp,
            shape    = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) { Text("SAVE & CLOSE — RETURN LATER", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold) }
    }
}

// ── Final Summary View ────────────────────────────────────────────────────────

@Composable
fun FinalSummaryView(
    className: String,
    enrolledStudents: List<Student>,
    photo1Statuses: List<StudentDetectionStatus>,
    photo2Statuses: List<StudentDetectionStatus>,
    onConfirm: (Set<String>) -> Unit,
    onRetake: () -> Unit
) {
    val p1Present = photo1Statuses.filter { it.isMarkedPresent }.map { it.student.studentId }.toSet()
    val p2Present = photo2Statuses.filter { it.isMarkedPresent }.map { it.student.studentId }.toSet()

    val autoPresent = p1Present.intersect(p2Present)          // Both photos → auto PRESENT
    val photo1Only  = p1Present - p2Present                   // Left early?
    val photo2Only  = p2Present - p1Present                   // Arrived late?
    val neitherIds  = enrolledStudents.map { it.studentId }.toSet() - p1Present - p2Present

    // Mutable teacher decisions for edge-case students
    // photo1Only default = false (absent unless teacher marks present)
    val earlyLeaveMap = remember(photo1Only) {
        mutableStateMapOf<String, Boolean>().also { m -> photo1Only.forEach { m[it] = false } }
    }
    // photo2Only default = true (arrived late but was there)
    val lateArriveMap = remember(photo2Only) {
        mutableStateMapOf<String, Boolean>().also { m -> photo2Only.forEach { m[it] = true } }
    }

    val finalPresentIds: Set<String> =
        autoPresent +
        earlyLeaveMap.filter { it.value }.keys +
        lateArriveMap.filter { it.value }.keys

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Text("Attendance Summary", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
        Text(className, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AttChip("✓ Present", finalPresentIds.size,                    SuccessGreen)
            AttChip("✕ Absent",  enrolledStudents.size - finalPresentIds.size, ErrorRose)
            AttChip("Total",     enrolledStudents.size,                   PrimaryCyan)
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.weight(1f)
        ) {

            // ── Section 1: Auto-present (both photos) ──────────────────────
            if (autoPresent.isNotEmpty()) {
                item { FinalSectionHeader("✓ Present in Both Photos (${autoPresent.size})", SuccessGreen) }
                items(autoPresent.toList()) { sid ->
                    enrolledStudents.find { it.studentId == sid }?.let { s ->
                        FinalStudentRow(s, "✓ Present", SuccessGreen)
                    }
                }
            }

            // ── Section 2: Photo 1 only — left early? ─────────────────────
            if (photo1Only.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    FinalSectionHeader("⚠ Seen in Photo 1 only — Left Early? (${photo1Only.size})", WarningAmber)
                    Text(
                        "Toggle each student: Present (left early) or Absent",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(photo1Only.toList()) { sid ->
                    val student = enrolledStudents.find { it.studentId == sid } ?: return@items
                    val present = earlyLeaveMap[sid] ?: false
                    EarlyLeaveRow(
                        student  = student,
                        isPresent = present,
                        label    = if (present) "Left Early — Marked Present" else "Left Early — Marked Absent",
                        onToggle = { earlyLeaveMap[sid] = !present }
                    )
                }
            }

            // ── Section 3: Photo 2 only — arrived late? ───────────────────
            if (photo2Only.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    FinalSectionHeader("⚠ Seen in Photo 2 only — Late Arrival? (${photo2Only.size})", WarningAmber)
                    Text(
                        "Toggle each student: Present (arrived late) or Absent",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(photo2Only.toList()) { sid ->
                    val student = enrolledStudents.find { it.studentId == sid } ?: return@items
                    val present = lateArriveMap[sid] ?: true
                    EarlyLeaveRow(
                        student  = student,
                        isPresent = present,
                        label    = if (present) "Late Arrival — Marked Present" else "Late Arrival — Marked Absent",
                        onToggle = { lateArriveMap[sid] = !present }
                    )
                }
            }

            // ── Section 4: Absent (neither photo) ─────────────────────────
            if (neitherIds.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    FinalSectionHeader("✕ Absent — Not in Either Photo (${neitherIds.size})", ErrorRose)
                }
                items(neitherIds.toList()) { sid ->
                    enrolledStudents.find { it.studentId == sid }?.let { s ->
                        FinalStudentRow(s, "✕ Absent", ErrorRose)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick  = onRetake,
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(52.dp)
            ) { Text("RETAKE", color = MaterialTheme.colorScheme.onSurfaceVariant) }

            Button(
                onClick  = { onConfirm(finalPresentIds) },
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                modifier = Modifier.weight(2f).height(52.dp)
            ) {
                Text(
                    "CONFIRM (${finalPresentIds.size} Present)",
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
            }
        }
    }
}

@Composable
private fun FinalSectionHeader(text: String, color: Color) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
}

@Composable
private fun FinalStudentRow(student: Student, status: String, statusColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(statusColor.copy(alpha = 0.07f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "Roll ${student.rollNumber}: ${student.fullName}",
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(student.classSection ?: "General", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(status, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun EarlyLeaveRow(student: Student, isPresent: Boolean, label: String, onToggle: () -> Unit) {
    val color = if (isPresent) SuccessGreen else ErrorRose
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.07f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Roll ${student.rollNumber}: ${student.fullName}",
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(if (isPresent) "✓" else "✕", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── processPhoto (1-to-1 Multi-Face Bipartite Matching) ───────────────────────

private fun processPhoto(
    bitmap: Bitmap,
    detector: FaceDetectorHelper,
    classifier: FaceClassifierHelper,
    allEmbeddings: List<Pair<Student, List<FloatArray>>>,
    sessionId: String,
    photoLabel: String,
    logDao: RecognitionLogDao
): List<RecognitionResult> {

    val faces = detector.detectFaces(bitmap)
    if (faces.isEmpty()) return emptyList()

    // Step A: Extract embeddings and candidate lists for each face in the bench
    val faceCandidateMap = mutableMapOf<Int, List<RecognitionCandidate>>()
    val faceElapsedMap = mutableMapOf<Int, Long>()

    for ((index, face) in faces.withIndex()) {
        val faceIndex = index + 1
        val alignedChip = FaceAligner.align(bitmap, face)
        val queryEmbedding = classifier.getFaceEmbedding(alignedChip)
        try { if (alignedChip != bitmap) alignedChip.recycle() } catch (_: Exception) {}

        val startMs = System.currentTimeMillis()
        val sortedCandidates = FaceMath.findCandidatesForFace(queryEmbedding, allEmbeddings)
        val elapsedMs = System.currentTimeMillis() - startMs

        faceCandidateMap[faceIndex] = sortedCandidates
        faceElapsedMap[faceIndex] = elapsedMs
    }

    // Step B: 1-to-1 Bipartite Multi-Face Optimal Matching (REQ-ATT-001 / REQ-ATT-006)
    val assignedMatches = FaceMath.assignMultiFaceMatches(faceCandidateMap)

    val results = mutableListOf<RecognitionResult>()

    for ((index, face) in faces.withIndex()) {
        val faceIndex = index + 1
        val sortedCandidates = faceCandidateMap[faceIndex] ?: emptyList()
        val elapsedMs = faceElapsedMap[faceIndex] ?: 0L
        val assignedCandidate = assignedMatches[faceIndex]

        val tier = if (assignedCandidate != null) {
            FaceMath.getConfidenceTier(assignedCandidate.confidenceScore)
        } else {
            ConfidenceTier.LOW
        }

        val top5 = sortedCandidates.take(5)
        val top5Summary = top5.joinToString(",") { "${it.studentId}:${"%.4f".format(it.confidenceScore)}" }

        sortedCandidates.forEachIndexed { ci, candidate ->
            try {
                logDao.insertLog(
                    RecognitionLog(
                        sessionId = sessionId,
                        detectedFaceIndex = faceIndex,
                        matchedStudentId = candidate.studentId,
                        confidenceScore = candidate.confidenceScore,
                        top5Candidates = if (ci == 0) top5Summary else null,
                        processingTimeMs = if (ci == 0) elapsedMs else null,
                        photoLabel = photoLabel
                    )
                )
            } catch (e: Exception) { e.printStackTrace() }
        }

        if (sortedCandidates.isEmpty()) {
            try {
                logDao.insertLog(
                    RecognitionLog(
                        sessionId = sessionId,
                        detectedFaceIndex = faceIndex,
                        matchedStudentId = null,
                        confidenceScore = 0f,
                        errorMessage = "no_enrolled_students",
                        photoLabel = photoLabel
                    )
                )
            } catch (e: Exception) { e.printStackTrace() }
        }

        results.add(
            RecognitionResult(
                faceIndex = faceIndex,
                boundingBox = face.boundingBox,
                topMatch = assignedCandidate ?: sortedCandidates.firstOrNull(),
                top5Candidates = top5,
                confidenceTier = tier,
                selectedStudentId = if (tier != ConfidenceTier.LOW) assignedCandidate?.studentId else null
            )
        )
    }
    return results
}
