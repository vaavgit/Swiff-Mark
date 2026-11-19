package com.vaibhav.facialattendancesystem.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class FaceClassifierHelper(private val context: Context) {

    private val tag = "FaceClassifierHelper"
    private var interpreter: Interpreter? = null
    private var inputSize: Int = 112
    private var outputDim: Int = 512

    var loadErrorMessage: String? = null
        private set

    val isModelLoaded: Boolean
        get() = interpreter != null

    init {
        setupInterpreter()
    }

    private fun setupInterpreter() {
        val modelName = "adaface_model.tflite"
        try {
            val modelBuffer = loadModelFile(context, modelName)
            val modelSize = modelBuffer.capacity()
            // Model must be binary weights, not a Git LFS pointer text file (< 1 KB) or truncated download
            if (modelSize < 5 * 1024 * 1024) {
                val errorMsg = "Model file $modelName is invalid or corrupted ($modelSize bytes). Git LFS binary download required."
                Log.e(tag, errorMsg)
                loadErrorMessage = errorMsg
                return
            }

            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            val tflite = Interpreter(modelBuffer, options)

            // Dynamically inspect model input and output shapes
            val inputShape = tflite.getInputTensor(0).shape() // e.g. [1, 112, 112, 3]
            if (inputShape.size >= 3) {
                inputSize = inputShape[1]
            }

            val outputShape = tflite.getOutputTensor(0).shape() // e.g. [1, 512] for AdaFace
            if (outputShape.size >= 2) {
                outputDim = outputShape[1]
            }

            interpreter = tflite
            loadErrorMessage = null
            Log.i(tag, "Loaded $modelName successfully. Input size: $inputSize, Output dim: $outputDim")
        } catch (e: Exception) {
            val errorMsg = "Failed to load face recognition model $modelName: ${e.message}"
            Log.e(tag, errorMsg, e)
            loadErrorMessage = errorMsg
            interpreter = null
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Extracts face embedding vector from cropped face Bitmap.
     * Normalized BGR format [-1.0, 1.0] matching AdaFace requirements.
     *
     * Upgraded with Flip-Fusion (Horizontal TTA - Test Time Augmentation):
     * Computes embeddings for both the canonical face chip and its horizontal mirror:
     * v_fused = L2(v_orig + v_flip).
     * This mathematically guarantees that the feature vector is 100% invariant
     * to camera mirroring (front camera vs back camera) and increases SNR for
     * distant faces (1m-3m in classrooms).
     */
    fun getFaceEmbedding(faceBitmap: Bitmap): FloatArray {
        val tflite = interpreter ?: run {
            Log.w(tag, "getFaceEmbedding called but model is not loaded.")
            return FloatArray(outputDim) { 0f }
        }

        // 1. Resize cropped face to inputSize x inputSize
        val resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true)
        val vec1 = extractSingleEmbedding(tflite, resizedBitmap)

        // 2. Flip-Fusion: extract embedding of horizontally mirrored face
        val matrix = Matrix().apply { postScale(-1f, 1f) }
        val flippedBitmap = Bitmap.createBitmap(resizedBitmap, 0, 0, resizedBitmap.width, resizedBitmap.height, matrix, true)
        val vec2 = extractSingleEmbedding(tflite, flippedBitmap)

        if (resizedBitmap != faceBitmap) {
            try { resizedBitmap.recycle() } catch (_: Exception) {}
        }
        try { flippedBitmap.recycle() } catch (_: Exception) {}

        // 3. Vector sum and L2 normalize
        val fused = FloatArray(outputDim) { i -> vec1[i] + vec2[i] }
        return l2Normalize(fused)
    }

    private fun extractSingleEmbedding(tflite: Interpreter, bitmap: Bitmap): FloatArray {
        val imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixelValue in intValues) {
            val r = (pixelValue shr 16 and 0xFF)
            val g = (pixelValue shr 8 and 0xFF)
            val b = (pixelValue and 0xFF)

            // AdaFace canonical input: BGR format, normalized to [-1.0, 1.0]
            imgData.putFloat((b - 127.5f) / 127.5f)
            imgData.putFloat((g - 127.5f) / 127.5f)
            imgData.putFloat((r - 127.5f) / 127.5f)
        }

        val outputBuffer = Array(1) { FloatArray(outputDim) }
        tflite.run(imgData, outputBuffer)
        return outputBuffer[0]
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var norm = 0.0f
        for (f in vector) {
            norm += f * f
        }
        norm = sqrt(norm)
        if (norm > 0.0f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }
        return vector
    }

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
        } catch (e: Exception) {
            Log.e(tag, "Error closing interpreter", e)
        }
    }
}
