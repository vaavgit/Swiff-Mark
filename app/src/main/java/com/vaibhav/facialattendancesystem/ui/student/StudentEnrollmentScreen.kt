package com.vaibhav.facialattendancesystem.ui.student

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.facialattendancesystem.ml.FaceAligner
import com.vaibhav.facialattendancesystem.ml.DetectedFace
import com.vaibhav.facialattendancesystem.ml.FaceClassifierHelper
import com.vaibhav.facialattendancesystem.ml.FaceDetectorHelper
import com.vaibhav.facialattendancesystem.ml.FaceMath
import com.vaibhav.facialattendancesystem.ml.FaceQualityMetrics
import com.vaibhav.facialattendancesystem.ml.ImageQualityValidator
import com.vaibhav.facialattendancesystem.ui.components.CameraView
import com.vaibhav.facialattendancesystem.ui.components.adaptiveBorderColor
import com.vaibhav.facialattendancesystem.ui.theme.ErrorRose
import com.vaibhav.facialattendancesystem.ui.theme.PrimaryCyan
import com.vaibhav.facialattendancesystem.ui.theme.SuccessGreen
import com.vaibhav.facialattendancesystem.ui.theme.WarningAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CapturedStepData(
    val stepAngle: String,
    val bitmap: Bitmap,
    val faceBox: RectF,
    val embedding: FloatArray,
    val qualityScore: Float
)

@Composable
fun StudentEnrollmentScreen(
    studentId: String,
    studentName: String,
    onEnrollmentComplete: (List<CapturedStepData>, FloatArray) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val detectorHelper = remember { FaceDetectorHelper(context) }
    val classifierHelper = remember { FaceClassifierHelper(context) }

    DisposableEffect(Unit) {
        onDispose {
            detectorHelper.close()
            classifierHelper.close()
        }
    }

    if (!classifierHelper.isModelLoaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Model Not Loaded",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ErrorRose
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = classifierHelper.loadErrorMessage
                        ?: "Face recognition neural weights missing. Please verify asset file adaface_model.tflite.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val steps = listOf(
        "STRAIGHT" to "Look Straight at Camera",
        "BLINK" to "Blink Eyes Naturally (Liveness Check)",
        "UP" to "Tilt Head Up Slightly (30°)",
        "DOWN" to "Tilt Head Down Slightly (30°)",
        "LEFT" to "Turn Head Left (30°)",
        "RIGHT" to "Turn Head Right (30°)",
        "SMILE" to "Smile Naturally"
    )

    var currentStepIndex by remember { mutableStateOf(0) }
    var hasBlinkedDuringStep by remember(currentStepIndex) { mutableStateOf(false) }
    var currentFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detectedFaces by remember { mutableStateOf<List<DetectedFace>>(emptyList()) }
    var qualityMetrics by remember { mutableStateOf<FaceQualityMetrics?>(null) }

    val capturedData = remember { mutableStateListOf<CapturedStepData>() }
    var isProcessingStep by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }

    if (isFinished) {
        val averagedVector = remember {
            FaceMath.averageEmbeddings(capturedData.map { it.embedding })
        }

        EnrollmentSummaryView(
            studentName = studentName,
            capturedSteps = capturedData,
            onConfirm = { onEnrollmentComplete(capturedData, averagedVector) }
        )
        return
    }

    val (currentAngle, currentInstruction) = steps[currentStepIndex]

    Column(


        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 28.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {



        // ── Dot Step Indicator ────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Step ${currentStepIndex + 1} / ${steps.size}  ·  ${currentAngle}",
                color = PrimaryCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = studentName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }

        // Dot progress row
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            repeat(steps.size) { i ->
                Box(
                    modifier = Modifier
                        .size(if (i == currentStepIndex) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (i <= currentStepIndex) PrimaryCyan
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = currentInstruction,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Keep your face inside the oval",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = PrimaryCyan,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )


    val isAnalyzingFrame = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    // Camera Preview Frame
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
    ) {
        CameraView(
            modifier = Modifier.fillMaxSize(),
            isFrontCamera = true,
            detectedFaces = detectedFaces,
            drawBoundingBoxes = false,
            drawOvalGuide = true,
            onFrameAnalyzed = { bitmap ->
                currentFrameBitmap = bitmap
                if (isAnalyzingFrame.compareAndSet(false, true)) {
                    scope.launch(Dispatchers.Default) {
                        try {
                            val faces = detectorHelper.detectFaces(bitmap)
                            withContext(Dispatchers.Main) {
                                detectedFaces = faces
                                val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                                if (primaryFace != null && primaryFace.boundingBox.width() >= bitmap.width * 0.20f) {
                                    qualityMetrics = ImageQualityValidator.validateFaceQuality(bitmap, primaryFace.boundingBox)
                                    if (currentAngle == "BLINK") {
                                        val leftProb = primaryFace.leftEyeOpenProbability ?: 1.0f
                                        val rightProb = primaryFace.rightEyeOpenProbability ?: 1.0f
                                        if (leftProb < 0.25f || rightProb < 0.25f) {
                                            hasBlinkedDuringStep = true
                                        }
                                    }
                                } else {
                                    qualityMetrics = null
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            isAnalyzingFrame.set(false)
                        }
                    }
                }
            }
        )


            // Real-time Live Quality Badge
            qualityMetrics?.let { metrics ->
                val badgeColor = if (metrics.isSharp && metrics.isBrightnessOk) SuccessGreen else WarningAmber
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(badgeColor.copy(alpha = 0.9f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = metrics.qualityMessage,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Capture Button & Quality Status
        val primaryFace = detectedFaces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        val frame = currentFrameBitmap
        val hasValidFace = primaryFace != null && frame != null &&
            (primaryFace.boundingBox.width() >= frame.width * 0.20f) &&
            (primaryFace.boundingBox.centerX() in (frame.width * 0.20f)..(frame.width * 0.80f)) &&
            (primaryFace.boundingBox.centerY() in (frame.height * 0.15f)..(frame.height * 0.85f))
        val haptic = LocalHapticFeedback.current
        val (isPoseValid, posePrompt) = checkStepPose(currentAngle, primaryFace, hasBlinkedDuringStep)
        val isReadyToCapture = hasValidFace && isPoseValid && !isProcessingStep

        val onExecuteCapture: () -> Unit = {
            val currentFrame = currentFrameBitmap
            if (currentFrame != null && !isProcessingStep && hasValidFace && isPoseValid) {
                try {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } catch (_: Exception) {}
                val faceBox = primaryFace?.boundingBox ?: RectF(
                    currentFrame.width * 0.15f,
                    currentFrame.height * 0.15f,
                    currentFrame.width * 0.85f,
                    currentFrame.height * 0.85f
                )
                val targetFace = primaryFace
                isProcessingStep = true

                scope.launch(Dispatchers.Default) {
                    val displayCrop = ImageQualityValidator.cropBitmapSafely(currentFrame, faceBox)
                    val alignedFace = if (targetFace != null) {
                        FaceAligner.align(currentFrame, targetFace)
                    } else {
                        displayCrop
                    }
                    val embedding = classifierHelper.getFaceEmbedding(alignedFace)
                    if (alignedFace != displayCrop && alignedFace != currentFrame) {
                        try { alignedFace.recycle() } catch (_: Exception) {}
                    }
                    val score = qualityMetrics?.overallQualityScore ?: 0.8f

                    withContext(Dispatchers.Main) {
                        capturedData.add(
                            CapturedStepData(
                                stepAngle = currentAngle,
                                bitmap = displayCrop,
                                faceBox = faceBox,
                                embedding = embedding,
                                qualityScore = score
                            )
                        )
                        isProcessingStep = false

                        if (currentStepIndex < steps.size - 1) {
                            currentStepIndex++
                        } else {
                            isFinished = true
                        }
                    }
                }
            }
        }

        var isHoldingValidPose by remember { mutableStateOf(false) }

        // Hands-free auto capture: automatically captures when user holds the verified pose
        LaunchedEffect(isReadyToCapture, currentStepIndex) {
            if (isReadyToCapture && !isProcessingStep && !isFinished) {
                isHoldingValidPose = true
                kotlinx.coroutines.delay(600L)
                if (isReadyToCapture && !isProcessingStep && !isFinished) {
                    onExecuteCapture()
                }
                isHoldingValidPose = false
            } else {
                isHoldingValidPose = false
            }
        }

        Button(
            onClick = onExecuteCapture,
            enabled = isReadyToCapture,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryCyan,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (isProcessingStep) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else if (!hasValidFace) {
                Text(
                    text = if (primaryFace == null) "ALIGN FACE IN OVAL" else "MOVE CLOSER TO CAMERA",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!isPoseValid) {
                Text(
                    text = posePrompt,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarningAmber
                )
            } else if (isHoldingValidPose) {
                Text(
                    text = "HOLD STILL... AUTO-CAPTURING",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            } else {
                Text(
                    text = "CAPTURE PHOTO (${currentStepIndex + 1}/${steps.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

private fun checkStepPose(angle: String, face: DetectedFace?, hasBlinked: Boolean = false): Pair<Boolean, String> {
    if (face == null) return Pair(false, "ALIGN FACE IN OVAL")
    val pitch = face.headEulerAngleX
    val yaw = face.headEulerAngleY
    val smile = face.smilingProbability ?: 0f

    return when (angle) {
        "STRAIGHT" -> {
            if (kotlin.math.abs(pitch) > 16f || kotlin.math.abs(yaw) > 16f) {
                Pair(false, "LOOK DIRECTLY AT CAMERA")
            } else {
                Pair(true, "PERFECT - HOLD STILL")
            }
        }
        "BLINK" -> {
            val leftProb = face.leftEyeOpenProbability ?: 1.0f
            val rightProb = face.rightEyeOpenProbability ?: 1.0f
            if (!hasBlinked) {
                Pair(false, "BLINK YOUR EYES NATURALLY")
            } else if (leftProb < 0.35f || rightProb < 0.35f) {
                Pair(false, "OPEN EYES AFTER BLINK")
            } else {
                Pair(true, "LIVENESS VERIFIED (LIVE PERSON)")
            }
        }
        "UP" -> {
            if (pitch < 7f) {
                Pair(false, "TILT HEAD UP SLIGHTLY")
            } else {
                Pair(true, "UPWARD POSE CONFIRMED")
            }
        }
        "DOWN" -> {
            if (pitch > -7f) {
                Pair(false, "TILT HEAD DOWN SLIGHTLY")
            } else {
                Pair(true, "DOWNWARD POSE CONFIRMED")
            }
        }
        "LEFT" -> {
            if (kotlin.math.abs(yaw) < 9f) {
                Pair(false, "TURN HEAD TO THE LEFT")
            } else {
                Pair(true, "LEFT POSE CONFIRMED")
            }
        }
        "RIGHT" -> {
            if (kotlin.math.abs(yaw) < 9f) {
                Pair(false, "TURN HEAD TO THE RIGHT")
            } else {
                Pair(true, "RIGHT POSE CONFIRMED")
            }
        }
        "SMILE" -> {
            if (smile < 0.45f) {
                Pair(false, "SMILE NATURALLY (LIVENESS)")
            } else {
                Pair(true, "LIVENESS CONFIRMED")
            }
        }
        else -> Pair(true, "READY")
    }
}

@Composable
fun EnrollmentSummaryView(
    studentName: String,
    capturedSteps: List<CapturedStepData>,
    onConfirm: () -> Unit
) {
    var isSaving by remember { mutableStateOf(false) }

    val straightStep = capturedSteps.find { it.stepAngle == "STRAIGHT" } ?: capturedSteps.firstOrNull()
    val hasInconsistentFace = remember(capturedSteps) {
        if (straightStep == null) false
        else {
            capturedSteps.any { step ->
                if (step.stepAngle == "STRAIGHT") false
                else {
                    val sim = FaceMath.cosineSimilarity(straightStep.embedding, step.embedding)
                    sim < 0.25f
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (hasInconsistentFace) ErrorRose.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f)
        ) {
            Text(
                text = if (hasInconsistentFace) "Face Validation Failed" else "Face Profile Complete",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasInconsistentFace) ErrorRose else SuccessGreen,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (hasInconsistentFace) "Inconsistent identity detected across capture angles" else "6 angles captured for $studentName",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (hasInconsistentFace) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ErrorRose.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = "Pairwise biometric check failed: Captures appear to be from different people or altered photos. Please discard and re-enroll.",
                    color = ErrorRose,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(capturedSteps) { step ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, adaptiveBorderColor(), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            bitmap = step.bitmap.asImageBitmap(),
                            contentDescription = step.stepAngle,
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = step.stepAngle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryCyan,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (!isSaving && !hasInconsistentFace) {
                    isSaving = true
                    onConfirm()
                }
            },
            enabled = !isSaving && !hasInconsistentFace,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryCyan,
                disabledContainerColor = PrimaryCyan.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (isSaving) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "SAVING PROFILE...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            } else {
                Text(
                    text = "SAVE PROFILE & JOIN CLASSES",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        }
    }
}

