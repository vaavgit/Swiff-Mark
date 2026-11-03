-- ==============================================================================
-- SWIFF MARK: SUPABASE DATABASE SCHEMA & RLS SECURITY POLICIES
-- Run this script in your Supabase project's SQL Editor (https://supabase.com/dashboard)
-- Idempotent: safe to run repeatedly on new or existing databases.
-- Type-safe: explicitly casts both sides to text to support both UUID and TEXT schemas.
-- ==============================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. PROFILES TABLE
CREATE TABLE IF NOT EXISTS public.profiles (
    id TEXT PRIMARY KEY,
    email TEXT,
    full_name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('TEACHER', 'STUDENT')),
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

-- Ensure non-destructive addition of audit & versioning columns if tables already exist
ALTER TABLE public.face_embeddings ADD COLUMN IF NOT EXISTS pipeline_version INTEGER NOT NULL DEFAULT 2;
ALTER TABLE public.attendance_records ADD COLUMN IF NOT EXISTS manually_overridden_at TIMESTAMPTZ;
ALTER TABLE public.attendance_records ADD COLUMN IF NOT EXISTS overridden_by TEXT;

-- ==============================================================================
-- AUTOMATIC PROFILE CREATION TRIGGER (SECURITY DEFINER)
-- Synchronizes users registered via Supabase GoTrue Auth to public.profiles
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    INSERT INTO public.profiles (id, email, full_name, role)
    VALUES (
        NEW.id::text,
        NEW.email,
        COALESCE(NEW.raw_user_meta_data->>'full_name', 'User'),
        COALESCE(UPPER(NEW.raw_user_meta_data->>'role'), 'STUDENT')
    )
    ON CONFLICT (id) DO UPDATE SET
        email = EXCLUDED.email,
        full_name = EXCLUDED.full_name,
        role = EXCLUDED.role;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ==============================================================================
-- ROLE IMMUTABILITY TRIGGER (SECURITY DEFINER)
-- Prevents authenticated users from escalating their role to TEACHER or ADMIN
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.prevent_role_escalation()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF OLD.role IS DISTINCT FROM NEW.role THEN
        RAISE EXCEPTION 'Role changes are not permitted for security reasons.';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS tr_prevent_role_escalation ON public.profiles;
CREATE TRIGGER tr_prevent_role_escalation
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW EXECUTE FUNCTION public.prevent_role_escalation();

-- ==============================================================================
-- ATTENDANCE OVERRIDE AUDIT TRIGGER (SECURITY DEFINER)
-- Enforces server-side audit timestamps and actor identification on manual overrides
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.audit_attendance_override()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF (OLD.marked_present IS DISTINCT FROM NEW.marked_present) THEN
        NEW.manually_overridden := true;
        NEW.manually_overridden_at := now();
        NEW.overridden_by := COALESCE(auth.uid()::text, 'TEACHER');
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS tr_audit_attendance_override ON public.attendance_records;
CREATE TRIGGER tr_audit_attendance_override
    BEFORE UPDATE ON public.attendance_records
    FOR EACH ROW EXECUTE FUNCTION public.audit_attendance_override();

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
    v_uid uuid;
    v_is_student boolean;
BEGIN
    v_uid := auth.uid();
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    -- Verify caller is registered with student role (type-safe comparison)
    SELECT EXISTS (
        SELECT 1 FROM public.profiles 
        WHERE id::text = v_uid::text AND role = 'STUDENT'
    ) INTO v_is_student;

    IF NOT v_is_student THEN
        RAISE EXCEPTION 'Only registered students can join classes';
    END IF;

    SELECT class_id, class_name, subject_code, teacher_id, department, semester
    INTO v_class
    FROM public.classes
    WHERE UPPER(TRIM(join_code)) = UPPER(TRIM(p_join_code)) AND is_active = true;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Invalid class join code';
    END IF;

    -- Insert enrollment safely (compatible with UUID or TEXT columns)
    INSERT INTO public.class_enrollments (class_id, student_id, enrollment_verified)
    VALUES (v_class.class_id::text, v_uid::text, 1)
    ON CONFLICT DO NOTHING;

    RETURN json_build_object(
        'class_id', v_class.class_id::text,
        'class_name', v_class.class_name,
        'subject_code', v_class.subject_code,
        'teacher_id', v_class.teacher_id::text,
        'department', v_class.department,
        'semester', v_class.semester
    );
END;
$$;

REVOKE EXECUTE ON FUNCTION public.join_class_by_code(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.join_class_by_code(text) TO authenticated;

-- ==============================================================================
-- RLS HELPER FUNCTIONS (SECURITY DEFINER)
-- Breaks mutual recursion between classes and class_enrollments policies
-- ==============================================================================
CREATE OR REPLACE FUNCTION public.is_student_in_class(p_class_id text, p_student_id text)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.class_enrollments
        WHERE class_id::text = p_class_id AND student_id::text = p_student_id
    );
$$;

CREATE OR REPLACE FUNCTION public.is_teacher_of_class(p_class_id text, p_teacher_id text)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.classes
        WHERE class_id::text = p_class_id AND teacher_id::text = p_teacher_id
    );
$$;

CREATE OR REPLACE FUNCTION public.is_teacher_of_student(p_student_id text, p_teacher_id text)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.class_enrollments ce
        JOIN public.classes c ON c.class_id::text = ce.class_id::text
        WHERE ce.student_id::text = p_student_id AND c.teacher_id::text = p_teacher_id
    );
$$;

CREATE OR REPLACE FUNCTION public.get_user_role(p_user_id text)
RETURNS text
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
    SELECT role FROM public.profiles WHERE id::text = p_user_id;
$$;

-- ==============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- Idempotent policy setup with DROP POLICY IF EXISTS
-- Both sides are cast to ::text to guarantee type compatibility with UUID or TEXT columns.
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
DROP POLICY IF EXISTS "Allow all on profiles" ON public.profiles;

CREATE POLICY "profiles_select" ON public.profiles FOR SELECT TO authenticated
    USING (id::text = (auth.uid())::text OR public.is_teacher_of_student(public.profiles.id::text, (auth.uid())::text));
CREATE POLICY "profiles_insert" ON public.profiles FOR INSERT TO authenticated
    WITH CHECK (id::text = (auth.uid())::text);
CREATE POLICY "profiles_update" ON public.profiles FOR UPDATE TO authenticated
    USING (id::text = (auth.uid())::text) WITH CHECK (id::text = (auth.uid())::text);
-- Direct client DELETE on profiles is disabled to prevent students from erasing academic history.

-- 2. Students
DROP POLICY IF EXISTS "students_select" ON public.students;
DROP POLICY IF EXISTS "students_insert" ON public.students;
DROP POLICY IF EXISTS "students_update" ON public.students;
DROP POLICY IF EXISTS "students_delete" ON public.students;
DROP POLICY IF EXISTS "Students select policy" ON public.students;
DROP POLICY IF EXISTS "Students insert policy" ON public.students;
DROP POLICY IF EXISTS "Students update policy" ON public.students;
DROP POLICY IF EXISTS "Students delete policy" ON public.students;
DROP POLICY IF EXISTS "Allow all on students" ON public.students;

CREATE POLICY "students_select" ON public.students FOR SELECT TO authenticated
    USING (student_id::text = (auth.uid())::text OR public.is_teacher_of_student(public.students.student_id::text, (auth.uid())::text));
CREATE POLICY "students_insert" ON public.students FOR INSERT TO authenticated
    WITH CHECK (student_id::text = (auth.uid())::text);
CREATE POLICY "students_update" ON public.students FOR UPDATE TO authenticated
    USING (student_id::text = (auth.uid())::text) WITH CHECK (student_id::text = (auth.uid())::text);
CREATE POLICY "students_delete" ON public.students FOR DELETE TO authenticated
    USING (student_id::text = (auth.uid())::text);

-- 3. Teachers (Enforce teacher role on creation)
DROP POLICY IF EXISTS "teachers_select" ON public.teachers;
DROP POLICY IF EXISTS "teachers_insert" ON public.teachers;
DROP POLICY IF EXISTS "teachers_update" ON public.teachers;
DROP POLICY IF EXISTS "teachers_delete" ON public.teachers;
DROP POLICY IF EXISTS "Teachers select policy" ON public.teachers;
DROP POLICY IF EXISTS "Teachers insert policy" ON public.teachers;
DROP POLICY IF EXISTS "Teachers update policy" ON public.teachers;
DROP POLICY IF EXISTS "Teachers delete policy" ON public.teachers;
DROP POLICY IF EXISTS "Allow all on teachers" ON public.teachers;

CREATE POLICY "teachers_select" ON public.teachers FOR SELECT TO authenticated
    USING (
        teacher_id::text = (auth.uid())::text
        OR EXISTS (
            SELECT 1 FROM public.classes c
            JOIN public.class_enrollments ce ON ce.class_id::text = c.class_id::text
            WHERE c.teacher_id::text = public.teachers.teacher_id::text
            AND ce.student_id::text = (auth.uid())::text
        )
    );
CREATE POLICY "teachers_insert" ON public.teachers FOR INSERT TO authenticated
    WITH CHECK (teacher_id::text = (auth.uid())::text AND public.get_user_role((auth.uid())::text) = 'TEACHER');
CREATE POLICY "teachers_update" ON public.teachers FOR UPDATE TO authenticated
    USING (teacher_id::text = (auth.uid())::text) WITH CHECK (teacher_id::text = (auth.uid())::text);
CREATE POLICY "teachers_delete" ON public.teachers FOR DELETE TO authenticated
    USING (teacher_id::text = (auth.uid())::text);

-- 4. Classes (Restrict SELECT to owning teacher and enrolled students; prevent scraping join codes)
DROP POLICY IF EXISTS "classes_select" ON public.classes;
DROP POLICY IF EXISTS "classes_insert" ON public.classes;
DROP POLICY IF EXISTS "classes_update" ON public.classes;
DROP POLICY IF EXISTS "classes_delete" ON public.classes;
DROP POLICY IF EXISTS "Classes select policy" ON public.classes;
DROP POLICY IF EXISTS "Classes insert policy" ON public.classes;
DROP POLICY IF EXISTS "Classes update policy" ON public.classes;
DROP POLICY IF EXISTS "Classes delete policy" ON public.classes;
DROP POLICY IF EXISTS "Allow all on classes" ON public.classes;

CREATE POLICY "classes_select" ON public.classes FOR SELECT TO authenticated
    USING (
        teacher_id::text = (auth.uid())::text
        OR is_active = true
    );
CREATE POLICY "classes_insert" ON public.classes FOR INSERT TO authenticated
    WITH CHECK (
        teacher_id::text = (auth.uid())::text 
        AND public.get_user_role((auth.uid())::text) = 'TEACHER'
    );
CREATE POLICY "classes_update" ON public.classes FOR UPDATE TO authenticated
    USING (teacher_id::text = (auth.uid())::text) WITH CHECK (teacher_id::text = (auth.uid())::text);
CREATE POLICY "classes_delete" ON public.classes FOR DELETE TO authenticated
    USING (teacher_id::text = (auth.uid())::text);

-- 5. Class Enrollments
DROP POLICY IF EXISTS "enrollments_select" ON public.class_enrollments;
DROP POLICY IF EXISTS "enrollments_insert" ON public.class_enrollments;
DROP POLICY IF EXISTS "enrollments_delete" ON public.class_enrollments;
DROP POLICY IF EXISTS "Enrollments select policy" ON public.class_enrollments;
DROP POLICY IF EXISTS "Enrollments insert policy" ON public.class_enrollments;
DROP POLICY IF EXISTS "Enrollments delete policy" ON public.class_enrollments;
DROP POLICY IF EXISTS "Allow all on enrollments" ON public.class_enrollments;

CREATE POLICY "enrollments_select" ON public.class_enrollments FOR SELECT TO authenticated
    USING (
        student_id::text = (auth.uid())::text 
        OR public.is_teacher_of_class(public.class_enrollments.class_id::text, (auth.uid())::text)
    );
CREATE POLICY "enrollments_insert" ON public.class_enrollments FOR INSERT TO authenticated
    WITH CHECK (student_id::text = (auth.uid())::text);
CREATE POLICY "enrollments_delete" ON public.class_enrollments FOR DELETE TO authenticated
    USING (
        student_id::text = (auth.uid())::text 
        OR public.is_teacher_of_class(public.class_enrollments.class_id::text, (auth.uid())::text)
    );

-- 6. Face Embeddings
DROP POLICY IF EXISTS "embeddings_select" ON public.face_embeddings;
DROP POLICY IF EXISTS "embeddings_insert" ON public.face_embeddings;
DROP POLICY IF EXISTS "embeddings_delete" ON public.face_embeddings;
DROP POLICY IF EXISTS "Embeddings select policy" ON public.face_embeddings;
DROP POLICY IF EXISTS "Embeddings insert policy" ON public.face_embeddings;
DROP POLICY IF EXISTS "Embeddings delete policy" ON public.face_embeddings;
DROP POLICY IF EXISTS "Allow all on embeddings" ON public.face_embeddings;

CREATE POLICY "embeddings_select" ON public.face_embeddings FOR SELECT TO authenticated
    USING (
        student_id::text = (auth.uid())::text 
        OR public.is_teacher_of_student(public.face_embeddings.student_id::text, (auth.uid())::text)
    );
CREATE POLICY "embeddings_insert" ON public.face_embeddings FOR INSERT TO authenticated
    WITH CHECK (student_id::text = (auth.uid())::text);
CREATE POLICY "embeddings_delete" ON public.face_embeddings FOR DELETE TO authenticated
    USING (student_id::text = (auth.uid())::text);

-- 7. Attendance Sessions
DROP POLICY IF EXISTS "sessions_select" ON public.attendance_sessions;
DROP POLICY IF EXISTS "sessions_insert" ON public.attendance_sessions;
DROP POLICY IF EXISTS "sessions_update" ON public.attendance_sessions;
DROP POLICY IF EXISTS "sessions_delete" ON public.attendance_sessions;
DROP POLICY IF EXISTS "Sessions select policy" ON public.attendance_sessions;
DROP POLICY IF EXISTS "Sessions insert policy" ON public.attendance_sessions;
DROP POLICY IF EXISTS "Sessions update policy" ON public.attendance_sessions;
DROP POLICY IF EXISTS "Sessions delete policy" ON public.attendance_sessions;
DROP POLICY IF EXISTS "Allow all on sessions" ON public.attendance_sessions;

CREATE POLICY "sessions_select" ON public.attendance_sessions FOR SELECT TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.classes c WHERE c.class_id::text = public.attendance_sessions.class_id::text AND (
            c.teacher_id::text = (auth.uid())::text OR EXISTS (
                SELECT 1 FROM public.class_enrollments ce WHERE ce.class_id::text = c.class_id::text AND ce.student_id::text = (auth.uid())::text
            )
        )
    ));
CREATE POLICY "sessions_insert" ON public.attendance_sessions FOR INSERT TO authenticated
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.classes c WHERE c.class_id::text = public.attendance_sessions.class_id::text AND c.teacher_id::text = (auth.uid())::text
    ));
CREATE POLICY "sessions_update" ON public.attendance_sessions FOR UPDATE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.classes c WHERE c.class_id::text = public.attendance_sessions.class_id::text AND c.teacher_id::text = (auth.uid())::text
    ));
CREATE POLICY "sessions_delete" ON public.attendance_sessions FOR DELETE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.classes c WHERE c.class_id::text = public.attendance_sessions.class_id::text AND c.teacher_id::text = (auth.uid())::text
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
DROP POLICY IF EXISTS "Allow all on records" ON public.attendance_records;

CREATE POLICY "records_select" ON public.attendance_records FOR SELECT TO authenticated
    USING (student_id::text = (auth.uid())::text OR EXISTS (
        SELECT 1 FROM public.attendance_sessions s
        JOIN public.classes c ON c.class_id::text = s.class_id::text
        WHERE s.session_id::text = public.attendance_records.session_id::text AND c.teacher_id::text = (auth.uid())::text
    ));
CREATE POLICY "records_insert" ON public.attendance_records FOR INSERT TO authenticated
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.attendance_sessions s
        JOIN public.classes c ON c.class_id::text = s.class_id::text
        WHERE s.session_id::text = public.attendance_records.session_id::text AND c.teacher_id::text = (auth.uid())::text
    ));
CREATE POLICY "records_update" ON public.attendance_records FOR UPDATE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.attendance_sessions s
        JOIN public.classes c ON c.class_id::text = s.class_id::text
        WHERE s.session_id::text = public.attendance_records.session_id::text AND c.teacher_id::text = (auth.uid())::text
    ));
CREATE POLICY "records_delete" ON public.attendance_records FOR DELETE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.attendance_sessions s
        JOIN public.classes c ON c.class_id::text = s.class_id::text
        WHERE s.session_id::text = public.attendance_records.session_id::text AND c.teacher_id::text = (auth.uid())::text
    ));
