package com.vaibhav.facialattendancesystem.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.vaibhav.facialattendancesystem.ml.DetectedFace
import com.vaibhav.facialattendancesystem.ui.theme.PrimaryCyan
import com.vaibhav.facialattendancesystem.ui.theme.SuccessGreen
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.concurrent.Executors

@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    isFrontCamera: Boolean = true,
    detectedFaces: List<DetectedFace> = emptyList(),
    drawBoundingBoxes: Boolean = true,
    drawOvalGuide: Boolean = false,
    onFrameAnalyzed: ((Bitmap) -> Unit)? = null
) {
    val context = LocalContext.current
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

    if (!hasCameraPermission) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CameraPermissionFallbackCard(
                title = "Camera Permission Required",
                description = "Swiff Mark requires camera access to capture your facial angles for biometric enrollment.",
                onRequestPermission = { permissionLauncher.launch(android.Manifest.permission.CAMERA) },
                onOpenSettings = { openAppSettings(context) }
            )
        }
        return
    }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var frameWidth by remember { mutableStateOf(1080f) }
    var frameHeight by remember { mutableStateOf(1920f) }

    DisposableEffect(isFrontCamera, hasCameraPermission) {
        val cameraExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = if (isFrontCamera)
                CameraSelector.DEFAULT_FRONT_CAMERA
            else
                CameraSelector.DEFAULT_BACK_CAMERA

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            onFrameAnalyzed?.let { callback ->
                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val bitmap = imageProxy.toBitmapSafely(isFrontCamera)
                    if (bitmap != null) {
                        frameWidth = bitmap.width.toFloat()
                        frameHeight = bitmap.height.toFloat()
                        callback(bitmap)
                    }
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    if (onFrameAnalyzed != null) imageAnalysis else null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose { cameraExecutor.shutdown() }
    }

    // ── Oval pulse animation (scale 1.0 → 1.02 → 1.0, 2s loop) ─────
    val primaryFace = detectedFaces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
    val faceAligned = drawOvalGuide && primaryFace != null && frameWidth > 0 &&
        (primaryFace.boundingBox.width() >= frameWidth * 0.20f) &&
        (primaryFace.boundingBox.centerX() in (frameWidth * 0.20f)..(frameWidth * 0.80f)) &&
        (primaryFace.boundingBox.centerY() in (frameHeight * 0.15f)..(frameHeight * 0.85f))

    val infiniteTransition = rememberInfiniteTransition(label = "ovalpulse")
    val ovalScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (drawOvalGuide && !faceAligned) 1.02f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // ── Oval color: cyan → green when face aligned ───────────────────
    val ovalColor by animateColorAsState(
        targetValue = if (faceAligned) SuccessGreen else PrimaryCyan,
        animationSpec = tween(300),
        label = "ovalcolor"
    )

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth  = size.width
            val canvasHeight = size.height

            // ── Static centered oval guide (enrollment) ──────────────
            if (drawOvalGuide) {
                val centerX    = canvasWidth / 2f
                val centerY    = canvasHeight / 2f
                val ovalWidth  = minOf(canvasWidth * 0.72f, 290.dp.toPx()) * ovalScale
                val ovalHeight = ovalWidth * 1.25f

                // Oval stroke — cyan normally, green when aligned
                drawOval(
                    color    = ovalColor.copy(alpha = 0.7f),
                    topLeft  = Offset(centerX - ovalWidth / 2f, centerY - ovalHeight / 2f),
                    size     = ComposeSize(ovalWidth, ovalHeight),
                    style    = Stroke(width = 3.dp.toPx())
                )

                // Green checkmark badge when face is inside oval
                if (faceAligned) {
                    val checkCenter = Offset(
                        centerX + ovalWidth / 2f - 18.dp.toPx(),
                        centerY - ovalHeight / 2f + 18.dp.toPx()
                    )
                    drawCircle(
                        color  = SuccessGreen,
                        radius = 18.dp.toPx(),
                        center = checkCenter
                    )
                    val checkPath = Path().apply {
                        moveTo(checkCenter.x - 7.dp.toPx(), checkCenter.y)
                        lineTo(checkCenter.x - 2.dp.toPx(), checkCenter.y + 5.dp.toPx())
                        lineTo(checkCenter.x + 7.dp.toPx(), checkCenter.y - 5.dp.toPx())
                    }
                    drawPath(
                        path  = checkPath,
                        color = Color.White,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            // ── Dynamic bounding boxes (attendance capture) ──────────
            if (drawBoundingBoxes && !drawOvalGuide) {
                val imageWidth  = frameWidth
                val imageHeight = frameHeight
                val scale = maxOf(canvasWidth / imageWidth, canvasHeight / imageHeight)
                val dx = (canvasWidth  - imageWidth  * scale) / 2f
                val dy = (canvasHeight - imageHeight * scale) / 2f

                for (face in detectedFaces) {
                    val box = face.boundingBox
                    val rawLeft  = if (isFrontCamera) (imageWidth - box.right) else box.left
                    val rawRight = if (isFrontCamera) (imageWidth - box.left)  else box.right
                    drawRect(
                        color    = PrimaryCyan,
                        topLeft  = Offset(rawLeft  * scale + dx, box.top    * scale + dy),
                        size     = ComposeSize((rawRight - rawLeft) * scale, (box.bottom - box.top) * scale),
                        style    = Stroke(width = 3.dp.toPx())
                    )
                }
            }
        }
    }
}

fun ImageProxy.toBitmapSafely(isFrontCamera: Boolean = true): Bitmap? {
    return try {
        val rawBitmap = this.toBitmap()
        val matrix = Matrix()
        matrix.postRotate(imageInfo.rotationDegrees.toFloat())
        if (isFrontCamera) matrix.postScale(-1f, 1f)
        val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        if (rotated != rawBitmap) {
            rawBitmap.recycle()
        }
        rotated
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}
