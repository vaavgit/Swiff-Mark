package com.vaibhav.facialattendancesystem.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUser(user: User): Long

    @Update
    fun updateUser(user: User): Int

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE user_id = :userId LIMIT 1")
    fun getUserById(userId: String): User?

    @Query("SELECT * FROM users WHERE user_id = :userId LIMIT 1")
    fun getUserByIdFlow(userId: String): Flow<User?>
}

@Dao
interface StudentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStudent(student: Student): Long

    @Update
    fun updateStudent(student: Student): Int

    @Query("SELECT * FROM students WHERE user_id = :userId LIMIT 1")
    fun getStudentByUserId(userId: String): Student?

    @Query("SELECT * FROM students WHERE student_id = :studentId LIMIT 1")
    fun getStudentById(studentId: String): Student?

    @Query("SELECT * FROM students WHERE student_id = :studentId LIMIT 1")
    fun getStudentByIdFlow(studentId: String): Flow<Student?>

    @Query("SELECT * FROM students ORDER BY roll_number ASC")
    fun getAllStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE student_id IN (:studentIds)")
    fun getStudentsByIds(studentIds: List<String>): List<Student>
}

@Dao
interface TeacherDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTeacher(teacher: Teacher): Long

    @Update
    fun updateTeacher(teacher: Teacher): Int

    @Query("SELECT * FROM teachers WHERE user_id = :userId LIMIT 1")
    fun getTeacherByUserId(userId: String): Teacher?

    @Query("SELECT * FROM teachers WHERE teacher_id = :teacherId LIMIT 1")
    fun getTeacherById(teacherId: String): Teacher?

    @Query("SELECT * FROM teachers WHERE teacher_id = :teacherId LIMIT 1")
    fun getTeacherByIdFlow(teacherId: String): Flow<Teacher?>
}

@Dao
interface FaceEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEmbedding(embedding: FaceEmbedding): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEmbeddings(embeddings: List<FaceEmbedding>): List<Long>

    @Query("SELECT * FROM face_embeddings WHERE student_id = :studentId")
    fun getEmbeddingsForStudent(studentId: String): List<FaceEmbedding>

    @Query("SELECT * FROM face_embeddings")
    fun getAllEmbeddings(): List<FaceEmbedding>

    @Query("DELETE FROM face_embeddings WHERE student_id = :studentId")
    fun deleteEmbeddingsForStudent(studentId: String): Int
}

@Dao
interface ClazzDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertClass(clazz: Clazz): Long

    @Query("SELECT * FROM classes WHERE teacher_id = :teacherId ORDER BY created_at DESC")
    fun getClassesForTeacher(teacherId: String): Flow<List<Clazz>>

    @Query("SELECT * FROM classes WHERE teacher_id = :teacherId ORDER BY created_at DESC")
    fun getClassesListForTeacher(teacherId: String): List<Clazz>

    @Query("SELECT * FROM classes WHERE class_id = :classId LIMIT 1")
    fun getClassById(classId: String): Clazz?

    @Query("SELECT * FROM classes WHERE class_code = :classCode LIMIT 1")
    fun getClassByCode(classCode: String): Clazz?

    @Query("SELECT c.* FROM classes c INNER JOIN class_enrollments e ON c.class_id = e.class_id WHERE e.student_id = :studentId ORDER BY c.created_at DESC")
    fun getClassesForStudent(studentId: String): Flow<List<Clazz>>

    @Query("DELETE FROM classes")
    fun deleteAllClasses(): Int

    @Query("DELETE FROM classes WHERE class_id = :classId")
    fun deleteClassById(classId: String): Int

    @Query("SELECT c.* FROM classes c INNER JOIN class_enrollments e ON c.class_id = e.class_id WHERE e.student_id = :studentId")
    fun getClassesListForStudent(studentId: String): List<Clazz>
}

@Dao
interface ClassEnrollmentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun enrollStudent(enrollment: ClassEnrollment): Long

    @Query("SELECT student_id FROM class_enrollments WHERE class_id = :classId")
    fun getEnrolledStudentIds(classId: String): List<String>

    @Query("SELECT COUNT(*) FROM class_enrollments WHERE class_id = :classId")
    fun getEnrolledCount(classId: String): Flow<Int>

    @Query("SELECT * FROM class_enrollments")
    fun getAllEnrollmentsFlow(): Flow<List<ClassEnrollment>>

    @Query("""
        SELECT e.* FROM class_enrollments e
        INNER JOIN classes c ON e.class_id = c.class_id
        WHERE c.teacher_id = :teacherId
    """)
    fun getEnrollmentsForTeacherFlow(teacherId: String): Flow<List<ClassEnrollment>>

    @Query("""
        SELECT COUNT(DISTINCT e.student_id) 
        FROM class_enrollments e 
        INNER JOIN classes c ON e.class_id = c.class_id 
        WHERE c.teacher_id = :teacherId
    """)
    fun getTotalStudentsForTeacher(teacherId: String): Flow<Int>

    // Only returns IDs for APPROVED students (enrollmentVerified = 1)
    // Used for face matching — pending students must not appear in attendance
    @Query("SELECT student_id FROM class_enrollments WHERE class_id = :classId AND enrollment_verified = 1")
    fun getApprovedStudentIds(classId: String): List<String>

    // Returns pending join requests for a class (for teacher approval UI)
    @Query("SELECT * FROM class_enrollments WHERE class_id = :classId AND enrollment_verified = 0")
    fun getPendingEnrollmentsForClass(classId: String): List<ClassEnrollment>

    // Flow version so teacher UI updates live
    @Query("SELECT * FROM class_enrollments WHERE class_id = :classId AND enrollment_verified = 0")
    fun getPendingEnrollmentsForClassFlow(classId: String): Flow<List<ClassEnrollment>>

    // Count of pending requests across all classes of a teacher (for dashboard badge)
    @Query("""
        SELECT COUNT(*) FROM class_enrollments e
        INNER JOIN classes c ON e.class_id = c.class_id
        WHERE c.teacher_id = :teacherId AND e.enrollment_verified = 0
    """)
    fun getPendingCountForTeacherFlow(teacherId: String): Flow<Int>

    @Query("UPDATE class_enrollments SET enrollment_verified = :status WHERE class_id = :classId AND student_id = :studentId")
    fun updateEnrollmentStatus(classId: String, studentId: String, status: Int): Int

    @Query("DELETE FROM class_enrollments WHERE class_id = :classId AND student_id = :studentId")
    fun unenrollStudent(classId: String, studentId: String): Int

    @Query("DELETE FROM class_enrollments")
    fun deleteAllEnrollments(): Int

    @Query("DELETE FROM class_enrollments WHERE class_id = :classId")
    fun deleteEnrollmentsForClass(classId: String): Int
}

@Dao
interface AttendanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSession(session: AttendanceSession): Long

    @Update
    fun updateSession(session: AttendanceSession): Int

    @Query("SELECT * FROM attendance_sessions WHERE session_id = :sessionId LIMIT 1")
    fun getSessionById(sessionId: String): AttendanceSession?

    @Query("SELECT * FROM attendance_sessions WHERE class_id = :classId ORDER BY session_date DESC")
    fun getSessionsForClass(classId: String): Flow<List<AttendanceSession>>

    @Query("SELECT * FROM attendance_sessions WHERE class_id = :classId ORDER BY session_date DESC")
    fun getSessionsListForClass(classId: String): List<AttendanceSession>

    @Query("SELECT * FROM attendance_sessions")
    fun getAllSessionsFlow(): Flow<List<AttendanceSession>>

    @Query("""
        SELECT s.* FROM attendance_sessions s
        INNER JOIN classes c ON s.class_id = c.class_id
        WHERE c.teacher_id = :teacherId
    """)
    fun getSessionsForTeacherFlow(teacherId: String): Flow<List<AttendanceSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAttendanceRecords(records: List<AttendanceRecord>): List<Long>

    @Query("SELECT * FROM attendance_records WHERE session_id = :sessionId")
    fun getRecordsForSession(sessionId: String): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE session_id = :sessionId")
    fun getRecordsFlowForSession(sessionId: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE student_id = :studentId")
    fun getRecordsForStudent(studentId: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_sessions WHERE is_synced = 0 AND session_status = 'COMPLETED'")
    fun getUnsyncedSessions(): List<AttendanceSession>

    @Query("SELECT COUNT(*) FROM attendance_sessions WHERE is_synced = 0 AND session_status = 'COMPLETED'")
    fun getUnsyncedSessionsCountFlow(): Flow<Int>

    @Query("""
        SELECT COUNT(*) 
        FROM attendance_sessions s 
        INNER JOIN classes c ON s.class_id = c.class_id 
        WHERE c.teacher_id = :teacherId AND s.is_synced = 0 AND s.session_status = 'COMPLETED'
    """)
    fun getUnsyncedSessionsCountForTeacherFlow(teacherId: String): Flow<Int>

    @Query("UPDATE attendance_sessions SET is_synced = 1 WHERE session_id = :sessionId")
    fun markSessionSynced(sessionId: String)

    @Query("""
        SELECT r.session_id AS sessionId, s.class_id AS classId, c.class_name AS className, s.session_date AS sessionDate, r.marked_present AS markedPresent
        FROM attendance_records r
        INNER JOIN attendance_sessions s ON r.session_id = s.session_id
        INNER JOIN classes c ON s.class_id = c.class_id
        WHERE r.student_id = :studentId
        ORDER BY s.session_date DESC
    """)
    fun getAttendanceFeedForStudent(studentId: String): Flow<List<AttendanceFeedItem>>

    @Query("""
        SELECT r.* FROM attendance_records r
        INNER JOIN attendance_sessions s ON r.session_id = s.session_id
        WHERE s.class_id = :classId
    """)
    fun getAllRecordsForClass(classId: String): Flow<List<AttendanceRecord>>

    @Query("""
        SELECT r.* FROM attendance_records r
        INNER JOIN attendance_sessions s ON r.session_id = s.session_id
        WHERE s.class_id = :classId AND r.student_id = :studentId
    """)
    fun getRecordsForStudentInClass(classId: String, studentId: String): Flow<List<AttendanceRecord>>

    @Query("""
        SELECT r.* FROM attendance_records r
        INNER JOIN attendance_sessions s ON r.session_id = s.session_id
        WHERE s.class_id = :classId AND r.student_id = :studentId
    """)
    fun getRecordsListForStudentInClass(classId: String, studentId: String): List<AttendanceRecord>

    @Query("""
        SELECT COUNT(*) 
        FROM attendance_sessions s 
        INNER JOIN classes c ON s.class_id = c.class_id 
        WHERE c.teacher_id = :teacherId AND s.session_status = 'COMPLETED'
    """)
    fun getTotalLecturesForTeacher(teacherId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM attendance_sessions WHERE class_id = :classId")
    fun getSessionCountForClass(classId: String): Int

    @Query("DELETE FROM attendance_records")
    fun deleteAllAttendanceRecords(): Int

    @Query("DELETE FROM attendance_sessions")
    fun deleteAllAttendanceSessions(): Int

    @Query("DELETE FROM attendance_records WHERE session_id IN (SELECT session_id FROM attendance_sessions WHERE class_id = :classId)")
    fun deleteRecordsForClass(classId: String): Int

    @Query("DELETE FROM attendance_sessions WHERE class_id = :classId")
    fun deleteSessionsForClass(classId: String): Int
}

data class AttendanceFeedItem(
    val sessionId: String,
    val classId: String,
    val className: String,
    val sessionDate: Long,
    val markedPresent: Int // 1: present, 0: absent
)


@Dao
interface RecognitionLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertLog(log: RecognitionLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertLogs(logs: List<RecognitionLog>): List<Long>

    /** All log rows for a session, sorted by face index then cosine score descending. */
    @Query("SELECT * FROM recognition_logs WHERE session_id = :sessionId ORDER BY detected_face_index ASC, confidence_score DESC")
    fun getLogsForSession(sessionId: String): List<RecognitionLog>

    /**
     * Filter by photo label ("PHOTO_1" or "PHOTO_2").
     * Use this to compare stranger scores per capture separately.
     */
    @Query("SELECT * FROM recognition_logs WHERE session_id = :sessionId AND photo_label = :label ORDER BY detected_face_index ASC, confidence_score DESC")
    fun getLogsForSessionByLabel(sessionId: String, label: String): List<RecognitionLog>

    @Query("DELETE FROM recognition_logs")
    fun deleteAllLogs(): Int
}
