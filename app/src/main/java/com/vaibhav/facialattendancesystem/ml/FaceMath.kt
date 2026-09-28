package com.vaibhav.facialattendancesystem.ml

import com.vaibhav.facialattendancesystem.data.Student
import kotlin.math.sqrt

enum class ConfidenceTier {
    HIGH,    // >= 0.40 Similarity (Calibrated >= 75% - Auto-Marked High Confidence)
    MEDIUM,  // 0.30 - 0.40 Similarity (Calibrated 50% - 74% - Teacher Review Needed)
    LOW      // < 0.30 Similarity (Calibrated < 50% - Unrecognized)
}

data class RecognitionCandidate(
    val studentId: String,
    val rollNumber: Int,
    val fullName: String,
    val distance: Float, // Cosine distance (1.0 - Cosine Similarity)
    val confidenceScore: Float, // Cosine Similarity (0.0 to 1.0)
    val calibratedPercent: Int = FaceMath.calibratedConfidencePercent(confidenceScore)
)

data class RecognitionResult(
    val faceIndex: Int,
    val boundingBox: android.graphics.RectF,
    val topMatch: RecognitionCandidate?,
    val top5Candidates: List<RecognitionCandidate>,
    val confidenceTier: ConfidenceTier,
    var manuallyConfirmed: Boolean = false,
    var selectedStudentId: String? = topMatch?.studentId
)

data class AttendanceIntersectionResult(
    val presentStudents: Set<String>,
    val onlyInPhoto1: Set<String>,
    val onlyInPhoto2: Set<String>,
    val missingEntirely: Set<String>,
    val alerts: List<String>
)

object FaceMath {

    /**
     * Calibrates raw 512-D cosine similarity into an intuitive confidence percentage (0% to 100%).
     * In 512-D hypersphere embedding space (AdaFace / ArcFace):
     * - Random/unrelated people have cosine similarity of ~0.00 - 0.18 (near-orthogonal).
     * - Genuine matches at classroom distance (1-3m) with varying lighting/angles score ~0.35 - 0.60.
     * - This calibrated function maps biometric cosine similarity to human-friendly 0-100% confidence.
     */
    fun calibratedConfidencePercent(similarity: Float): Int {
        return when {
            similarity >= 0.60f -> {
                // Exceptional biometric match: 0.60 .. 0.85+ -> 95% .. 99%
                val fraction = ((similarity - 0.60f) / 0.25f).coerceIn(0f, 1f)
                (95 + fraction * 4).toInt().coerceIn(95, 99)
            }
            similarity >= 0.45f -> {
                // High confidence match: 0.45 .. 0.60 -> 80% .. 94%
                val fraction = ((similarity - 0.45f) / 0.15f).coerceIn(0f, 1f)
                (80 + fraction * 14).toInt().coerceIn(80, 94)
            }
            similarity >= 0.35f -> {
                // Medium confidence match: 0.35 .. 0.45 -> 65% .. 79%
                val fraction = ((similarity - 0.35f) / 0.10f).coerceIn(0f, 1f)
                (65 + fraction * 14).toInt().coerceIn(65, 79)
            }
            similarity >= 0.28f -> {
                // Borderline match: 0.28 .. 0.35 -> 45% .. 64%
                val fraction = ((similarity - 0.28f) / 0.07f).coerceIn(0f, 1f)
                (45 + fraction * 19).toInt().coerceIn(45, 64)
            }
            similarity >= 0.18f -> {
                // Low similarity / likely different person: 0.18 .. 0.28 -> 20% .. 44%
                val fraction = ((similarity - 0.18f) / 0.10f).coerceIn(0f, 1f)
                (20 + fraction * 24).toInt().coerceIn(20, 44)
            }
            else -> {
                // Distinct / unrelated face: < 0.18 -> 0% .. 19%
                (similarity * 100f).coerceIn(0f, 19f).toInt()
            }
        }
    }

    /**
     * Calculates cosine distance between two float vectors.
     * Cosine distance = 1 - Cosine Similarity
     * Range: 0.0 (identical) to 2.0 (opposite)
     */
    fun cosineDistance(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size || vectorA.isEmpty()) return 1.0f

        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f

        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
            normA += vectorA[i] * vectorA[i]
            normB += vectorB[i] * vectorB[i]
        }

        if (normA == 0.0f || normB == 0.0f) return 1.0f

        val similarity = dotProduct / (sqrt(normA) * sqrt(normB))
        return (1.0f - similarity).coerceIn(0.0f, 2.0f)
    }

    /**
     * Calibrated 512-D Cosine Similarity to Confidence Tier:
     * High: >= 0.40 (Calibrated >= 75%) -> Auto-Mark
     * Medium: 0.30 - 0.40 (Calibrated 50% - 74%) -> Teacher Review
     * Low: < 0.30 (Calibrated < 50%) -> Unrecognized
     */
    fun getConfidenceTier(similarityScore: Float): ConfidenceTier {
        return when {
            similarityScore >= 0.40f -> ConfidenceTier.HIGH
            similarityScore >= 0.30f -> ConfidenceTier.MEDIUM
            else -> ConfidenceTier.LOW
        }
    }

    /**
     * Best-of-6 Enrollment Embeddings Matching Strategy:
     * Compares detected classroom face embedding against ALL 6 stored angle embeddings of each student.
     * Takes the best match (minimum cosine distance / highest similarity) for each student.
     */
    fun findCandidatesForFace(
        queryEmbedding: FloatArray,
        allStudentsEmbeddings: List<Pair<Student, List<FloatArray>>>
    ): List<RecognitionCandidate> {
        val candidates = mutableListOf<RecognitionCandidate>()

        for ((student, embeddings) in allStudentsEmbeddings) {
            var minDistance = 2.0f
            for (emb in embeddings) {
                if (emb.size == queryEmbedding.size) {
                    val dist = cosineDistance(queryEmbedding, emb)
                    if (dist < minDistance) {
                        minDistance = dist
                    }
                }
            }

            val similarity = (1.0f - minDistance).coerceIn(0.0f, 1.0f)
            candidates.add(
                RecognitionCandidate(
                    studentId = student.studentId,
                    rollNumber = student.rollNumber,
                    fullName = student.fullName,
                    distance = minDistance,
                    confidenceScore = similarity
                )
            )
        }

        return candidates.sortedByDescending { it.confidenceScore }
    }

    /**
     * Computes average float vector across multiple angle embeddings.
     */
    fun averageEmbeddings(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return FloatArray(0)
        val dimension = embeddings.first().size
        val result = FloatArray(dimension)

        for (embedding in embeddings) {
            if (embedding.size == dimension) {
                for (i in 0 until dimension) {
                    result[i] += embedding[i]
                }
            }
        }

        val count = embeddings.size.toFloat()
        for (i in 0 until dimension) {
            result[i] /= count
        }

        var norm = 0.0f
        for (f in result) {
            norm += f * f
        }
        norm = sqrt(norm)
        if (norm > 0.0f) {
            for (i in result.indices) {
                result[i] /= norm
            }
        }
        return result
    }

    /**
     * Performs dual-capture intersection validation.
     * Only students present in BOTH photo 1 and photo 2 get marked present.
     */
    fun computeDualCaptureIntersection(
        photo1Results: List<RecognitionResult>,
        photo2Results: List<RecognitionResult>,
        enrolledStudentIds: List<String>
    ): AttendanceIntersectionResult {

        val photo1Ids = photo1Results
            .filter { (it.confidenceTier != ConfidenceTier.LOW || it.manuallyConfirmed) && it.selectedStudentId != null }
            .mapNotNull { it.selectedStudentId }
            .toSet()

        val photo2Ids = photo2Results
            .filter { (it.confidenceTier != ConfidenceTier.LOW || it.manuallyConfirmed) && it.selectedStudentId != null }
            .mapNotNull { it.selectedStudentId }
            .toSet()

        val presentStudents = photo1Ids.intersect(photo2Ids)
        val unionStudents = photo1Ids.union(photo2Ids)
        val onlyInPhoto1 = photo1Ids - photo2Ids
        val onlyInPhoto2 = photo2Ids - photo1Ids
        val totalEnrolled = enrolledStudentIds.toSet()
        val missingEntirely = totalEnrolled - unionStudents

        val alerts = mutableListOf<String>()
        if (presentStudents.size < (totalEnrolled.size * 0.7)) {
            alerts.add("Low detection: Only ${presentStudents.size}/${totalEnrolled.size} verified in both photos.")
        }
        if (onlyInPhoto1.size > 3 || onlyInPhoto2.size > 3) {
            alerts.add("Movement detected: Several students were seen in only one photo.")
        }

        return AttendanceIntersectionResult(
            presentStudents = presentStudents,
            onlyInPhoto1 = onlyInPhoto1,
            onlyInPhoto2 = onlyInPhoto2,
            missingEntirely = missingEntirely,
            alerts = alerts
        )
    }

    /**
     * 1-to-1 Bipartite Multi-Face Matcher (SDD Step 5 Optimization - REQ-ATT-001 / REQ-ATT-006):
     * Solves greedy collision when multiple students are captured in the same frame (benches of 2 to 5 students).
     * Ensures no two detected faces claim the same enrolled student, and prevents stranger collisions.
     *
     * @param allFaceCandidates Map of faceIndex -> List<RecognitionCandidate> sorted by confidenceScore
     * @return Map of faceIndex -> RecognitionCandidate? (the assigned candidate, or null if below threshold or eclipsed)
     */
    fun assignMultiFaceMatches(
        allFaceCandidates: Map<Int, List<RecognitionCandidate>>
    ): Map<Int, RecognitionCandidate?> {
        val assignedFaces = mutableSetOf<Int>()
        val assignedStudents = mutableSetOf<String>()
        val assignmentResult = mutableMapOf<Int, RecognitionCandidate?>()

        data class FaceCandidatePair(val faceIndex: Int, val candidate: RecognitionCandidate)
        val candidatePairs = mutableListOf<FaceCandidatePair>()

        for ((faceIdx, candidates) in allFaceCandidates) {
            for (cand in candidates) {
                // Only consider candidates with confidence >= 0.30 (above LOW/stranger threshold)
                if (cand.confidenceScore >= 0.30f) {
                    candidatePairs.add(FaceCandidatePair(faceIdx, cand))
                }
            }
        }

        // Sort all possible face-student candidate pairs in descending order of similarity
        candidatePairs.sortByDescending { it.candidate.confidenceScore }

        // Greedily lock in assignments: highest-confidence pairs take priority
        for (pair in candidatePairs) {
            if (!assignedFaces.contains(pair.faceIndex) && !assignedStudents.contains(pair.candidate.studentId)) {
                assignedFaces.add(pair.faceIndex)
                assignedStudents.add(pair.candidate.studentId)
                assignmentResult[pair.faceIndex] = pair.candidate
            }
        }

        // Faces without an assignment get null
        for (faceIdx in allFaceCandidates.keys) {
            if (!assignmentResult.containsKey(faceIdx)) {
                assignmentResult[faceIdx] = null
            }
        }

        return assignmentResult
    }
}
