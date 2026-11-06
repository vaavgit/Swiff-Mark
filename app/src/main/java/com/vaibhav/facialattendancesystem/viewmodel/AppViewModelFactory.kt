package com.vaibhav.facialattendancesystem.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vaibhav.facialattendancesystem.data.AppDatabase
import com.vaibhav.facialattendancesystem.util.SessionManager

/**
 * Single factory for all app ViewModels.
 * Accepts AppDatabase and SessionManager so ViewModels can access local data
 * and session state without depending on Android Context directly.
 */
class AppViewModelFactory(
    private val db: AppDatabase,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                AuthViewModel(db, sessionManager) as T
            modelClass.isAssignableFrom(StudentDashboardViewModel::class.java) ->
                StudentDashboardViewModel(db, sessionManager) as T
            modelClass.isAssignableFrom(TeacherDashboardViewModel::class.java) ->
                TeacherDashboardViewModel(db, sessionManager) as T
            modelClass.isAssignableFrom(ClassDetailsViewModel::class.java) ->
                ClassDetailsViewModel(db, sessionManager) as T
            modelClass.isAssignableFrom(AttendanceViewModel::class.java) ->
                AttendanceViewModel(db, sessionManager) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
