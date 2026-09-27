# Swiff Mark

> Smarter classroom attendance using face recognition. No roll call shouting, no paper sheets, no proxy attendance.

[![Download Latest APK](https://img.shields.io/badge/Download_APK-v1.0.0-2ea44f?style=for-the-badge&logo=android&logoColor=white)](https://github.com/vaavgit/Swiff-Mark/releases/latest)

[![Android](https://img.shields.io/badge/Android-8.0+-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

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
- **College Register CSV Export:** Need to submit monthly reports to the department or HOD? One tap generates an official attendance matrix register with:
  - Day-by-day attendance (`P` / `A`) for every student.
  - Automatic defaulter calculation (shows students below 75% and exactly how many classes they need to attend to become eligible).
  - Ready-to-print header and signature rows for the Dean / HOD.

### 🎓 For Students
- **Instant Attendance Alerts:** The moment your professor finishes marking the session, you get a clean push notification showing if you were marked Present or Absent, plus your updated attendance percentage.
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
- **No face photos are stored in the cloud.** When a student registers their face, the app converts their face into a 512-number mathematical vector directly on the phone.
- Only the non-reversible numbers are saved to the Supabase database. You cannot reconstruct a person's photo from these numbers.
- Attendance photos taken by the teacher are processed on-device and discarded.

---

## 🛠️ Tech Stack

- **Android / UI:** Kotlin, Jetpack Compose, Material 3, Coroutines & Flow
- **Local Storage:** Room Database (SQLite) + Android WorkManager
- **Machine Learning:** Google ML Kit (Face Detection) + TensorFlow Lite (AdaFace 512-D embeddings)
- **Computer Vision:** OpenCV for affine landmark alignment
- **Cloud Backend:** Supabase (PostgreSQL, Row Level Security, Auth)
- **Build / Packaging:** Gradle Kotlin DSL, R8 / ProGuard obfuscation, Git LFS

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
Copy the sample properties file:
```bash
cp local.properties.example local.properties
```
Open `local.properties` and add your Android SDK path and Supabase keys:
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

## 📄 License

This project is licensed under the [MIT License](./LICENSE). Feel free to fork, customize, or contribute!
