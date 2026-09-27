package com.vaibhav.facialattendancesystem

import com.vaibhav.facialattendancesystem.ml.ConfidenceTier
import com.vaibhav.facialattendancesystem.ml.FaceMath
import com.vaibhav.facialattendancesystem.ml.RecognitionCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceMathTest {

    @Test
    fun testConfidenceTiers() {
        assertEquals(ConfidenceTier.HIGH, FaceMath.getConfidenceTier(0.45f))
        assertEquals(ConfidenceTier.HIGH, FaceMath.getConfidenceTier(0.40f))
        assertEquals(ConfidenceTier.MEDIUM, FaceMath.getConfidenceTier(0.35f))
        assertEquals(ConfidenceTier.MEDIUM, FaceMath.getConfidenceTier(0.30f))
        assertEquals(ConfidenceTier.LOW, FaceMath.getConfidenceTier(0.29f))
        assertEquals(ConfidenceTier.LOW, FaceMath.getConfidenceTier(0.15f))
    }

    @Test
    fun testCalibratedConfidencePercent() {
        assertTrue(FaceMath.calibratedConfidencePercent(0.65f) >= 95)
        val p45 = FaceMath.calibratedConfidencePercent(0.45f)
        assertTrue(p45 in 80..94)
        val p35 = FaceMath.calibratedConfidencePercent(0.35f)
        assertTrue(p35 in 65..79)
        assertTrue(FaceMath.calibratedConfidencePercent(0.10f) < 20)
    }

    @Test
    fun testCosineDistanceIdenticalAndOrthogonal() {
        val vecA = floatArrayOf(1.0f, 0.0f, 0.0f)
        val vecB = floatArrayOf(1.0f, 0.0f, 0.0f)
        val vecC = floatArrayOf(0.0f, 1.0f, 0.0f)

        val distIdentical = FaceMath.cosineDistance(vecA, vecB)
        assertEquals(0.0f, distIdentical, 0.0001f)

        val distOrthogonal = FaceMath.cosineDistance(vecA, vecC)
        assertEquals(1.0f, distOrthogonal, 0.0001f)
    }

    @Test
    fun testAssignMultiFaceMatches_resolvesCollisionsOptimally() {
        val face1Candidates = listOf(
            RecognitionCandidate("student_A", 101, "Student Alpha", 0.35f, 0.65f),
            RecognitionCandidate("student_B", 102, "Student Beta", 0.75f, 0.25f)
        )

        val face2Candidates = listOf(
            RecognitionCandidate("student_A", 101, "Student Alpha", 0.58f, 0.42f),
            RecognitionCandidate("student_B", 102, "Student Beta", 0.60f, 0.40f)
        )

        val allCandidates = mapOf(
            1 to face1Candidates,
            2 to face2Candidates
        )

        val assignment = FaceMath.assignMultiFaceMatches(allCandidates)

        assertNotNull(assignment[1])
        assertEquals("student_A", assignment[1]?.studentId)

        assertNotNull(assignment[2])
        assertEquals("student_B", assignment[2]?.studentId)
    }

    @Test
    fun testAssignMultiFaceMatches_rejectsStrangerBelowThreshold() {
        val face1Candidates = listOf(
            RecognitionCandidate("student_A", 101, "Student Alpha", 0.45f, 0.55f)
        )

        val face2Candidates = listOf(
            RecognitionCandidate("student_A", 101, "Student Alpha", 0.78f, 0.22f),
            RecognitionCandidate("student_B", 102, "Student Beta", 0.81f, 0.19f)
        )

        val allCandidates = mapOf(
            1 to face1Candidates,
            2 to face2Candidates
        )

        val assignment = FaceMath.assignMultiFaceMatches(allCandidates)

        assertEquals("student_A", assignment[1]?.studentId)
        assertNull(assignment[2])
    }

    @Test
    fun testAssignMultiFaceMatches_benchOfThreeStudents() {
        val face1Candidates = listOf(
            RecognitionCandidate("s1", 101, "Student 1", 0.40f, 0.60f),
            RecognitionCandidate("s2", 102, "Student 2", 0.65f, 0.35f)
        )
        val face2Candidates = listOf(
            RecognitionCandidate("s2", 102, "Student 2", 0.45f, 0.55f),
            RecognitionCandidate("s1", 101, "Student 1", 0.68f, 0.32f)
        )
        val face3Candidates = listOf(
            RecognitionCandidate("s3", 103, "Student 3", 0.52f, 0.48f)
        )

        val allCandidates = mapOf(
            1 to face1Candidates,
            2 to face2Candidates,
            3 to face3Candidates
        )

        val assignment = FaceMath.assignMultiFaceMatches(allCandidates)

        assertEquals("s1", assignment[1]?.studentId)
        assertEquals("s2", assignment[2]?.studentId)
        assertEquals("s3", assignment[3]?.studentId)
    }
}
