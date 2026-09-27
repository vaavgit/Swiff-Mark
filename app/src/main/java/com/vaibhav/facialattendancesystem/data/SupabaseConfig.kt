package com.vaibhav.facialattendancesystem.data

import com.vaibhav.facialattendancesystem.BuildConfig

/**
 * Supabase Cloud Configuration.
 * Keys are injected from local.properties via BuildConfig for security.
 */
object SupabaseConfig {
    val SUPABASE_URL: String = BuildConfig.SUPABASE_URL

    val SUPABASE_ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY

    val isConfigured: Boolean
        get() = SUPABASE_URL.isNotBlank() && SUPABASE_ANON_KEY.isNotBlank()
}
