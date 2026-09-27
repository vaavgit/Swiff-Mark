package com.vaibhav.facialattendancesystem.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class FaceClassifierHelper(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var inputSize: Int = 112
    private var outputDim: Int = 128

    init {
        setupInterpreter()
    }

    private fun setupInterpreter() {
        try {
            val assetList = context.assets.list("")?.toList() ?: emptyList()
            val modelName = when {
                assetList.contains("adaface_model.tflite") -> "adaface_model.tflite"
                assetList.contains("arcface_model.tflite") -> "arcface_model.tflite"
                else -> "mobilefacenet.tflite"
            }
            val modelBuffer = loadModelFile(context, modelName)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            val tflite = Interpreter(modelBuffer, options)


            // Dynamically inspect model input and output shapes
            val inputShape = tflite.getInputTensor(0).shape() // e.g. [1, 112, 112, 3] or [1, 150, 150, 3]
            if (inputShape.size >= 3) {
                inputSize = inputShape[1]
            }

            val outputShape = tflite.getOutputTensor(0).shape() // e.g. [1, 512] for ArcFace
            if (outputShape.size >= 2) {
                outputDim = outputShape[1]
            }

            interpreter = tflite
        } catch (e: Exception) {
            e.printStackTrace()
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
     */
    fun getFaceEmbedding(faceBitmap: Bitmap): FloatArray {
        val tflite = interpreter ?: return FloatArray(outputDim)

        // 1. Resize cropped face to inputSize x inputSize
        val resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true)

        // 2. Preprocess into float ByteBuffer normalized: (pixel - 127.5) / 128.0
        val imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        val intValues = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixelValue in intValues) {
            val r = (pixelValue shr 16 and 0xFF)
            val g = (pixelValue shr 8 and 0xFF)
            val b = (pixelValue and 0xFF)

            imgData.putFloat((r - 127.5f) / 128.0f)
            imgData.putFloat((g - 127.5f) / 128.0f)
            imgData.putFloat((b - 127.5f) / 128.0f)
        }

        // 3. Run inference
        val outputBuffer = Array(1) { FloatArray(outputDim) }
        tflite.run(imgData, outputBuffer)

        // 4. L2 Normalize the output vector
        val rawVector = outputBuffer[0]
        return l2Normalize(rawVector)
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
            e.printStackTrace()
        }
    }
}
