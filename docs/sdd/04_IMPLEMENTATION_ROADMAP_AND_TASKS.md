# Spec 04: Implementation Roadmap & Task Breakdown
**Project:** AttendAI — On-Device Biometric Classroom Attendance System  
**Document ID:** SPEC-TASK-004  
**Status:** In Progress / Active Execution  

---

## 1. Requirements Traceability Matrix (RTM)

| Requirement ID | Module / Component | Primary Files | Assigned Task | Status |
|---|---|---|---|---|
| `REQ-AUTH-001` | Supabase GoTrue Auth | `SupabaseAuthManager.kt`, `SessionManager.kt` | `TASK-001` | 🟢 Done |
| `REQ-AUTH-002` | Role-Based Navigation | `Navigation.kt`, `MainActivity.kt` | `TASK-002` | 🟢 Done |
| `REQ-AUTH-003` | Offline Auth Cache | `Navigation.kt`, `UserDao.kt` | `TASK-003` | 🟢 Done |
| `REQ-AUTH-004` | Password Recovery URI | `MainActivity.kt`, `AuthScreens.kt` | `TASK-004` | 🟢 Done |
| `REQ-BIO-001` | Guided 5-Step Enrollment | `StudentEnrollmentScreen.kt` | `TASK-005` | 🟢 Done |
| `REQ-BIO-002` | Biometric Quality Gate | `FaceDetectorHelper.kt`, `FaceMath.kt` | `TASK-006` | 🟡 Ready for Calibration |
| `REQ-BIO-003` | L2 Unit Normalization | `FaceMath.kt`, `FaceEmbeddingHelper.kt` | `TASK-007` | 🟢 Done |
| `REQ-ATT-001` | CameraX 1080p+ Still Capture | `AttendanceCameraHelper.kt`, `CameraView.kt` | `TASK-008` | 🟢 Done |
| `REQ-ATT-002` | ML Kit BlazeFace Accurate Mode | `FaceDetectorHelper.kt` | `TASK-009` | 🟢 Done |
| `REQ-ATT-003` | Edge TFLite Feature Extraction | `FaceEmbeddingHelper.kt` | `TASK-010` | 🟢 Done |
| `REQ-ATT-004` | Calibrated Confidence Tiers | `FaceMath.kt` | `TASK-011` | 🟡 Ready for Stranger Test |
| `REQ-ATT-005` | Dual-Capture Conjunction Engine | `ClassroomAttendanceScreen.kt` | `TASK-012` | 🟢 Done |
| `REQ-ATT-006` | Faculty Review & Overrides | `ClassroomAttendanceScreen.kt` | `TASK-013` | 🟢 Done |
| `REQ-DATA-001` | Room SQLite DB Schema v6 | `AppDatabase.kt`, `Entities.kt`, `Daos.kt`| `TASK-014` | 🟢 Done |
| `REQ-DATA-002` | Asynchronous Cloud Sync | `CloudSyncManager.kt` | `TASK-015` | 🟢 Done |
| `REQ-DATA-003` | Standard CSV Export | `TeacherDashboardScreen.kt`, `ClassroomAttendanceScreen.kt` | `TASK-016` | 🟢 Done |

---

## 2. Granular Task Breakdown (9 Target Changes / Enhancements)

Below are the 9 specific implementation and verification tasks scheduled for execution under the SDD framework:

---

### Task 1: Navigation Route Parameter Sanitization & URL Encoding
* **Task ID**: `TASK-001-NAV-ENCODE`
* **Target Files**: [`app/src/main/java/com/vaibhav/facialattendancesystem/ui/Navigation.kt`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/app/src/main/java/com/vaibhav/facialattendancesystem/ui/Navigation.kt)
* **Goal**: Ensure that user names, class names, and tokens containing spaces, slashes, or symbols never cause Jetpack Compose NavHost parse exceptions.
* **Acceptance Criteria**:
  1. All `createRoute(...)` calls URL-encode dynamic path strings.
  2. All composable argument readers decode parameters via `URLDecoder.decode(..., "UTF-8")` with fallback.
* **Status**: 🟢 **Completed & Verified**

---

### Task 2: Kotlin 2.0 Toolchain & AGP Compilation Unification
* **Task ID**: `TASK-002-KOTLIN-BUILD`
* **Target Files**:
  - [`gradle/libs.versions.toml`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/gradle/libs.versions.toml)
  - [`build.gradle.kts`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/build.gradle.kts)
  - [`app/build.gradle.kts`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/app/build.gradle.kts)
  - [`gradle.properties`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/gradle.properties)
* **Goal**: Restore missing `org.jetbrains.kotlin.android` plugin, unify Java 17 / Kotlin 17 / KSP toolchain via `jvmToolchain(17)`, and suppress AGP 37 preview warnings.
* **Acceptance Criteria**:
  1. Zero `ClassNotFoundException` for `MainActivity`.
  2. Gradle `./gradlew assembleDebug` completes with 0 errors.
* **Status**: 🟢 **Completed & Verified on Device**

---

### Task 3: Stranger Test & Confidence Threshold Calibration
* **Task ID**: `TASK-003-STRANGER-TEST`
* **Target Files**: [`app/src/main/java/com/vaibhav/facialattendancesystem/ml/FaceMath.kt`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/app/src/main/java/com/vaibhav/facialattendancesystem/ml/FaceMath.kt)
* **Goal**: Benchmark cosine similarity distributions on registered students vs. unregistered strangers to validate the High ($\ge 0.40$) and Medium ($[0.30, 0.40)$) thresholds.
* **Acceptance Criteria**:
  1. Record similarity scores across 5 enrolled faces and 5 unknown faces.
  2. Stranger false acceptance rate $< 0.1\%$ at $\ge 0.40$.
  3. Adjust thresholds in `FaceMath.kt` if empirical distribution suggests higher separation boundary (e.g., $0.45$).
* **Status**: 🟡 **Pending Physical Classroom Test**

---

### Task 4: CameraX Memory Footprint & Bitmap Recycling
* **Task ID**: `TASK-004-CAM-OPT`
* **Target Files**: [`app/src/main/java/com/vaibhav/facialattendancesystem/ui/components/AttendanceCameraHelper.kt`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/app/src/main/java/com/vaibhav/facialattendancesystem/ui/components/AttendanceCameraHelper.kt)
* **Goal**: Prevent Android `OutOfMemoryError` (OOM) when capturing 12MP high-resolution still images in succession.
* **Acceptance Criteria**:
  1. Intermediate cropped face bitmaps explicitly recycled (`bitmap.recycle()`) after tensor normalization.
  2. Background single-thread executors cleanly terminated in `release()`.
* **Status**: 🟢 **Verified Stable**

---

### Task 5: MobileFaceNet vs. AdaFace Model Switcher
* **Task ID**: `TASK-005-MODEL-SWITCH`
* **Target Files**: [`app/src/main/java/com/vaibhav/facialattendancesystem/ml/FaceEmbeddingHelper.kt`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/app/src/main/java/com/vaibhav/facialattendancesystem/ml/FaceEmbeddingHelper.kt)
* **Goal**: Support runtime or compile-time switching between MobileFaceNet (192-d, lightweight) and AdaFace (512-d, quality-adaptive for low-res distance faces).
* **Acceptance Criteria**:
  1. Assets directory verified for presence of both `mobilefacenet.tflite` (301 KB) and `adaface_model.tflite` (96 MB).
  2. Tensor shape checks dynamically adjust output buffer size ($192$ vs $512$).
* **Status**: ⏸️ **Deferred to Later / Post-MVP** (Current stable pipeline retained)

---

### Task 6: Room SQLite Database Migration Safety & Verification
* **Task ID**: `TASK-006-ROOM-MIGRATE`
* **Target Files**:
  - [`app/src/main/java/com/vaibhav/facialattendancesystem/data/AppDatabase.kt`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/app/src/main/java/com/vaibhav/facialattendancesystem/data/AppDatabase.kt)
  - [`app/src/main/java/com/vaibhav/facialattendancesystem/data/Entities.kt`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/app/src/main/java/com/vaibhav/facialattendancesystem/data/Entities.kt)
* **Goal**: Ensure Room schema v6 handles table creation and column queries without SQLite runtime syntax crashes.
* **Acceptance Criteria**:
  1. Room queries for classes, enrollments, sessions, and records execute cleanly.
  2. Destructive migration safely recreates tables during schema updates without crash.
* **Status**: 🟢 **Verified on Device**

---

### Task 7: Dual-Capture Temporal Correlation & State Tracking
* **Task ID**: `TASK-007-DUAL-CAPTURE`
* **Target Files**: [`app/src/main/java/com/vaibhav/facialattendancesystem/ui/teacher/ClassroomAttendanceScreen.kt`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/app/src/main/java/com/vaibhav/facialattendancesystem/ui/teacher/ClassroomAttendanceScreen.kt)
* **Goal**: Provide transparent UI indicators for Photo 1 (Start of Class) and Photo 2 (End of Class), computing dual-presence status with automatic summary badges.
* **Acceptance Criteria**:
  1. Teacher cannot commit session without either taking Photo 2 or explicitly overriding early departure.
  2. Status badges clearly show "Photo 1: Present", "Photo 2: Present" $\implies$ "Final: PRESENT".
* **Status**: 🟢 **Verified on Device**

---

### Task 8: Teacher Review Screen Ambiguity Cards & Manual Toggles
* **Task ID**: `TASK-008-TEACHER-REVIEW`
* **Target Files**: [`app/src/main/java/com/vaibhav/facialattendancesystem/ui/teacher/ClassroomAttendanceScreen.kt`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/app/src/main/java/com/vaibhav/facialattendancesystem/ui/teacher/ClassroomAttendanceScreen.kt)
* **Goal**: Present faculty with responsive card carousels showing faces needing review, with quick one-tap toggle for students sitting far away.
* **Acceptance Criteria**:
  1. Medium confidence matches ($0.30 - 0.40$) display cropped face chip alongside registered student profile.
  2. Teacher taps "Confirm" or "Reject"; state updates immediately in Compose list.
* **Status**: 🟢 **Verified on Device**

---

### Task 9: CSV Export & Institutional ERP Formatting
* **Task ID**: `TASK-009-CSV-EXPORT`
* **Target Files**:
  - [`app/src/main/java/com/vaibhav/facialattendancesystem/ui/teacher/TeacherDashboardScreen.kt`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/app/src/main/java/com/vaibhav/facialattendancesystem/ui/teacher/TeacherDashboardScreen.kt)
  - [`app/src/main/java/com/vaibhav/facialattendancesystem/util/CsvExportHelper.kt`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/app/src/main/java/com/vaibhav/facialattendancesystem/util/NotificationHelper.kt)
* **Goal**: Generate institutional standard CSV attendance registers with UTF-8 encoding and Android Share sheet intent.
* **Acceptance Criteria**:
  1. CSV contains: Roll Number, Name, Photo 1 Status, Photo 2 Status, Final Status, Confidence %, Timestamp.
  2. Saved safely to device external downloads/documents with Android 13+ scoped storage compliance.
* **Status**: 🟢 **Verified on Device**

---

### Task 10: Multi-Face Group Capture Engine
* **Task ID**: `TASK-010-MULTI-GROUP`
* **Target Files**:
  - [`app/src/main/java/com/vaibhav/facialattendancesystem/ml/FaceMath.kt`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/app/src/main/java/com/vaibhav/facialattendancesystem/ml/FaceMath.kt)
  - [`app/src/main/java/com/vaibhav/facialattendancesystem/ui/components/AttendanceCameraHelper.kt`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/app/src/main/java/com/vaibhav/facialattendancesystem/ui/components/AttendanceCameraHelper.kt)
  - [`app/src/main/java/com/vaibhav/facialattendancesystem/ui/teacher/ClassroomAttendanceScreen.kt`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/app/src/main/java/com/vaibhav/facialattendancesystem/ui/teacher/ClassroomAttendanceScreen.kt)
  - [`app/src/test/java/com/vaibhav/facialattendancesystem/FaceMathTest.kt`](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/app/src/test/java/com/vaibhav/facialattendancesystem/FaceMathTest.kt)
* **Goal**: Enable reliable capture, detection, and recognition of multiple students simultaneously in a single photo, preventing greedy match collisions and eliminating intrusive modal loops.
* **Acceptance Criteria**:
  1. 1-to-1 Bipartite matching in `FaceMath.assignMultiFaceMatches` ensures zero student double-booking across multiple faces.
  2. Camera preview analysis stream provides real-time `👥 X Faces in Frame` counter.
  3. Clean single-action capture flow: teacher snaps photo and directly enters Review screen with all recognized students marked.
* **Status**: 🟢 **Gate 1 (Build) & Gate 2 (Unit Tests) Passed — Verified**

