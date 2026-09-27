package com.vaibhav.facialattendancesystem.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class User(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String = UUID.randomUUID().toString(),
    val email: String,
    @ColumnInfo(name = "password_hash")
    val passwordHash: String,
    @ColumnInfo(name = "user_type")
    val userType: String, // 'STUDENT' or 'TEACHER'
    val name: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "students",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id"], unique = true),
        Index(value = ["email"], unique = true)
    ]
)
data class Student(
    @PrimaryKey
    @ColumnInfo(name = "student_id")
    val studentId: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "roll_number")
    val rollNumber: Int,
    @ColumnInfo(name = "full_name")
    val fullName: String,
    val email: String,
    @ColumnInfo(name = "class_section")
    val classSection: String? = null,
    @ColumnInfo(name = "enrollment_status")
    val enrollmentStatus: Int = 0, // 1: complete, 0: incomplete
    @ColumnInfo(name = "enrollment_date")
    val enrollmentDate: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "teachers",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["user_id"], unique = true)]
)
data class Teacher(
    @PrimaryKey
    @ColumnInfo(name = "teacher_id")
    val teacherId: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "user_id")
    val userId: String,
    val subject: String,
    val department: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "face_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["student_id"],
            childColumns = ["student_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["student_id"])]
)
data class FaceEmbedding(
    @PrimaryKey
    @ColumnInfo(name = "embedding_id")
    val embeddingId: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "student_id")
    val studentId: String,
    @ColumnInfo(name = "embedding_vector")
    val embeddingVector: ByteArray, // Serialized FloatArray

    @ColumnInfo(name = "source_angle")
    val sourceAngle: String, // 'STRAIGHT', 'UP', 'DOWN', 'LEFT', 'RIGHT', 'SMILE', 'AVERAGED'
    @ColumnInfo(name = "quality_score")
    val qualityScore: Float,
    @ColumnInfo(name = "source_photo_path")
    val sourcePhotoPath: String? = null,
    @ColumnInfo(name = "enrollment_timestamp")
    val enrollmentTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "classes",
    indices = [Index(value = ["teacher_id"])]
)
data class Clazz(
    @PrimaryKey
    @ColumnInfo(name = "class_id")
    val classId: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "teacher_id")
    val teacherId: String,
    @ColumnInfo(name = "class_name")
    val className: String,
    val subject: String,
    val semester: Int,
    val section: String,
    @ColumnInfo(name = "class_code")
    val classCode: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)


@Entity(
    tableName = "class_enrollments",
    foreignKeys = [
        ForeignKey(
            entity = Clazz::class,
            parentColumns = ["class_id"],
            childColumns = ["class_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Student::class,
            parentColumns = ["student_id"],
            childColumns = ["student_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["class_id", "student_id"], unique = true),
        Index(value = ["student_id"])
    ]
)
data class ClassEnrollment(
    @PrimaryKey
    @ColumnInfo(name = "enrollment_id")
    val enrollmentId: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "class_id")
    val classId: String,
    @ColumnInfo(name = "student_id")
    val studentId: String,
    @ColumnInfo(name = "enrolled_at")
    val enrolledAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "enrollment_verified")
    // 0 = PENDING teacher approval, 1 = APPROVED, 2 = REJECTED
    val enrollmentVerified: Int = 0
)

@Entity(
    tableName = "attendance_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Clazz::class,
            parentColumns = ["class_id"],
            childColumns = ["class_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["class_id"])]
)
data class AttendanceSession(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "class_id")
    val classId: String,
    @ColumnInfo(name = "session_date")
    val sessionDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "photo_1_timestamp")
    val photo1Timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "photo_1_path")
    val photo1Path: String,
    @ColumnInfo(name = "photo_1_faces_detected")
    val photo1FacesDetected: Int,
    @ColumnInfo(name = "photo_2_timestamp")
    val photo2Timestamp: Long? = null,
    @ColumnInfo(name = "photo_2_path")
    val photo2Path: String? = null,
    @ColumnInfo(name = "photo_2_faces_detected")
    val photo2FacesDetected: Int? = null,
    @ColumnInfo(name = "session_status")
    val sessionStatus: String = "IN_PROGRESS", // 'IN_PROGRESS', 'COMPLETED', 'DRAFT'
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "attendance_records",
    foreignKeys = [
        ForeignKey(
            entity = AttendanceSession::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Student::class,
            parentColumns = ["student_id"],
            childColumns = ["student_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id", "student_id"], unique = true),
        Index(value = ["session_id"]),
        Index(value = ["student_id"])
    ]
)
data class AttendanceRecord(
    @PrimaryKey
    @ColumnInfo(name = "attendance_id")
    val attendanceId: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "student_id")
    val studentId: String,
    @ColumnInfo(name = "photo_1_match_confidence")
    val photo1MatchConfidence: Float? = null,
    @ColumnInfo(name = "photo_1_matched")
    val photo1Matched: Int? = null, // 1 or 0
    @ColumnInfo(name = "photo_2_match_confidence")
    val photo2MatchConfidence: Float? = null,
    @ColumnInfo(name = "photo_2_matched")
    val photo2Matched: Int? = null, // 1 or 0
    @ColumnInfo(name = "dual_capture_present")
    val dualCapturePresent: Int? = null, // 1 or 0
    @ColumnInfo(name = "marked_present")
    val markedPresent: Int = 0, // 1: present, 0: absent
    @ColumnInfo(name = "manual_override")
    val manualOverride: Int? = 0, // 1: manually overrode
    @ColumnInfo(name = "override_notes")
    val overrideNotes: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recognition_logs",
    foreignKeys = [
        ForeignKey(
            entity = AttendanceSession::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["session_id"])]
)
data class RecognitionLog(
    @PrimaryKey
    @ColumnInfo(name = "log_id")
    val logId: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "detected_face_index")
    val detectedFaceIndex: Int,
    @ColumnInfo(name = "matched_student_id")
    val matchedStudentId: String? = null,
    // Raw cosine similarity (0.0 – 1.0) between detected face and this enrolled student's best embedding.
    // Strangers will produce scores < 0.30 against every enrolled student.
    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float,
    @ColumnInfo(name = "top_5_candidates")
    val top5Candidates: String? = null, // compact "id:score,id:score" string for the top 5
    @ColumnInfo(name = "processing_time_ms")
    val processingTimeMs: Long? = null,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    // "PHOTO_1" or "PHOTO_2" — distinguishes start-of-class vs end-of-class captures
    @ColumnInfo(name = "photo_label")
    val photoLabel: String = "PHOTO_1",
    val timestamp: Long = System.currentTimeMillis()
)
