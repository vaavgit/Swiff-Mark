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
    version = 9,
    exportSchema = true
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

        /**
         * MIGRATION 8→9: Pipeline-version data integrity fix (H6).
         *
         * MIGRATION_7_8 added the pipeline_version column with DEFAULT 2, which
         * incorrectly labelled every pre-existing embedding (enrolled before the
         * BGR→RGB + alignment pipeline change) as version 2. The actual new-pipeline
         * embeddings are correct, but old ones were never re-enrolled, so the
         * AttendanceViewModel's `>= 2` filter has been silently including stale data.
         *
         * The safest fix at the Room level is to wipe all local embeddings and force
         * every device to re-sync from Supabase, which will now correctly report
         * version 1 or 2 after the one-time Supabase UPDATE (run separately):
         *   UPDATE public.face_embeddings
         *   SET pipeline_version = 1
         *   WHERE created_at < '<your BGR-change commit date>';
         *
         * Students who re-open the app will trigger a background sync that pulls the
         * correctly-labelled embeddings back down. Teachers will see the corrected
         * pipeline_version filter take effect for the next attendance session.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Wipe local embeddings — they will be re-fetched from Supabase on next sync.
                // Supabase is the source of truth for pipeline_version after the one-time UPDATE.
                db.execSQL("DELETE FROM face_embeddings")
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
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
