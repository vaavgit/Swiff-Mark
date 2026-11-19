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
        // Baseline (facePixelWidth=0): highThresh=0.36f, medThresh=0.26f
        assertEquals(ConfidenceTier.HIGH,   FaceMath.getConfidenceTier(0.45f))
        assertEquals(ConfidenceTier.HIGH,   FaceMath.getConfidenceTier(0.36f))   // exactly at high boundary
        assertEquals(ConfidenceTier.MEDIUM, FaceMath.getConfidenceTier(0.35f))   // just below high boundary
        assertEquals(ConfidenceTier.MEDIUM, FaceMath.getConfidenceTier(0.30f))
        assertEquals(ConfidenceTier.MEDIUM, FaceMath.getConfidenceTier(0.26f))   // exactly at medium boundary
        assertEquals(ConfidenceTier.LOW,    FaceMath.getConfidenceTier(0.25f))   // just below medium boundary
        assertEquals(ConfidenceTier.LOW,    FaceMath.getConfidenceTier(0.15f))

        // Adaptive: very distant face (facePixelWidth=30): highThresh=0.22f, medThresh=0.14f
        assertEquals(ConfidenceTier.HIGH,   FaceMath.getConfidenceTier(0.22f, 30f))
        assertEquals(ConfidenceTier.MEDIUM, FaceMath.getConfidenceTier(0.18f, 30f))
        assertEquals(ConfidenceTier.LOW,    FaceMath.getConfidenceTier(0.13f, 30f))

        // Adaptive: mid-range face (facePixelWidth=90): highThresh=0.26f, medThresh=0.18f
        assertEquals(ConfidenceTier.HIGH,   FaceMath.getConfidenceTier(0.27f, 90f))
        assertEquals(ConfidenceTier.MEDIUM, FaceMath.getConfidenceTier(0.20f, 90f))
        assertEquals(ConfidenceTier.LOW,    FaceMath.getConfidenceTier(0.17f, 90f))

        // Adaptive: close-up face (facePixelWidth=150): highThresh=0.28f, medThresh=0.20f
        assertEquals(ConfidenceTier.HIGH,   FaceMath.getConfidenceTier(0.30f, 150f))
        assertEquals(ConfidenceTier.MEDIUM, FaceMath.getConfidenceTier(0.22f, 150f))
        assertEquals(ConfidenceTier.LOW,    FaceMath.getConfidenceTier(0.19f, 150f))
    }

    @Test
    fun testCalibratedConfidencePercentMatchesConfidenceTiers() {
        // calibratedConfidencePercent uses fixed breakpoints (0.60/0.40/0.30/0.20) independent of adaptive tiers.
        // getConfidenceTier(0f baseline) uses adaptive baseline: HIGH >= 0.36f, MEDIUM >= 0.26f.

        // High Tier: similarity >= 0.36 (baseline) -> HIGH
        val p40 = FaceMath.calibratedConfidencePercent(0.40f)
        assertTrue("0.40 must map to >= 85% (HIGH calib range), got $p40", p40 >= 85)
        assertEquals(ConfidenceTier.HIGH, FaceMath.getConfidenceTier(0.40f))

        val p36 = FaceMath.calibratedConfidencePercent(0.36f)
        assertTrue("0.36 must map to >= 75%, got $p36", p36 >= 75)
        assertEquals(ConfidenceTier.HIGH, FaceMath.getConfidenceTier(0.36f))

        val p65 = FaceMath.calibratedConfidencePercent(0.65f)
        assertTrue("0.65 must map to >= 95%, got $p65", p65 >= 95)

        // Medium Tier (baseline): 0.26 <= similarity < 0.36 -> MEDIUM
        val p35 = FaceMath.calibratedConfidencePercent(0.35f)
        assertTrue("0.35 must map to 65..84% (MEDIUM calib range), got $p35", p35 in 65..84)
        assertEquals(ConfidenceTier.MEDIUM, FaceMath.getConfidenceTier(0.35f))

        val p30 = FaceMath.calibratedConfidencePercent(0.30f)
        assertTrue("0.30 must map to 65..84% (MEDIUM calib range), got $p30", p30 in 65..84)
        assertEquals(ConfidenceTier.MEDIUM, FaceMath.getConfidenceTier(0.30f))

        val p26 = FaceMath.calibratedConfidencePercent(0.26f)
        assertTrue("0.26 must map to >= 34%, got $p26", p26 >= 34)
        assertEquals(ConfidenceTier.MEDIUM, FaceMath.getConfidenceTier(0.26f))

        // Low Tier (baseline): similarity < 0.26 -> LOW
        val p25 = FaceMath.calibratedConfidencePercent(0.25f)
        assertTrue("0.25 must map to < 65% (LOW tier), got $p25", p25 < 65)
        assertEquals(ConfidenceTier.LOW, FaceMath.getConfidenceTier(0.25f))

        val p10 = FaceMath.calibratedConfidencePercent(0.10f)
        assertTrue("0.10 must map to < 35%, got $p10", p10 < 35)
    }

    @Test
    fun testEyeAnchorLandmarksAreUpright() {
        // In FaceAligner, canonical coordinates place subject's right eye (viewer's left)
        // at x ~ 38.29 and subject's left eye (viewer's right) at x ~ 73.53 on the 112x112 chip.
        // Confirm x_rightEye < x_leftEye so the aligned face is upright and not inverted 180 degrees.
        val rightEyeX = 38.29f
        val leftEyeX = 73.53f
        assertTrue("Viewer left (anatomical right) eye x must be < viewer right eye x", rightEyeX < leftEyeX)
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
    fun testAssignMultiFaceMatches_resolvesCollisionsGreedily() {
        val face1Candidates = listOf(
            RecognitionCandidate("student_A", "101", "Student Alpha", 0.35f, 0.65f),
            RecognitionCandidate("student_B", "102", "Student Beta", 0.75f, 0.25f)
        )

        val face2Candidates = listOf(
            RecognitionCandidate("student_A", "101", "Student Alpha", 0.58f, 0.42f),
            RecognitionCandidate("student_B", "102", "Student Beta", 0.60f, 0.40f)
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
            RecognitionCandidate("student_A", "101", "Student Alpha", 0.45f, 0.55f)
        )

        val face2Candidates = listOf(
            RecognitionCandidate("student_A", "101", "Student Alpha", 0.78f, 0.22f),
            // student_B has confidenceScore=0.14f (below 0.15 gate) — total stranger, must be rejected
            RecognitionCandidate("student_B", "102", "Student Beta", 0.86f, 0.14f)
        )

        val allCandidates = mapOf(
            1 to face1Candidates,
            2 to face2Candidates
        )

        val assignment = FaceMath.assignMultiFaceMatches(allCandidates)

        assertEquals("student_A", assignment[1]?.studentId)
        // face2: student_A already claimed by face1; student_B below 0.15 gate → face2 gets null
        assertNull(assignment[2])
    }

    @Test
    fun testAssignMultiFaceMatches_benchOfThreeStudents() {
        val face1Candidates = listOf(
            RecognitionCandidate("s1", "101", "Student 1", 0.40f, 0.60f),
            RecognitionCandidate("s2", "102", "Student 2", 0.65f, 0.35f)
        )
        val face2Candidates = listOf(
            RecognitionCandidate("s2", "102", "Student 2", 0.45f, 0.55f),
            RecognitionCandidate("s1", "101", "Student 1", 0.68f, 0.32f)
        )
        val face3Candidates = listOf(
            RecognitionCandidate("s3", "103", "Student 3", 0.52f, 0.48f)
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
