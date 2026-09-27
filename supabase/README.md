# Supabase Backend Setup

This directory contains the database migration scripts and Row Level Security (RLS) policies for **Swiff Mark** (AI-Powered Facial Attendance System).

---

## Quick Setup Instructions

### 1. Create a Supabase Project
1. Log in to [Supabase](https://supabase.com).
2. Click **New Project** and configure your organization, project name, and database password.

### 2. Run Database Schema
1. Open your project dashboard and go to **SQL Editor** (left sidebar).
2. Click **New Query**.
3. Copy the contents of [`schema.sql`](./schema.sql) and paste them into the editor.
4. Click **Run**. This will create:
   - `profiles` — User profile details, roles (`TEACHER`, `STUDENT`), and avatar URLs.
   - `students` — Student-specific academic metadata (roll number, semester, department).
   - `teachers` — Faculty metadata (employee ID, department, designation).
   - `classes` — Course and classroom details with unique join codes.
   - `class_enrollments` — Student enrollment mappings.
   - `face_embeddings` — 512-dimensional AdaFace facial embedding vectors.
   - `attendance_sessions` — Timestamped lecture sessions created by faculty.
   - `attendance_records` — Per-student session results (photo 1 matched, photo 2 matched, dual presence, final status).

### 3. Apply Production Security & RLS Policies
1. In the **SQL Editor**, open another **New Query**.
2. Copy the contents of [`policies.sql`](./policies.sql) and paste them into the editor.
3. Click **Run**. This establishes strict Row Level Security (RLS) rules:
   - **Teachers** can only view/manage classes and attendance sessions they created.
   - **Students** can only view their own enrolled classes and attendance history.
   - **Biometric Embeddings** are protected against unauthorized manipulation.

### 4. Connect to the Android App
1. Go to **Project Settings** $\rightarrow$ **API**.
2. Copy your **Project URL** and **anon / public key**.
3. Paste them into your `local.properties` file in the Android project root:
   ```properties
   SUPABASE_URL=https://<your-project-ref>.supabase.co
   SUPABASE_ANON_KEY=your_anon_key_here
   ```
4. Build and run the app.
