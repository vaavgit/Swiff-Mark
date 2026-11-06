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
    version = 10,
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

        /**
         * MIGRATION 9→10: Change roll_number column from INTEGER to TEXT.
         *
         * SQLite cannot change column types directly, so we:
         * 1. Rename the old table to a temp name.
         * 2. Create a new table with roll_number as TEXT.
         * 3. Copy all data, casting the old INTEGER roll_number to TEXT.
         * 4. Drop the old temp table.
         *
         * This allows alphanumeric roll numbers like "22CS041" or "007".
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite cannot change column types directly.
                // Create new table with roll_number as TEXT, copy data, replace.
                db.execSQL("ALTER TABLE students RENAME TO students_old")
                db.execSQL("""
                    CREATE TABLE students (
                        student_id TEXT NOT NULL PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        roll_number TEXT NOT NULL DEFAULT '',
                        full_name TEXT NOT NULL,
                        email TEXT NOT NULL,
                        class_section TEXT,
                        enrollment_status INTEGER NOT NULL DEFAULT 0,
                        enrollment_date INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_students_user_id ON students (user_id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_students_email ON students (email)")
                db.execSQL("INSERT INTO students SELECT student_id, user_id, CAST(roll_number AS TEXT), full_name, email, class_section, enrollment_status, enrollment_date FROM students_old")
                db.execSQL("DROP TABLE students_old")
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
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
