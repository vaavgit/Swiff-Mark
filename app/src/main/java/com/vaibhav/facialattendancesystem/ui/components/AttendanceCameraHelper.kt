package com.vaibhav.facialattendancesystem.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
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

                @Suppress("DEPRECATION")
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetResolution(Size(1920, 1080))
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
     * Fires the full-resolution ImageCapture.  [onBitmapReady] is always invoked on the
     * **main thread**.  If ImageCapture is not yet bound, the call is silently ignored.
     */
    fun capturePhoto(onBitmapReady: (Bitmap) -> Unit) {
        val capture = imageCapture ?: return
        val mainExecutor = ContextCompat.getMainExecutor(context)

        capture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmapSafely(isFrontCamera)
                    image.close()
                    if (bitmap != null) {
                        // Deliver on main thread -- safe for Compose state updates
                        mainExecutor.execute { onBitmapReady(bitmap) }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                }
            }
        )
    }

    /** Call this from a DisposableEffect's onDispose block. */
    fun release() {
        try { analysisExecutor.shutdown() } catch (_: Exception) {}
        try { captureExecutor.shutdown()  } catch (_: Exception) {}
    }
}
