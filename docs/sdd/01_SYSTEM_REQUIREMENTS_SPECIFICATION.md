# Spec 01: System Requirements Specification (SRS)
**Project:** AttendAI — On-Device Biometric Classroom Attendance System  
**Document ID:** SPEC-SRS-001  
**Status:** Approved Baseline  
**Target Platform:** Android 8.0+ (API 26+) / Tested on Motorola g34 5G (Snapdragon 695, Android 14)

---

## 1. Product Scope & Objectives

AttendAI is an edge-first, AI-powered biometric attendance application designed to automate student attendance marking in high-density university classrooms (up to 60–80 students).

### Key Objectives:
1. **Eliminate Proxy Attendance**: Replace physical call-outs and paper signature sheets with biometric facial feature verification coupled with a **Dual-Capture temporal checkpoint** (start of lecture + end of lecture).
2. **Zero Cloud Latency on Capture**: Perform 100% of detection, quality filtering, feature extraction, and roster matching locally on the instructor's smartphone without waiting for network API round-trips.
3. **100% Offline Capability**: Function reliably in campus network dead zones, basements, or congested Wi-Fi environments using local Room SQLite storage with background sync when connectivity resumes.
4. **Transparent Faculty Oversight**: Maintain the instructor as the final arbiter with instant review cards, ambiguity flags, and manual override capability before finalizing records.

---

## 2. User Personas & Actors

| Persona | Role | Key Responsibilities | Device / Interface |
|---|---|---|---|
| **Faculty / Instructor (`TEACHER`)** | Primary Operator | Creates classes, initiates Dual-Capture attendance sessions, reviews flagged detections, confirms attendance, exports CSVs. | Instructor Android smartphone |
| **Student (`STUDENT`)** | Biometric Subject | Enrolls multi-angle facial template (5-step enrollment), joins classes via class codes, tracks personal attendance percentage & log. | Personal Android smartphone |
| **Department Admin** | Institutional Reviewer | Audits attendance history, imports CSV records into campus ERP, verifies proxy anomaly reports. | Web / ERP Ingestion |

---

## 3. Functional Requirements Catalog

### 3.1 Authentication & Profile Management (`REQ-AUTH`)

| ID | Requirement Statement | Acceptance Criteria |
|---|---|---|
| `REQ-AUTH-001` | **Official Supabase GoTrue Auth** | Authentication must use Supabase GoTrue REST (`/auth/v1/token?grant_type=password`). Password hashing must occur server-side with bcrypt; zero plain-text passwords in database. |
| `REQ-AUTH-002` | **Role-Based Routing** | Logins must resolve user role (`TEACHER` vs `STUDENT`). Teachers navigate to `TeacherDashboard`; students navigate to `StudentDashboard`. Portal mismatch must display explicit banner. |
| `REQ-AUTH-003` | **Offline Fallback Credential Cache** | When network is unreachable (DNS timeout/503), the app must verify credentials against local Room SQLite `users` table cache to permit offline classroom usage. |
| `REQ-AUTH-004` | **Password Recovery Flow** | App must support password reset links with custom deep link (`facialattendance://reset-password?token=...`). |

### 3.2 Student Biometric Enrollment (`REQ-BIO-ENROLL`)

| ID | Requirement Statement | Acceptance Criteria |
|---|---|---|
| `REQ-BIO-001` | **Multi-Angle Guided Capture** | Enrollment must capture 5 discrete poses: Frontal, Tilt Left (15°), Tilt Right (15°), Tilt Up (10°), Slight Smile. |
| `REQ-BIO-002` | **On-Device Quality Gate** | Enrollment frames must be rejected if Laplacian blur variance $< 100.0$ or face bounding box $< 150\times 150$ px. |
| `REQ-BIO-003` | **Composite Vector Generation** | The app must compute an averaged, L2-normalized 192-d embedding vector from the 5 valid poses to serve as the baseline template. |
| `REQ-BIO-004` | **Cloud Sync of Embeddings** | Enrolled 192-d vectors must sync to Supabase `face_embeddings` table (`BYTEA` / Base64) to allow cross-device student recognition. |

### 3.3 Classroom Attendance Capture & Processing (`REQ-ATT`)

| ID | Requirement Statement | Acceptance Criteria |
|---|---|---|
| `REQ-ATT-001` | **Wide-Angle Multi-Face Ingestion** | CameraX must capture classroom still photos at maximum sensor resolution ($1080p$ minimum, up to $8$–$12$ MP). |
| `REQ-ATT-002` | **Simultaneous Multi-Face Detection** | ML Kit BlazeFace must detect up to 30+ faces per shot with `minFaceSize = 0.02f` and `PERFORMANCE_MODE_ACCURATE`. |
| `REQ-ATT-003` | **Real-Time Vector Extraction** | Each cropped face must be normalized to $112\times 112$ px and processed via quantized MobileFaceNet/AdaFace within $< 40$ ms per face on GPU/NNAPI. |
| `REQ-ATT-004` | **Tiered Metric Classification** | Cosine similarity against enrolled class roster must categorize: $\ge 0.40 \to$ High (Auto-Present); $[0.30, 0.40) \to$ Medium (Review Flag); $< 0.30 \to$ Stranger. |
| `REQ-ATT-005` | **Dual-Capture Temporal Conjunction** | Full attendance requires presence in Photo 1 (Start of class) AND Photo 2 (End of class). Status = $P_1 \land P_2$. Single-photo presence marked as "Incomplete / Early Departure". |
| `REQ-ATT-006` | **Faculty Review & Manual Overrides** | Jetpack Compose review screen must present review cards for ambiguous faces with one-tap toggle for Present/Absent. |

### 3.4 Data Persistence, Synchronization & Export (`REQ-DATA`)

| ID | Requirement Statement | Acceptance Criteria |
|---|---|---|
| `REQ-DATA-001` | **Room SQLite Local Persistence** | All session metadata, raw logs, and student attendance statuses must be committed transactionally in Room DB (Schema v6). |
| `REQ-DATA-002` | **Asynchronous Cloud Sync** | An Android WorkManager task or coroutine syncs pending sessions to Supabase when network is active without blocking the UI. |
| `REQ-DATA-003` | **Standard CSV Export** | The teacher can export formatted CSV reports containing: Student Roll, Full Name, Photo 1 Status, Photo 2 Status, Final Status, Confidence Score, Timestamp. |

---

## 4. Non-Functional Requirements (NFR)

### 4.1 Performance & Latency Budgets
- **Cold App Startup**: $< 1.8$ seconds on mid-range devices (Snapdragon 695).
- **Face Detection Latency**: $< 200$ ms for 20 faces in a single $1080p$ frame.
- **Feature Extraction Latency**: $< 35$ ms per face chip on NNAPI / GPU delegate.
- **Classroom Roster Matching (60 students)**: $< 15$ ms total for 30 detected faces.

### 4.2 Accuracy & False Acceptance Thresholds
- **False Acceptance Rate (FAR)**: $< 0.1\%$ at calibrated High Confidence threshold ($\ge 0.40$).
- **Stranger Rejection Rate**: $> 99.0\%$ on unregistered non-class individuals.
- **Minimum Detectable Face Size**: $32\times 32$ pixels in high-res camera frame.

### 4.3 Memory & Resource Constraints
- **Peak RAM Usage**: $\le 380$ MB during active CameraX 12MP processing and TFLite tensor allocation.
- **APK Size**: $\le 320$ MB including all bundled TFLite weights (`adaface_model.tflite`, `mobilefacenet.tflite`, `blaze_face_short_range.tflite`).

### 4.4 Security & Privacy
- **Biometric Templates**: Stored strictly as abstract mathematical 192-d floating point vectors. Raw facial images are never transmitted or stored permanently.
- **Encrypted Channels**: All Supabase REST API traffic strictly over TLS 1.3.
- **Row-Level Security (RLS)**: Enforced on Postgres tables; teachers only view their classes; students only view their personal records.
