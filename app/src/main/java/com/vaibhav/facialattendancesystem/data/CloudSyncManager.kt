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

    @Volatile
    private var sessionManagerRef: com.vaibhav.facialattendancesystem.util.SessionManager? = null

    @Volatile
    private var dbRef: AppDatabase? = null

    @Volatile
    private var contextRef: android.content.Context? = null

    fun initSessionManager(
        sm: com.vaibhav.facialattendancesystem.util.SessionManager,
        db: AppDatabase? = null,
        context: android.content.Context? = null
    ) {
        sessionManagerRef = sm
        if (db != null) dbRef = db
        if (context != null) contextRef = context.applicationContext
    }

    private fun saveBase64AvatarToLocal(userId: String, avatarUrl: String): Boolean {
        val ctx = contextRef ?: return false
        if (userId.isBlank() || !avatarUrl.startsWith("data:image/") || !avatarUrl.contains("base64,")) return false
        return try {
            val base64Data = avatarUrl.substringAfter("base64,")
            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
            if (decodedBytes.isNotEmpty()) {
                val targetFile = java.io.File(ctx.filesDir, "avatar_${userId}.jpg")
                targetFile.writeBytes(decodedBytes)
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    fun setAuthToken(token: String?) {
        authToken = token
    }

    private fun getHeaders(overrideToken: String? = null): Map<String, String> {
        val liveToken = overrideToken?.ifBlank { null }
            ?: sessionManagerRef?.getAccessToken()?.ifBlank { null }
            ?: authToken?.ifBlank { null }
            ?: SupabaseConfig.SUPABASE_ANON_KEY
        if (!overrideToken.isNullOrBlank()) {
            authToken = overrideToken
        }
        return mapOf(
            "apikey" to SupabaseConfig.SUPABASE_ANON_KEY,
            "Authorization" to "Bearer $liveToken",
            "Content-Type" to "application/json",
            "Prefer" to "return=representation"
        )
    }

    /**
     * Attempts to silently refresh an expired JWT (401) using:
     * 1. Saved refresh_token via GoTrue (/auth/v1/token?grant_type=refresh_token)
     * 2. Saved login credentials in SessionManager
     * 3. Cached passwordHash in local Room DB (users table)
     * Returns the new access_token if successful, or null if recovery was not possible.
     */
    @Synchronized
    private fun recoverFrom401(): String? {
        val sm = sessionManagerRef ?: return null
        val session = sm.getSession()

        // 1. Try refresh_token first
        val refreshTok = sm.getRefreshToken()
        if (!refreshTok.isNullOrBlank()) {
            val refreshed = SupabaseAuthManager.refreshSessionBlocking(refreshTok)
            if (refreshed != null) {
                sm.updateTokens(refreshed.first, refreshed.second)
                authToken = refreshed.first
                return refreshed.first
            }
        }

        // 2. Try saved login credentials in SessionManager
        val savedEmail = sm.getSavedEmail()
        val savedPass = sm.getSavedPassword()
        if (savedEmail.isNotBlank() && savedPass.isNotBlank()) {
            val reSigned = SupabaseAuthManager.signInBlocking(savedEmail, savedPass)
            if (reSigned != null) {
                sm.updateTokens(reSigned.first, reSigned.second)
                authToken = reSigned.first
                return reSigned.first
            }
        }

        // 3. Cannot use Room DB passwordHash for Supabase re-auth — it is SHA-256 hashed (for local
        //    offline login only). Supabase requires the original plaintext password which we do NOT
        //    store. Steps 1 (refresh_token) and 2 (saved credentials in SharedPrefs) are the only
        //    recovery paths.

        // 4. Clear expired token so fallback to SUPABASE_ANON_KEY can be attempted
        sm.updateAccessToken(null)
        authToken = null
        return null
    }

    private fun performHttpCall(
        endpoint: String,
        method: String,
        body: String? = null,
        extraHeaders: Map<String, String>? = null,
        overrideToken: String? = null
    ): Pair<Int, String> {
        val fullUrl = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}$endpoint"
        val connection = URL(fullUrl).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 10000
        connection.readTimeout = 15000

        getHeaders(overrideToken).forEach { (k, v) -> connection.setRequestProperty(k, v) }
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

    private fun executeRequest(
        endpoint: String,
        method: String,
        body: String? = null,
        extraHeaders: Map<String, String>? = null
    ): Pair<Int, String> {
        if (!SupabaseConfig.isConfigured) return Pair(400, "Supabase not configured")

        val (statusCode, response) = performHttpCall(endpoint, method, body, extraHeaders)

        // If 401 Unauthorized (e.g., JWT expired after 1 hour), automatically refresh token / re-authenticate and retry once!
        if (statusCode == 401 || (statusCode == 403 && response.contains("JWT", ignoreCase = true))) {
            val freshToken = recoverFrom401()
            return performHttpCall(
                endpoint = endpoint,
                method = method,
                body = body,
                extraHeaders = extraHeaders,
                overrideToken = freshToken ?: SupabaseConfig.SUPABASE_ANON_KEY
            )
        }

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
            executeRequest("/rest/v1/profiles?on_conflict=id", "POST", profileJson.toString(), mapOf("Prefer" to "resolution=merge-duplicates"))

            if (role.uppercase() == "STUDENT") {
                val studentJson = JSONObject().apply {
                    put("student_id", userId)
                    if (rollOrSubject.isNotBlank()) put("roll_number", rollOrSubject)
                    if (sectionOrDept.isNotBlank()) put("department", sectionOrDept)
                }
                executeRequest("/rest/v1/students?on_conflict=student_id", "POST", studentJson.toString(), mapOf("Prefer" to "resolution=merge-duplicates"))
            } else {
                val teacherJson = JSONObject().apply {
                    put("teacher_id", userId)
                    put("department", sectionOrDept.ifBlank { "CSE" })
                    put("designation", rollOrSubject.ifBlank { "Faculty" })
                }
                executeRequest("/rest/v1/teachers?on_conflict=teacher_id", "POST", teacherJson.toString(), mapOf("Prefer" to "resolution=merge-duplicates"))
            }
            true
        } catch (e: Exception) { false }
    }

    suspend fun syncClassRosterForTeacher(classId: String, db: AppDatabase): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Query class_enrollments to get all enrolled student IDs
            val query = "/rest/v1/class_enrollments?class_id=eq.$classId&select=*"
            val (status, response) = executeRequest(query, "GET")
            if (status !in 200..299) return@withContext Pair(false, "Cloud error ($status)")

            val enrollArray = JSONArray(response)
            if (enrollArray.length() == 0) {
                return@withContext Pair(true, "No students enrolled yet.")
            }

            val studentIds = mutableListOf<String>()
            val verifiedStatusMap = mutableMapOf<String, Int>()
            for (i in 0 until enrollArray.length()) {
                val obj = enrollArray.getJSONObject(i)
                val sId = obj.optString("student_id", "")
                if (sId.isNotBlank()) {
                    studentIds.add(sId)
                    verifiedStatusMap[sId] = obj.optInt("enrollment_verified", 1)
                }
            }
            if (studentIds.isEmpty()) return@withContext Pair(true, "No students enrolled yet.")

            val idsFilter = studentIds.joinToString(",")

            // Step 2: Fetch student profiles (name, email, avatar_url)
            val profilesMap = mutableMapOf<String, Pair<String, String>>()
            val (pStatus, pResponse) = executeRequest("/rest/v1/profiles?id=in.($idsFilter)&select=*", "GET")
            if (pStatus in 200..299) {
                val profilesArray = JSONArray(pResponse)
                for (i in 0 until profilesArray.length()) {
                    val p = profilesArray.getJSONObject(i)
                    val id = p.getString("id")
                    val name = p.optString("full_name", "Student")
                    val email = p.optString("email", "")
                    val avatarUrl = p.optString("avatar_url", "")
                    profilesMap[id] = Pair(name, email)
                    if (avatarUrl.isNotBlank()) {
                        saveBase64AvatarToLocal(id, avatarUrl)
                    }
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

            // Step 5: Save all data to local Room database + ensure student avatars are cached
            val ctx = contextRef
            for (sId in studentIds) {
                val (fullName, email) = profilesMap[sId] ?: Pair("Student", "")
                val (rollNo, dept) = studentMetaMap[sId] ?: Pair(0, "")
                val verified = verifiedStatusMap[sId] ?: 1

                if (db.userDao().getUserById(sId) == null) {
                    db.userDao().insertUser(User(userId = sId, email = email, passwordHash = "", userType = "STUDENT", name = fullName))
                }
                db.studentDao().insertStudent(Student(studentId = sId, userId = sId, fullName = fullName, rollNumber = rollNo, email = email, classSection = dept, enrollmentStatus = 1))
                db.classEnrollmentDao().enrollStudent(ClassEnrollment(classId = classId, studentId = sId, enrollmentVerified = verified))
                db.classEnrollmentDao().updateEnrollmentStatus(classId, sId, verified)

                if (ctx != null) {
                    val avatarFile = java.io.File(ctx.filesDir, "avatar_${sId}.jpg")
                    if (!avatarFile.exists() || avatarFile.length() == 0L) {
                        downloadAndCacheProfileAvatar(ctx, sId)
                    }
                }

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
            if (sessionManager != null) initSessionManager(sessionManager, db)
            val deletedClassIds = sessionManager?.getDeletedClassIds(teacherId) ?: emptySet()

            // Step 1: Ensure teacher row exists in Supabase profiles & teachers tables
            val localUser = db.userDao().getUserById(teacherId)
            val localTeacher = db.teacherDao().getTeacherById(teacherId)
            val sess = sessionManager?.getSession()
            ensureCloudUserProfile(
                userId = teacherId,
                email = localUser?.email ?: sess?.userEmail ?: "",
                fullName = localUser?.name ?: sess?.userName ?: "Teacher",
                role = "TEACHER",
                rollOrSubject = localTeacher?.subject ?: "Faculty",
                sectionOrDept = localTeacher?.department ?: "CSE"
            )

            // Sync teacher's own profile avatar (upload if local exists, else download from cloud)
            contextRef?.let { ctx ->
                val localAvatar = java.io.File(ctx.filesDir, "avatar_${teacherId}.jpg")
                if (localAvatar.exists() && localAvatar.length() > 0L) {
                    uploadProfileAvatar(ctx, teacherId)
                } else {
                    downloadAndCacheProfileAvatar(ctx, teacherId)
                }
            }

            // Step 2: Push any local classes created on this device that haven't been synced to cloud yet
            val localClasses = db.clazzDao().getClassesListForTeacher(teacherId)
            for (localClazz in localClasses) {
                if (!deletedClassIds.contains(localClazz.classId)) {
                    uploadNewClass(localClazz)
                }
            }

            // Step 3: Pull all teacher classes from Supabase
            val (status, response) = executeRequest("/rest/v1/classes?teacher_id=eq.$teacherId&select=*&order=created_at.desc", "GET")
            if (status !in 200..299) return@withContext Pair(false, "Cloud sync status $status")
            val classesArray = JSONArray(response)
            val cloudClassIds = mutableSetOf<String>()
            for (i in 0 until classesArray.length()) {
                val c = classesArray.getJSONObject(i)
                val classId = c.getString("class_id")
                if (deletedClassIds.contains(classId)) {
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
                syncClassAttendanceSessionsForTeacher(classId, db)
            }
            val totalSynced = maxOf(classesArray.length(), localClasses.count { !deletedClassIds.contains(it.classId) })
            Pair(true, "Synced $totalSynced classes.")
        } catch (e: Exception) { Pair(false, e.localizedMessage ?: "Error") }
    }

    suspend fun uploadAttendanceSession(session: AttendanceSession, records: List<AttendanceRecord>, teacherId: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(session.sessionDate))
            val sessionJson = JSONObject().apply {
                put("session_id", session.sessionId); put("class_id", session.classId); put("teacher_id", teacherId); put("session_date", dateStr)
                put("photo1_faces_detected", session.photo1FacesDetected); put("photo2_faces_detected", session.photo2FacesDetected); put("session_status", session.sessionStatus)
            }
            executeRequest("/rest/v1/attendance_sessions?on_conflict=session_id", "POST", sessionJson.toString(),
                mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))
            
            val recordsArray = JSONArray()
            for (r in records) {
                recordsArray.put(JSONObject().apply {
                    put("session_id", r.sessionId); put("student_id", r.studentId); put("marked_present", r.markedPresent == 1)
                })
            }
            executeRequest("/rest/v1/attendance_records?on_conflict=session_id,student_id", "POST", recordsArray.toString(), mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))
            true
        } catch (e: Exception) { false }
    }

    suspend fun syncStudentAttendance(studentId: String, db: AppDatabase, context: Context? = null, notify: Boolean = false): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val query = "/rest/v1/attendance_records?student_id=eq.$studentId&select=*,attendance_sessions(*,classes(*))"
            val (status, response) = executeRequest(query, "GET")
            if (status !in 200..299) return@withContext Pair(false, "Sync error ($status)")

            val records = JSONArray(response)
            val sm = sessionManagerRef ?: context?.let { com.vaibhav.facialattendancesystem.util.SessionManager(it) }
            val isFirstNotificationInit = sm != null && !sm.hasInitializedNotifiedSessions(studentId)
            val alreadyNotifiedIds = sm?.getNotifiedSessionIds(studentId) ?: emptySet()
            val allSeenSessionIds = mutableSetOf<String>()

            var newCount = 0
            var latestClassId = ""
            var latestClassName = ""
            var latestDateStr = ""
            var latestIsPresent = false
            val nowMs = System.currentTimeMillis()

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
                allSeenSessionIds.add(sessionId)
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
                val wasMissingInLocalDb = db.attendanceDao().getSessionById(sessionId) == null

                if (wasMissingInLocalDb) {
                    db.attendanceDao().insertSession(AttendanceSession(
                        sessionId = sessionId, classId = classId,
                        sessionDate = parsedDate,
                        photo1Path = "", photo1FacesDetected = 0
                    ))
                }

                // Only trigger a notification if:
                // 1. It wasn't already notified in SharedPreferences
                // 2. It wasn't the very first baseline sync on this install
                // 3. It was missing in local DB and occurred within the last 24 hours
                val isRecentSession = (nowMs - parsedDate) <= 24L * 60L * 60L * 1000L
                if (wasMissingInLocalDb && !isFirstNotificationInit && !alreadyNotifiedIds.contains(sessionId) && isRecentSession) {
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

            // Persist all seen session IDs so none of them can ever notify again
            sm?.markSessionsNotified(studentId, allSeenSessionIds)

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
            if (sessionManager != null) initSessionManager(sessionManager, db)

            // Sync student's own profile & avatar (upload if local exists, else download from cloud)
            contextRef?.let { ctx ->
                val localAvatar = java.io.File(ctx.filesDir, "avatar_${studentId}.jpg")
                if (localAvatar.exists() && localAvatar.length() > 0L) {
                    uploadProfileAvatar(ctx, studentId)
                } else {
                    downloadAndCacheProfileAvatar(ctx, studentId)
                }
            }

            val query = "/rest/v1/class_enrollments?student_id=eq.$studentId&select=class_id,classes(*)"
            val (status, response) = executeRequest(query, "GET")
            if (status !in 200..299) return@withContext Pair(false, "Sync error ($status)")

            val leftClassIds = sessionManager?.getLeftClassIds(studentId) ?: emptySet()
            val array = JSONArray(response)
            val cloudClassIds = mutableSetOf<String>()
            val teacherIds = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val classId = item.getString("class_id")
                if (leftClassIds.contains(classId)) {
                    db.classEnrollmentDao().unenrollStudent(classId, studentId)
                    continue
                }
                cloudClassIds.add(classId)
                val classObj = item.optJSONObject("classes")
                if (classObj != null) {
                    val existing = db.clazzDao().getClassById(classId)
                    val createdAt = existing?.createdAt ?: System.currentTimeMillis()
                    val tId = classObj.optString("teacher_id", "")
                    if (tId.isNotBlank()) teacherIds.add(tId)
                    db.clazzDao().insertClass(Clazz(
                        classId = classId,
                        teacherId = tId,
                        className = classObj.optString("class_name", "Class"),
                        subject = classObj.optString("subject_code", ""),
                        semester = classObj.optInt("semester", 1),
                        section = classObj.optString("department", ""),
                        classCode = classObj.optString("join_code", ""),
                        createdAt = createdAt
                    ))
                }
                db.classEnrollmentDao().enrollStudent(ClassEnrollment(classId = classId, studentId = studentId, enrollmentVerified = 1))
                db.classEnrollmentDao().updateEnrollmentStatus(classId, studentId, 1)
            }

            // Fetch teacher profiles & avatars for all joined classes so student sees teacher name & photo
            if (teacherIds.isNotEmpty()) {
                val tIdsFilter = teacherIds.joinToString(",")
                val (tpStatus, tpResponse) = executeRequest("/rest/v1/profiles?id=in.($tIdsFilter)&select=*", "GET")
                if (tpStatus in 200..299) {
                    val tpArray = JSONArray(tpResponse)
                    for (i in 0 until tpArray.length()) {
                        val p = tpArray.getJSONObject(i)
                        val tId = p.getString("id")
                        val tName = p.optString("full_name", "Teacher")
                        val tEmail = p.optString("email", "")
                        val tAvatar = p.optString("avatar_url", "")
                        val existingUser = db.userDao().getUserById(tId)
                        db.userDao().insertUser(
                            User(
                                userId = tId,
                                email = tEmail,
                                passwordHash = existingUser?.passwordHash ?: "",
                                userType = "TEACHER",
                                name = tName
                            )
                        )
                        if (db.teacherDao().getTeacherById(tId) == null) {
                            db.teacherDao().insertTeacher(
                                Teacher(teacherId = tId, userId = tId, subject = "Faculty", department = "General")
                            )
                        }
                        if (tAvatar.isNotBlank()) {
                            saveBase64AvatarToLocal(tId, tAvatar)
                        }
                    }
                }
                contextRef?.let { ctx ->
                    for (tId in teacherIds) {
                        val avatarFile = java.io.File(ctx.filesDir, "avatar_${tId}.jpg")
                        if (!avatarFile.exists() || avatarFile.length() == 0L) {
                            downloadAndCacheProfileAvatar(ctx, tId)
                        }
                    }
                }
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
            if (sessionManager != null) initSessionManager(sessionManager, db)
            val token = sessionManager?.getAccessToken()
            if (!token.isNullOrBlank()) {
                setAuthToken(token)
            }

            val rawClean = code.trim().uppercase().replace(" ", "")
            // Support both "CS-4921" and "CS4921" formats automatically
            val altCode = if (!rawClean.contains("-") && rawClean.length >= 3) {
                val letters = rawClean.takeWhile { it.isLetter() }
                val digits = rawClean.dropWhile { it.isLetter() }
                if (letters.isNotEmpty() && digits.isNotEmpty()) "$letters-$digits" else rawClean
            } else {
                rawClean.replace("-", "")
            }

            var (status, response) = executeRequest("/rest/v1/classes?join_code=ilike.$rawClean&select=*", "GET")
            var array = if (status in 200..299) JSONArray(response) else JSONArray()
            if (array.length() == 0 && altCode != rawClean) {
                val retry = executeRequest("/rest/v1/classes?join_code=ilike.$altCode&select=*", "GET")
                status = retry.first
                response = retry.second
                array = if (status in 200..299) JSONArray(response) else JSONArray()
            }

            val newClazz: Clazz = if (array.length() > 0) {
                val classObj = array.getJSONObject(0)
                Clazz(
                    classId = classObj.getString("class_id"),
                    teacherId = classObj.optString("teacher_id", ""),
                    className = classObj.getString("class_name"),
                    subject = classObj.optString("subject_code", ""),
                    semester = classObj.optInt("semester", 1),
                    section = classObj.optString("department", ""),
                    classCode = classObj.optString("join_code", rawClean)
                )
            } else {
                // Fallback: check local Room database for matching class code
                val localClass = db?.clazzDao()?.getClassByCode(rawClean)
                    ?: db?.clazzDao()?.getClassByCode(altCode)
                    ?: db?.clazzDao()?.getClassByCode(code.trim())
                if (localClass != null) {
                    localClass
                } else {
                    return@withContext Pair(
                        false,
                        if (status == 401 || status == 403) "Authentication error ($status). Please log out and log in again."
                        else "Invalid Class Code \"$rawClean\". Ask your teacher to open Swiff Mark so their class syncs to the cloud."
                    )
                }
            }

            // Check if already enrolled
            val existingIds = db?.classEnrollmentDao()?.getEnrolledStudentIds(newClazz.classId) ?: emptyList()
            if (existingIds.contains(studentId)) {
                return@withContext Pair(false, "You are already enrolled in \"${newClazz.className}\".")
            }

            // Re-allow class if previously left
            sessionManager?.unmarkClassLeft(studentId, newClazz.classId)

            // Ensure student profile exists in Supabase BEFORE inserting enrollment
            if (studentId.isNotBlank()) {
                val localStudent = db?.studentDao()?.getStudentById(studentId)
                val sess = sessionManager?.getSession()
                ensureCloudUserProfile(
                    userId = studentId,
                    email = localStudent?.email ?: sess?.userEmail ?: "",
                    fullName = localStudent?.fullName ?: sess?.userName ?: "Student",
                    role = "STUDENT",
                    rollOrSubject = (localStudent?.rollNumber ?: 0).toString(),
                    sectionOrDept = localStudent?.classSection ?: ""
                )
            }

            // Insert into Supabase public.class_enrollments (schema has class_id, student_id)
            val enrollmentJson = JSONObject().apply {
                put("class_id", newClazz.classId)
                put("student_id", studentId)
            }
            val (enrollStatus, _) = executeRequest(
                "/rest/v1/class_enrollments?on_conflict=class_id,student_id",
                "POST",
                enrollmentJson.toString(),
                mapOf("Prefer" to "resolution=merge-duplicates")
            )

            // Save class & enrollment locally as verified (1) so student and teacher both see it immediately
            db?.clazzDao()?.insertClass(newClazz)
            db?.classEnrollmentDao()?.enrollStudent(
                ClassEnrollment(classId = newClazz.classId, studentId = studentId, enrollmentVerified = 1)
            )
            db?.classEnrollmentDao()?.updateEnrollmentStatus(newClazz.classId, studentId, 1)

            if (enrollStatus in 200..299 || enrollStatus == 409) {
                Pair(true, newClazz.className)
            } else {
                Pair(true, "${newClazz.className} (saved locally, will sync)")
            }
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
            val (status, _) = executeRequest(
                "/rest/v1/classes?on_conflict=class_id",
                "POST",
                json.toString(),
                mapOf("Prefer" to "resolution=merge-duplicates")
            )
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
            val (status, response) = executeRequest("/rest/v1/attendance_sessions?class_id=eq.$classId&select=*&order=created_at.desc", "GET")
            if (status !in 200..299) return@withContext Pair(false, "Sync error ($status)")
            val array = JSONArray(response)
            val sessionIds = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val sId = obj.getString("session_id")
                sessionIds.add(sId)
                val rawCreatedAt = obj.optString("created_at", "")
                val rawDate = obj.optString("session_date", "")
                val dateToParse = rawCreatedAt.ifBlank { rawDate }
                val parsedDate: Long = if (dateToParse.isNotEmpty()) {
                    try {
                        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(dateToParse)?.time ?: System.currentTimeMillis()
                    } catch (e1: Exception) {
                        try {
                            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(dateToParse)?.time ?: System.currentTimeMillis()
                        } catch (e2: Exception) { System.currentTimeMillis() }
                    }
                } else System.currentTimeMillis()
                db.attendanceDao().insertSession(AttendanceSession(
                    sessionId = sId, classId = classId,
                    sessionDate = parsedDate,
                    photo1Path = "", photo1FacesDetected = obj.optInt("photo1_faces_detected", 0),
                    photo2Path = "", photo2FacesDetected = obj.optInt("photo2_faces_detected", 0),
                    sessionStatus = obj.optString("session_status", "COMPLETED"),
                    isSynced = true
                ))
            }

            // Also download all attendance_records for these sessions so teacher Attendance History & CSV exports are complete
            if (sessionIds.isNotEmpty()) {
                val sIdsFilter = sessionIds.joinToString(",")
                val (rStatus, rResp) = executeRequest("/rest/v1/attendance_records?session_id=in.($sIdsFilter)&select=*", "GET")
                if (rStatus in 200..299) {
                    val rArray = JSONArray(rResp)
                    val recordsToInsert = mutableListOf<AttendanceRecord>()
                    for (i in 0 until rArray.length()) {
                        val rObj = rArray.getJSONObject(i)
                        val isPres = if (rObj.optBoolean("marked_present", false)) 1 else 0
                        recordsToInsert.add(
                            AttendanceRecord(
                                sessionId = rObj.getString("session_id"),
                                studentId = rObj.getString("student_id"),
                                photo1Matched = isPres,
                                photo2Matched = isPres,
                                dualCapturePresent = isPres,
                                markedPresent = isPres
                            )
                        )
                    }
                    if (recordsToInsert.isNotEmpty()) {
                        db.attendanceDao().insertAttendanceRecords(recordsToInsert)
                    }
                }
            }

            Pair(true, "Synced ${array.length()} sessions.")
        } catch (e: Exception) { Pair(false, "Sync Error: ${e.localizedMessage}") }
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
                val liveToken = sessionManagerRef?.getAccessToken()?.ifBlank { null }
                    ?: authToken?.ifBlank { null } ?: SupabaseConfig.SUPABASE_ANON_KEY
                val storageUrl = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/avatars/${userId}.jpg"

                val connection = java.net.URL(storageUrl).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 12000
                connection.readTimeout = 15000
                connection.setRequestProperty("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer $liveToken")
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
     * Download a user's avatar from Supabase and cache it locally
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
            // Strategy 1: Check profiles.avatar_url (ONLY explicitly user-uploaded profile photos)
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
                            com.vaibhav.facialattendancesystem.ui.components.ProfileImageHelper.notifyAvatarChanged()
                            return@withContext true
                        }
                    } else if (avatarUrl.startsWith("http")) {
                        val connection = java.net.URL(avatarUrl).openConnection() as java.net.HttpURLConnection
                        connection.connectTimeout = 10000
                        connection.readTimeout = 12000
                        val bytes = connection.inputStream.use { it.readBytes() }
                        if (bytes.isNotEmpty()) {
                            targetFile.writeBytes(bytes)
                            com.vaibhav.facialattendancesystem.ui.components.ProfileImageHelper.notifyAvatarChanged()
                            return@withContext true
                        }
                    }
                }
            }

            // Never fall back to face enrollment photos! Default is blank with First+Last initials (e.g. VV).
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * One-time cleanup that removes any face enrollment photos that were previously
     * auto-copied into avatar_<userId>.jpg or profiles.avatar_url.
     *
     * Scoped to the CURRENT USER only — does NOT touch other users' files or cloud data.
     */
    @Volatile private var purgeInProgress = false

    suspend fun purgeAutoFaceAvatarsOnce(context: android.content.Context) = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("facial_attendance_session", android.content.Context.MODE_PRIVATE)
            if (prefs.getBoolean("auto_face_avatars_purged_v3", false)) return@withContext
            // In-memory guard to prevent concurrent executions racing before prefs is written
            if (purgeInProgress) return@withContext
            purgeInProgress = true

            try {
                // Only delete the current user's own avatar file (not all users' cached avatars)
                val currentUserId = sessionManagerRef?.getSession()?.userId
                if (!currentUserId.isNullOrBlank()) {
                    val ownFile = java.io.File(context.filesDir, "avatar_${currentUserId}.jpg")
                    if (ownFile.exists()) ownFile.delete()
                }

                // Clear avatar_url only on the current user's Supabase profile row
                if (SupabaseConfig.isConfigured && !currentUserId.isNullOrBlank()) {
                    val clearJson = JSONObject().apply { put("avatar_url", JSONObject.NULL) }.toString()
                    executeRequest("/rest/v1/profiles?id=eq.$currentUserId", "PATCH", clearJson)
                }

                prefs.edit().putBoolean("auto_face_avatars_purged_v3", true).commit()
                com.vaibhav.facialattendancesystem.ui.components.ProfileImageHelper.notifyAvatarChanged()
            } finally {
                purgeInProgress = false
            }
        } catch (e: Exception) {
            purgeInProgress = false
            e.printStackTrace()
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
                val liveToken3 = sessionManagerRef?.getAccessToken()?.ifBlank { null }
                    ?: authToken?.ifBlank { null } ?: SupabaseConfig.SUPABASE_ANON_KEY
                val storageUrl = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/avatars/${userId}.jpg"
                val connection = java.net.URL(storageUrl).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "DELETE"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer $liveToken3")
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

    /**
     * Checks https://api.github.com/repos/vaavgit/Swiff-Mark/releases/latest
     * Returns Triple(latestVersionTag, releaseNotes, downloadUrl) if a newer version than BuildConfig.VERSION_NAME exists, else null.
     */
    suspend fun checkForGitHubUpdate(): Triple<String, String, String>? = withContext(Dispatchers.IO) {
        try {
            val conn = URL("https://api.github.com/repos/vaavgit/Swiff-Mark/releases/latest").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "SwiffMark-Android")
            if (conn.responseCode !in 200..299) return@withContext null
            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isBlank()) return@withContext null

            val cleanRemote = tagName.removePrefix("v").removePrefix("V").trim()
            val cleanLocal = com.vaibhav.facialattendancesystem.BuildConfig.VERSION_NAME.removePrefix("v").removePrefix("V").trim()

            if (isRemoteVersionNewer(cleanRemote, cleanLocal)) {
                val notes = json.optString("body", "Bug fixes and performance improvements.").trim()
                var downloadUrl = json.optString("html_url", "https://github.com/vaavgit/Swiff-Mark/releases/latest")
                val assets = json.optJSONArray("assets")
                if (assets != null && assets.length() > 0) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetUrl = asset.optString("browser_download_url", "")
                        if (assetUrl.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = assetUrl
                            break
                        }
                    }
                }
                Triple(tagName, notes, downloadUrl)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun isRemoteVersionNewer(remote: String, local: String): Boolean {
        val rParts = remote.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val lParts = local.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val maxLen = kotlin.math.max(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
