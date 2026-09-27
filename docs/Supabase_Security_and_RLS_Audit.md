# Supabase Security & RLS Policy Audit Report

This report documents the security audit across your Supabase database tables, explains the policy fixes implemented in [`supabase_security_audit_and_policies.sql`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/supabase_security_audit_and_policies.sql), and provides the dashboard settings for the authentication migration.

---

## 1. Urgent Audit Findings ("Do This Today")

### 🚨 1.1 The `profiles` Table Exposure
* **The Vulnerability:**
  Previously, `public.profiles` had a blanket policy:
  ```sql
  CREATE POLICY "Allow all on profiles" ON public.profiles FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
  ```
  Because the policy targeted both `anon` and `authenticated` with `USING (true)`, **any person with your public anon key could read all user profiles, including plaintext passwords stored in `avatar_url` (`pwd:password123`)**.
* **Remediation in SQL Script:**
  1. Purges all plaintext passwords sitting in `avatar_url`:
     ```sql
     UPDATE public.profiles SET avatar_url = NULL WHERE avatar_url LIKE 'pwd:%';
     ```
  2. Drops the permissive policy and disables all `anon` access.
  3. Replaces it with 4 strict, explicit policies (`SELECT`, `INSERT`, `UPDATE`, `DELETE`):
     - `SELECT`: Strictly scoped to `auth.uid()::text = id` OR a teacher whose class the student is enrolled in (`ce.student_id = profiles.id AND c.teacher_id = auth.uid()::text`).
     - `INSERT / UPDATE / DELETE`: Strictly restricted to `auth.uid()::text = id`.

---

## 2. Table-by-Table Security Audit Across All 9 Tables

| Table | Threat / Attack Vector if Permissive | Production Policy Applied in Script |
| :--- | :--- | :--- |
| **`profiles`** | Anyone reading passwords or other teachers reading unrelated student profiles. | `SELECT` restricted to own profile OR a teacher whose class the student is enrolled in; `INSERT/UPDATE/DELETE` restricted to `auth.uid() = id`. `anon` blocked. |
| **`students`** | Teachers seeing students outside their courses or students modifying others' details. | `SELECT` restricted to self OR teacher whose class the student is enrolled in; `INSERT/UPDATE/DELETE` restricted to `auth.uid() = student_id`. |
| **`teachers`** | Impersonating faculty or modifying department data. | `SELECT` public to authenticated; `INSERT/UPDATE/DELETE` restricted to `auth.uid() = teacher_id`. |
| **`classes`** | Students modifying class names or deleting courses. | `SELECT` open to authenticated (needed for join codes); `INSERT` restricted to teachers; `UPDATE/DELETE` restricted to the owning `teacher_id`. |
| **`class_enrollments`** | A student fake-enrolling other students into classes. | `INSERT` strictly restricted to `student_id = auth.uid()::text`. Students can only enroll themselves. |
| **`face_embeddings`** | Unauthorized harvesting or tampering with biometric vectors. | `SELECT` restricted to the student or teachers of their enrolled classes; `INSERT/UPDATE/DELETE` restricted to `student_id = auth.uid()::text`. |
| **`attendance_sessions`** | Students creating fake sessions or deleting lectures. | `SELECT` allowed for enrolled students & teacher; `INSERT/UPDATE/DELETE` strictly restricted to the class's `teacher_id`. |
| **`attendance_records`** | **CRITICAL:** Students marking themselves present via direct API calls, bypassing facial recognition. | `SELECT` restricted to own record (`student_id = auth.uid()::text`) or teacher; **`INSERT/UPDATE/DELETE` strictly restricted to the teacher owning the session.** Students have ZERO write access! |

---

## 3. Supabase Auth Settings for Migration

### 3.1 Confirm Email Setting (Verified)
* **Status:** In our API inspection of your project's GoTrue settings:
  ```json
  "mailer_autoconfirm": false,
  "email": true
  ```
  **"Confirm email" is currently ACTIVE (ON).**
  Supabase will automatically send confirmation emails on `signUp`.

### 3.2 Implicit vs. PKCE Flow Verification
* In Supabase GoTrue, password recovery emails trigger:
  `https://zayyocgtefnpqlegoqkx.supabase.co/auth/v1/verify?token=...&type=recovery&redirect_to=facialattendance://reset-password`
* When tapped on mobile:
  - **Implicit Flow:** Redirects to `facialattendance://reset-password#access_token=...&refresh_token=...&type=recovery`.
  - **PKCE Flow:** Redirects to `facialattendance://reset-password?code=...`.
* **Our Client Strategy:** In [`MainActivity.kt`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/app/src/main/java/com/vaibhav/facialattendancesystem/MainActivity.kt), our deep-link parser handles **both formats simultaneously**:
  - If `#access_token=` is in the URL fragment, it extracts the token directly.
  - If `?code=` is in the URL query, it exchanges the code via `/auth/v1/token?grant_type=pkce`.
  This guarantees the reset flow never fails regardless of project setting toggles.

### 3.3 Add Allowed Redirect URL in Dashboard
> [!IMPORTANT]
> **Action Required in Supabase Dashboard:**
> 1. Go to: **Authentication** $\rightarrow$ **URL Configuration** $\rightarrow$ **Redirect URLs**.
> 2. Click **Add URL**.
> 3. Enter exactly:
>    ```
>    facialattendance://reset-password
>    ```
> 4. Click **Save**.

---

## 4. How to Apply the Policies (1-Step Action)

The complete SQL script has been placed in your project root:
📄 **[`supabase_security_audit_and_policies.sql`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/supabase_security_audit_and_policies.sql)**

1. Open your [Supabase SQL Editor](https://supabase.com/dashboard/project/zayyocgtefnpqlegoqkx/sql).
2. Copy the contents of `supabase_security_audit_and_policies.sql`.
3. Paste into the editor and click **Run**.
4. All 9 tables will be locked down, plaintext passwords in `avatar_url` will be wiped, and unauthorized attendance self-marking will be blocked at the database level.
