-- ==============================================================================
-- SWIFF MARK: SUPABASE DATABASE SCHEMA & RLS SECURITY POLICIES
-- Run this script in your Supabase project's SQL Editor (https://supabase.com/dashboard)
-- Idempotent: safe to run repeatedly on new or existing databases.
-- ==============================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. PROFILES TABLE
CREATE TABLE IF NOT EXISTS public.profiles (
    id TEXT PRIMARY KEY,
    email TEXT,
    full_name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('TEACHER', 'STUDENT', 'ADMIN')),
    avatar_url TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- 2. STUDENTS METADATA TABLE
CREATE TABLE IF NOT EXISTS public.students (
    student_id TEXT PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    roll_number TEXT,
    department TEXT,
    semester INT DEFAULT 1,
    enrollment_status TEXT DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 3. TEACHERS METADATA TABLE
CREATE TABLE IF NOT EXISTS public.teachers (
    teacher_id TEXT PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    employee_id TEXT,
    department TEXT,
    designation TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 4. CLASSES TABLE (With Unique Join Codes)
CREATE TABLE IF NOT EXISTS public.classes (
    class_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    teacher_id TEXT REFERENCES public.profiles(id) ON DELETE CASCADE,
    class_name TEXT NOT NULL,
    subject_code TEXT NOT NULL,
    department TEXT,
    semester INT DEFAULT 1,
    join_code VARCHAR(12) UNIQUE NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_classes_join_code ON public.classes(join_code);

-- 5. CLASS ENROLLMENTS
CREATE TABLE IF NOT EXISTS public.class_enrollments (
    enrollment_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    class_id TEXT NOT NULL REFERENCES public.classes(class_id) ON DELETE CASCADE,
    student_id TEXT NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    enrollment_verified INT DEFAULT 1,
    enrolled_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(class_id, student_id)
);
CREATE INDEX IF NOT EXISTS idx_enrollment_class ON public.class_enrollments(class_id);
CREATE INDEX IF NOT EXISTS idx_enrollment_student ON public.class_enrollments(student_id);

-- 6. FACE EMBEDDINGS (AdaFace 512-D vectors stored as compact Base64)
CREATE TABLE IF NOT EXISTS public.face_embeddings (
    embedding_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    student_id TEXT NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    embedding_vector_base64 TEXT NOT NULL,
    angle_label TEXT DEFAULT 'FRONTAL',
    quality_score REAL DEFAULT 1.0,
    pipeline_version INTEGER NOT NULL DEFAULT 2,
    is_primary BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_embeddings_student ON public.face_embeddings(student_id);

-- 7. ATTENDANCE SESSIONS
CREATE TABLE IF NOT EXISTS public.attendance_sessions (
    session_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    class_id TEXT NOT NULL REFERENCES public.classes(class_id) ON DELETE CASCADE,
    teacher_id TEXT REFERENCES public.profiles(id) ON DELETE SET NULL,
    session_date DATE DEFAULT CURRENT_DATE,
    photo1_faces_detected INT DEFAULT 0,
    photo2_faces_detected INT DEFAULT 0,
    session_status TEXT DEFAULT 'COMPLETED',
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sessions_class ON public.attendance_sessions(class_id);

-- 8. ATTENDANCE RECORDS (Per student per session)
CREATE TABLE IF NOT EXISTS public.attendance_records (
    record_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    session_id TEXT NOT NULL REFERENCES public.attendance_sessions(session_id) ON DELETE CASCADE,
    student_id TEXT NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    photo1_matched BOOLEAN DEFAULT false,
    photo2_matched BOOLEAN DEFAULT false,
    dual_capture_present BOOLEAN DEFAULT false,
    marked_present BOOLEAN NOT NULL DEFAULT false,
    confidence_score REAL DEFAULT 0.0,
    manually_overridden BOOLEAN DEFAULT false,
    manually_overridden_at TIMESTAMPTZ,
    overridden_by TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(session_id, student_id)
);
CREATE INDEX IF NOT EXISTS idx_records_session ON public.attendance_records(session_id);
CREATE INDEX IF NOT EXISTS idx_records_student ON public.attendance_records(student_id);

-- ==============================================================================
-- SECURE JOIN CODE RPC (SECURITY DEFINER)
-- Prevents exposing join codes via public SELECT queries.
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.join_class_by_code(p_join_code text)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_class record;
    v_user_id text;
    v_is_student boolean;
BEGIN
    v_user_id := auth.uid()::text;
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    -- Verify caller is enrolled with student role
    SELECT EXISTS (SELECT 1 FROM public.profiles WHERE id = v_user_id AND role = 'STUDENT') INTO v_is_student;
    IF NOT v_is_student THEN
        RAISE EXCEPTION 'Only registered students can join classes';
    END IF;

    SELECT class_id, class_name, subject_code, teacher_id, department, semester
    INTO v_class
    FROM public.classes
    WHERE join_code = UPPER(TRIM(p_join_code)) AND is_active = true;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Invalid class join code';
    END IF;

    INSERT INTO public.class_enrollments (class_id, student_id, enrollment_verified)
    VALUES (v_class.class_id, v_user_id, 1)
    ON CONFLICT (class_id, student_id) DO NOTHING;

    RETURN json_build_object(
        'class_id', v_class.class_id,
        'class_name', v_class.class_name,
        'subject_code', v_class.subject_code,
        'teacher_id', v_class.teacher_id,
        'department', v_class.department,
        'semester', v_class.semester
    );
END;
$$;

-- ==============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- Idempotent policy setup with DROP POLICY IF EXISTS
-- ==============================================================================
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.students ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.teachers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.classes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.class_enrollments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.face_embeddings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance_records ENABLE ROW LEVEL SECURITY;

-- 1. Profiles
DROP POLICY IF EXISTS "profiles_select" ON public.profiles;
DROP POLICY IF EXISTS "profiles_insert" ON public.profiles;
DROP POLICY IF EXISTS "profiles_update" ON public.profiles;
DROP POLICY IF EXISTS "profiles_delete" ON public.profiles;
DROP POLICY IF EXISTS "Profiles select policy" ON public.profiles;
DROP POLICY IF EXISTS "Profiles insert policy" ON public.profiles;
DROP POLICY IF EXISTS "Profiles update policy" ON public.profiles;
DROP POLICY IF EXISTS "Profiles delete policy" ON public.profiles;

CREATE POLICY "profiles_select" ON public.profiles FOR SELECT TO authenticated
    USING (auth.uid()::text = id OR EXISTS (
        SELECT 1 FROM public.class_enrollments ce
        JOIN public.classes c ON c.class_id = ce.class_id
        WHERE ce.student_id = public.profiles.id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "profiles_insert" ON public.profiles FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = id);
CREATE POLICY "profiles_update" ON public.profiles FOR UPDATE TO authenticated
    USING (auth.uid()::text = id) WITH CHECK (auth.uid()::text = id);
CREATE POLICY "profiles_delete" ON public.profiles FOR DELETE TO authenticated
    USING (auth.uid()::text = id);

-- 2. Students
DROP POLICY IF EXISTS "students_select" ON public.students;
DROP POLICY IF EXISTS "students_insert" ON public.students;
DROP POLICY IF EXISTS "students_update" ON public.students;
DROP POLICY IF EXISTS "students_delete" ON public.students;
DROP POLICY IF EXISTS "Students select policy" ON public.students;
DROP POLICY IF EXISTS "Students insert policy" ON public.students;
DROP POLICY IF EXISTS "Students update policy" ON public.students;
DROP POLICY IF EXISTS "Students delete policy" ON public.students;

CREATE POLICY "students_select" ON public.students FOR SELECT TO authenticated
    USING (auth.uid()::text = student_id OR EXISTS (
        SELECT 1 FROM public.class_enrollments ce
        JOIN public.classes c ON c.class_id = ce.class_id
        WHERE ce.student_id = public.students.student_id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "students_insert" ON public.students FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = student_id);
CREATE POLICY "students_update" ON public.students FOR UPDATE TO authenticated
    USING (auth.uid()::text = student_id) WITH CHECK (auth.uid()::text = student_id);
CREATE POLICY "students_delete" ON public.students FOR DELETE TO authenticated
    USING (auth.uid()::text = student_id);

-- 3. Teachers (Enforce teacher role on creation)
DROP POLICY IF EXISTS "teachers_select" ON public.teachers;
DROP POLICY IF EXISTS "teachers_insert" ON public.teachers;
DROP POLICY IF EXISTS "teachers_update" ON public.teachers;
DROP POLICY IF EXISTS "teachers_delete" ON public.teachers;
DROP POLICY IF EXISTS "Teachers select policy" ON public.teachers;
DROP POLICY IF EXISTS "Teachers insert policy" ON public.teachers;
DROP POLICY IF EXISTS "Teachers update policy" ON public.teachers;
DROP POLICY IF EXISTS "Teachers delete policy" ON public.teachers;

CREATE POLICY "teachers_select" ON public.teachers FOR SELECT TO authenticated USING (true);
CREATE POLICY "teachers_insert" ON public.teachers FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = teacher_id AND EXISTS (
        SELECT 1 FROM public.profiles WHERE id = auth.uid()::text AND role = 'TEACHER'
    ));
CREATE POLICY "teachers_update" ON public.teachers FOR UPDATE TO authenticated
    USING (auth.uid()::text = teacher_id) WITH CHECK (auth.uid()::text = teacher_id);
CREATE POLICY "teachers_delete" ON public.teachers FOR DELETE TO authenticated
    USING (auth.uid()::text = teacher_id);

-- 4. Classes (Restrict SELECT to owning teacher and enrolled students; prevent scraping join codes)
DROP POLICY IF EXISTS "classes_select" ON public.classes;
DROP POLICY IF EXISTS "classes_insert" ON public.classes;
DROP POLICY IF EXISTS "classes_update" ON public.classes;
DROP POLICY IF EXISTS "classes_delete" ON public.classes;
DROP POLICY IF EXISTS "Classes select policy" ON public.classes;
DROP POLICY IF EXISTS "Classes insert policy" ON public.classes;
DROP POLICY IF EXISTS "Classes update policy" ON public.classes;
DROP POLICY IF EXISTS "Classes delete policy" ON public.classes;

CREATE POLICY "classes_select" ON public.classes FOR SELECT TO authenticated
    USING (
        teacher_id = auth.uid()::text
        OR EXISTS (
            SELECT 1 FROM public.class_enrollments ce
            WHERE ce.class_id = public.classes.class_id AND ce.student_id = auth.uid()::text
        )
    );
CREATE POLICY "classes_insert" ON public.classes FOR INSERT TO authenticated
    WITH CHECK (
        auth.uid()::text = teacher_id AND EXISTS (
            SELECT 1 FROM public.profiles WHERE id = auth.uid()::text AND role = 'TEACHER'
        )
    );
CREATE POLICY "classes_update" ON public.classes FOR UPDATE TO authenticated
    USING (auth.uid()::text = teacher_id) WITH CHECK (auth.uid()::text = teacher_id);
CREATE POLICY "classes_delete" ON public.classes FOR DELETE TO authenticated
    USING (auth.uid()::text = teacher_id);

-- 5. Class Enrollments
DROP POLICY IF EXISTS "enrollments_select" ON public.class_enrollments;
DROP POLICY IF EXISTS "enrollments_insert" ON public.class_enrollments;
DROP POLICY IF EXISTS "enrollments_delete" ON public.class_enrollments;
DROP POLICY IF EXISTS "Enrollments select policy" ON public.class_enrollments;
DROP POLICY IF EXISTS "Enrollments insert policy" ON public.class_enrollments;
DROP POLICY IF EXISTS "Enrollments delete policy" ON public.class_enrollments;

CREATE POLICY "enrollments_select" ON public.class_enrollments FOR SELECT TO authenticated
    USING (auth.uid()::text = student_id OR EXISTS (
        SELECT 1 FROM public.classes c WHERE c.class_id = public.class_enrollments.class_id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "enrollments_insert" ON public.class_enrollments FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = student_id);
CREATE POLICY "enrollments_delete" ON public.class_enrollments FOR DELETE TO authenticated
    USING (auth.uid()::text = student_id OR EXISTS (
        SELECT 1 FROM public.classes c WHERE c.class_id = public.class_enrollments.class_id AND c.teacher_id = auth.uid()::text
    ));

-- 6. Face Embeddings
DROP POLICY IF EXISTS "embeddings_select" ON public.face_embeddings;
DROP POLICY IF EXISTS "embeddings_insert" ON public.face_embeddings;
DROP POLICY IF EXISTS "embeddings_delete" ON public.face_embeddings;
DROP POLICY IF EXISTS "Embeddings select policy" ON public.face_embeddings;
DROP POLICY IF EXISTS "Embeddings insert policy" ON public.face_embeddings;
DROP POLICY IF EXISTS "Embeddings delete policy" ON public.face_embeddings;

CREATE POLICY "embeddings_select" ON public.face_embeddings FOR SELECT TO authenticated
    USING (auth.uid()::text = student_id OR EXISTS (
        SELECT 1 FROM public.class_enrollments ce
        JOIN public.classes c ON c.class_id = ce.class_id
        WHERE ce.student_id = public.face_embeddings.student_id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "embeddings_insert" ON public.face_embeddings FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = student_id);
CREATE POLICY "embeddings_delete" ON public.face_embeddings FOR DELETE TO authenticated
    USING (auth.uid()::text = student_id);

-- 7. Attendance Sessions
DROP POLICY IF EXISTS "sessions_select" ON public.attendance_sessions;
DROP POLICY IF EXISTS "sessions_insert" ON public.attendance_sessions;
DROP POLICY IF EXISTS "sessions_update" ON public.attendance_sessions;
DROP POLICY IF EXISTS "sessions_delete" ON public.attendance_sessions;
DROP POLICY IF EXISTS "Sessions select policy" ON public.attendance_sessions;
DROP POLICY IF EXISTS "Sessions insert policy" ON public.attendance_sessions;
DROP POLICY IF EXISTS "Sessions update policy" ON public.attendance_sessions;
DROP POLICY IF EXISTS "Sessions delete policy" ON public.attendance_sessions;

CREATE POLICY "sessions_select" ON public.attendance_sessions FOR SELECT TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.classes c WHERE c.class_id = public.attendance_sessions.class_id AND (
            c.teacher_id = auth.uid()::text OR EXISTS (
                SELECT 1 FROM public.class_enrollments ce WHERE ce.class_id = c.class_id AND ce.student_id = auth.uid()::text
            )
        )
    ));
CREATE POLICY "sessions_insert" ON public.attendance_sessions FOR INSERT TO authenticated
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.classes c WHERE c.class_id = public.attendance_sessions.class_id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "sessions_update" ON public.attendance_sessions FOR UPDATE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.classes c WHERE c.class_id = public.attendance_sessions.class_id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "sessions_delete" ON public.attendance_sessions FOR DELETE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.classes c WHERE c.class_id = public.attendance_sessions.class_id AND c.teacher_id = auth.uid()::text
    ));

-- 8. Attendance Records
DROP POLICY IF EXISTS "records_select" ON public.attendance_records;
DROP POLICY IF EXISTS "records_insert" ON public.attendance_records;
DROP POLICY IF EXISTS "records_update" ON public.attendance_records;
DROP POLICY IF EXISTS "records_delete" ON public.attendance_records;
DROP POLICY IF EXISTS "Records select policy" ON public.attendance_records;
DROP POLICY IF EXISTS "Records insert policy" ON public.attendance_records;
DROP POLICY IF EXISTS "Records update policy" ON public.attendance_records;
DROP POLICY IF EXISTS "Records delete policy" ON public.attendance_records;

CREATE POLICY "records_select" ON public.attendance_records FOR SELECT TO authenticated
    USING (auth.uid()::text = student_id OR EXISTS (
        SELECT 1 FROM public.attendance_sessions s
        JOIN public.classes c ON c.class_id = s.class_id
        WHERE s.session_id = public.attendance_records.session_id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "records_insert" ON public.attendance_records FOR INSERT TO authenticated
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.attendance_sessions s
        JOIN public.classes c ON c.class_id = s.class_id
        WHERE s.session_id = public.attendance_records.session_id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "records_update" ON public.attendance_records FOR UPDATE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.attendance_sessions s
        JOIN public.classes c ON c.class_id = s.class_id
        WHERE s.session_id = public.attendance_records.session_id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "records_delete" ON public.attendance_records FOR DELETE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.attendance_sessions s
        JOIN public.classes c ON c.class_id = s.class_id
        WHERE s.session_id = public.attendance_records.session_id AND c.teacher_id = auth.uid()::text
    ));
