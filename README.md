# Swiff Mark

> Smarter classroom attendance using face recognition. No roll call shouting, no paper sheets, no proxy attendance.

[![Download Latest APK](https://img.shields.io/badge/Download_APK-v1.2.2-2ea44f?style=for-the-badge&logo=android&logoColor=white)](https://github.com/vaavgit/Swiff-Mark/releases/latest)

[![Android](https://img.shields.io/badge/Android-8.0+-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Privacy Policy](https://img.shields.io/badge/Privacy-Policy-blue?style=flat)](./docs/privacy-policy.md)

In almost every college lecture, 10 to 15 minutes get wasted just calling out roll numbers or passing around an attendance sheet that inevitably gets filled with fake signatures for absent friends.

**Swiff Mark** is an Android app built to eliminate that hassle. Instead of wasting lecture time, the professor simply takes two quick photos of the room—one when lecture starts, and one before everyone leaves. The app finds the students in the photos, matches them against the enrolled class roster on the spot, and marks attendance automatically.

---

## 💡 Why We Built It (And Why Two Photos?)

Proxy attendance is the oldest trick in college. Students slip out halfway through class or get friends to sign on paper registers.

To stop that without needing expensive biometric turnstiles, Swiff Mark uses a **Dual-Capture check**:
1. **Start of Class:** Professor clicks a photo of the classroom.
2. **End of Class:** Professor takes a second photo before students pack up.
3. **The Result:** If you're in both photos, you're marked **Present**. If you skipped out early or strolled in late, you're marked **Absent** (with an optional manual override if the teacher needs to make an exception).

Everything runs locally on the teacher's phone in a few seconds, so it doesn't grind to a halt even if the campus Wi-Fi is spotty.

---

## ✨ Features That Actually Matter

### 👨‍🏫 For Professors & Instructors
- **Fast Roll Call:** Snap two photos, and the app matches everyone in seconds.
- **Class Join Codes:** Create a class, share a short join code (like `CS-4921`), and only enrolled students can join.
- **Manual Review:** See green/red bounding boxes over detected student faces with an easy toggle to fix edge cases or overrides.
- **Attendance History Deep-Dive:** Tap any session to see a full breakdown of who was present, absent, or late. Correct any mistakes instantly.
- **Fast Search & Filters:** Filter your class roster by name, status, or attendance percentage. One-tap defaulter (< 75%) filter.
- **College Register CSV Export:** Need to submit monthly reports to the department or HOD? One tap generates an official attendance matrix register with:
  - Day-by-day attendance (`P` / `A`) for every student.
  - Automatic defaulter calculation (shows students below 75% and exactly how many classes they need to attend to become eligible).
  - Ready-to-print header and signature rows for the Dean / HOD.
- **1-Tap Defaulter Notice:** Auto-generates a warning notice for students below 75% attendance and exports it as a shareable CSV.

### 🎓 For Students
- **Instant Attendance Alerts:** The moment your professor finishes marking the session, you get a clean push notification (✅ green tick) showing if you were marked Present or Absent, plus your updated attendance percentage — typically within 2–8 seconds.
- **No Duplicate Alerts:** Anti-spam deduplication ensures you only get notified once per session, never again for the same class.
- **Personal Dashboard:** Track your attendance across all your subjects, see your overall percentage, and know when you're getting dangerously close to the 75% shortage limit.
- **Mutual Profile Photos:** Clean profile customization so teachers recognize who's who in their roster.

---

## 🧠 How the Face Matching Works

Under the hood, all recognition happens directly on the device using Google ML Kit and TensorFlow Lite:

```
[ Classroom Photo ]
       │
       ▼
1. Face Detection (Google ML Kit)
   Finds all faces in the wide classroom photo and crops each face chip.
       │
       ▼
2. Face Alignment (OpenCV Affine Transform)
   Normalizes the face chip to 112×112 based on eye and nose landmarks.
       │
       ▼
3. Vector Embedding (AdaFace TFLite)
   Converts each face into a 512-dimensional mathematical vector.
       │
       ▼
4. Cosine Similarity Matching
   Compares the extracted vectors against enrolled student embeddings.
       │
       ▼
[ Attendance Logged in Room SQLite & Synced with Supabase ]
```

---

## 🔒 Privacy & Biometrics

We take student biometric privacy seriously:
- **Explicit consent required:** Students must agree to our biometric consent dialog before any face data is captured.
- **No face photos are stored in the cloud.** When a student registers their face, the app converts their face into a 512-number mathematical vector directly on the phone.
- Only the non-reversible numbers are saved to the Supabase database. You cannot reconstruct a person's photo from these numbers.
- Attendance photos taken by the teacher are processed on-device and discarded.
- Students can delete their face profile at any time from Profile settings.
- Compliant with **India's DPDP Act 2023** and **GDPR** principles.

📄 **[Read our full Privacy Policy](./docs/privacy-policy.md)**

---

## 🛠️ Tech Stack

- **Android / UI:** Kotlin, Jetpack Compose, Material 3, Coroutines & Flow
- **Local Storage:** Room Database (SQLite) + Android WorkManager
- **Machine Learning:** Google ML Kit (Face Detection) + TensorFlow Lite (AdaFace 512-D embeddings)
- **Computer Vision:** OpenCV for affine landmark alignment
- **Cloud Backend:** Supabase (PostgreSQL, Row Level Security, Auth)
- **Build / Packaging:** Gradle Kotlin DSL, R8 / ProGuard obfuscation, signed release APK, Git LFS

---

## 🚀 Setting Up the Project Locally

Want to run the project or contribute? Here's how to get it running in 5 minutes:

### 1. Prerequisites
- Android Studio Ladybug (2024.2+) or newer
- JDK 17
- An Android device or emulator running Android 8.0 (API 26) or higher
- A free [Supabase](https://supabase.com) account

### 2. Clone the repo
```bash
git clone https://github.com/vaavgit/Swiff-Mark.git
cd Swiff-Mark
```

### 3. Add your keys
Open `local.properties` and fill in your SDK path and Supabase keys:
```properties
sdk.dir=C\:\\Users\\<YourName>\\AppData\\Local\\Android\\Sdk
SUPABASE_URL=https://<your-project-id>.supabase.co
SUPABASE_ANON_KEY=your_supabase_anon_key
```

### 4. Setup Database
1. Open your Supabase project's **SQL Editor**.
2. Run [`supabase/schema.sql`](./supabase/schema.sql) to create the tables.
3. Run [`supabase/policies.sql`](./supabase/policies.sql) to enable Row Level Security.

### 5. Build & Run
Hit **Run** (`Shift + F10`) in Android Studio.

---

## 📋 Changelog

### v1.2.2 (Latest)
- ✅ Enhanced multi-face capture with full-sensor 4:3 high-resolution imaging
- ✅ Real-time frame sharpness selection (best-of-3 burst with Laplacian blur gate)
- ✅ OpenCV CLAHE luminance normalisation and edge enhancement on face chips
- ✅ Adaptive size-aware confidence scoring for distant classroom seating
- ✅ Quality-weighted embedding fusion across enrollment angles
- ✅ Screen orientation portrait lock & state retention during sessions
- ✅ In-app "Delete Face Profile" button in Student Profile (DPDP Act right to erasure)

### v1.2.0
- ✅ Instant push notifications when attendance is marked (2–8 second delivery)
- ✅ Anti-spam deduplication — one notification per session, never repeats
- ✅ Biometric consent dialog before face enrollment (DPDP Act 2023 compliance)
- ✅ Privacy Policy added (`docs/privacy-policy.md`)
- ✅ Hardened ProGuard/R8 rules — no more R8 stripping data models
- ✅ Signed release APK with official keystore

### v1.1.4
- Attendance history deep-dive & manual session corrections
- Fast search & filters in class rosters
- 1-tap defaulter (< 75%) notice & CSV export

### v1.1.3
- Comprehensive UI polish, zero overlaps, gesture nav padding & theme contrast

### v1.1.2
- Slim arm64-v8a APK (-90 MB) & local Wi-Fi 2-second update server support

### v1.1.1 / v1.1.0 / v1.0.0
- Initial public releases

---

## 📄 License

This project is licensed under the [MIT License](./LICENSE). Feel free to fork, customize, or contribute!