package com.vaibhav.facialattendancesystem.util

import android.content.Context
import android.content.SharedPreferences

data class UserSession(
    val userId: String,
    val userName: String,
    val userRole: String, // "STUDENT" or "TEACHER"
    val userEmail: String,
    val accessToken: String? = null,
    val refreshToken: String? = null
)

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("facial_attendance_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "session_user_id"
        private const val KEY_USER_NAME = "session_user_name"
        private const val KEY_USER_ROLE = "session_user_role"
        private const val KEY_USER_EMAIL = "session_user_email"
        private const val KEY_ACCESS_TOKEN = "session_access_token"
        private const val KEY_REFRESH_TOKEN = "session_refresh_token"
        private const val KEY_DATA_VERSION = "session_data_version"
        private const val CURRENT_DATA_VERSION = 2

        // Saved login credentials keys
        private const val KEY_SAVE_LOGIN_ENABLED = "saved_login_enabled"
        private const val KEY_SAVED_EMAIL = "saved_login_email"
        private const val KEY_SAVED_PASSWORD = "saved_login_password"
        private const val KEY_SAVED_IS_TEACHER = "saved_login_is_teacher"
    }

    init {
        val ver = prefs.getInt(KEY_DATA_VERSION, 1)
        if (ver < CURRENT_DATA_VERSION) {
            clearSession()
            prefs.edit().putInt(KEY_DATA_VERSION, CURRENT_DATA_VERSION).commit()
        }
    }

    fun saveSession(
        userId: String,
        userName: String,
        userRole: String,
        userEmail: String,
        accessToken: String? = null,
        refreshToken: String? = null
    ) {
        val editor = prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, userName)
            .putString(KEY_USER_ROLE, userRole.uppercase())
            .putString(KEY_USER_EMAIL, userEmail)
            .putInt(KEY_DATA_VERSION, CURRENT_DATA_VERSION)

        if (!accessToken.isNullOrBlank()) {
            editor.putString(KEY_ACCESS_TOKEN, accessToken)
        }
        if (!refreshToken.isNullOrBlank()) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        }
        editor.commit()
    }

    fun updateUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name.trim()).commit()
    }

    fun updateTokens(accessToken: String?, refreshToken: String? = null) {
        val editor = prefs.edit()
        if (accessToken.isNullOrBlank()) {
            editor.remove(KEY_ACCESS_TOKEN)
        } else {
            editor.putString(KEY_ACCESS_TOKEN, accessToken)
        }
        if (!refreshToken.isNullOrBlank()) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        }
        editor.commit()
    }

    fun updateAccessToken(token: String?) {
        updateTokens(token, null)
    }

    fun isTestDataPurged(): Boolean {
        return prefs.getBoolean("test_data_purged_v2", false)
    }

    fun setTestDataPurged(purged: Boolean = true) {
        prefs.edit().putBoolean("test_data_purged_v2", purged).commit()
    }

    fun getSession(): UserSession? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val userName = prefs.getString(KEY_USER_NAME, "User") ?: "User"
        val userRole = prefs.getString(KEY_USER_ROLE, "STUDENT") ?: "STUDENT"
        val userEmail = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        return UserSession(userId, userName, userRole, userEmail, accessToken, refreshToken)
    }

    fun getAccessToken(): String? {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        return if (token.isNullOrBlank()) null else token
    }

    fun getRefreshToken(): String? {
        val token = prefs.getString(KEY_REFRESH_TOKEN, null)
        return if (token.isNullOrBlank()) null else token
    }

    /**
     * Clears only the active session tokens so the user logs out.
     * Preserves KEY_DATA_VERSION and saved login credentials so app close / reopen
     * and saved autofill work reliably without wiping.
     */
    fun clearSession() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_ROLE)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .commit()
    }

    fun isLoggedIn(): Boolean {
        return prefs.getString(KEY_USER_ID, null) != null
    }

    // ── Save Login Info Credentials ──────────────────────────────────────────

    fun saveLoginCredentials(email: String, password: String, isTeacher: Boolean, save: Boolean) {
        val editor = prefs.edit()
        editor.putBoolean(KEY_SAVE_LOGIN_ENABLED, save)
        if (save) {
            editor.putString(KEY_SAVED_EMAIL, email.trim())
            editor.putString(KEY_SAVED_PASSWORD, password)
            editor.putBoolean(KEY_SAVED_IS_TEACHER, isTeacher)
        } else {
            editor.remove(KEY_SAVED_EMAIL)
            editor.remove(KEY_SAVED_PASSWORD)
            editor.remove(KEY_SAVED_IS_TEACHER)
        }
        editor.commit()
    }

    fun isSaveLoginEnabled(): Boolean = prefs.getBoolean(KEY_SAVE_LOGIN_ENABLED, false)
    fun getSavedEmail(): String = prefs.getString(KEY_SAVED_EMAIL, "") ?: ""
    fun getSavedPassword(): String = prefs.getString(KEY_SAVED_PASSWORD, "") ?: ""
    fun getSavedIsTeacher(): Boolean = prefs.getBoolean(KEY_SAVED_IS_TEACHER, false)

    // ── Theme Preference Persistence ──────────────────────────────────────────
    fun isDarkTheme(): Boolean = prefs.getBoolean("app_dark_theme", true)
    fun setDarkTheme(isDark: Boolean) {
        prefs.edit().putBoolean("app_dark_theme", isDark).apply()
    }

    // ── Student Left Classes Tracking ─────────────────────────────────────────
    fun getLeftClassIds(studentId: String): Set<String> {
        return prefs.getStringSet("left_classes_$studentId", emptySet()) ?: emptySet()
    }

    fun markClassLeft(studentId: String, classId: String) {
        val current = getLeftClassIds(studentId).toMutableSet()
        current.add(classId)
        prefs.edit().putStringSet("left_classes_$studentId", current).apply()
    }

    fun unmarkClassLeft(studentId: String, classId: String) {
        val current = getLeftClassIds(studentId).toMutableSet()
        if (current.remove(classId)) {
            prefs.edit().putStringSet("left_classes_$studentId", current).apply()
        }
    }

    // ── Teacher Deleted Classes Tracking ──────────────────────────────────────
    fun getDeletedClassIds(teacherId: String): Set<String> {
        return prefs.getStringSet("deleted_classes_$teacherId", emptySet()) ?: emptySet()
    }

    fun markClassDeleted(teacherId: String, classId: String) {
        val current = getDeletedClassIds(teacherId).toMutableSet()
        current.add(classId)
        prefs.edit().putStringSet("deleted_classes_$teacherId", current).apply()
    }

    // ── Attendance Notification Deduplication ─────────────────────────────────
    fun hasInitializedNotifiedSessions(studentId: String): Boolean {
        return prefs.getBoolean("notified_sessions_init_$studentId", false)
    }

    fun getNotifiedSessionIds(studentId: String): Set<String> {
        return prefs.getStringSet("notified_session_ids_$studentId", emptySet()) ?: emptySet()
    }

    fun markSessionsNotified(studentId: String, sessionIds: Collection<String>) {
        val current = getNotifiedSessionIds(studentId).toMutableSet()
        current.addAll(sessionIds)
        prefs.edit()
            .putStringSet("notified_session_ids_$studentId", current)
            .putBoolean("notified_sessions_init_$studentId", true)
            .commit()
    }
}
