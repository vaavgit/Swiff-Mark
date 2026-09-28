package com.vaibhav.facialattendancesystem

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import com.vaibhav.facialattendancesystem.ui.AppNavigation
import com.vaibhav.facialattendancesystem.ui.theme.FacialAttendanceSystemTheme
import com.vaibhav.facialattendancesystem.util.NotificationHelper

class MainActivity : ComponentActivity() {

    private val recoveryTokenState = mutableStateOf<String?>(null)
    private val targetClassState = mutableStateOf<Pair<String, String>?>(null)

    private val multiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle incoming deep link or notification extras
        handleIntent(intent)

        // Initialize notification channel
        NotificationHelper.initChannel(this)

        val neededPermissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (neededPermissions.isNotEmpty()) {
            multiPermissionLauncher.launch(neededPermissions.toTypedArray())
        }

        // Schedule background attendance sync worker (periodic 15m)
        schedulePeriodicAttendanceSync()

        setContent {
            FacialAttendanceSystemTheme {
                AppNavigation(
                    recoveryToken = recoveryTokenState.value,
                    onClearRecoveryToken = { recoveryTokenState.value = null },
                    targetClass = targetClassState.value,
                    onClearTargetClass = { targetClassState.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        // 1. Check for notification click extras (navigates student/teacher directly to class attendance)
        val targetClassId = intent.getStringExtra("target_class_id")
        val targetClassName = intent.getStringExtra("target_class_name")
        if (!targetClassId.isNullOrBlank()) {
            targetClassState.value = Pair(targetClassId, targetClassName ?: "Class")
        }

        // 2. Check for password recovery deep link
        val uri = intent.data ?: return
        if (uri.scheme == "facialattendance" && uri.host == "reset-password") {
            var token: String? = null

            // Check URI Fragment (#access_token=...&type=recovery)
            val fragment = uri.fragment
            if (!fragment.isNullOrBlank()) {
                val params = fragment.split("&").associate { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
                }
                if (params["type"] == "recovery" || params.containsKey("access_token")) {
                    token = params["access_token"]
                }
            }

            // Check Query Parameters (?access_token=... or ?code=...)
            if (token.isNullOrBlank()) {
                token = uri.getQueryParameter("access_token") ?: uri.getQueryParameter("code")
            }

            if (!token.isNullOrBlank()) {
                recoveryTokenState.value = token
            }
        }
    }

    private fun schedulePeriodicAttendanceSync() {
        try {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val syncRequest = androidx.work.PeriodicWorkRequestBuilder<com.vaibhav.facialattendancesystem.util.AttendanceSyncWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "swiffmark_attendance_sync",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}