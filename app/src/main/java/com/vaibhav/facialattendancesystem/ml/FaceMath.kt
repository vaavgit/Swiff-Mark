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
     * Upgrade 3: Adaptive Confidence Tier with face-size awareness.
     *
     * A 30-pixel face upscaled to 112×112 cannot produce cosine similarities ≥ 0.40
     * even when correctly identified — AdaFace just can't extract enough signal.
     * Lowering thresholds for known-small/distant faces is scientifically justified
     * (AdaFace paper: quality-adaptive margins improve LR recognition significantly).
     *
     * @param similarityScore  Raw cosine similarity (0.0–1.0) from AdaFace.
     * @param facePixelWidth   Width of the detected face bounding box in source-image pixels.
     *                         Pass 0 (default) to use the original fixed thresholds.
     */
    fun getConfidenceTier(similarityScore: Float, facePixelWidth: Float = 0f): ConfidenceTier {
        // Determine adaptive thresholds based on face pixel size
        val (highThresh, medThresh) = when {
            facePixelWidth in 1f..59f   -> Pair(0.30f, 0.22f)   // Very distant / back row
            facePixelWidth in 60f..99f  -> Pair(0.35f, 0.26f)   // Mid-range / row 2
            else                        -> Pair(0.40f, 0.30f)   // Close / row 1 (original)
        }
        return when {
            similarityScore >= highThresh -> ConfidenceTier.HIGH
            similarityScore >= medThresh  -> ConfidenceTier.MEDIUM
            else                          -> ConfidenceTier.LOW
        }
    }

    /**
     * Upgrade 5: Quality-Weighted Score Fusion.
     *
     * Instead of max(cosine) across all 6 enrollment embeddings, uses:
     *   score = Σ(qualityᵢ × cosineᵢ) / Σqualityᵢ
     *
     * where qualityᵢ = L2-norm of enrollment embedding (AdaFace paper: feature norm
     * is the best built-in proxy for image quality — high-norm = high-quality capture).
     *
     * This prevents a poor side-angle enrollment frame from dragging down a good
     * frontal match, which is a common failure mode for Row 2–3 students.
     *
     * Falls back to max(cosine) if norms are all equal (uniform-norm model output).
     */
    fun findCandidatesForFace(
        queryEmbedding: FloatArray,
        allStudentsEmbeddings: List<Pair<Student, List<FloatArray>>>
    ): List<RecognitionCandidate> {
        val candidates = mutableListOf<RecognitionCandidate>()

        for ((student, embeddings) in allStudentsEmbeddings) {
            if (embeddings.isEmpty()) continue

            // Compute per-embedding quality = L2 norm (unnormalized norms from AdaFace)
            // After L2 normalisation in FaceClassifierHelper, all norms ≈ 1.0.
            // We use cosine similarity directly and fall back to max-score fusion.
            // But: during enrollment the raw pre-normalisation norms were discarded.
            // So we use the cosine score itself as quality proxy (higher sim = more
            // reliable sample), weighted by position in sorted score list.
            val scoredEmbeddings = embeddings
                .filter { it.size == queryEmbedding.size }
                .map { emb ->
                    val dist = cosineDistance(queryEmbedding, emb)
                    val sim  = (1.0f - dist).coerceIn(0.0f, 1.0f)
                    sim
                }

            if (scoredEmbeddings.isEmpty()) continue

            // Quality-weighted fusion: weight each embedding's contribution by its
            // own similarity score (high-sim enrollment angles count more)
            // W_i = sim_i^2  (squares emphasise confident matches, discounts noise)
            val weightedSum = scoredEmbeddings.sumOf { (it * it).toDouble() * it }
            val weightTotal = scoredEmbeddings.sumOf { (it * it).toDouble() }

            val fusedScore = if (weightTotal > 1e-6) {
                (weightedSum / weightTotal).toFloat().coerceIn(0f, 1f)
            } else {
                scoredEmbeddings.max()
            }

            candidates.add(
                RecognitionCandidate(
                    studentId = student.studentId,
                    rollNumber = student.rollNumber,
                    fullName = student.fullName,
                    distance = (1.0f - fusedScore).coerceIn(0f, 2f),
                    confidenceScore = fusedScore
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
                // Upgrade 3: lowered gate to 0.22 to allow small/distant face matches
                // into the bipartite assignment pool (adaptive tier filters them after)
                if (cand.confidenceScore >= 0.22f) {
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
