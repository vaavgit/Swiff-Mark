package com.vaibhav.facialattendancesystem.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        User::class,
        Student::class,
        Teacher::class,
        FaceEmbedding::class,
        Clazz::class,
        ClassEnrollment::class,
        AttendanceSession::class,
        AttendanceRecord::class,
        RecognitionLog::class
    ],
    version = 7,
    exportSchema = false
)

@TypeConverters(VectorTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun studentDao(): StudentDao
    abstract fun teacherDao(): TeacherDao
    abstract fun faceEmbeddingDao(): FaceEmbeddingDao
    abstract fun clazzDao(): ClazzDao
    abstract fun classEnrollmentDao(): ClassEnrollmentDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun recognitionLogDao(): RecognitionLogDao

    suspend fun purgeClassesAndAttendanceData(purgeCloud: Boolean = true): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            recognitionLogDao().deleteAllLogs()
            attendanceDao().deleteAllAttendanceRecords()
            attendanceDao().deleteAllAttendanceSessions()
            classEnrollmentDao().deleteAllEnrollments()
            clazzDao().deleteAllClasses()
            if (purgeCloud) {
                CloudSyncManager.purgeCloudClassAndAttendanceData()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "facial_attendance_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
