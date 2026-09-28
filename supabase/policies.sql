-- ==============================================================================
-- FACIAL ATTENDANCE SYSTEM: COMPREHENSIVE PRODUCTION RLS SECURITY POLICIES
-- Run this in your Supabase SQL Editor: https://supabase.com/dashboard/project/<YOUR_PROJECT_REF>/sql
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 0. CLEANUP: PURGE PLAINTEXT PASSWORDS FROM AVATAR_URL IMMEDIATELY
-- ------------------------------------------------------------------------------
UPDATE public.profiles 
SET avatar_url = NULL 
WHERE avatar_url LIKE 'pwd:%';

-- ------------------------------------------------------------------------------
-- 1. DROP ALL PREVIOUS PERMISSIVE POLICIES
-- ------------------------------------------------------------------------------
DROP POLICY IF EXISTS "Allow all on profiles" ON public.profiles;
DROP POLICY IF EXISTS "Allow all on students" ON public.students;
DROP POLICY IF EXISTS "Allow all on teachers" ON public.teachers;
DROP POLICY IF EXISTS "Allow all on classes" ON public.classes;
DROP POLICY IF EXISTS "Allow all on enrollments" ON public.class_enrollments;
DROP POLICY IF EXISTS "Allow all on embeddings" ON public.face_embeddings;
DROP POLICY IF EXISTS "Allow all on sessions" ON public.attendance_sessions;
DROP POLICY IF EXISTS "Allow all on records" ON public.attendance_records;

-- Ensure RLS is enabled on all tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.students ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.teachers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.classes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.class_enrollments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.face_embeddings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance_records ENABLE ROW LEVEL SECURITY;

-- ------------------------------------------------------------------------------
-- 2. TABLE: PROFILES
-- Rules:
-- - No anon access.
-- - Authenticated users can read their own profile OR teachers can view ONLY students enrolled in their classes.
-- - Users can ONLY insert/update/delete their OWN profile row.
-- ------------------------------------------------------------------------------
CREATE POLICY "profiles_select" ON public.profiles
FOR SELECT TO authenticated
USING (
    auth.uid()::text = id 
    OR EXISTS (
        SELECT 1 FROM public.class_enrollments ce
        JOIN public.classes c ON ce.class_id = c.class_id
        WHERE ce.student_id = profiles.id
        AND c.teacher_id = auth.uid()::text
    )
    OR EXISTS (
        SELECT 1 FROM public.classes c
        WHERE c.teacher_id = profiles.id
    )
);

CREATE POLICY "profiles_insert" ON public.profiles
FOR INSERT TO authenticated
WITH CHECK (auth.uid()::text = id);

CREATE POLICY "profiles_update" ON public.profiles
FOR UPDATE TO authenticated
USING (auth.uid()::text = id)
WITH CHECK (auth.uid()::text = id);

CREATE POLICY "profiles_delete" ON public.profiles
FOR DELETE TO authenticated
USING (auth.uid()::text = id);

-- ------------------------------------------------------------------------------
-- 3. TABLE: STUDENTS
-- Rules:
-- - Students can view and manage their own student row.
-- - Teachers can view ONLY students enrolled in classes they teach.
-- ------------------------------------------------------------------------------
CREATE POLICY "students_select" ON public.students
FOR SELECT TO authenticated
USING (
    student_id = auth.uid()::text 
    OR EXISTS (
        SELECT 1 FROM public.class_enrollments ce
        JOIN public.classes c ON ce.class_id = c.class_id
        WHERE ce.student_id = students.student_id
        AND c.teacher_id = auth.uid()::text
    )
);

CREATE POLICY "students_insert" ON public.students
FOR INSERT TO authenticated
WITH CHECK (student_id = auth.uid()::text);

CREATE POLICY "students_update" ON public.students
FOR UPDATE TO authenticated
USING (student_id = auth.uid()::text)
WITH CHECK (student_id = auth.uid()::text);

CREATE POLICY "students_delete" ON public.students
FOR DELETE TO authenticated
USING (student_id = auth.uid()::text);

-- ------------------------------------------------------------------------------
-- 4. TABLE: TEACHERS
-- Rules:
-- - Anyone authenticated can read teacher info (to see who teaches a class).
-- - Only the teacher can insert/update/delete their own record.
-- ------------------------------------------------------------------------------
CREATE POLICY "teachers_select" ON public.teachers
FOR SELECT TO authenticated
USING (true);

CREATE POLICY "teachers_insert" ON public.teachers
FOR INSERT TO authenticated
WITH CHECK (teacher_id = auth.uid()::text);

CREATE POLICY "teachers_update" ON public.teachers
FOR UPDATE TO authenticated
USING (teacher_id = auth.uid()::text)
WITH CHECK (teacher_id = auth.uid()::text);

CREATE POLICY "teachers_delete" ON public.teachers
FOR DELETE TO authenticated
USING (teacher_id = auth.uid()::text);

-- ------------------------------------------------------------------------------
-- 5. TABLE: CLASSES
-- Rules:
-- - Authenticated users can view classes (needed for students joining via code).
-- - ONLY teachers can create classes.
-- - ONLY the teacher who owns the class can UPDATE or DELETE it.
-- ------------------------------------------------------------------------------
CREATE POLICY "classes_select" ON public.classes
FOR SELECT TO authenticated
USING (true);

CREATE POLICY "classes_insert" ON public.classes
FOR INSERT TO authenticated
WITH CHECK (
    teacher_id = auth.uid()::text 
    AND EXISTS (SELECT 1 FROM public.teachers WHERE teacher_id = auth.uid()::text)
);

CREATE POLICY "classes_update" ON public.classes
FOR UPDATE TO authenticated
USING (teacher_id = auth.uid()::text)
WITH CHECK (teacher_id = auth.uid()::text);

CREATE POLICY "classes_delete" ON public.classes
FOR DELETE TO authenticated
USING (teacher_id = auth.uid()::text);

-- ------------------------------------------------------------------------------
-- 6. TABLE: CLASS_ENROLLMENTS
-- Rules:
-- - Students can view their own enrollments; Teachers can view enrollments for their classes.
-- - CRITICAL: A student can ONLY insert an enrollment for THEIR OWN student_id (prevents fake-enrolling others).
-- - Student can unenroll self, or teacher can remove a student from their class.
-- ------------------------------------------------------------------------------
CREATE POLICY "enrollments_select" ON public.class_enrollments
FOR SELECT TO authenticated
USING (
    student_id = auth.uid()::text 
    OR EXISTS (
        SELECT 1 FROM public.classes c 
        WHERE c.class_id = class_enrollments.class_id 
        AND c.teacher_id = auth.uid()::text
    )
);

CREATE POLICY "enrollments_insert" ON public.class_enrollments
FOR INSERT TO authenticated
WITH CHECK (
    student_id = auth.uid()::text
);

CREATE POLICY "enrollments_delete" ON public.class_enrollments
FOR DELETE TO authenticated
USING (
    student_id = auth.uid()::text 
    OR EXISTS (
        SELECT 1 FROM public.classes c 
        WHERE c.class_id = class_enrollments.class_id 
        AND c.teacher_id = auth.uid()::text
    )
);

-- ------------------------------------------------------------------------------
-- 7. TABLE: FACE_EMBEDDINGS (Sensitive Biometric Data)
-- Rules:
-- - Students can read ONLY their own embeddings.
-- - Teachers can read embeddings ONLY for students currently enrolled in classes taught by that teacher.
-- - ONLY the student can insert, update, or delete their own biometric vectors.
-- ------------------------------------------------------------------------------
CREATE POLICY "embeddings_select" ON public.face_embeddings
FOR SELECT TO authenticated
USING (
    student_id = auth.uid()::text 
    OR EXISTS (
        SELECT 1 FROM public.class_enrollments ce
        JOIN public.classes c ON ce.class_id = c.class_id
        WHERE ce.student_id = face_embeddings.student_id
        AND c.teacher_id = auth.uid()::text
    )
);

CREATE POLICY "embeddings_insert" ON public.face_embeddings
FOR INSERT TO authenticated
WITH CHECK (student_id = auth.uid()::text);

CREATE POLICY "embeddings_update" ON public.face_embeddings
FOR UPDATE TO authenticated
USING (student_id = auth.uid()::text)
WITH CHECK (student_id = auth.uid()::text);

CREATE POLICY "embeddings_delete" ON public.face_embeddings
FOR DELETE TO authenticated
USING (student_id = auth.uid()::text);

-- ------------------------------------------------------------------------------
-- 8. TABLE: ATTENDANCE_SESSIONS
-- Rules:
-- - Enrolled students & class teachers can view sessions.
-- - ONLY the teacher of the class can insert, update, or delete sessions.
-- ------------------------------------------------------------------------------
CREATE POLICY "sessions_select" ON public.attendance_sessions
FOR SELECT TO authenticated
USING (
    teacher_id = auth.uid()::text 
    OR EXISTS (
        SELECT 1 FROM public.class_enrollments ce 
        WHERE ce.class_id = attendance_sessions.class_id 
        AND ce.student_id = auth.uid()::text
    )
);

CREATE POLICY "sessions_insert" ON public.attendance_sessions
FOR INSERT TO authenticated
WITH CHECK (
    teacher_id = auth.uid()::text 
    AND EXISTS (
        SELECT 1 FROM public.classes c 
        WHERE c.class_id = attendance_sessions.class_id 
        AND c.teacher_id = auth.uid()::text
    )
);

CREATE POLICY "sessions_update" ON public.attendance_sessions
FOR UPDATE TO authenticated
USING (teacher_id = auth.uid()::text)
WITH CHECK (teacher_id = auth.uid()::text);

CREATE POLICY "sessions_delete" ON public.attendance_sessions
FOR DELETE TO authenticated
USING (teacher_id = auth.uid()::text);

-- ------------------------------------------------------------------------------
-- 10. TABLE: ATTENDANCE_RECORDS
-- Rules:
-- - Students can view ONLY their own attendance records.
-- - Teachers can view all records for sessions belonging to their classes.
-- - CRITICAL: ONLY the teacher who owns the session can INSERT, UPDATE, or DELETE records.
--   A student CAN NEVER directly insert or update their own attendance record!
-- ------------------------------------------------------------------------------
CREATE POLICY "records_select" ON public.attendance_records
FOR SELECT TO authenticated
USING (
    student_id = auth.uid()::text 
    OR EXISTS (
        SELECT 1 FROM public.attendance_sessions s
        JOIN public.classes c ON s.class_id = c.class_id
        WHERE s.session_id = attendance_records.session_id
        AND c.teacher_id = auth.uid()::text
    )
);

CREATE POLICY "records_insert" ON public.attendance_records
FOR INSERT TO authenticated
WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.attendance_sessions s
        JOIN public.classes c ON s.class_id = c.class_id
        WHERE s.session_id = attendance_records.session_id
        AND c.teacher_id = auth.uid()::text
    )
);

CREATE POLICY "records_update" ON public.attendance_records
FOR UPDATE TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.attendance_sessions s
        JOIN public.classes c ON s.class_id = c.class_id
        WHERE s.session_id = attendance_records.session_id
        AND c.teacher_id = auth.uid()::text
    )
)
WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.attendance_sessions s
        JOIN public.classes c ON s.class_id = c.class_id
        WHERE s.session_id = attendance_records.session_id
        AND c.teacher_id = auth.uid()::text
    )
);

CREATE POLICY "records_delete" ON public.attendance_records
FOR DELETE TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.attendance_sessions s
        JOIN public.classes c ON s.class_id = c.class_id
        WHERE s.session_id = attendance_records.session_id
        AND c.teacher_id = auth.uid()::text
    )
);
