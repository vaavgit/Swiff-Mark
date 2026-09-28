package com.vaibhav.facialattendancesystem.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val htmlUrl: String,
    val isNewer: Boolean
)

object AppUpdateDownloader {

    /**
     * Queries GitHub Releases for the latest APK asset.
     * Works both for automatic newer-version checks and manual wireless update requests.
     */
    suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL("https://api.github.com/repos/vaavgit/Swiff-Mark/releases/latest")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "SwiffMark-Android")

            if (conn.responseCode !in 200..299) return@withContext null
            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isBlank()) return@withContext null

            val notes = json.optString("body", "Bug fixes and wireless sync improvements.").trim()
            val htmlUrl = json.optString("html_url", "https://github.com/vaavgit/Swiff-Mark/releases/latest")
            var apkUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null && assets.length() > 0) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val browserUrl = asset.optString("browser_download_url", "")
                    if (browserUrl.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = browserUrl
                        break
                    }
                }
            }
            if (apkUrl.isBlank()) {
                val match = Regex("""https://[^\s")\]]+\.apk""", RegexOption.IGNORE_CASE).find(notes)
                if (match != null) {
                    apkUrl = match.value
                }
            }

            val cleanRemote = tagName.removePrefix("v").removePrefix("V").trim()
            val cleanLocal = com.vaibhav.facialattendancesystem.BuildConfig.VERSION_NAME
                .removePrefix("v").removePrefix("V").trim()

            ReleaseInfo(
                tagName = tagName,
                releaseNotes = notes,
                apkDownloadUrl = apkUrl.ifBlank { htmlUrl },
                htmlUrl = htmlUrl,
                isNewer = isRemoteVersionNewer(cleanRemote, cleanLocal)
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Downloads the APK from [apkUrl] into cache directory with progress callbacks (0..100)
     * and launches Android's native Package Installer wirelessly.
     */
    suspend fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            if (!apkUrl.endsWith(".apk", ignoreCase = true)) {
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                return@withContext Pair(true, "Opened release page in browser.")
            }

            val targetDir = context.cacheDir
            val apkFile = File(targetDir, "Swiff-Mark-update.apk")
            if (apkFile.exists()) apkFile.delete()

            var currentUrl = apkUrl
            var redirectCount = 0
            var connection: HttpURLConnection

            while (true) {
                connection = URL(currentUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.setRequestProperty("User-Agent", "SwiffMark-Android")
                val code = connection.responseCode
                if (code in 300..399 && redirectCount < 5) {
                    val loc = connection.getHeaderField("Location")
                    if (!loc.isNullOrBlank()) {
                        currentUrl = loc
                        redirectCount++
                        continue
                    }
                }
                break
            }

            if (connection.responseCode !in 200..299) {
                return@withContext Pair(false, "Download failed (HTTP ${connection.responseCode})")
            }

            val totalBytes = connection.contentLengthLong
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    var read: Int
                    var lastReportedPct = -1
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) {
                            val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (pct != lastReportedPct) {
                                lastReportedPct = pct
                                withContext(Dispatchers.Main) { onProgress(pct) }
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (!apkFile.exists() || apkFile.length() < 1024L) {
                return@withContext Pair(false, "Downloaded APK file is incomplete.")
            }

            withContext(Dispatchers.Main) {
                onProgress(100)
                installApkFile(context, apkFile)
            }
            Pair(true, "Installer launched!")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.localizedMessage ?: "Wireless update failed")
        }
    }

    fun installApkFile(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permIntent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    private fun isRemoteVersionNewer(remote: String, local: String): Boolean {
        val rParts = remote.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val lParts = local.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val maxLen = kotlin.math.max(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
