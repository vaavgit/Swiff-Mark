package com.vaibhav.facialattendancesystem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies client-side validation logic for authentication and sign-up requirements.
 */
class AuthValidationTest {

    private fun isPasswordStrong(password: String): Boolean {
        return password.length >= 8 && password.any { it.isUpperCase() } && password.any { it.isDigit() }
    }

    private fun isStudentFormValid(
        fullName: String,
        email: String,
        password: String,
        rollNumber: String,
        hasConsented: Boolean
    ): Boolean {
        val emailValid = email.contains("@") && email.contains(".")
        return fullName.isNotBlank() && emailValid && isPasswordStrong(password) && rollNumber.isNotBlank() && hasConsented
    }

    @Test
    fun testPasswordStrengthValidation() {
        assertFalse("Too short", isPasswordStrong("Ab1"))
        assertFalse("No uppercase", isPasswordStrong("password123"))
        assertFalse("No digit", isPasswordStrong("PasswordXYZ"))
        assertTrue("Valid strong password", isPasswordStrong("Pass1234"))
        assertTrue("Valid alphanumeric password", isPasswordStrong("SwiffMark2026!"))
    }

    @Test
    fun testStudentRegistrationRequirements() {
        // Missing consent should fail
        assertFalse(
            "Registration without biometric consent must fail",
            isStudentFormValid("Arjun Kumar", "arjun@college.edu", "Password123", "21CS041", false)
        )

        // Blank roll number should fail
        assertFalse(
            "Registration without roll number must fail",
            isStudentFormValid("Arjun Kumar", "arjun@college.edu", "Password123", "", true)
        )

        // Weak password should fail
        assertFalse(
            "Registration with weak password must fail",
            isStudentFormValid("Arjun Kumar", "arjun@college.edu", "weakpass", "21CS041", true)
        )

        // Complete and valid student registration
        assertTrue(
            "Valid student registration must pass",
            isStudentFormValid("Arjun Kumar", "arjun@college.edu", "Password123", "21CS041", true)
        )
    }
}
