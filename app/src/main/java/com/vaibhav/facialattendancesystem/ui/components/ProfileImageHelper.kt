package com.vaibhav.facialattendancesystem.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object ProfileImageHelper {

    fun getProfileImageFile(context: Context, userId: String): File {
        return File(context.filesDir, "avatar_${userId}.jpg")
    }

    fun hasCustomProfileImage(context: Context, userId: String): Boolean {
        if (userId.isBlank()) return false
        val file = getProfileImageFile(context, userId)
        return file.exists() && file.length() > 0
    }

    fun loadProfileBitmap(context: Context, userId: String): Bitmap? {
        if (!hasCustomProfileImage(context, userId)) return null
        return try {
            val file = getProfileImageFile(context, userId)
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveProfileImageFromUri(context: Context, userId: String, uri: Uri): Boolean {
        if (userId.isBlank()) return false
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return false
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (original == null) return false

            // Scale to max 512x512 to preserve memory and keep UI ultra fast
            val maxDimension = 512
            val width = original.width
            val height = original.height
            val scaled = if (width > maxDimension || height > maxDimension) {
                val ratio = max(width.toFloat() / maxDimension, height.toFloat() / maxDimension)
                val targetW = (width / ratio).toInt().coerceAtLeast(1)
                val targetH = (height / ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(original, targetW, targetH, true)
            } else {
                original
            }

            val targetFile = getProfileImageFile(context, userId)
            FileOutputStream(targetFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            if (scaled != original) {
                scaled.recycle()
            }
            original.recycle()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteProfileImage(context: Context, userId: String): Boolean {
        return try {
            val file = getProfileImageFile(context, userId)
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
