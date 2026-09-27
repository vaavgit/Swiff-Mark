# A
# Synopsis Report
### On
## “FaceID Attendance: Autonomous On-Device Facial Recognition Attendance System Using Deep Metric Learning and Mobile Computer Vision”

**Submitted in Partial Fulfillment of the Requirements for the Award of the Degree of**  
### Bachelor of Computer Applications  
**in**  
### Data Science and Artificial Intelligence  

---

**Submitted by:**  
**Vaibhav** (Roll No: [Your Roll Number])  

**Under the Guidance of:**  
**[Guide Name / Er. Shilpi Shukla / Faculty Mentor]**  
*Department of Computer Science & Information Systems*  

---

### Shri Ramswaroop Memorial University  
**Lucknow – Deva Road, Barabanki (UP)**  
**September, 2026**  

<div style="page-break-after: always;"></div>

---

## TABLE OF CONTENTS

- **1. Introduction**
- **2. Statement of the Problem**
- **3. Why I Chose This Topic**
  - 3.1 A Critical Problem Experienced in Every Classroom
  - 3.2 Direct Alignment with Data Science, AI & Computer Vision Studies
  - 3.3 The Growing Industry Shift Toward Edge AI & On-Device Processing
  - 3.4 Realistic, Feasible, and Privacy-Centric Architecture
  - 3.5 Real-World Value and Institutional Impact
- **4. Objective and Scope of the Project**
  - 4.1 Objectives
  - 4.2 Scope of the Project
    - 4.2.1 Included in Scope
    - 4.2.2 Excluded from Scope
- **5. Methodology**
  - 5.1 Phase-wise Approach
  - 5.2 Context-Level Data Flow Diagram (DFD Level 0)
  - 5.3 First-Level Data Flow Diagram (DFD Level 1)
  - 5.4 System Flowchart
  - 5.5 Use Case Description
- **6. Hardware & Software Requirements**
  - 6.1 Hardware Requirements
  - 6.2 Software Requirements
- **7. Contribution of the Project**
  - 7.1 My Own Contribution
  - 7.2 What the Project Offers to Users
  - 7.3 What I Personally Gain From It
- **8. Project Schedule (Gantt Chart)**
- **9. References / Bibliography**
- **Appendix A: Dual-Capture Anti-Proxy Algorithm & Liveness Verification**
  - A.1 Why Dual-Capture Was Designed
  - A.2 Algorithmic Logic & Mathematical Intersection

<div style="page-break-after: always;"></div>

---

## 1. Introduction

Student attendance recording is an essential administrative ritual in schools, colleges, and universities across the globe. Maintaining accurate attendance records is not merely a formality; it directly influences eligibility criteria for semester examinations, continuous internal assessment grades, institutional compliance, and overall student discipline. Despite dramatic advancements in artificial intelligence and mobile computing, the vast majority of university classrooms still rely on manual attendance registers or verbal roll calls.

In a typical university classroom of 60 to 120 students, calling out roll numbers takes anywhere from 8 to 15 minutes of a 50-minute lecture. Over a 15-week academic semester with multiple daily subjects, hundreds of cumulative instructional hours are wasted solely on calling names, marking paper sheets, and reconciling disputed records. Furthermore, manual roll calls are notoriously vulnerable to proxy attendance (“buddy punching”), where students answer on behalf of absent peers, compromising academic integrity.

Alternative solutions attempted in recent years include hardware-based biometric wall units (fingerprint or fixed camera scanners) and RFID card readers. However, these systems introduce severe bottlenecks: students must wait in single-file queues before or after class, leading to congestion at classroom doorways. Hardware devices are also capital-intensive, fragile, and prone to mechanical failures or hygiene concerns. Mobile Bluetooth/Wi-Fi beacon apps have also been trialed, but they suffer from spoofing via proxy device sharing and GPS signal attenuation within multi-story university blocks.

This project, **“FaceID Attendance: Autonomous On-Device Facial Recognition Attendance System Using Deep Metric Learning and Mobile Computer Vision”**, solves this challenge by developing a native Android application that marks collective classroom attendance in seconds using edge-based computer vision. 

Instead of requiring individual scans or slow roll calls, the instructor simply captures two wide-angle group photographs of the classroom—one at the start of the lecture and one toward the conclusion. Using a lightweight, on-device pipeline comprising Google MediaPipe BlazeFace for real-time face detection, quality validation algorithms (sharpness, illumination, pose estimation), and an optimized TensorFlow Lite neural network based on **AdaFace (Adaptive Margin Cosine Loss for Face Recognition)**, the system detects every face in the classroom simultaneously, maps each face into a 512-dimensional Euclidean hypersphere, and identifies enrolled students via high-speed Cosine Similarity search.

Crucially, **all machine learning inference and biometric vector comparisons run 100% locally on the instructor’s mobile device**. Student biometric data never needs to travel to insecure third-party cloud servers, ensuring absolute privacy compliance, zero server operational expenses, and complete operability even in university basements or rooms without Wi-Fi connectivity.

<div style="page-break-after: always;"></div>

---

## 2. Statement of the Problem

While conceptualizing this project, I studied the daily, real-world friction points encountered by professors, administrative officers, and students within our own university campus. The key issues that make existing attendance methods inadequate include:

1. **Massive Instructional Time Loss:** Conducting manual roll calls consumes 15–20% of scheduled instructional lecture time. Across an entire university semester, this equates to thousands of lost academic learning hours.
2. **Prevalence of Proxy Attendance (Buddy Punching):** In large lecture halls, students routinely mark attendance on paper sign-in sheets or call out roll numbers on behalf of absent friends. Professors cannot visually cross-check 80+ faces against a list while simultaneously teaching.
3. **Queue Bottlenecks with Traditional Hardware Biometrics:** Fixed wall-mounted fingerprint or facial biometric devices require students to line up one by one. For a class of 80 students, individual scans cause severe corridor congestion and delay the start of lectures.
4. **Early Departures & Partial Attendance:** In traditional single-instance attendance checks (whether manual or RFID), students frequently slip out of the classroom immediately after their names are called, leaving classes half-empty before the lecture concludes.
5. **Human Errors in Calculation & Record Tampering:** Paper registers are vulnerable to ink spills, loss, accidental miscalculations, and unauthorized physical tampering. Transferring manual records to university ERP portals requires tedious post-class data entry.
6. **Cloud Latency, Privacy, and Connectivity Roadblocks:** Existing cloud-based AI vision tools require streaming high-resolution classroom images to remote servers. Campus networks frequently suffer from bandwidth choking, latency spikes, or complete dead zones, making cloud-dependent systems unreliable during time-sensitive lectures.

**Problem Statement:**  
*There is an acute need for a portable, offline-capable, cost-free, and automated attendance verification system that can simultaneously authenticate all students in a classroom within seconds, prevent both proxy marking and premature departures, and store audit-ready records without requiring specialized hardware or cloud dependencies.*

<div style="page-break-after: always;"></div>

---

## 3. Why I Chose This Topic

Choosing a final-year academic project requires balancing theoretical depth, practical feasibility, curriculum alignment, and tangible usefulness. Below are the primary reasons I selected this specific topic:

### 3.1 A Critical Problem Experienced in Every Classroom
Rather than choosing an abstract or purely synthetic problem, I chose a real issue that my classmates, professors, and I encounter daily in university lectures. Experiencing the frustration of 15-minute roll calls firsthand ensured that I deeply understood the edge cases—such as lighting variations, tilted faces, seating distances, and deliberate proxy attempts.

### 3.2 Direct Alignment with Data Science, AI & Computer Vision Studies
Throughout my Bachelor of Computer Applications (BCA in Data Science & Artificial Intelligence) degree, I studied subjects including Machine Learning, Linear Algebra, Statistics, Data Structures, Image Processing, and Database Management. However, classroom lab exercises often focus on small, disconnected Jupyter notebook exercises. This project provided the perfect opportunity to unite all these domains into a production-grade, end-to-end engineered system:
- Applying deep metric learning (AdaFace margin loss) to high-dimensional vectors.
- Implementing Euclidean and Cosine similarity metrics on 512-D unit hyperspheres.
- Designing spatial bounding-box geometric scaling and image matrix transformations.
- Architecting an offline relational schema using Room ORM / SQLite.

### 3.3 The Growing Industry Shift Toward Edge AI & On-Device Processing
While cloud AI was the standard over the past decade, modern enterprise applications are shifting rapidly toward **Edge AI**. Running machine learning models directly on edge hardware (mobile GPUs, NPUs, and Neural Engine silicon) delivers zero latency, eliminates recurring cloud server hosting bills, and provides airtight data privacy. Mastering TensorFlow Lite, Android CameraX, and hardware acceleration directly prepares me for modern AI engineering and mobile ML careers.

### 3.4 Realistic, Feasible, and Privacy-Centric Architecture
As an individual student developer, relying on paid cloud APIs (such as AWS Rekognition or Azure Face API) would create continuous recurring costs and require constant internet connectivity. By building an on-device architecture using open-source TFLite models and Android Jetpack Compose, the system costs zero rupees to operate, requires no monthly subscriptions, and operates with zero data leakage.

### 3.5 Real-World Value and Institutional Impact
This project is not merely a theoretical submission. It produces a fully functional, installable Android APK that faculty members can directly install on their phones to manage actual lecture attendance, export official CSV attendance sheets, and reclaim lost lecture time.

<div style="page-break-after: always;"></div>

---

## 4. Objective and Scope of the Project

### 4.1 Objectives
The primary technical and functional objectives of this project are:

1. **Automated Classroom Attendance Capture:** To design an automated mobile computer vision system capable of identifying multiple students in a single high-resolution classroom snapshot.
2. **High-Accuracy Deep Metric Learning:** To integrate an optimized TensorFlow Lite neural network (**AdaFace IR-18**) producing 512-dimensional normalized facial feature embeddings, achieving high discrimination under varying classroom illuminations.
3. **Robust 6-Angle Face Profile Enrollment:** To develop an interactive student onboarding pipeline that captures and validates six discrete facial poses (Straight, Tilt Up, Tilt Down, Turn Left, Turn Right, Smile/Liveness) to build an invariant composite facial profile.
4. **Real-Time Image Quality Validation:** To implement on-device heuristics that evaluate image sharpness (Laplacian variance), facial brightness, and bounding-box alignment before accepting an enrollment or recognition frame.
5. **Dual-Capture Anti-Proxy Algorithm:** To implement a mathematical verification algorithm that compares classroom snapshots taken at lecture commencement ($T_1$) and lecture conclusion ($T_2$). A student is marked present if and only if their face is verified in both captures ($T_1 \cap T_2$).
6. **Role-Based Architecture (Student & Teacher Portals):** To create segregated user interfaces for students (profile review, enrolled classes, live attendance percentages) and instructors (class management, unique class code generation, roster synchronization, camera attendance capture).
7. **Complete On-Device Data Persistence:** To construct a relational SQLite database using Android Jetpack Room to locally store user credentials, student profiles, class rosters, face vectors, and dated session history.
8. **Institutional Export Capability:** To generate standardized CSV attendance sheets formatted with student names, roll numbers, timestamps, and dual-capture verification flags ready for university ERP upload.

### 4.2 Scope of the Project

#### 4.2.1 Included in Scope
- **Native Android Application:** Built natively using Kotlin and Jetpack Compose (Material 3) adhering to Modern Android Architecture (MVVM/Unidirectional Data Flow).
- **On-Device Computer Vision Pipeline:** Integration of Google MediaPipe Face Detection (BlazeFace Short-Range model) for multi-face localization and landmark extraction.
- **Embedded Neural Inference:** TFLite execution of AdaFace IR-18 (int8/float16 quantized) with multithreaded CPU/GPU acceleration.
- **Vector Math Engine:** Real-time cosine similarity computation and top-k candidate ranking across stored student gallery vectors.
- **Guided 6-Angle Guided Enrollment:** Visual oval guide with real-time breathing scale pulse, color-state feedback (cyan to green), and step indicators.
- **Dual-Capture Attendance Protocol:** Two-stage classroom photo capture with automated intersection analysis, discrepancy flagging, and manual instructor override dialogs.
- **Local Relational Database:** Multi-table Room database managing users, classes, enrollments, sessions, and binary vector blobs.
- **Data Export:** Direct local export of attendance sessions into timestamped `.csv` spreadsheet files.
- **Adaptive UI Theming:** Full dynamic support for both Dark (#0F172A) and Light (#F8FAFC) modes with custom glassmorphism visual styling.

#### 4.2.2 Excluded from Scope
- **Physical Hardware Turnstile / Gate Actuation:** The application is a software-based classroom attendance system and does not directly trigger physical solenoid locks or gate turnstiles.
- **Continuous CCTV Video Stream Monitoring:** The system analyzes high-resolution dual still photos rather than processing continuous 24/7 video streams to prevent excessive battery drain and thermal throttling on mobile devices.
- **Centralized University ERP Integration via Proprietary APIs:** ERP integration is handled via universal CSV exports rather than hardcoding proprietary university database connectors.

<div style="page-break-after: always;"></div>

---

## 5. Methodology

The development of **FaceID Attendance** follows the standard **Software Development Life Cycle (SDLC)** utilizing an Agile iterative methodology. The workflow progresses from mathematical formulation and data modeling to Android architecture implementation, computer vision integration, testing, and field validation.

```
+-----------------------------------------------------------------------------------+
|                           PHASE-WISE METHODOLOGY                                  |
+-----------------------------------------------------------------------------------+
|  1. Requirement & Biometric Protocol Analysis                                     |
|  2. Deep Learning Model Selection & TFLite Quantization (AdaFace IR-18)           |
|  3. Android Architecture & Jetpack Compose UI/UX Design                           |
|  4. On-Device Vision Pipeline (MediaPipe BlazeFace + Quality Validator)           |
|  5. Metric Learning Engine (512-D L2 Normalization & Cosine Matching)             |
|  6. Dual-Capture Anti-Proxy Logic ($T_1 \cap T_2$ Set Intersection)               |
|  7. Local Relational Persistence (Room ORM & Vector Serialization)                |
|  8. Testing, Performance Benchmarking & Optimization                              |
+-----------------------------------------------------------------------------------+
```

### 5.1 Phase-wise Approach

1. **Requirement & Biometric Protocol Analysis:** Formulating functional needs for both student enrollment and classroom group recognition, establishing strict threshold boundaries for cosine similarity ($\tau = 0.60$ for high confidence match, $\tau = 0.50$ for manual review).
2. **Deep Learning Model Optimization:** Evaluating face recognition models (MobileFaceNet, ArcFace, AdaFace). AdaFace was chosen for its adaptive margin loss function that handles low-quality or partially occluded classroom faces. The model was converted and quantized into a 15MB flatbuffer (`adaface_model.tflite`) accepting $[1, 112, 112, 3]$ tensors.
3. **Android Jetpack Compose UI Design:** Implementing all screens using modern declarative UI, glassmorphism cards, animated status pills, adaptive dark/light color palettes, and responsive layouts.
4. **Computer Vision Pipeline Implementation:** Pairing CameraX `ImageAnalysis` with MediaPipe BlazeFace. Implementing an atomic concurrency gate (`AtomicBoolean`) to throttle incoming frames, ensuring only one frame is processed at a time while maintaining silky-smooth 60fps preview performance.
5. **Quality Validation Module:** Writing algorithmic routines to compute image variance of Laplacian to reject motion-blurred frames, calculating pixel luminance to warn against extreme backlighting, and enforcing face bounding-box size requirements.
6. **Dual-Capture Logic:** Developing the core verification stage where Photo 1 ($T_1$) and Photo 2 ($T_2$) are analyzed independently. Results are merged using set intersection logic:
   $$\text{Final Present Set} = \{s \in \text{Roster} \mid s \in P_1 \land s \in P_2\}$$
7. **Relational Database Design:** Structuring normalized Room tables with foreign keys and cascade rules. Storing 512-D float vectors as compact byte arrays using custom Room type converters.
8. **Testing & Validation:** Validating recognition accuracy under varying classroom seating arrangements, multi-face group densities, illumination shifts, and testing execution time on real Android smartphones.

<div style="page-break-after: always;"></div>

---

### 5.2 Context-Level Data Flow Diagram (DFD Level 0)

The Level 0 DFD illustrates the external interaction between the two primary human actors (Student and Teacher) and the centralized **FaceID Attendance System**.

```
                         +-----------------------------------+
                         |         STUDENT (User)            |
                         +-----------------------------------+
                           |                               ^
                           | 1. Profile Data &             | 2. Enrollment Status &
                           |    6-Pose Face Images         |    Attendance Statistics
                           v                               |
            +-------------------------------------------------------------+
            |                                                             |
            |                            0.0                              |
            |                 FACEID ATTENDANCE SYSTEM                    |
            |             (On-Device Autonomous Platform)                 |
            |                                                             |
            +-------------------------------------------------------------+
                           ^                               |
                           | 3. Class Creation,            | 4. Recognized Roster,
                           |    Dual Classroom Photos &    |    Discrepancy Flags &
                           |    Manual Overrides           |    Exported CSV Reports
                           |                               v
                         +-----------------------------------+
                         |         TEACHER (User)            |
                         +-----------------------------------+
```
*Figure 5.1: Context-Level Data Flow Diagram (DFD Level 0)*

---

### 5.3 First-Level Data Flow Diagram (DFD Level 1)

The Level 1 DFD decomposes the system into its discrete functional modules and illustrates data exchanges with local storage stores ($D_1, D_2, D_3$).

```
 [STUDENT]
    |
    | (Name, Roll, Poses)
    v
+--------------------------+    Store User & Student     +-----------------------+
| 1.0 Student Registration |---------------------------->| D1: User & Student    |
|     & 6-Pose Enrollment  |                             |     Table (Room DB)   |
+--------------------------+                             +-----------------------+
    |                                                                |
    | (Normalized Face Bitmaps)                                      |
    v                                                                |
+--------------------------+    Write 512-D Binary Vectors   +-----------------------+
| 2.0 AdaFace Feature      |-------------------------------->| D2: Face Embeddings   |
|     Vector Extraction    |                                 |     Table (Room DB)   |
+--------------------------+                                 +-----------------------+
                                                                     |
 [TEACHER]                                                           | (Stored Reference
    |                                                                |  Vectors)
    | (Class Code, Dual Photos)                                      v
    v                                                    +-----------------------+
+--------------------------+    Query Enrolled Gallery   | 4.0 Metric Matching   |
| 3.0 Classroom Snapshot   |---------------------------->|     & Cosine Ranker   |
|     Capture (T1 & T2)    |                             +-----------------------+
+--------------------------+                                         |
                                                                     | (Individual Match Sets)
                                                                     v
+--------------------------+    Write Completed Sessions +-----------------------+
| 5.0 Dual-Capture         |---------------------------->| D3: Attendance Sessions|
|     Intersection Logic   |                             |     & Records Table   |
+--------------------------+                             +-----------------------+
    |                                                                |
    | (Audit Roster & Overrides)                                     | (Generate Sheet)
    v                                                                v
 [TEACHER] <---------------------------------------------+-----------------------+
                                                         | 6.0 CSV Export Engine |
                                                         +-----------------------+
```
*Figure 5.2: First-Level Data Flow Diagram (DFD Level 1)*

<div style="page-break-after: always;"></div>

---

### 5.4 System Flowchart

The system flowchart delineates the exact sequential operational pipeline executed during a live classroom lecture:

```
                                 [ START ]
                                     |
                                     v
                       [ Teacher Logs in to Portal ]
                                     |
                                     v
                     [ Select Class / Generate Code ]
                                     |
                                     v
                 [ Capture Photo 1 (Start of Lecture T1) ]
                                     |
                                     v
               [ BlazeFace Multi-Face Detection on Bitmap ]
                                     |
                                     v
             [ Loop: Extract 112x112 Crop -> AdaFace Inference ]
                                     |
                                     v
          [ Compute Cosine Similarity against Class Stored Vectors ]
                                     |
                                     v
            [ Rank Candidates: Top match >= 0.60 ? -> Mark P1 ]
                                     |
                                     v
               [ Review Photo 1 Detections & Count Badges ]
                                     |
                                     v
               [ Normal Lecture Instructional Period Proceeds ]
                                     |
                                     v
                  [ Capture Photo 2 (End of Lecture T2) ]
                                     |
                                     v
             [ Execute Same Detection & Recognition Pipeline ]
                                     |
                                     v
          +------------------------------------------------------+
          |   Compute Mathematical Set Intersection: P1 AND P2   |
          +------------------------------------------------------+
                                     |
                  +------------------+------------------+
                  |                                     |
                  v                                     v
      [ Match in BOTH P1 & P2 ]             [ Detected in Only ONE Photo ]
                  |                                     |
                  v                                     v
        [ STATUS: PRESENT (1) ]               [ STATUS: PROXY / ABSENT (0) ]
                  |                                     |
                  +------------------+------------------+
                                     |
                                     v
                   [ Instructor Manual Override Dialog ]
                   (Optional adjustment of edge cases)
                                     |
                                     v
                 [ Commit Session & Records to Room DB ]
                                     |
                                     v
                   [ Export Official CSV Attendance File ]
                                     |
                                     v
                                  [ END ]
```
*Figure 5.3: End-to-End System Flowchart*

<div style="page-break-after: always;"></div>

---

### 5.5 Use Case Description

The application defines two distinct primary actors: the **Student** and the **Teacher**.

| Use Case Identifier | Use Case Name | Primary Actor | Description |
|---|---|---|---|
| **UC-01** | Student Account Registration | Student | Enters full name, roll number, department, section, and creates secure local login credentials. |
| **UC-02** | 6-Angle Face Profile Enrollment | Student | Follows on-screen prompts to capture 6 facial angles (Straight, Up, Down, Left, Right, Smile). Evaluates sharpness and stores averaged composite vector. |
| **UC-03** | Join Class via Secret Code | Student | Enters alphanumeric class code (e.g., `ML-4871`) generated by instructor to associate profile with classroom roster. |
| **UC-04** | View Personal Attendance Status | Student | Views enrolled subjects, cumulative attendance percentages, and color-coded status badges ($>75\%$ Green, $50-75\%$ Amber, $<50\%$ Red). |
| **UC-05** | Teacher Account Registration | Teacher | Creates faculty account with name, department, and primary teaching subject. |
| **UC-06** | Classroom Course Creation | Teacher | Creates new class section specifying course name, subject, semester, and automatically receives unique shareable class code. |
| **UC-07** | Roster Inspection & Code Copying | Teacher | Inspects enrolled student roster, checks enrollment completion statuses, and copies class code with a single tap. |
| **UC-08** | Initiate Attendance Session | Teacher | Activates CameraX viewfinder to take Photo 1 at lecture start and Photo 2 at lecture end. |
| **UC-09** | Multi-Face Detection & Review | Teacher | Inspects detected bounding boxes, checks student names with confidence pills, and reviews discrepancy badges. |
| **UC-10** | Manual Attendance Override | Teacher | Manually toggles a student between Present and Absent in case of camera edge-cases or verified medical exemptions. |
| **UC-11** | Export Audit Attendance Sheet | Teacher | Generates and shares formatted CSV file containing roll numbers, student names, session timestamps, and dual-capture status. |
| **UC-12** | Theme & System Customization | Both | Toggles between Dark and Light visual themes; updates profile information. |

*Table 5.1: Detailed Use Case Specifications*

<div style="page-break-after: always;"></div>

---

## 6. Hardware & Software Requirements

### 6.1 Hardware Requirements

#### Development Workstation Requirements:
- **Processor:** Intel Core i5 / AMD Ryzen 5 (2.5 GHz or higher)
- **RAM:** 8 GB minimum; 16 GB recommended for running Android Studio and Gradle daemon smoothly
- **Storage:** 256 GB SSD with at least 15 GB free disk space for Android SDKs, build caches, and tools
- **Display:** Full HD Display ($1920 \times 1080$ resolution)
- **Peripherals:** Keyboard, mouse, USB cable for Android hardware debugging

#### Target Android Smartphone (Deployment Device):
- **Device Type:** Android Smartphone (Tested on Motorola Moto G34 5G)
- **Processor:** Qualcomm Snapdragon 695 5G / MediaTek Dimensity or higher (Octa-core 2.0 GHz)
- **RAM:** 4 GB minimum (8 GB recommended for multi-face batch TFLite inference)
- **Camera:** 16 MP primary camera with autofocus (for classroom group capture); 8 MP front camera (for student enrollment)
- **Storage Space:** Minimum 100 MB free internal flash storage

### 6.2 Software Requirements

| Component | Software Tool / Library | Version / Specifications | Purpose |
|---|---|---|---|
| **Operating System** | Microsoft Windows 11 (64-bit) | Build 22H2 or higher | Primary development environment |
| **Mobile OS Platform** | Android | API 26 (Android 8.0) to API 36/37 | Target deployment runtime platform |
| **Integrated Dev Environment (IDE)** | Android Studio Ladybug / Koala | 2024.1+ | Primary Android IDE |
| **Programming Language** | Kotlin | 2.0.21 | Type-safe native Android programming |
| **UI Toolkit** | Jetpack Compose (Material 3) | BOM 2024.09.00+ | Modern declarative UI design |
| **Camera Framework** | Android Jetpack CameraX | 1.4.0-rc01 | High-performance hardware camera abstraction |
| **Face Detection Engine** | Google MediaPipe Tasks Vision | 0.10.14 | Real-time multi-face bounding box localization |
| **Deep Learning Runtime** | TensorFlow Lite & TFLite GPU | 2.16.1 | Execution of quantized neural network models |
| **Recognition Model** | AdaFace IR-18 Flatbuffer | `adaface_model.tflite` (15 MB) | 512-D facial feature extraction |
| **Computer Vision Utilities** | OpenCV for Android | 4.9.0 | Safe bitmap cropping & matrix transformations |
| **Local Database Engine** | Android Jetpack Room | 2.6.1 | SQLite ORM for local persistent storage |
| **Documentation & Diagramming** | Draw.io / Mermaid / MS Word | Standard | Architectural diagrams and project reporting |

*Table 6.1: Complete Software Stack and Dependencies*

<div style="page-break-after: always;"></div>

---

## 7. Contribution of the Project

### 7.1 My Own Contribution
As an individual developer under the guidance of my faculty project mentor, I designed, implemented, and tested every tier of the **FaceID Attendance** application:
1. **Machine Learning Pipeline Design:** Researched, converted, and optimized the AdaFace deep metric model into TensorFlow Lite format; implemented L2 normalization and cosine similarity ranking.
2. **Computer Vision & Quality Validator:** Implemented MediaPipe BlazeFace integration and authored custom mathematical image quality routines (Laplacian variance sharpness testing and illumination checking).
3. **Android Jetpack Compose UI/UX:** Authored all 9 screen layouts in Jetpack Compose Material 3, incorporating adaptive Dark/Light themes, animated step indicators, and glassmorphism styling.
4. **Concurrency & Lag Elimination:** Resolved critical frame-flooding bottlenecks by engineering an atomic concurrency gate (`AtomicBoolean`) that maintains 60fps camera performance.
5. **Database & Persistence:** Designed the normalized Room SQLite schema, entities, type converters, and CSV export functionality.

### 7.2 What the Project Offers to Users
- **To Instructors:** Saves 10–15 minutes per lecture, eliminates paper register management, automates percentage tracking, and prevents student deception.
- **To Students:** Provides transparent, instant visibility into their own attendance records and ensures fair grading without proxy manipulation by peers.
- **To Educational Institutions:** Eliminates costly biometric wall hardware installations, requires zero recurring cloud subscriptions, protects student biometric privacy, and generates verifiable, audit-proof attendance logs.

### 7.3 What I Personally Gain From It
This project bridged the gap between academic theory and real-world engineering. I gained deep, hands-on mastery in:
- Deploying neural network models on mobile edge devices using TensorFlow Lite and GPU delegates.
- Implementing modern declarative Android UI using Kotlin and Jetpack Compose.
- Developing real-time image processing pipelines using MediaPipe, CameraX, and OpenCV.
- Designing offline-first, crash-resilient architectures using Android Room ORM and Kotlin Coroutines.

<div style="page-break-after: always;"></div>

---

## 8. Project Schedule

The project was executed systematically over a structured **12-week academic timeline**, as mapped in the Gantt chart below:

| Project Phase | W1 | W2 | W3 | W4 | W5 | W6 | W7 | W8 | W9 | W10 | W11 | W12 |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Problem Identification & Literature Review** | █ | █ | | | | | | | | | | |
| **Model Selection & TFLite Quantization (AdaFace)** | | █ | █ | | | | | | | | | |
| **Database Schema & Room Architecture Design** | | | █ | █ | | | | | | | | |
| **CameraX & MediaPipe Vision Pipeline Setup** | | | | █ | █ | | | | | | | |
| **6-Angle Student Enrollment UI & Quality Checks** | | | | | █ | █ | | | | | | |
| **Teacher Portal & Dual-Capture Logic ($T_1 \cap T_2$)** | | | | | | █ | █ | █ | | | | |
| **Jetpack Compose UI/UX Polish & Theme Adaptation** | | | | | | | | █ | █ | | |
| **Performance Throttling & Camera Optimization** | | | | | | | | | █ | █ | | |
| **Classroom Testing & Recognition Validation** | | | | | | | | | | █ | █ | |
| **Documentation, Synopsis & Final Presentation** | | | | | | | | | | | █ | █ |

*Figure 8.1: 12-Week Project Schedule (Gantt Chart)*

---

## 9. References / Bibliography

1. **Kim, M., Jain, A. K., & Liu, X.** (2022). *AdaFace: Quality Adaptive Margin for Face Recognition*. Proceedings of the IEEE/CVF Conference on Computer Vision and Pattern Recognition (CVPR), pp. 18750-18759.
2. **Deng, J., Guo, J., Xue, N., & Zafeiriou, S.** (2019). *ArcFace: Additive Angular Margin Loss for Deep Face Recognition*. IEEE Transactions on Pattern Analysis and Machine Intelligence, 44(10), 5962-5979.
3. **Lugaresi, C., et al.** (2019). *MediaPipe: A Framework for Building Perception Pipelines*. arXiv preprint arXiv:1906.08172.
4. **Bazarevsky, V., et al.** (2019). *BlazeFace: Sub-millisecond Neural Face Detection on Mobile GPUs*. CVPR Workshop on Computer Vision for Augmented Reality.
5. **Google Developers.** (2024). *Android CameraX Overview & Image Analysis Architecture*. Android Open Source Project. [Online]. Available: `https://developer.android.com/training/camerax`
6. **TensorFlow Team.** (2024). *TensorFlow Lite for Mobile and Edge Devices*. Google Research. [Online]. Available: `https://www.tensorflow.org/lite`
7. **Android Jetpack Documentation.** (2024). *Compose UI and Room Persistence Library*. Google Developers. [Online]. Available: `https://developer.android.com/jetpack/compose`
8. **Shri Ramswaroop Memorial University.** (2026). *BCA Project Synopsis & Dissertation Guidelines*. Department of Computer Science & Information Systems, SRMU, Lucknow-Deva Road, Barabanki.

<div style="page-break-after: always;"></div>

---

## Appendix A: Dual-Capture Anti-Proxy Algorithm & Liveness Verification

### A.1 Why Dual-Capture Was Designed
Existing automated attendance systems suffer from a fatal procedural flaw: they evaluate attendance at a single instant in time. Students frequently enter the room, get marked on a scanner or single photo, and leave immediately afterwards. 

To permanently defeat this loophole without requiring continuous surveillance, **FaceID Attendance** implements a **Dual-Capture Verification Protocol**:
1. **Photo 1 ($T_1$):** Captured during the first 10 minutes of the lecture.
2. **Photo 2 ($T_2$):** Captured during the concluding 5 minutes of the lecture.

### A.2 Algorithmic Logic & Mathematical Intersection

Let $R = \{s_1, s_2, \dots, s_n\}$ represent the set of all students officially enrolled in the class roster.  
Let $P_1 \subseteq R$ denote the set of students successfully recognized in Photo 1.  
Let $P_2 \subseteq R$ denote the set of students successfully recognized in Photo 2.

The system evaluates each student’s attendance status $A(s)$ via strict boolean intersection:

$$A(s) = \begin{cases} 
1 \text{ (Present)}, & \text{if } s \in (P_1 \cap P_2) \\
0 \text{ (Absent / Proxy Flagged)}, & \text{if } s \notin (P_1 \cap P_2) 
\end{cases}$$

```
   Photo 1 Detections (P1)                Photo 2 Detections (P2)
  +-----------------------+              +-----------------------+
  |                       |              |                       |
  |     Arrived Late      |  CONFIRMED   |     Left Early        |
  |     Only in P1        |   PRESENT    |     Only in P2        |
  |   (FLAGGED PROXY)     |  (P1 ∩ P2)   |   (FLAGGED PROXY)     |
  |                       |              |                       |
  +-----------------------+              +-----------------------+
```

#### Discrepancy Flagging for Faculty:
- If a student is detected in $P_1$ but missing in $P_2$, the system flags **"Early Departure Suspected"**.
- If a student is detected in $P_2$ but missing in $P_1$, the system flags **"Late Arrival / Unverified Start"**.
- Only students verified in both $P_1$ and $P_2$ receive automatic credit, completely eradicating proxy attendance while keeping total instructor effort under 30 seconds per class.
