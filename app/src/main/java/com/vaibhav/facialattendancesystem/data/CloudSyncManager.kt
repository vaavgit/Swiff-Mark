package com.vaibhav.facialattendancesystem.data

import android.content.Context
import android.util.Base64
import com.vaibhav.facialattendancesystem.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * CloudSyncManager - Optimized REST client for Supabase.
 */
object CloudSyncManager {

    @Volatile
    private var authToken: String? = null

    fun setAuthToken(token: String?) {
        authToken = token
    }

    private fun getHeaders(): Map<String, String> {
        val token = authToken?.ifBlank { null } ?: SupabaseConfig.SUPABASE_ANON_KEY
        return mapOf(
            "apikey" to SupabaseConfig.SUPABASE_ANON_KEY,
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json",
            "Prefer" to "return=representation"
        )
    }

    private fun executeRequest(
        endpoint: String,
        method: String,
        body: String? = null,
        extraHeaders: Map<String, String>? = null
    ): Pair<Int, String> {
        if (!SupabaseConfig.isConfigured) return Pair(400, "Supabase not configured")

        val fullUrl = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}$endpoint"
        val connection = URL(fullUrl).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 10000
        connection.readTimeout = 15000

        getHeaders().forEach { (k, v) -> connection.setRequestProperty(k, v) }
        extraHeaders?.forEach { (k, v) -> connection.setRequestProperty(k, v) }

        if (body != null && (method == "POST" || method == "PUT" || method == "PATCH")) {
            connection.doOutput = true
            OutputStreamWriter(connection.outputStream).use { it.write(body) }
        }

        val statusCode = try { connection.responseCode } catch (e: Exception) { 503 }
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.let {
            BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
        } ?: ""

        return Pair(statusCode, response)
    }

    suspend fun ensureCloudUserProfile(userId: String, email: String, fullName: String, role: String, rollOrSubject: String = "", sectionOrDept: String = ""): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || userId.isBlank()) return@withContext false
        try {
            val profileJson = JSONObject().apply {
                put("id", userId)
                put("email", email.trim())
                put("full_name", fullName.trim())
                put("role", role.uppercase())
            }
            executeRequest("/rest/v1/profiles", "POST", profileJson.toString(), mapOf("Prefer" to "resolution=merge-duplicates"))

            if (role.uppercase() == "STUDENT") {
                val studentJson = JSONObject().apply {
                    put("student_id", userId)
                    if (rollOrSubject.isNotBlank()) put("roll_number", rollOrSubject)
                    if (sectionOrDept.isNotBlank()) put("department", sectionOrDept)
                }
                executeRequest("/rest/v1/students", "POST", studentJson.toString(), mapOf("Prefer" to "resolution=merge-duplicates"))
            } else {
                val teacherJson = JSONObject().apply {
                    put("teacher_id", userId)
                    put("department", sectionOrDept.ifBlank { "CSE" })
                    put("designation", rollOrSubject.ifBlank { "Faculty" })
                }
                executeRequest("/rest/v1/teachers", "POST", teacherJson.toString(), mapOf("Prefer" to "resolution=merge-duplicates"))
            }
            true
        } catch (e: Exception) { false }
    }

    suspend fun syncClassRosterForTeacher(classId: String, db: AppDatabase): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Query class_enrollments to get all enrolled student IDs
            val query = "/rest/v1/class_enrollments?class_id=eq.$classId&select=student_id"
            val (status, response) = executeRequest(query, "GET")
            if (status !in 200..299) return@withContext Pair(false, "Cloud error ($status)")

            val enrollArray = JSONArray(response)
            if (enrollArray.length() == 0) {
                return@withContext Pair(true, "No students enrolled yet.")
            }

            val studentIds = mutableListOf<String>()
            for (i in 0 until enrollArray.length()) {
                val sId = enrollArray.getJSONObject(i).optString("student_id", "")
                if (sId.isNotBlank()) studentIds.add(sId)
            }
            if (studentIds.isEmpty()) return@withContext Pair(true, "No students enrolled yet.")

            val idsFilter = studentIds.joinToString(",")

            // Step 2: Fetch student profiles (name, email)
            val profilesMap = mutableMapOf<String, Pair<String, String>>()
            val (pStatus, pResponse) = executeRequest("/rest/v1/profiles?id=in.($idsFilter)&select=*", "GET")
            if (pStatus in 200..299) {
                val profilesArray = JSONArray(pResponse)
                for (i in 0 until profilesArray.length()) {
                    val p = profilesArray.getJSONObject(i)
                    val id = p.getString("id")
                    val name = p.optString("full_name", "Student")
                    val email = p.optString("email", "")
                    profilesMap[id] = Pair(name, email)
                }
            }

            // Step 3: Fetch student details (roll number & department) if available
            val studentMetaMap = mutableMapOf<String, Pair<Int, String>>()
            val (sStatus, sResponse) = executeRequest("/rest/v1/students?student_id=in.($idsFilter)&select=*", "GET")
            if (sStatus in 200..299) {
                val sArray = JSONArray(sResponse)
                for (i in 0 until sArray.length()) {
                    val s = sArray.getJSONObject(i)
                    val id = s.getString("student_id")
                    val roll = s.optString("roll_number", "0").toIntOrNull() ?: 0
                    val dept = s.optString("department", "")
                    studentMetaMap[id] = Pair(roll, dept)
                }
            }

            // Step 4: Fetch face embeddings for biometrics
            val embeddingsMap = mutableMapOf<String, MutableList<Pair<String, String>>>()
            val (eStatus, eResponse) = executeRequest("/rest/v1/face_embeddings?student_id=in.($idsFilter)&select=*", "GET")
            if (eStatus in 200..299) {
                val eArray = JSONArray(eResponse)
                for (i in 0 until eArray.length()) {
                    val e = eArray.getJSONObject(i)
                    val sId = e.getString("student_id")
                    val base64 = e.optString("embedding_vector_base64", "")
                    val angle = e.optString("angle_label", "FRONTAL")
                    if (base64.isNotEmpty()) {
                        embeddingsMap.getOrPut(sId) { mutableListOf() }.add(Pair(base64, angle))
                    }
                }
            }

            // Step 5: Save all data to local Room database
            for (sId in studentIds) {
                val (fullName, email) = profilesMap[sId] ?: Pair("Student", "")
                val (rollNo, dept) = studentMetaMap[sId] ?: Pair(0, "")

                if (db.userDao().getUserById(sId) == null) {
                    db.userDao().insertUser(User(userId = sId, email = email, passwordHash = "", userType = "STUDENT", name = fullName))
                }
                db.studentDao().insertStudent(Student(studentId = sId, userId = sId, fullName = fullName, rollNumber = rollNo, email = email, classSection = dept, enrollmentStatus = 1))
                db.classEnrollmentDao().enrollStudent(ClassEnrollment(classId = classId, studentId = sId, enrollmentVerified = 1))

                val embs = embeddingsMap[sId]
                if (embs != null && embs.isNotEmpty()) {
                    db.faceEmbeddingDao().deleteEmbeddingsForStudent(sId)
                    for ((base64, angle) in embs) {
                        try {
                            db.faceEmbeddingDao().insertEmbedding(FaceEmbedding(
                                studentId = sId,
                                embeddingVector = Base64.decode(base64, Base64.DEFAULT),
                                sourceAngle = angle,
                                qualityScore = 1.0f
                            ))
                        } catch (ex: Exception) { ex.printStackTrace() }
                    }
                }
            }
            Pair(true, "Synced ${studentIds.size} students.")
        } catch (e: Exception) { Pair(false, e.localizedMessage ?: "Sync Error") }
    }

    suspend fun syncTeacherAllClassesAndRosters(
        teacherId: String,
        db: AppDatabase,
        sessionManager: com.vaibhav.facialattendancesystem.util.SessionManager? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val (status, response) = executeRequest("/rest/v1/classes?teacher_id=eq.$teacherId&select=*&order=created_at.desc", "GET")
            if (status !in 200..299) return@withContext Pair(false, "Error $status")
            val deletedClassIds = sessionManager?.getDeletedClassIds(teacherId) ?: emptySet()
            val classesArray = JSONArray(response)
            val cloudClassIds = mutableSetOf<String>()
            for (i in 0 until classesArray.length()) {
                val c = classesArray.getJSONObject(i)
                val classId = c.getString("class_id")
                if (deletedClassIds.contains(classId)) {
                    // Do not restore classes deleted by teacher
                    db.clazzDao().deleteClassById(classId)
                    continue
                }
                cloudClassIds.add(classId)
                val existing = db.clazzDao().getClassById(classId)
                val createdAt = existing?.createdAt ?: run {
                    val rawCreatedAt = c.optString("created_at", "")
                    if (rawCreatedAt.isNotEmpty()) {
                        try {
                            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(rawCreatedAt)?.time ?: System.currentTimeMillis()
                        } catch (e: Exception) { System.currentTimeMillis() }
                    } else System.currentTimeMillis()
                }
                db.clazzDao().insertClass(Clazz(
                    classId = classId, teacherId = teacherId, className = c.getString("class_name"),
                    subject = c.optString("subject_code", ""), semester = c.optInt("semester", 1),
                    section = c.optString("department", ""), classCode = c.optString("join_code", ""),
                    createdAt = createdAt
                ))
                syncClassRosterForTeacher(classId, db)
            }
            Pair(true, "Synced ${classesArray.length()} classes.")
        } catch (e: Exception) { Pair(false, e.localizedMessage ?: "Error") }
    }

    suspend fun uploadAttendanceSession(session: AttendanceSession, records: List<AttendanceRecord>, teacherId: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(session.sessionDate))
            val sessionJson = JSONObject().apply {
                put("session_id", session.sessionId); put("class_id", session.classId); put("teacher_id", teacherId); put("session_date", dateStr)
                put("photo1_faces_detected", session.photo1FacesDetected); put("photo2_faces_detected", session.photo2FacesDetected); put("session_status", session.sessionStatus)
            }
            executeRequest("/rest/v1/attendance_sessions", "POST", sessionJson.toString())
            
            val recordsArray = JSONArray()
            for (r in records) {
                recordsArray.put(JSONObject().apply {
                    put("session_id", r.sessionId); put("student_id", r.studentId); put("marked_present", r.markedPresent == 1)
                })
            }
            executeRequest("/rest/v1/attendance_records", "POST", recordsArray.toString(), mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))
            true
        } catch (e: Exception) { false }
    }

    suspend fun syncStudentAttendance(studentId: String, db: AppDatabase, context: Context? = null, notify: Boolean = false): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val query = "/rest/v1/attendance_records?student_id=eq.$studentId&select=*,attendance_sessions(*,classes(*))"
            val (status, response) = executeRequest(query, "GET")
            if (status !in 200..299) return@withContext Pair(false, "Sync error ($status)")

            val records = JSONArray(response)
            var newCount = 0
            var latestClassId = ""
            var latestClassName = ""
            var latestDateStr = ""
            var latestIsPresent = false

            for (i in 0 until records.length()) {
                val recObj = records.getJSONObject(i)
                val sessObj = recObj.optJSONObject("attendance_sessions") ?: continue
                val classObj = sessObj.optJSONObject("classes") ?: continue
                
                val classId = sessObj.getString("class_id")
                val className = classObj.optString("class_name", "Class")
                if (db.clazzDao().getClassById(classId) == null) {
                    db.clazzDao().insertClass(Clazz(
                        classId = classId, teacherId = classObj.optString("teacher_id", ""),
                        className = className, subject = classObj.optString("subject_code", ""),
                        semester = 1, section = "", classCode = ""
                    ))
                }

                val sessionId = sessObj.getString("session_id")
                // Parse the real session date from cloud; prefer created_at for accurate time
                val rawCreatedAt = sessObj.optString("created_at", "")
                val rawDate = sessObj.optString("session_date", "")
                val dateToParse = rawCreatedAt.ifBlank { rawDate }
                val parsedDate: Long = if (dateToParse.isNotEmpty()) {
                    try {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                        sdf.parse(dateToParse)?.time ?: System.currentTimeMillis()
                    } catch (e1: Exception) {
                        try {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            sdf.parse(dateToParse)?.time ?: System.currentTimeMillis()
                        } catch (e2: Exception) {
                            System.currentTimeMillis()
                        }
                    }
                } else System.currentTimeMillis()

                val isPresent = recObj.optBoolean("marked_present", false)

                if (db.attendanceDao().getSessionById(sessionId) == null) {
                    db.attendanceDao().insertSession(AttendanceSession(
                        sessionId = sessionId, classId = classId,
                        sessionDate = parsedDate,
                        photo1Path = "", photo1FacesDetected = 0
                    ))
                    newCount++
                    latestClassId = classId
                    latestClassName = className
                    latestIsPresent = isPresent
                    latestDateStr = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(parsedDate))
                }

                db.attendanceDao().insertAttendanceRecords(listOf(AttendanceRecord(
                    sessionId = sessionId, studentId = studentId, markedPresent = if (isPresent) 1 else 0
                )))
            }
            if (newCount > 0 && notify && context != null && latestClassId.isNotBlank()) {
                val recordsInClass = db.attendanceDao().getRecordsListForStudentInClass(latestClassId, studentId)
                val totalLectures = db.attendanceDao().getSessionsListForClass(latestClassId).size
                val attendedLectures = recordsInClass.count { it.markedPresent == 1 }
                val pct = if (totalLectures > 0) ((attendedLectures.toFloat() / totalLectures) * 100).toInt() else 100

                val title = if (latestIsPresent) {
                    "✓ Attendance: PRESENT in $latestClassName"
                } else {
                    "✕ Attendance: ABSENT in $latestClassName"
                }

                val message = if (newCount == 1) {
                    "Lecture on $latestDateStr recorded.\nYour attendance is now $pct% ($attendedLectures/$totalLectures lectures attended)."
                } else {
                    "$newCount new lecture attendance records published.\nYour attendance in $latestClassName is now $pct% ($attendedLectures/$totalLectures lectures)."
                }

                NotificationHelper.showAttendanceNotification(
                    context = context,
                    title = title,
                    message = message,
                    classId = latestClassId,
                    className = latestClassName
                )
            }
            Pair(true, "Synced $newCount updates.")
        } catch (e: Exception) { Pair(false, e.localizedMessage ?: "Sync Error") }
    }

    suspend fun syncStudentClassesAndEnrollments(
        studentId: String,
        db: AppDatabase,
        sessionManager: com.vaibhav.facialattendancesystem.util.SessionManager? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val query = "/rest/v1/class_enrollments?student_id=eq.$studentId&select=class_id,classes(*)"
            val (status, response) = executeRequest(query, "GET")
            if (status !in 200..299) return@withContext Pair(false, "Sync error ($status)")

            val leftClassIds = sessionManager?.getLeftClassIds(studentId) ?: emptySet()
            val array = JSONArray(response)
            val cloudClassIds = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val classId = item.getString("class_id")
                if (leftClassIds.contains(classId)) {
                    // Student has explicitly left this class, unenroll locally
                    db.classEnrollmentDao().unenrollStudent(classId, studentId)
                    continue
                }
                cloudClassIds.add(classId)
                val classObj = item.optJSONObject("classes")
                if (classObj != null) {
                    val existing = db.clazzDao().getClassById(classId)
                    val createdAt = existing?.createdAt ?: System.currentTimeMillis()
                    db.clazzDao().insertClass(Clazz(
                        classId = classId,
                        teacherId = classObj.optString("teacher_id", ""),
                        className = classObj.optString("class_name", "Class"),
                        subject = classObj.optString("subject_code", ""),
                        semester = classObj.optInt("semester", 1),
                        section = classObj.optString("department", ""),
                        classCode = classObj.optString("join_code", ""),
                        createdAt = createdAt
                    ))
                }
                db.classEnrollmentDao().enrollStudent(ClassEnrollment(classId = classId, studentId = studentId, enrollmentVerified = 1))
            }
            Pair(true, "Synced ${cloudClassIds.size} enrolled classes.")
        } catch (e: Exception) { Pair(false, e.localizedMessage ?: "Sync Error") }
    }

    suspend fun purgeCloudClassAndAttendanceData(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val zeroUuid = "00000000-0000-0000-0000-000000000000"
            executeRequest("/rest/v1/attendance_records?session_id=neq.$zeroUuid", "DELETE")
            executeRequest("/rest/v1/attendance_sessions?session_id=neq.$zeroUuid", "DELETE")
            executeRequest("/rest/v1/class_enrollments?class_id=neq.$zeroUuid", "DELETE")
            executeRequest("/rest/v1/classes?class_id=neq.$zeroUuid", "DELETE")
            Pair(true, "Cloud classes and attendance data purged.")
        } catch (e: Exception) {
            Pair(false, e.localizedMessage ?: "Purge Error")
        }
    }

    suspend fun joinClassByCode(
        code: String,
        studentId: String,
        db: AppDatabase?,
        sessionManager: com.vaibhav.facialattendancesystem.util.SessionManager? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val token = sessionManager?.getAccessToken()
            if (!token.isNullOrBlank() && authToken.isNullOrBlank()) {
                setAuthToken(token)
            }
            val cleanCode = code.trim().uppercase()
            val (status, response) = executeRequest("/rest/v1/classes?join_code=ilike.$cleanCode&select=*", "GET")
            val array = if (status in 200..299) JSONArray(response) else JSONArray()

            val newClazz: Clazz = if (array.length() > 0) {
                val classObj = array.getJSONObject(0)
                Clazz(
                    classId = classObj.getString("class_id"),
                    teacherId = classObj.optString("teacher_id", ""),
                    className = classObj.getString("class_name"),
                    subject = classObj.optString("subject_code", ""),
                    semester = classObj.optInt("semester", 1),
                    section = classObj.optString("department", ""),
                    classCode = cleanCode
                )
            } else {
                // Fallback: check local Room database for matching class code
                val localClass = db?.clazzDao()?.getClassByCode(cleanCode)
                    ?: db?.clazzDao()?.getClassByCode(code.trim())
                if (localClass != null) {
                    localClass
                } else {
                    return@withContext Pair(false, "Invalid Class Code. Check with your teacher.")
                }
            }

            // Check if already enrolled (approved or pending)
            val existingIds = db?.classEnrollmentDao()?.getEnrolledStudentIds(newClazz.classId) ?: emptyList()
            if (existingIds.contains(studentId)) {
                val existing = db?.classEnrollmentDao()?.getPendingEnrollmentsForClass(newClazz.classId)
                    ?.any { it.studentId == studentId } ?: false
                return@withContext if (existing) {
                    Pair(false, "⏳ Your join request for \"${newClazz.className}\" is already pending teacher approval.")
                } else {
                    Pair(false, "You are already enrolled in \"${newClazz.className}\".")
                }
            }

            // Re-allow class if previously left
            sessionManager?.unmarkClassLeft(studentId, newClazz.classId)

            // Insert into Supabase as PENDING (status = 'PENDING')
            val enrollmentJson = JSONObject().apply {
                put("class_id", newClazz.classId)
                put("student_id", studentId)
                put("enrollment_verified", 0)
            }
            executeRequest(
                "/rest/v1/class_enrollments?on_conflict=class_id,student_id",
                "POST",
                enrollmentJson.toString(),
                mapOf("Prefer" to "resolution=ignore-duplicates")
            )

            // Save class locally so student can see it (as pending)
            db?.clazzDao()?.insertClass(newClazz)
            // enrollmentVerified = 0 → PENDING, teacher must approve
            db?.classEnrollmentDao()?.enrollStudent(
                ClassEnrollment(classId = newClazz.classId, studentId = studentId, enrollmentVerified = 0)
            )
            Pair(true, "📨 Join request sent for \"${newClazz.className}\". Waiting for teacher approval.")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.localizedMessage ?: "Join Error")
        }
    }

    /** Teacher approves a pending student join request */
    suspend fun approveEnrollment(
        classId: String,
        studentId: String,
        db: AppDatabase?
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // Update Supabase
            val patchBody = JSONObject().apply { put("enrollment_verified", 1) }.toString()
            val (status, _) = executeRequest(
                "/rest/v1/class_enrollments?class_id=eq.$classId&student_id=eq.$studentId",
                "PATCH", patchBody
            )
            // Update local Room
            db?.classEnrollmentDao()?.updateEnrollmentStatus(classId, studentId, 1)
            if (status in 200..299) Pair(true, "Student approved.")
            else Pair(false, "Cloud update failed, approved locally.")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.localizedMessage ?: "Approval error")
        }
    }

    /** Teacher rejects a pending student join request */
    suspend fun rejectEnrollment(
        classId: String,
        studentId: String,
        db: AppDatabase?
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // Remove from Supabase entirely
            executeRequest("/rest/v1/class_enrollments?class_id=eq.$classId&student_id=eq.$studentId", "DELETE")
            // Remove from local Room
            db?.classEnrollmentDao()?.unenrollStudent(classId, studentId)
            Pair(true, "Request rejected.")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.localizedMessage ?: "Rejection error")
        }
    }



    suspend fun leaveClass(
        classId: String,
        studentId: String,
        db: AppDatabase?,
        sessionManager: com.vaibhav.facialattendancesystem.util.SessionManager? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            sessionManager?.markClassLeft(studentId, classId)
            db?.classEnrollmentDao()?.unenrollStudent(classId, studentId)
            executeRequest("/rest/v1/class_enrollments?class_id=eq.$classId&student_id=eq.$studentId", "DELETE")
            Pair(true, "Left class successfully.")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.localizedMessage ?: "Error leaving class")
        }
    }

    suspend fun deleteClass(
        classId: String,
        teacherId: String,
        db: AppDatabase,
        sessionManager: com.vaibhav.facialattendancesystem.util.SessionManager? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            sessionManager?.markClassDeleted(teacherId, classId)

            // 1. Delete from local Room DB immediately
            db.attendanceDao().deleteRecordsForClass(classId)
            db.attendanceDao().deleteSessionsForClass(classId)
            db.classEnrollmentDao().deleteEnrollmentsForClass(classId)
            db.clazzDao().deleteClassById(classId)

            // 2. Delete from Supabase in background (clean cascade)
            val (sStatus, sResp) = executeRequest("/rest/v1/attendance_sessions?class_id=eq.$classId&select=session_id", "GET")
            if (sStatus in 200..299) {
                val sArray = JSONArray(sResp)
                val sIds = mutableListOf<String>()
                for (i in 0 until sArray.length()) {
                    val sId = sArray.getJSONObject(i).optString("session_id", "")
                    if (sId.isNotBlank()) sIds.add(sId)
                }
                if (sIds.isNotEmpty()) {
                    executeRequest("/rest/v1/attendance_records?session_id=in.(${sIds.joinToString(",")})", "DELETE")
                }
            }
            executeRequest("/rest/v1/attendance_sessions?class_id=eq.$classId", "DELETE")
            executeRequest("/rest/v1/class_enrollments?class_id=eq.$classId", "DELETE")
            executeRequest("/rest/v1/classes?class_id=eq.$classId", "DELETE")

            Pair(true, "Class deleted successfully.")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.localizedMessage ?: "Error deleting class")
        }
    }

    suspend fun uploadStudentProfile(student: Student, embeddings: List<FloatArray>, password: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureCloudUserProfile(student.studentId, student.email, student.fullName, "STUDENT", student.rollNumber.toString(), student.classSection ?: "")
            val converters = VectorTypeConverters()
            executeRequest("/rest/v1/face_embeddings?student_id=eq.${student.studentId}", "DELETE")
            for (emb in embeddings) {
                val base64 = Base64.encodeToString(converters.floatArrayToByteArray(emb), Base64.NO_WRAP)
                executeRequest("/rest/v1/face_embeddings", "POST", JSONObject().apply {
                    put("student_id", student.studentId); put("embedding_vector_base64", base64); put("angle_label", "FRONTAL")
                }.toString())
            }
            true
        } catch (e: Exception) { false }
    }

    suspend fun uploadCalibrationSamples(studentId: String, samples: List<Pair<String, android.graphics.Bitmap>>): Boolean = withContext(Dispatchers.IO) {
        try {
            val path = com.vaibhav.facialattendancesystem.BuildConfig.DIAGNOSTIC_SYNC_PATH
            if (path.isNullOrBlank()) return@withContext true
            for ((angle, bitmap) in samples) {
                val outputStream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
                val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                executeRequest("/rest/v1/$path", "POST", JSONObject().apply {
                    put("student_id", studentId); put("angle_label", angle); put("image_base64", "data:image/jpeg;base64,$base64")
                }.toString())
            }
            true
        } catch (e: Exception) { false }
    }

    suspend fun uploadNewClass(clazz: Clazz): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("class_id", clazz.classId)
                put("teacher_id", clazz.teacherId)
                put("class_name", clazz.className)
                put("subject_code", clazz.subject)
                put("join_code", clazz.classCode)
                put("department", clazz.section.ifBlank { "General" })
                put("semester", if (clazz.semester > 0) clazz.semester else 1)
            }
            val (status, _) = executeRequest("/rest/v1/classes", "POST", json.toString())
            status in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun syncStudentFaceEmbeddings(studentId: String, db: AppDatabase): Boolean = withContext(Dispatchers.IO) {
        try {
            val (code, response) = executeRequest("/rest/v1/face_embeddings?student_id=eq.$studentId&select=*", "GET")
            val array = JSONArray(response)
            if (code !in 200..299 || array.length() == 0) return@withContext false
            db.faceEmbeddingDao().deleteEmbeddingsForStudent(studentId)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                db.faceEmbeddingDao().insertEmbedding(FaceEmbedding(
                    studentId = studentId, embeddingVector = Base64.decode(obj.getString("embedding_vector_base64"), Base64.DEFAULT),
                    sourceAngle = obj.optString("angle_label", "FRONTAL"), qualityScore = 1.0f
                ))
            }
            true
        } catch (e: Exception) { false }
    }

    suspend fun syncClassAttendanceSessionsForTeacher(classId: String, db: AppDatabase): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val (status, response) = executeRequest("/rest/v1/attendance_sessions?class_id=eq.$classId&select=*", "GET")
            val array = JSONArray(response)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                db.attendanceDao().insertSession(AttendanceSession(
                    sessionId = obj.getString("session_id"), classId = classId, photo1Path = "", photo1FacesDetected = 0
                ))
            }
            Pair(true, "Synced ${array.length()} sessions.")
        } catch (e: Exception) { Pair(false, "Sync Error") }
    }

    // ─── Profile Avatar Cloud Sync ────────────────────────────────────────────────

    /**
     * Upload local avatar file to Supabase.
     * Dual-strategy:
     * 1. Updates `public.profiles.avatar_url` with compressed Base64 data URI (works immediately without storage bucket setup).
     * 2. Also attempts to upload directly to Supabase Storage `avatars` bucket if available.
     * Returns true on success.
     */
    suspend fun uploadProfileAvatar(context: android.content.Context, userId: String): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || userId.isBlank()) return@withContext false
        try {
            val file = java.io.File(context.filesDir, "avatar_${userId}.jpg")
            if (!file.exists() || file.length() == 0L) return@withContext false

            val bytes = file.readBytes()
            var anySuccess = false

            // Strategy 1: Save compact Base64 in profiles table (guaranteed to work with existing table policies)
            try {
                // Ensure image is <= 256x256 for compact database storage
                val originalBmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (originalBmp != null) {
                    val maxDim = 256
                    val scaled = if (originalBmp.width > maxDim || originalBmp.height > maxDim) {
                        val ratio = kotlin.math.max(originalBmp.width.toFloat() / maxDim, originalBmp.height.toFloat() / maxDim)
                        val w = (originalBmp.width / ratio).toInt().coerceAtLeast(1)
                        val h = (originalBmp.height / ratio).toInt().coerceAtLeast(1)
                        android.graphics.Bitmap.createScaledBitmap(originalBmp, w, h, true)
                    } else {
                        originalBmp
                    }
                    val stream = java.io.ByteArrayOutputStream()
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, stream)
                    val compactBytes = stream.toByteArray()
                    if (scaled != originalBmp) scaled.recycle()
                    originalBmp.recycle()

                    val base64Data = "data:image/jpeg;base64," + Base64.encodeToString(compactBytes, Base64.NO_WRAP)
                    val profileJson = JSONObject().apply {
                        put("avatar_url", base64Data)
                    }
                    val (pStatus, _) = executeRequest("/rest/v1/profiles?id=eq.$userId", "PATCH", profileJson.toString())
                    if (pStatus in 200..299) {
                        anySuccess = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Strategy 2: Attempt direct upload to Supabase Storage avatars bucket
            try {
                val token = authToken?.ifBlank { null } ?: SupabaseConfig.SUPABASE_ANON_KEY
                val storageUrl = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/avatars/${userId}.jpg"

                val connection = java.net.URL(storageUrl).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 12000
                connection.readTimeout = 15000
                connection.setRequestProperty("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "image/jpeg")
                connection.setRequestProperty("x-upsert", "true")
                connection.doOutput = true
                connection.outputStream.use { it.write(bytes) }

                val statusCode = try { connection.responseCode } catch (e: Exception) { 503 }
                if (statusCode in 200..299) {
                    anySuccess = true
                }
            } catch (e: Exception) {
                // Storage bucket might not be configured, fallback to Strategy 1 is sufficient
            }

            anySuccess
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Download another user's avatar from Supabase and cache it locally
     * using the same path convention as ProfileImageHelper: filesDir/avatar_<userId>.jpg
     * Returns true if downloaded (or already fresh locally).
     */
    suspend fun downloadAndCacheProfileAvatar(context: android.content.Context, userId: String, forceRefresh: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || userId.isBlank()) return@withContext false
        val targetFile = java.io.File(context.filesDir, "avatar_${userId}.jpg")
        if (!forceRefresh && targetFile.exists() && targetFile.length() > 0L) {
            return@withContext true
        }

        try {
            // Strategy 1: Check profiles.avatar_url
            val (status, response) = executeRequest("/rest/v1/profiles?id=eq.$userId&select=avatar_url", "GET")
            if (status in 200..299 && response.isNotBlank()) {
                val array = JSONArray(response)
                if (array.length() > 0) {
                    val avatarUrl = array.getJSONObject(0).optString("avatar_url", "")
                    if (avatarUrl.startsWith("data:image/") && avatarUrl.contains("base64,")) {
                        val base64Data = avatarUrl.substringAfter("base64,")
                        val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                        if (decodedBytes.isNotEmpty()) {
                            targetFile.writeBytes(decodedBytes)
                            return@withContext true
                        }
                    } else if (avatarUrl.startsWith("http")) {
                        val connection = java.net.URL(avatarUrl).openConnection() as java.net.HttpURLConnection
                        connection.connectTimeout = 10000
                        connection.readTimeout = 12000
                        val bytes = connection.inputStream.use { it.readBytes() }
                        if (bytes.isNotEmpty()) {
                            targetFile.writeBytes(bytes)
                            return@withContext true
                        }
                    }
                }
            }

            // Strategy 2: Check Supabase Storage avatars bucket
            val token = authToken?.ifBlank { null } ?: SupabaseConfig.SUPABASE_ANON_KEY
            val storageUrl = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/avatars/${userId}.jpg"

            val connection = java.net.URL(storageUrl).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 12000
            connection.setRequestProperty("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $token")

            val statusCode = try { connection.responseCode } catch (e: Exception) { 503 }
            if (statusCode in 200..299) {
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.isNotEmpty()) {
                    targetFile.writeBytes(bytes)
                    return@withContext true
                }
            }

            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Delete user's avatar both locally and from Supabase cloud.
     */
    suspend fun deleteProfileAvatar(context: android.content.Context, userId: String): Boolean = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext false
        try {
            // Delete local file
            val targetFile = java.io.File(context.filesDir, "avatar_${userId}.jpg")
            if (targetFile.exists()) {
                targetFile.delete()
            }

            if (!SupabaseConfig.isConfigured) return@withContext true

            // Clear from profiles table
            val profileJson = JSONObject().apply {
                put("avatar_url", JSONObject.NULL)
            }
            executeRequest("/rest/v1/profiles?id=eq.$userId", "PATCH", profileJson.toString())

            // Attempt deletion from storage bucket
            try {
                val token = authToken?.ifBlank { null } ?: SupabaseConfig.SUPABASE_ANON_KEY
                val storageUrl = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/avatars/${userId}.jpg"
                val connection = java.net.URL(storageUrl).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "DELETE"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.responseCode
            } catch (e: Exception) {
                // Ignore storage deletion errors
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
