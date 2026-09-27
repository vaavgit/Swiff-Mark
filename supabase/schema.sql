-- ==============================================================================
-- SWIFF MARK: SUPABASE DATABASE SCHEMA
-- Run this script in your Supabase project's SQL Editor (https://supabase.com/dashboard)
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

-- 4. CLASSES TABLE (With Unique 6-8 char Join Codes)
CREATE TABLE IF NOT EXISTS public.classes (
    class_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    teacher_id TEXT,
    class_name TEXT NOT NULL,
    subject_code TEXT NOT NULL,
    department TEXT,
    semester INT DEFAULT 1,
    join_code VARCHAR(12) UNIQUE NOT NULL, -- e.g. "CS-4921"
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_classes_join_code ON public.classes(join_code);

-- 5. CLASS ENROLLMENTS (When student enters the join code)
CREATE TABLE IF NOT EXISTS public.class_enrollments (
    enrollment_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    class_id TEXT NOT NULL REFERENCES public.classes(class_id) ON DELETE CASCADE,
    student_id TEXT NOT NULL,
    enrolled_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(class_id, student_id)
);
CREATE INDEX IF NOT EXISTS idx_enrollment_class ON public.class_enrollments(class_id);
CREATE INDEX IF NOT EXISTS idx_enrollment_student ON public.class_enrollments(student_id);

-- 6. FACE EMBEDDINGS (AdaFace 512-D vectors stored as compact Base64)
CREATE TABLE IF NOT EXISTS public.face_embeddings (
    embedding_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    student_id TEXT NOT NULL,
    embedding_vector_base64 TEXT NOT NULL,
    angle_label TEXT DEFAULT 'FRONTAL',
    quality_score REAL DEFAULT 1.0,
    is_primary BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_embeddings_student ON public.face_embeddings(student_id);

-- 7. ATTENDANCE SESSIONS (Teacher creates when marking class)
CREATE TABLE IF NOT EXISTS public.attendance_sessions (
    session_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    class_id TEXT NOT NULL,
    teacher_id TEXT,
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
    student_id TEXT NOT NULL,
    photo1_matched BOOLEAN DEFAULT false,
    photo2_matched BOOLEAN DEFAULT false,
    dual_capture_present BOOLEAN DEFAULT false,
    marked_present BOOLEAN NOT NULL DEFAULT false,
    confidence_score REAL DEFAULT 0.0,
    manually_overridden BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(session_id, student_id)
);
CREATE INDEX IF NOT EXISTS idx_records_session ON public.attendance_records(session_id);
CREATE INDEX IF NOT EXISTS idx_records_student ON public.attendance_records(student_id);

-- ==============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- Allows access via anon public key and authenticated users
-- ==============================================================================
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.students ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.teachers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.classes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.class_enrollments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.face_embeddings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance_records ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow all on profiles" ON public.profiles FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow all on students" ON public.students FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow all on teachers" ON public.teachers FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow all on classes" ON public.classes FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow all on enrollments" ON public.class_enrollments FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow all on embeddings" ON public.face_embeddings FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow all on sessions" ON public.attendance_sessions FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow all on records" ON public.attendance_records FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);