package com.vaibhav.facialattendancesystem.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class UserSession(
    val userId: String,
    val userName: String,
    val userRole: String, // "STUDENT" or "TEACHER"
    val userEmail: String,
    val accessToken: String? = null,
    val refreshToken: String? = null
)

class SessionManager(context: Context) {

    /**
     * Encrypted store for security-sensitive values:
     * user ID, name, role, email, access token, refresh token.
     * Backed by Android Keystore AES-256-GCM.
     *
     * Note: EncryptedSharedPreferences does not support getStringSet / putStringSet.
     * Non-sensitive state (notification tracking, left class IDs, theme) lives in
     * the plain [statePrefs] store below.
     */
    private val securePrefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "swiffmark_secure_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Android Keystore is unavailable (e.g. device reset, corrupted keystore).
        // Security fix: DO NOT fall back to plain SharedPreferences for sensitive
        // JWT tokens — that would expose access/refresh tokens in plaintext on disk.
        // Instead we return an ephemeral (unique-name, immediately-cleared) prefs file
        // so all reads return null/default, getSession() returns null, and the app
        // routes the user back to the login screen automatically.
        Log.e("SessionManager", "EncryptedSharedPreferences unavailable — session cleared, user must re-authenticate.", e)
        context.getSharedPreferences(
            "swiffmark_session_dead_${System.currentTimeMillis()}",
            Context.MODE_PRIVATE
        ).also { it.edit().clear().apply() }
    }

    /**
     * Plain store for non-sensitive application state.
     * Holds String sets (notification IDs, left/deleted class IDs) and boolean flags
     * that EncryptedSharedPreferences cannot hold.
     */
    private val statePrefs: SharedPreferences =
        context.getSharedPreferences("swiffmark_app_state", Context.MODE_PRIVATE)

    companion object {
        // Secure prefs keys
        private const val KEY_USER_ID = "session_user_id"
        private const val KEY_USER_NAME = "session_user_name"
        private const val KEY_USER_ROLE = "session_user_role"
        private const val KEY_USER_EMAIL = "session_user_email"
        private const val KEY_ACCESS_TOKEN = "session_access_token"
        private const val KEY_REFRESH_TOKEN = "session_refresh_token"
        private const val KEY_DATA_VERSION = "session_data_version"
        private const val CURRENT_DATA_VERSION = 2

        private const val KEY_SAVE_LOGIN_ENABLED = "saved_login_enabled"
        private const val KEY_SAVED_EMAIL = "saved_login_email"
        private const val KEY_SAVED_IS_TEACHER = "saved_login_is_teacher"
    }

    init {
        // Remove any previously stored plaintext password from the old single-prefs approach.
        if (securePrefs.contains("saved_login_password")) {
            securePrefs.edit().remove("saved_login_password").apply()
        }
    }

    // ── Session CRUD ────────────────────────────────────────────────────────────

    fun saveSession(
        userId: String,
        userName: String,
        userRole: String,
        userEmail: String,
        accessToken: String? = null,
        refreshToken: String? = null
    ) {
        val editor = securePrefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, userName)
            .putString(KEY_USER_ROLE, userRole.uppercase())
            .putString(KEY_USER_EMAIL, userEmail)
            .putInt(KEY_DATA_VERSION, CURRENT_DATA_VERSION)

        if (!accessToken.isNullOrBlank()) editor.putString(KEY_ACCESS_TOKEN, accessToken)
        if (!refreshToken.isNullOrBlank()) editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        editor.commit()
    }

    fun updateUserName(name: String) {
        securePrefs.edit().putString(KEY_USER_NAME, name.trim()).commit()
    }

    fun updateTokens(accessToken: String?, refreshToken: String? = null) {
        val editor = securePrefs.edit()
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

    fun getSession(): UserSession? {
        val userId = securePrefs.getString(KEY_USER_ID, null) ?: return null
        val userName = securePrefs.getString(KEY_USER_NAME, "User") ?: "User"
        val userRole = securePrefs.getString(KEY_USER_ROLE, "STUDENT") ?: "STUDENT"
        val userEmail = securePrefs.getString(KEY_USER_EMAIL, "") ?: ""
        val accessToken = securePrefs.getString(KEY_ACCESS_TOKEN, null)
        val refreshToken = securePrefs.getString(KEY_REFRESH_TOKEN, null)
        return UserSession(userId, userName, userRole, userEmail, accessToken, refreshToken)
    }

    fun getAccessToken(): String? {
        val token = securePrefs.getString(KEY_ACCESS_TOKEN, null)
        return if (token.isNullOrBlank()) null else token
    }

    fun getRefreshToken(): String? {
        val token = securePrefs.getString(KEY_REFRESH_TOKEN, null)
        return if (token.isNullOrBlank()) null else token
    }

    /**
     * Clears only the active session tokens so the user logs out.
     * Preserves saved login credentials and app state so autofill and theme survive logout.
     */
    fun clearSession() {
        securePrefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_ROLE)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .commit()
    }

    fun isLoggedIn(): Boolean = securePrefs.getString(KEY_USER_ID, null) != null

    // ── Save Login Credentials ──────────────────────────────────────────────────

    fun saveLoginCredentials(email: String, isTeacher: Boolean, save: Boolean) {
        val editor = securePrefs.edit()
        editor.putBoolean(KEY_SAVE_LOGIN_ENABLED, save)
        if (save) {
            editor.putString(KEY_SAVED_EMAIL, email.trim())
            editor.putBoolean(KEY_SAVED_IS_TEACHER, isTeacher)
        } else {
            editor.remove(KEY_SAVED_EMAIL)
            editor.remove(KEY_SAVED_IS_TEACHER)
        }
        editor.commit()
    }

    // Overload kept for compatibility with existing call sites.
    fun saveLoginCredentials(email: String, password: String, isTeacher: Boolean, save: Boolean) {
        saveLoginCredentials(email, isTeacher, save)
    }

    fun isSaveLoginEnabled(): Boolean = securePrefs.getBoolean(KEY_SAVE_LOGIN_ENABLED, true)
    fun getSavedEmail(): String = securePrefs.getString(KEY_SAVED_EMAIL, "") ?: ""
    fun getSavedIsTeacher(): Boolean = securePrefs.getBoolean(KEY_SAVED_IS_TEACHER, false)

    // ── Test Data Tracking (plain store) ────────────────────────────────────────

    fun isTestDataPurged(): Boolean = statePrefs.getBoolean("test_data_purged_v2", false)
    fun setTestDataPurged(purged: Boolean = true) {
        statePrefs.edit().putBoolean("test_data_purged_v2", purged).commit()
    }

    // ── Theme Preference (plain store) ──────────────────────────────────────────

    fun isDarkTheme(): Boolean = statePrefs.getBoolean("app_dark_theme", true)
    fun setDarkTheme(isDark: Boolean) {
        statePrefs.edit().putBoolean("app_dark_theme", isDark).apply()
    }

    // ── Student Left Classes Tracking (plain store — String sets) ───────────────

    fun getLeftClassIds(studentId: String): Set<String> =
        statePrefs.getStringSet("left_classes_$studentId", emptySet()) ?: emptySet()

    fun markClassLeft(studentId: String, classId: String) {
        val current = getLeftClassIds(studentId).toMutableSet()
        current.add(classId)
        statePrefs.edit().putStringSet("left_classes_$studentId", current).apply()
    }

    fun unmarkClassLeft(studentId: String, classId: String) {
        val current = getLeftClassIds(studentId).toMutableSet()
        if (current.remove(classId)) {
            statePrefs.edit().putStringSet("left_classes_$studentId", current).apply()
        }
    }

    // ── Teacher Deleted Classes Tracking (plain store) ──────────────────────────

    fun getDeletedClassIds(teacherId: String): Set<String> =
        statePrefs.getStringSet("deleted_classes_$teacherId", emptySet()) ?: emptySet()

    fun markClassDeleted(teacherId: String, classId: String) {
        val current = getDeletedClassIds(teacherId).toMutableSet()
        current.add(classId)
        statePrefs.edit().putStringSet("deleted_classes_$teacherId", current).apply()
    }

    // ── Attendance Notification Deduplication (plain store) ─────────────────────

    fun hasInitializedNotifiedSessions(studentId: String): Boolean =
        statePrefs.getBoolean("notified_sessions_init_$studentId", false)

    fun getNotifiedEvents(studentId: String): Set<String> {
        val raw = statePrefs.getStringSet("notified_events_$studentId", null)
        if (raw != null) return raw.toSet()
        return statePrefs.getStringSet("notified_session_ids_$studentId", emptySet())?.toSet() ?: emptySet()
    }

    fun markEventsNotified(studentId: String, events: Collection<String>) {
        if (events.isEmpty()) return
        val current = HashSet(getNotifiedEvents(studentId))
        current.addAll(events)
        statePrefs.edit()
            .putStringSet("notified_events_$studentId", HashSet(current))
            .putStringSet("notified_session_ids_$studentId", HashSet(current))
            .putBoolean("notified_sessions_init_$studentId", true)
            .commit()
    }

    fun getNotifiedSessionIds(studentId: String): Set<String> = getNotifiedEvents(studentId)

    fun markSessionsNotified(studentId: String, sessionIds: Collection<String>) {
        markEventsNotified(studentId, sessionIds)
    }
}
