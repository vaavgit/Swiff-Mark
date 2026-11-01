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

-- 1. Profiles: Users can read/write their own profile; teachers can read profiles of enrolled students
CREATE POLICY "Profiles select policy" ON public.profiles FOR SELECT TO authenticated
    USING (auth.uid()::text = id OR EXISTS (
        SELECT 1 FROM public.class_enrollments ce
        JOIN public.classes c ON c.class_id = ce.class_id
        WHERE ce.student_id = public.profiles.id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "Profiles insert policy" ON public.profiles FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = id);
CREATE POLICY "Profiles update policy" ON public.profiles FOR UPDATE TO authenticated
    USING (auth.uid()::text = id) WITH CHECK (auth.uid()::text = id);
CREATE POLICY "Profiles delete policy" ON public.profiles FOR DELETE TO authenticated
    USING (auth.uid()::text = id);

-- 2. Students: Students can manage own profile; teachers can read enrolled students
CREATE POLICY "Students select policy" ON public.students FOR SELECT TO authenticated
    USING (auth.uid()::text = student_id OR EXISTS (
        SELECT 1 FROM public.class_enrollments ce
        JOIN public.classes c ON c.class_id = ce.class_id
        WHERE ce.student_id = public.students.student_id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "Students insert policy" ON public.students FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = student_id);
CREATE POLICY "Students update policy" ON public.students FOR UPDATE TO authenticated
    USING (auth.uid()::text = student_id) WITH CHECK (auth.uid()::text = student_id);
CREATE POLICY "Students delete policy" ON public.students FOR DELETE TO authenticated
    USING (auth.uid()::text = student_id);

-- 3. Teachers: Readable by authenticated users; managed only by the teacher
CREATE POLICY "Teachers select policy" ON public.teachers FOR SELECT TO authenticated USING (true);
CREATE POLICY "Teachers insert policy" ON public.teachers FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = teacher_id);
CREATE POLICY "Teachers update policy" ON public.teachers FOR UPDATE TO authenticated
    USING (auth.uid()::text = teacher_id) WITH CHECK (auth.uid()::text = teacher_id);
CREATE POLICY "Teachers delete policy" ON public.teachers FOR DELETE TO authenticated
    USING (auth.uid()::text = teacher_id);

-- 4. Classes: Searchable by code; insert/update/delete restricted to owning teacher
CREATE POLICY "Classes select policy" ON public.classes FOR SELECT TO authenticated USING (true);
CREATE POLICY "Classes insert policy" ON public.classes FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = teacher_id);
CREATE POLICY "Classes update policy" ON public.classes FOR UPDATE TO authenticated
    USING (auth.uid()::text = teacher_id) WITH CHECK (auth.uid()::text = teacher_id);
CREATE POLICY "Classes delete policy" ON public.classes FOR DELETE TO authenticated
    USING (auth.uid()::text = teacher_id);

-- 5. Class Enrollments: Students enroll themselves; teachers read/remove enrollments for their classes
CREATE POLICY "Enrollments select policy" ON public.class_enrollments FOR SELECT TO authenticated
    USING (auth.uid()::text = student_id OR EXISTS (
        SELECT 1 FROM public.classes c WHERE c.class_id = public.class_enrollments.class_id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "Enrollments insert policy" ON public.class_enrollments FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = student_id);
CREATE POLICY "Enrollments delete policy" ON public.class_enrollments FOR DELETE TO authenticated
    USING (auth.uid()::text = student_id OR EXISTS (
        SELECT 1 FROM public.classes c WHERE c.class_id = public.class_enrollments.class_id AND c.teacher_id = auth.uid()::text
    ));

-- 6. Face Embeddings: Strictly managed by student; readable by student and teachers of their classes
CREATE POLICY "Embeddings select policy" ON public.face_embeddings FOR SELECT TO authenticated
    USING (auth.uid()::text = student_id OR EXISTS (
        SELECT 1 FROM public.class_enrollments ce
        JOIN public.classes c ON c.class_id = ce.class_id
        WHERE ce.student_id = public.face_embeddings.student_id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "Embeddings insert policy" ON public.face_embeddings FOR INSERT TO authenticated
    WITH CHECK (auth.uid()::text = student_id);
CREATE POLICY "Embeddings delete policy" ON public.face_embeddings FOR DELETE TO authenticated
    USING (auth.uid()::text = student_id);

-- 7. Attendance Sessions: Managed exclusively by the class's teacher; readable by enrolled students
CREATE POLICY "Sessions select policy" ON public.attendance_sessions FOR SELECT TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.classes c WHERE c.class_id = public.attendance_sessions.class_id AND (
            c.teacher_id = auth.uid()::text OR EXISTS (
                SELECT 1 FROM public.class_enrollments ce WHERE ce.class_id = c.class_id AND ce.student_id = auth.uid()::text
            )
        )
    ));
CREATE POLICY "Sessions insert policy" ON public.attendance_sessions FOR INSERT TO authenticated
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.classes c WHERE c.class_id = public.attendance_sessions.class_id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "Sessions update policy" ON public.attendance_sessions FOR UPDATE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.classes c WHERE c.class_id = public.attendance_sessions.class_id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "Sessions delete policy" ON public.attendance_sessions FOR DELETE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.classes c WHERE c.class_id = public.attendance_sessions.class_id AND c.teacher_id = auth.uid()::text
    ));

-- 8. Attendance Records: Only teachers can mark attendance; students have read-only access to their own records
CREATE POLICY "Records select policy" ON public.attendance_records FOR SELECT TO authenticated
    USING (auth.uid()::text = student_id OR EXISTS (
        SELECT 1 FROM public.attendance_sessions s
        JOIN public.classes c ON c.class_id = s.class_id
        WHERE s.session_id = public.attendance_records.session_id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "Records insert policy" ON public.attendance_records FOR INSERT TO authenticated
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.attendance_sessions s
        JOIN public.classes c ON c.class_id = s.class_id
        WHERE s.session_id = public.attendance_records.session_id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "Records update policy" ON public.attendance_records FOR UPDATE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.attendance_sessions s
        JOIN public.classes c ON c.class_id = s.class_id
        WHERE s.session_id = public.attendance_records.session_id AND c.teacher_id = auth.uid()::text
    ));
CREATE POLICY "Records delete policy" ON public.attendance_records FOR DELETE TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.attendance_sessions s
        JOIN public.classes c ON c.class_id = s.class_id
        WHERE s.session_id = public.attendance_records.session_id AND c.teacher_id = auth.uid()::text
    ));