package com.vaibhav.facialattendancesystem.ui.student

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
    onEnrollmentComplete: (List<CapturedStepData>, FloatArray) -> Unit,
    onSkip: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    androidx.activity.compose.BackHandler(enabled = onSkip != null) {
        onSkip?.invoke()
    }

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
        "STRAIGHT" to "Look Directly at Camera",
        "LEFT" to "Turn Head Slowly Left (10-15°)",
        "RIGHT" to "Turn Head Slowly Right (10-15°)",
        "SMILE" to "Smile Naturally (Liveness Check)"
    )

    var currentStepIndex by remember { mutableStateOf(0) }
    var stepEnterTimestamp by remember(currentStepIndex) { mutableStateOf(System.currentTimeMillis()) }
    var currentFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detectedFaces by remember { mutableStateOf<List<DetectedFace>>(emptyList()) }
    var qualityMetrics by remember { mutableStateOf<FaceQualityMetrics?>(null) }
    var frameAnalysisCount by remember { mutableStateOf(0) }

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
    val progressFraction = currentStepIndex.toFloat() / steps.size.toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 28.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // ── Header & Step Indicator ────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Biometric Setup · Step ${currentStepIndex + 1} of ${steps.size}",
                color = PrimaryCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            if (onSkip != null) {
                TextButton(
                    onClick = onSkip,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Skip for now", color = PrimaryCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text(
                    text = studentName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }

        // 4 Segmented story-style progress bars (Apple / Revolut KYC style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            steps.forEachIndexed { i, _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when {
                                i < currentStepIndex -> SuccessGreen
                                i == currentStepIndex -> PrimaryCyan
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                            }
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = currentInstruction,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Center face in the oval and follow the prompts",
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
            progressFraction = progressFraction,
            onFrameAnalyzed = { bitmap ->
                currentFrameBitmap = bitmap
                if (isAnalyzingFrame.compareAndSet(false, true)) {
                    scope.launch(Dispatchers.Default) {
                        try {
                            val faces = detectorHelper.detectFaces(bitmap)
                            withContext(Dispatchers.Main) {
                                detectedFaces = faces
                                frameAnalysisCount++
                                val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                                if (primaryFace != null && primaryFace.boundingBox.width() >= bitmap.width * 0.15f) {
                                    if (frameAnalysisCount % 3 == 0 || qualityMetrics == null) {
                                        qualityMetrics = ImageQualityValidator.validateFaceQuality(bitmap, primaryFace.boundingBox)
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

            // Real-time Live Guidance Floating Pill (Apple Face ID / Revolut style)
            val primaryFace = detectedFaces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            val frame = currentFrameBitmap
            val faceWidthRatio = if (primaryFace != null && frame != null && frame.width > 0) {
                primaryFace.boundingBox.width() / frame.width
            } else 0f
            val faceCenterXRatio = if (primaryFace != null && frame != null && frame.width > 0) {
                primaryFace.boundingBox.centerX() / frame.width
            } else 0f
            val faceCenterYRatio = if (primaryFace != null && frame != null && frame.height > 0) {
                primaryFace.boundingBox.centerY() / frame.height
            } else 0f

            val isFaceSufficientSize = faceWidthRatio in 0.15f..0.85f
            val isFaceCentered = faceCenterXRatio in 0.15f..0.85f && faceCenterYRatio in 0.10f..0.90f
            val hasValidFace = primaryFace != null && frame != null && isFaceSufficientSize && isFaceCentered

            val facePrompt = when {
                primaryFace == null -> "POSITION FACE INSIDE OVAL"
                faceWidthRatio < 0.15f -> "MOVE CLOSER TO CAMERA"
                faceWidthRatio > 0.85f -> "MOVE SLIGHTLY BACK"
                !isFaceCentered -> "CENTER FACE IN OVAL"
                else -> "ALIGN FACE IN OVAL"
            }

            val elapsedStepMs = System.currentTimeMillis() - stepEnterTimestamp
            val allowEyeGrace = elapsedStepMs > 2500L

            val haptic = LocalHapticFeedback.current
            val (isPoseValid, posePrompt) = checkStepPose(
                angle = currentAngle,
                face = primaryFace,
                allowEyeGrace = allowEyeGrace
            )
            val isReadyToCapture = hasValidFace && isPoseValid && !isProcessingStep

            var isHoldingValidPose by remember { mutableStateOf(false) }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (isHoldingValidPose) SuccessGreen.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isHoldingValidPose) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "HOLD STILL... CAPTURING!",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    } else {
                        val promptText = if (!hasValidFace) facePrompt else posePrompt
                        val promptColor = if (!hasValidFace) MaterialTheme.colorScheme.onSurfaceVariant else if (isPoseValid) SuccessGreen else WarningAmber
                        Text(
                            text = promptText,
                            color = promptColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Capture Button & Quality Status
        val primaryFace = detectedFaces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        val frame = currentFrameBitmap
        val faceWidthRatio = if (primaryFace != null && frame != null && frame.width > 0) {
            primaryFace.boundingBox.width() / frame.width
        } else 0f
        val faceCenterXRatio = if (primaryFace != null && frame != null && frame.width > 0) {
            primaryFace.boundingBox.centerX() / frame.width
        } else 0f
        val faceCenterYRatio = if (primaryFace != null && frame != null && frame.height > 0) {
            primaryFace.boundingBox.centerY() / frame.height
        } else 0f

        val isFaceSufficientSize = faceWidthRatio in 0.15f..0.85f
        val isFaceCentered = faceCenterXRatio in 0.15f..0.85f && faceCenterYRatio in 0.10f..0.90f
        val hasValidFace = primaryFace != null && frame != null && isFaceSufficientSize && isFaceCentered

        val facePrompt = when {
            primaryFace == null -> "POSITION FACE INSIDE OVAL"
            faceWidthRatio < 0.15f -> "MOVE CLOSER TO CAMERA"
            faceWidthRatio > 0.85f -> "MOVE SLIGHTLY BACK"
            !isFaceCentered -> "CENTER FACE IN OVAL"
            else -> "ALIGN FACE IN OVAL"
        }

        val elapsedStepMs = System.currentTimeMillis() - stepEnterTimestamp
        val allowEyeGrace = elapsedStepMs > 2500L

        val haptic = LocalHapticFeedback.current
        val (isPoseValid, posePrompt) = checkStepPose(
            angle = currentAngle,
            face = primaryFace,
            allowEyeGrace = allowEyeGrace
        )
        val isReadyToCapture = hasValidFace && isPoseValid && !isProcessingStep

        val onExecuteCapture: () -> Unit = {
            val currentFrame = currentFrameBitmap
            if (currentFrame != null && !isProcessingStep && (hasValidFace || primaryFace != null) && (isPoseValid || allowEyeGrace)) {
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
                    val score = qualityMetrics?.overallQualityScore ?: 0.85f

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

        // Hands-free fast auto capture (Apple Face ID style - 350ms hold)
        LaunchedEffect(isReadyToCapture, currentStepIndex) {
            if (isReadyToCapture && !isProcessingStep && !isFinished) {
                isHoldingValidPose = true
                kotlinx.coroutines.delay(350L)
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
            enabled = (isReadyToCapture || (hasValidFace && allowEyeGrace)) && !isProcessingStep,
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
                    text = facePrompt,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!isPoseValid && !allowEyeGrace) {
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

private fun checkStepPose(
    angle: String,
    face: DetectedFace?,
    allowEyeGrace: Boolean = false
): Pair<Boolean, String> {
    if (face == null) return Pair(false, "POSITION FACE INSIDE OVAL")
    val pitch = face.headEulerAngleX
    val yaw = face.headEulerAngleY
    val smile = face.smilingProbability ?: 0f
    val leftProb = face.leftEyeOpenProbability ?: 1.0f
    val rightProb = face.rightEyeOpenProbability ?: 1.0f
    val eyesOpen = allowEyeGrace || (leftProb >= 0.35f && rightProb >= 0.35f)

    if (!eyesOpen) {
        return Pair(false, "KEEP EYES OPEN")
    }

    return when (angle) {
        "STRAIGHT" -> {
            if (kotlin.math.abs(pitch) > 14f || kotlin.math.abs(yaw) > 14f) {
                Pair(false, "LOOK DIRECTLY AT CAMERA")
            } else {
                Pair(true, "PERFECT — HOLD STILL")
            }
        }
        "LEFT" -> {
            val absYaw = kotlin.math.abs(yaw)
            when {
                absYaw < 7f -> Pair(false, "TURN HEAD SLIGHTLY LEFT")
                absYaw > 24f -> Pair(false, "TURNED TOO FAR — LOOK MORE FORWARD")
                kotlin.math.abs(pitch) > 16f -> Pair(false, "KEEP HEAD LEVEL")
                else -> Pair(true, "LEFT ANGLE LOCKED ✓")
            }
        }
        "RIGHT" -> {
            val absYaw = kotlin.math.abs(yaw)
            when {
                absYaw < 7f -> Pair(false, "TURN HEAD SLIGHTLY RIGHT")
                absYaw > 24f -> Pair(false, "TURNED TOO FAR — LOOK MORE FORWARD")
                kotlin.math.abs(pitch) > 16f -> Pair(false, "KEEP HEAD LEVEL")
                else -> Pair(true, "RIGHT ANGLE LOCKED ✓")
            }
        }
        "SMILE" -> {
            when {
                smile < 0.35f -> Pair(false, "SMILE NATURALLY (LIVENESS)")
                kotlin.math.abs(pitch) > 16f || kotlin.math.abs(yaw) > 16f -> Pair(false, "LOOK AT CAMERA WHILE SMILING")
                else -> Pair(true, "LIVENESS CONFIRMED ✓")
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
            text = if (hasInconsistentFace) "Inconsistent identity detected across capture angles" else "${capturedSteps.size} angles captured for $studentName",
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
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(capturedSteps) { step ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, adaptiveBorderColor(), RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            bitmap = step.bitmap.asImageBitmap(),
                            contentDescription = step.stepAngle,
                            modifier = Modifier
                                .size(112.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (step.stepAngle) {
                                "STRAIGHT" -> "1. Frontal"
                                "LEFT" -> "2. Left 3D"
                                "RIGHT" -> "3. Right 3D"
                                "SMILE" -> "4. Smile (Liveness)"
                                else -> step.stepAngle
                            },
                            fontSize = 12.sp,
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

