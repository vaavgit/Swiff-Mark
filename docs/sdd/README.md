# AttendAI — Spec-Driven Development (SDD) Documentation Suite

Welcome to the **Spec-Driven Development (SDD)** documentation repository for **AttendAI (Facial Attendance System)**.

Under the SDD standard, all engineering changes, AI pair-programming sessions (e.g. Claude Sonnet / Antigravity), and hardware calibrations are driven by formal specifications that serve as the Single Source of Truth.

---

## 📑 Specification Index

```
docs/sdd/
├── README.md                                  <- Master Index (This File)
├── 00_SDD_OVERVIEW_AND_WORKFLOW.md            <- SDD Lifecycle, Roles & Definition of Done
├── 01_SYSTEM_REQUIREMENTS_SPECIFICATION.md     <- System Requirements Specification (SRS/PRD)
├── 02_TECHNICAL_ARCHITECTURE_SPECIFICATION.md  <- Technical Design & Architecture (TDS)
├── 03_PIPELINE_AND_ALGORITHM_SPECIFICATION.md  <- Complete 9-Step Mathematical & AI Pipeline
├── 04_IMPLEMENTATION_ROADMAP_AND_TASKS.md      <- Traceability Matrix & 9 Target Changes
└── 05_TEST_AND_VERIFICATION_SPECIFICATION.md   <- Quality Assurance & Stranger Test Protocol
```

---

## 🚀 Quick Document Navigation

1. **[00_SDD_OVERVIEW_AND_WORKFLOW.md](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/docs/sdd/00_SDD_OVERVIEW_AND_WORKFLOW.md)**
   - Core rules of Spec-Driven Development.
   - The 6-phase engineering lifecycle (Specify $\to$ Design $\to$ Task $\to$ Test $\to$ Implement $\to$ Verify).
   - Definition of Done (DoD) checklist.

2. **[01_SYSTEM_REQUIREMENTS_SPECIFICATION.md](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/docs/sdd/01_SYSTEM_REQUIREMENTS_SPECIFICATION.md)**
   - Personas: Instructor (`TEACHER`), Student (`STUDENT`), Institutional Admin.
   - Requirements Catalog: `REQ-AUTH-*`, `REQ-BIO-*`, `REQ-ATT-*`, `REQ-DATA-*`.
   - Non-Functional limits (Latency budgets, memory limits, FAR/FRR thresholds).

3. **[02_TECHNICAL_ARCHITECTURE_SPECIFICATION.md](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/docs/sdd/02_TECHNICAL_ARCHITECTURE_SPECIFICATION.md)**
   - Layered Architecture (Presentation / Domain / Persistence).
   - Jetpack Compose navigation graph and URL encoding rules.
   - Room SQLite Schema v6 entity relationship diagram.
   - CameraX use-case separation (480p preview analysis vs. full-sensor still capture).

4. **[03_PIPELINE_AND_ALGORITHM_SPECIFICATION.md](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/docs/sdd/03_PIPELINE_AND_ALGORITHM_SPECIFICATION.md)**
   - End-to-end 9-step execution pipeline from frame ingestion to cloud sync.
   - Exact mathematical formulas (dynamic bounding box padding, bilinear normalization, L2 unit norm, cosine dot-product).
   - Calibrated Confidence Tiers: High ($\ge 0.40$), Medium ($[0.30, 0.40)$), Low/Stranger ($< 0.30$).
   - Dual-Capture temporal conjunction logic ($P_1 \land P_2$).
   - Architectural comparison: BlazeFace vs. YOLO and MobileFaceNet vs. AdaFace.

5. **[04_IMPLEMENTATION_ROADMAP_AND_TASKS.md](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/docs/sdd/04_IMPLEMENTATION_ROADMAP_AND_TASKS.md)**
   - Requirements Traceability Matrix (RTM).
   - Detailed task breakdown for the 9 core changes/enhancements.
   - Target files, dependencies, and acceptance criteria per task.

6. **[05_TEST_AND_VERIFICATION_SPECIFICATION.md](file:///C:/Users/Vaibhav/AndroidStudioProjects/FacialAttendanceSystem/docs/sdd/05_TEST_AND_VERIFICATION_SPECIFICATION.md)**
   - 4-Gate verification protocol (Build $\to$ Unit Tests $\to$ Device Logcat $\to$ Stranger Test).
   - Physical Stranger Test step-by-step procedure.
   - Regression test checklist.
