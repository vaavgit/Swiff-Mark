package com.vaibhav.facialattendancesystem.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 8,
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

    suspend fun purgeClassesAndAttendanceData(): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            recognitionLogDao().deleteAllLogs()
            attendanceDao().deleteAllAttendanceRecords()
            attendanceDao().deleteAllAttendanceSessions()
            classEnrollmentDao().deleteAllEnrollments()
            clazzDao().deleteAllClasses()
            true
        } catch (e: Exception) {
            android.util.Log.e("AppDatabase", "Error purging local data", e)
            false
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE face_embeddings ADD COLUMN pipeline_version INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE attendance_records ADD COLUMN manually_overridden_at INTEGER")
                db.execSQL("ALTER TABLE attendance_records ADD COLUMN overridden_by TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "facial_attendance_database"
                )
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA foreign_keys = ON;")
                        }
                    })
                    .addMigrations(MIGRATION_7_8)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
