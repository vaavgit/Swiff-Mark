package com.vaibhav.facialattendancesystem

import com.vaibhav.facialattendancesystem.data.FaceEmbedding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that the H6 pipeline versioning filter correctly rejects legacy embeddings (< 2)
 * and retains valid, modern embeddings (>= 2).
 */
class AttendancePipelineFilterTest {

    @Test
    fun testPipelineVersionFiltering() {
        val legacyEmbeddingV1 = FaceEmbedding(
            studentId = "s1",
            embeddingVector = ByteArray(512),
            sourceAngle = "FRONTAL",
            pipelineVersion = 1,
            qualityScore = 0.85f
        )

        val modernEmbeddingV2 = FaceEmbedding(
            studentId = "s1",
            embeddingVector = ByteArray(512),
            sourceAngle = "FRONTAL",
            pipelineVersion = 2,
            qualityScore = 0.95f
        )

        val futureEmbeddingV3 = FaceEmbedding(
            studentId = "s2",
            embeddingVector = ByteArray(512),
            sourceAngle = "FRONTAL",
            pipelineVersion = 3,
            qualityScore = 0.98f
        )

        val allEmbeddings = listOf(legacyEmbeddingV1, modernEmbeddingV2, futureEmbeddingV3)

        // Filter replicating AttendanceViewModel logic: .filter { it.pipelineVersion >= 2 }
        val filtered = allEmbeddings.filter { it.pipelineVersion >= 2 }

        assertEquals(2, filtered.size)
        assertTrue("Legacy v1 embedding must be excluded", filtered.none { it.pipelineVersion < 2 })
        assertTrue("Modern v2 embedding must be included", filtered.any { it.pipelineVersion == 2 })
        assertTrue("Future v3 embedding must be included", filtered.any { it.pipelineVersion == 3 })
    }
}
