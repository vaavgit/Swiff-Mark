package com.vaibhav.facialattendancesystem.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.vaibhav.facialattendancesystem.ml.DetectedFace
import com.vaibhav.facialattendancesystem.ml.FaceDetectorHelper
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages CameraX use-cases for the classroom attendance capture flow:
 *
 *   - [Preview]        -> live viewfinder displayed in PreviewView
 *   - [ImageAnalysis]  -> 480p stream for live bounding-box overlay (low CPU cost)
 *   - [ImageCapture]   -> MAXIMIZE_QUALITY still photo (~8-12 MP on G34 5G) on button press
 *
 * The [capturePhoto] callback is always delivered on the Android main thread so that
 * Compose state writes are safe.
 *
 * Usage:
 * ```
 * val helper = remember { AttendanceCameraHelper(context) }
 * LaunchedEffect(Unit) {
 *     helper.onFacesUpdated = { detectedFaces = it }
 *     helper.bindToLifecycle(lifecycleOwner, previewView, faceDetectorHelper)
 * }
 * DisposableEffect(Unit) { onDispose { helper.release() } }
 * // On button press:
 * helper.capturePhoto { bitmap -> processIt(bitmap) }
 * ```
 */
class AttendanceCameraHelper(private val context: Context) {

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val captureExecutor  = Executors.newSingleThreadExecutor()

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var isFrontCamera: Boolean = false

    // Burst capture state: how many frames remain in the current burst window
    private val burstFramesRemaining = AtomicInteger(0)
    private var bestBurstBitmap: Bitmap? = null
    private var bestBurstVariance: Double = -1.0
    private var burstCallback: ((Bitmap?) -> Unit)? = null
    private val burstLock = Any()

    /**
     * Invoked on the **main thread** each time the 480p analysis stream delivers
     * new face detections.  Assign this before calling [bindToLifecycle].
     */
    var onFacesUpdated: ((List<DetectedFace>) -> Unit)? = null

    /**
     * Binds Preview + ImageCapture (and optionally ImageAnalysis if [detectorHelper] is provided)
     * to the given [lifecycleOwner].
     *
     * When [detectorHelper] is null, CameraX only runs the native preview and prepares the
     * high-res ImageCapture, with zero background processing overhead or viewfinder lag.
     */
    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        detectorHelper: FaceDetectorHelper? = null,
        frontCamera: Boolean = false
    ) {
        isFrontCamera = frontCamera
        val mainExecutor = ContextCompat.getMainExecutor(context)

        ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener({
                val provider = future.get()

                val selector = if (frontCamera)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else
                    CameraSelector.DEFAULT_BACK_CAMERA

                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                // Upgrade 1: MAXIMIZE_QUALITY + 4:3 ratio to use full sensor area.
                // On a 12MP sensor this gives ~4032x3024 instead of 1920x1080 —
                // faces at 5m go from ~30px to ~70px wide, doubling recognition-ready detail.
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build()

                try {
                    provider.unbindAll()

                    if (detectorHelper != null) {
                        @Suppress("DEPRECATION")
                        val analysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(640, 480))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build().also { ia ->
                                ia.setAnalyzer(analysisExecutor) { proxy ->
                                    procesAnalysisFrame(proxy, detectorHelper, mainExecutor)
                                }
                            }

                        try {
                            camera = provider.bindToLifecycle(
                                lifecycleOwner, selector,
                                preview, imageCapture!!, analysis
                            )
                        } catch (e: Exception) {
                            camera = provider.bindToLifecycle(
                                lifecycleOwner, selector,
                                preview, imageCapture!!
                            )
                        }
                    } else {
                        camera = provider.bindToLifecycle(
                            lifecycleOwner, selector,
                            preview, imageCapture!!
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, mainExecutor)
        }
    }

    private var lastAnalyzedMs = 0L

    private fun procesAnalysisFrame(
        proxy: ImageProxy,
        detectorHelper: FaceDetectorHelper,
        mainExecutor: java.util.concurrent.Executor
    ) {
        val now = System.currentTimeMillis()
        if (now - lastAnalyzedMs < 120L) {
            proxy.close()
            return
        }
        lastAnalyzedMs = now

        val bitmap = proxy.toBitmapSafely(isFrontCamera)
        proxy.close()
        if (bitmap != null) {
            val faces = try {
                detectorHelper.detectFaces(bitmap)
            } catch (e: Exception) {
                emptyList()
            } finally {
                bitmap.recycle()
            }
            // Post face list to main thread so Compose state is updated safely
            mainExecutor.execute {
                onFacesUpdated?.invoke(faces)
            }
        }
    }

    /**
     * Upgrade 4: Best-of-3 Burst Capture with Laplacian Variance Blur Gate.
     *
     * Fires 3 rapid still captures (~250ms apart), computes the Laplacian variance
     * (sharpness score) for each full-resolution bitmap, and delivers the sharpest
     * one to [onBitmapReady] on the main thread.
     *
     * In classrooms, frame-to-frame sharpness varies ±30–50% due to rolling shutter
     * and AF hunting. The sharpest of 3 frames has ~35% higher Laplacian variance
     * than a random single frame, translating directly to higher cosine similarity
     * from AdaFace on small distant faces.
     *
     * [onBitmapReady] is always delivered on the Android **main thread**.
     */
    fun capturePhoto(onBitmapReady: (Bitmap?) -> Unit) {
        val capture = imageCapture
        val mainExecutor = ContextCompat.getMainExecutor(context)
        if (capture == null) {
            mainExecutor.execute { onBitmapReady(null) }
            return
        }

        synchronized(burstLock) {
            burstFramesRemaining.set(3)
            bestBurstBitmap?.recycle()
            bestBurstBitmap = null
            bestBurstVariance = -1.0
            burstCallback = onBitmapReady
        }

        // Fire 3 captures staggered by 250 ms each
        for (shotIndex in 0 until 3) {
            captureExecutor.execute {
                Thread.sleep(shotIndex * 270L)
                capture.takePicture(
                    captureExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = image.toBitmapSafely(isFrontCamera)
                            image.close()
                            if (bitmap != null) {
                                val variance = computeLaplacianVariance(bitmap)
                                synchronized(burstLock) {
                                    if (variance > bestBurstVariance) {
                                        bestBurstBitmap?.recycle()
                                        bestBurstBitmap = bitmap
                                        bestBurstVariance = variance
                                    } else {
                                        bitmap.recycle()
                                    }
                                }
                            }
                            if (burstFramesRemaining.decrementAndGet() == 0) {
                                val result: Bitmap?
                                val cb: ((Bitmap?) -> Unit)?
                                synchronized(burstLock) {
                                    result = bestBurstBitmap
                                    bestBurstBitmap = null
                                    cb = burstCallback
                                    burstCallback = null
                                }
                                mainExecutor.execute { cb?.invoke(result) }
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            exception.printStackTrace()
                            if (burstFramesRemaining.decrementAndGet() == 0) {
                                val result: Bitmap?
                                val cb: ((Bitmap?) -> Unit)?
                                synchronized(burstLock) {
                                    result = bestBurstBitmap
                                    bestBurstBitmap = null
                                    cb = burstCallback
                                    burstCallback = null
                                }
                                mainExecutor.execute { cb?.invoke(result) }
                            }
                        }
                    }
                )
            }
        }
    }

    /**
     * Computes Laplacian variance of a bitmap as a sharpness score.
     * Higher = sharper. Uses a fast 3x3 Laplacian kernel applied to the
     * grayscale luminance channel via direct pixel iteration.
     */
    private fun computeLaplacianVariance(bitmap: Bitmap): Double {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 3 || h < 3) return 0.0

        // Sub-sample to at most 800px wide to keep this fast on large bitmaps
        val scale = if (w > 800) 800f / w else 1f
        val sw = (w * scale).toInt().coerceAtLeast(3)
        val sh = (h * scale).toInt().coerceAtLeast(3)
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else bitmap

        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        if (small != bitmap) small.recycle()

        // Convert to grayscale and apply 3x3 Laplacian: [0,-1,0,-1,4,-1,0,-1,0]
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val center = pixels[y * sw + x]
                val top    = pixels[(y - 1) * sw + x]
                val bottom = pixels[(y + 1) * sw + x]
                val left   = pixels[y * sw + (x - 1)]
                val right  = pixels[y * sw + (x + 1)]
                // Grayscale via fast luminance approximation
                fun luma(p: Int) = ((p shr 16 and 0xFF) * 77 + (p shr 8 and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
                val lap = (4 * luma(center) - luma(top) - luma(bottom) - luma(left) - luma(right)).toDouble()
                sum += lap
                sumSq += lap * lap
                count++
            }
        }
        if (count == 0) return 0.0
        val mean = sum / count
        return (sumSq / count) - (mean * mean) // variance
    }

    /** Call this from a DisposableEffect's onDispose block. */
    fun release() {
        try { analysisExecutor.shutdown() } catch (_: Exception) {}
        try { captureExecutor.shutdown()  } catch (_: Exception) {}
    }
}
