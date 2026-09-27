package com.vaibhav.facialattendancesystem.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark

/**
 * Detected face from Google ML Kit.
 *
 * [landmarks] maps FaceLandmark.* integer constants (e.g. FaceLandmark.LEFT_EYE) to their
 * pixel position in the source bitmap coordinate space.  Used by FaceAligner to compute the
 * affine warp before passing the chip to AdaFace.
 */
data class DetectedFace(
    val boundingBox: RectF,
    val confidence: Float,
    val smilingProbability: Float? = null,
    val landmarks: Map<Int, PointF> = emptyMap()
)

class FaceDetectorHelper(
    private val context: Context,
    private val minDetectionConfidence: Float = 0.50f
) {
    private var faceDetector: FaceDetector? = null

    init {
        setupFaceDetector()
    }

    private fun setupFaceDetector() {
        try {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                // Enable 5-point landmarks so FaceAligner can compute eye-alignment warp
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.02f)
                .build()

            faceDetector = FaceDetection.getClient(options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Synchronously detects faces in a Bitmap image using Google ML Kit.
     */
    fun detectFaces(bitmap: Bitmap): List<DetectedFace> {
        val detector = faceDetector ?: return emptyList()

        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val task = detector.process(inputImage)
            val mlkitFaces = Tasks.await(task)

            val detectedFaces = mutableListOf<DetectedFace>()
            val imageWidth = bitmap.width.toFloat()
            val imageHeight = bitmap.height.toFloat()

            for (face in mlkitFaces) {
                val box = face.boundingBox
                val rectF = RectF(
                    box.left.toFloat().coerceIn(0f, imageWidth),
                    box.top.toFloat().coerceIn(0f, imageHeight),
                    box.right.toFloat().coerceIn(0f, imageWidth),
                    box.bottom.toFloat().coerceIn(0f, imageHeight)
                )

                if (rectF.width() > 8 && rectF.height() > 8) {
                    // Extract available landmarks — position is in source bitmap pixel coords.
                    // ML Kit resolves landmarks reliably up to ~4 m; may be null for very distant faces.
                    val landmarkTypes = listOf(
                        FaceLandmark.LEFT_EYE,
                        FaceLandmark.RIGHT_EYE,
                        FaceLandmark.NOSE_BASE,
                        FaceLandmark.MOUTH_LEFT,
                        FaceLandmark.MOUTH_RIGHT
                    )
                    val landmarkMap = buildMap {
                        for (type in landmarkTypes) {
                            face.getLandmark(type)?.position?.let { pos ->
                                put(type, PointF(pos.x, pos.y))
                            }
                        }
                    }

                    detectedFaces.add(
                        DetectedFace(
                            boundingBox = rectF,
                            confidence = 0.95f,
                            smilingProbability = face.smilingProbability,
                            landmarks = landmarkMap
                        )
                    )
                }
            }

            detectedFaces
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun close() {
        try {
            faceDetector?.close()
            faceDetector = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
