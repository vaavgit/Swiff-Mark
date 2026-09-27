package com.vaibhav.facialattendancesystem.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

sealed class AuthResult {
    data class Success(
        val userId: String,
        val email: String,
        val fullName: String,
        val role: String,
        val accessToken: String?,
        val refreshToken: String?,
        val rollOrSubject: String = "",
        val sectionOrDept: String = ""
    ) : AuthResult()

    data class NeedsEmailConfirmation(val email: String, val message: String) : AuthResult()
    data class EmailNotConfirmed(val email: String, val message: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * SupabaseAuthManager - Official Supabase GoTrue REST Client.
 *
 * Implements:
 *  - signUp (/auth/v1/signup) with metadata & email confirmation
 *  - signInWithPassword (/auth/v1/token?grant_type=password)
 *  - resend (/auth/v1/resend)
 *  - recover (/auth/v1/recover) for deep-linked password resets
 *  - updateUser (/auth/v1/user) for setting new passwords
 */
@Suppress("SpellCheckingInspection")
object SupabaseAuthManager {

    private fun getHeaders(bearerToken: String? = null): Map<String, String> {
        val map = mutableMapOf(
            "apikey" to SupabaseConfig.SUPABASE_ANON_KEY,
            "Content-Type" to "application/json"
        )
        if (!bearerToken.isNullOrBlank()) {
            map["Authorization"] = "Bearer $bearerToken"
        } else {
            map["Authorization"] = "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}"
        }
        return map
    }

    private fun executeAuthRequest(
        endpoint: String,
        method: String,
        body: String? = null,
        bearerToken: String? = null
    ): Pair<Int, String> {
        if (!SupabaseConfig.isConfigured) {
            return Pair(400, "{\"msg\":\"Supabase not configured\"}")
        }

        val fullUrl = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}$endpoint"
        val connection = URL(fullUrl).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 12000
        connection.readTimeout = 12000

        getHeaders(bearerToken).forEach { (k, v) -> connection.setRequestProperty(k, v) }

        if (body != null && (method == "POST" || method == "PUT" || method == "PATCH")) {
            connection.doOutput = true
            OutputStreamWriter(connection.outputStream).use { it.write(body) }
        }

        val statusCode = try {
            connection.responseCode
        } catch (e: Exception) {
            return Pair(503, "{\"msg\":\"Network connection error: ${e.localizedMessage}\"}")
        }

        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.let {
            BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
        } ?: ""

        return Pair(statusCode, response)
    }

    /**
     * Sign Up with Supabase GoTrue Auth.
     * Supabase sends a confirmation email automatically if email confirmation is enabled.
     */
    suspend fun signUp(
        email: String,
        password: String,
        fullName: String,
        role: String,
        rollOrSubject: String,
        sectionOrDept: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanPassword = password.trim()

        val dataObj = JSONObject().apply {
            put("full_name", fullName)
            put("role", role.uppercase())
            put("roll_number", rollOrSubject)
            put("section", sectionOrDept)
        }

        val body = JSONObject().apply {
            put("email", cleanEmail)
            put("password", cleanPassword)
            put("data", dataObj)
        }.toString()

        val (status, res) = executeAuthRequest("/auth/v1/signup", "POST", body)

        if (status in 200..299) {
            try {
                val obj = JSONObject(res)
                val userId = obj.optString("id", "")
                val confirmedAt = obj.optString("confirmed_at", "")
                val isAlreadyConfirmed = confirmedAt.isNotBlank() && confirmedAt != "null"

                if (!isAlreadyConfirmed) {
                    AuthResult.NeedsEmailConfirmation(
                        email = cleanEmail,
                        message = "Verification email sent to $cleanEmail! Please confirm your email before logging in."
                    )
                } else {
                    AuthResult.Success(
                        userId = userId,
                        email = cleanEmail,
                        fullName = fullName,
                        role = role.uppercase(),
                        accessToken = if (obj.has("access_token")) obj.getString("access_token") else null,
                        refreshToken = if (obj.has("refresh_token")) obj.getString("refresh_token") else null,
                        rollOrSubject = rollOrSubject,
                        sectionOrDept = sectionOrDept
                    )
                }
            } catch (_: Exception) {
                AuthResult.NeedsEmailConfirmation(
                    email = cleanEmail,
                    message = "Account created! Please check your email to confirm your account."
                )
            }
        } else {
            val errorMsg = try {
                val errObj = JSONObject(res)
                val msg = errObj.optString("msg", errObj.optString("error_description", ""))
                val errCode = errObj.optString("error_code", "")
                when {
                    errCode == "over_email_send_rate_limit" || msg.contains("rate limit", ignoreCase = true) ->
                        "Email rate limit exceeded (3 emails/hr max on free tier). Turn OFF 'Confirm email' in Supabase Dashboard -> Auth -> Providers -> Email to sign up instantly!"
                    msg.contains("already registered", ignoreCase = true) -> "An account with this email already exists."
                    errCode == "email_address_invalid" -> "Invalid email address."
                    msg.isNotBlank() -> msg
                    else -> "Registration failed (Status $status)"
                }
            } catch (_: Exception) {
                "Registration failed (Status $status)"
            }
            AuthResult.Error(errorMsg)
        }
    }

    /**
     * Sign In with Email and Password using Supabase GoTrue.
     */
    suspend fun signInWithPassword(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanPassword = password.trim()

        val body = JSONObject().apply {
            put("email", cleanEmail)
            put("password", cleanPassword)
        }.toString()

        val (status, res) = executeAuthRequest("/auth/v1/token?grant_type=password", "POST", body)

        if (status in 200..299) {
            try {
                val obj = JSONObject(res)
                val accessToken = if (obj.has("access_token")) obj.getString("access_token") else null
                val refreshToken = if (obj.has("refresh_token")) obj.getString("refresh_token") else null
                val user = obj.getJSONObject("user")
                val userId = user.getString("id")
                val metadata = user.optJSONObject("user_metadata")

                val fullName = metadata?.optString("full_name", "User") ?: "User"
                val role = metadata?.optString("role", "STUDENT")?.uppercase() ?: "STUDENT"
                val rollOrSubject = metadata?.optString("roll_number", metadata.optString("subject", "")) ?: ""
                val sectionOrDept = metadata?.optString("section", metadata.optString("department", "")) ?: ""

                AuthResult.Success(
                    userId = userId,
                    email = cleanEmail,
                    fullName = fullName,
                    role = role,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    rollOrSubject = rollOrSubject,
                    sectionOrDept = sectionOrDept
                )
            } catch (e: Exception) {
                AuthResult.Error("Error parsing server response: ${e.localizedMessage}")
            }
        } else {
            try {
                val errObj = JSONObject(res)
                val errCode = errObj.optString("error_code", "")
                val msg = errObj.optString("msg", errObj.optString("error_description", ""))

                if (errCode == "email_not_confirmed" || msg.contains("Email not confirmed", ignoreCase = true)) {
                    AuthResult.EmailNotConfirmed(
                        email = cleanEmail,
                        message = "Your email has not been verified yet. Please check your inbox or tap below to resend."
                    )
                } else if (errCode == "invalid_credentials" || msg.contains("Invalid login credentials", ignoreCase = true)) {
                    AuthResult.Error("Invalid email or password.")
                } else if (errCode == "over_request_rate_limit" || msg.contains("rate limit", ignoreCase = true)) {
                    AuthResult.Error("Too many attempts. Please wait a minute or turn off 'Confirm email' in Supabase.")
                } else {
                    AuthResult.Error(msg.ifBlank { "Login failed ($status)" })
                }
            } catch (_: Exception) {
                AuthResult.Error("Login failed (Status $status)")
            }
        }
    }

    /**
     * Resends email verification link to user.
     */
    suspend fun resendVerificationEmail(email: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val body = JSONObject().apply {
            put("type", "signup")
            put("email", cleanEmail)
        }.toString()

        val (status, res) = executeAuthRequest("/auth/v1/resend", "POST", body)
        if (status in 200..299) {
            Pair(true, "Verification email sent! Check your inbox.")
        } else {
            val msg = try { JSONObject(res).optString("msg", "Failed to resend email") } catch (_: Exception) { "Error $status" }
            Pair(false, msg)
        }
    }

    /**
     * Sends password recovery email with custom app deep link.
     */
    suspend fun resetPasswordForEmail(
        email: String,
        redirectUrl: String = "facialattendance://reset-password"
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val body = JSONObject().apply {
            put("email", cleanEmail)
            put("redirect_to", redirectUrl)
        }.toString()

        val (status, res) = executeAuthRequest("/auth/v1/recover", "POST", body)
        if (status in 200..299) {
            Pair(true, "Password recovery link sent to $cleanEmail! Open the link on your phone to reset your password.")
        } else {
            val msg = try { JSONObject(res).optString("msg", "Failed to send reset link") } catch (_: Exception) { "Error $status" }
            Pair(false, msg)
        }
    }

    /**
     * Updates the user's password using the recovery access token from the deep link.
     */
    suspend fun updateUserPassword(
        recoveryAccessToken: String,
        newPassword: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanPassword = newPassword.trim()
        val body = JSONObject().apply {
            put("password", cleanPassword)
        }.toString()

        val (status, res) = executeAuthRequest("/auth/v1/user", "PUT", body, bearerToken = recoveryAccessToken)
        if (status in 200..299) {
            Pair(true, "Password updated successfully! You can now log in.")
        } else {
            val msg = try { JSONObject(res).optString("msg", "Failed to update password") } catch (_: Exception) { "Error $status" }
            Pair(false, msg)
        }
    }
}
