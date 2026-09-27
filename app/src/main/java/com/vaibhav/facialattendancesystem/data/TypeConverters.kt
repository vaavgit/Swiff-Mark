package com.vaibhav.facialattendancesystem.data

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VectorTypeConverters {
    @TypeConverter
    fun floatArrayToByteArray(floatArray: FloatArray?): ByteArray? {
        if (floatArray == null) return null
        val byteBuffer = ByteBuffer.allocate(floatArray.size * 4)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        for (f in floatArray) {
            byteBuffer.putFloat(f)
        }
        return byteBuffer.array()
    }

    @TypeConverter
    fun byteArrayToFloatArray(byteArray: ByteArray?): FloatArray? {
        if (byteArray == null) return null
        val floatBuffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val floatArray = FloatArray(floatBuffer.remaining())
        floatBuffer.get(floatArray)
        return floatArray
    }
}
