package com.vaibhav.facialattendancesystem.ml

import android.graphics.Bitmap
import android.graphics.RectF
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.imgproc.Imgproc

data class FaceQualityMetrics(
    val sharpnessScore: Float,       // Laplacian variance (> 100 is sharp)
    val brightnessScore: Float,      // Mean pixel intensity (50-200 is acceptable)
    val isSharp: Boolean,
    val isBrightnessOk: Boolean,
    val overallQualityScore: Float,  // Aggregate 0.0 to 1.0
    val qualityMessage: String
)

object ImageQualityValidator {

    private var isOpenCVInitialized = false

    fun initOpenCV(): Boolean {
        if (!isOpenCVInitialized) {
            isOpenCVInitialized = OpenCVLoader.initLocal()
        }
        return isOpenCVInitialized
    }

    /**
     * Evaluates image quality for face enrollment.
     */
    fun validateFaceQuality(bitmap: Bitmap, faceBox: RectF? = null): FaceQualityMetrics {
        initOpenCV()

        val croppedBitmap = if (faceBox != null) {
            cropBitmapSafely(bitmap, faceBox)
        } else {
            bitmap
        }

        val sharpness = calculateSharpness(croppedBitmap)
        val brightness = calculateBrightness(croppedBitmap)
        if (faceBox != null && croppedBitmap != bitmap) {
            try { croppedBitmap.recycle() } catch (_: Exception) {}
        }

        val isSharp = sharpness >= 30.0f
        val isBrightnessOk = brightness in 35.0f..225.0f

        val normSharpness = (sharpness / 200.0f).coerceIn(0.0f, 1.0f)
        val normBrightness = when {
            brightness in 70.0f..180.0f -> 1.0f
            brightness < 70.0f -> (brightness / 70.0f).coerceIn(0.0f, 1.0f)
            else -> ((255.0f - brightness) / 75.0f).coerceIn(0.0f, 1.0f)
        }

        val overallScore = (0.6f * normSharpness) + (0.4f * normBrightness)

        val message = when {
            !isSharp -> "Hold steady for sharper scan"
            brightness < 35.0f -> "Lighting is dim. Face a light source."
            brightness > 225.0f -> "Lighting is too bright / glare."
            else -> "Face Aligned & Clear ✓"
        }

        return FaceQualityMetrics(
            sharpnessScore = sharpness,
            brightnessScore = brightness,
            isSharp = isSharp,
            isBrightnessOk = isBrightnessOk,
            overallQualityScore = overallScore,
            qualityMessage = message
        )
    }

    /**
     * Calculates Laplacian variance to measure sharpness.
     */
    fun calculateSharpness(bitmap: Bitmap): Float {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY)

            val laplacianMat = Mat()
            Imgproc.Laplacian(grayMat, laplacianMat, CvType.CV_64F)

            val mean = MatOfDouble()
            val stdDev = MatOfDouble()
            org.opencv.core.Core.meanStdDev(laplacianMat, mean, stdDev)

            val variance = stdDev.toArray()[0] * stdDev.toArray()[0]

            mat.release()
            grayMat.release()
            laplacianMat.release()

            variance.toFloat()
        } catch (e: Exception) {
            150.0f // Fallback if OpenCV fails
        }
    }

    /**
     * Calculates mean brightness of the image.
     */
    fun calculateBrightness(bitmap: Bitmap): Float {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY)

            val meanVal = org.opencv.core.Core.mean(grayMat)

            mat.release()
            grayMat.release()

            meanVal.`val`[0].toFloat()
        } catch (e: Exception) {
            120.0f // Fallback
        }
    }

    /**
     * Applies histogram equalization to improve contrast under bad lighting.
     */
    fun equalizeContrast(bitmap: Bitmap): Bitmap {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            val rgbMat = Mat()
            Imgproc.cvtColor(mat, rgbMat, Imgproc.COLOR_RGBA2RGB)
            mat.release()

            val yuvMat = Mat()
            Imgproc.cvtColor(rgbMat, yuvMat, Imgproc.COLOR_RGB2YCrCb)
            rgbMat.release()

            val channels = ArrayList<Mat>()
            org.opencv.core.Core.split(yuvMat, channels)

            Imgproc.equalizeHist(channels[0], channels[0])

            org.opencv.core.Core.merge(channels, yuvMat)
            val resultMat = Mat()
            Imgproc.cvtColor(yuvMat, resultMat, Imgproc.COLOR_YCrCb2RGB)
            yuvMat.release()
            for (c in channels) c.release()

            val rgbaMat = Mat()
            Imgproc.cvtColor(resultMat, rgbaMat, Imgproc.COLOR_RGB2RGBA)
            resultMat.release()

            val outputBitmap = Bitmap.createBitmap(rgbaMat.cols(), rgbaMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgbaMat, outputBitmap)
            rgbaMat.release()

            outputBitmap
        } catch (e: Exception) {
            bitmap
        }
    }

    fun cropBitmapSafely(bitmap: Bitmap, rect: RectF, marginPercent: Float = 0.15f): Bitmap {
        val widthMargin = rect.width() * marginPercent
        val heightMargin = rect.height() * marginPercent
        val maxW = bitmap.width
        val maxH = bitmap.height

        val expandedLeft = (rect.left - widthMargin).toInt().coerceIn(0, maxW - 1)
        val expandedTop = (rect.top - heightMargin).toInt().coerceIn(0, maxH - 1)
        val expandedRight = (rect.right + widthMargin).toInt().coerceIn(expandedLeft + 1, maxW)
        val expandedBottom = (rect.bottom + heightMargin).toInt().coerceIn(expandedTop + 1, maxH)

        val cropWidth = (expandedRight - expandedLeft).coerceIn(1, maxW - expandedLeft)
        val cropHeight = (expandedBottom - expandedTop).coerceIn(1, maxH - expandedTop)

        return Bitmap.createBitmap(bitmap, expandedLeft, expandedTop, cropWidth, cropHeight)
    }
}

