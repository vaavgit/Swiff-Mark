package com.vaibhav.facialattendancesystem.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object ProfileImageHelper {

    /**
     * Incremented whenever any profile image is saved, downloaded, or deleted
     * so all active Compose tabs/windows re-read the updated avatar immediately.
     */
    var avatarVersion by mutableIntStateOf(0)
        private set

    fun notifyAvatarChanged() {
        avatarVersion++
    }

    /**
     * Computes first letters of First and Last name:
     * e.g., "Vaibhav Verma" -> "VV", "Aarav Kumar Sharma" -> "AS", "Vaibhav" -> "V"
     */
    fun getInitials(fullName: String, fallback: String = "U"): String {
        val parts = fullName.trim()
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.any { ch -> ch.isLetterOrDigit() } }
        if (parts.isEmpty()) return fallback
        if (parts.size == 1) {
            return parts[0].firstOrNull()?.uppercaseChar()?.toString() ?: fallback
        }
        val first = parts.first().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
        val last = parts.last().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
        return listOfNotNull(first, last).joinToString("").ifEmpty { fallback }
    }

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
            notifyAvatarChanged()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun saveProfileBitmap(context: Context, userId: String, bitmap: Bitmap): Boolean {
        if (userId.isBlank()) return false
        return try {
            val maxDimension = 512
            val width = bitmap.width
            val height = bitmap.height
            val scaled = if (width > maxDimension || height > maxDimension) {
                val ratio = max(width.toFloat() / maxDimension, height.toFloat() / maxDimension)
                val targetW = (width / ratio).toInt().coerceAtLeast(1)
                val targetH = (height / ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }

            val targetFile = getProfileImageFile(context, userId)
            FileOutputStream(targetFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            if (scaled != bitmap) {
                scaled.recycle()
            }
            notifyAvatarChanged()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteProfileImage(context: Context, userId: String): Boolean {
        return try {
            val file = getProfileImageFile(context, userId)
            val res = if (file.exists()) file.delete() else true
            notifyAvatarChanged()
            res
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
