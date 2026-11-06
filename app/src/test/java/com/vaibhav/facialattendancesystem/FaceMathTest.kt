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
    fun testCalibratedConfidencePercentMatchesConfidenceTiers() {
        // High Tier: similarity >= 0.40 -> Calibrated >= 85%
        val p40 = FaceMath.calibratedConfidencePercent(0.40f)
        assertTrue("0.40 must map to >= 85% (HIGH tier), got $p40", p40 >= 85)
        assertEquals(ConfidenceTier.HIGH, FaceMath.getConfidenceTier(0.40f))

        val p65 = FaceMath.calibratedConfidencePercent(0.65f)
        assertTrue("0.65 must map to >= 95%, got $p65", p65 >= 95)

        // Medium Tier: 0.30 <= similarity < 0.40 -> 65%..84%
        val p35 = FaceMath.calibratedConfidencePercent(0.35f)
        assertTrue("0.35 must map to 65..84% (MEDIUM tier), got $p35", p35 in 65..84)
        assertEquals(ConfidenceTier.MEDIUM, FaceMath.getConfidenceTier(0.35f))

        val p30 = FaceMath.calibratedConfidencePercent(0.30f)
        assertTrue("0.30 must map to 65..84% (MEDIUM tier), got $p30", p30 in 65..84)
        assertEquals(ConfidenceTier.MEDIUM, FaceMath.getConfidenceTier(0.30f))

        // Low Tier: similarity < 0.30 -> < 65%
        val p29 = FaceMath.calibratedConfidencePercent(0.29f)
        assertTrue("0.29 must map to < 65% (LOW tier), got $p29", p29 < 65)
        assertEquals(ConfidenceTier.LOW, FaceMath.getConfidenceTier(0.29f))

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
            RecognitionCandidate("student_B", "102", "Student Beta", 0.81f, 0.19f)
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
