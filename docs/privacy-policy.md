# Privacy Policy for Swiff Mark

**Effective Date:** October 13, 2026  
**Last Updated:** October 13, 2026  
**App Name:** Swiff Mark  
**Developer:** Vaibhav (vaavgit)

---

## 1. Introduction

Swiff Mark ("the App") is a facial-recognition-based classroom attendance system designed for educational institutions. This Privacy Policy explains how we collect, use, store, and protect your personal information — including biometric data — when you use our App.

By using Swiff Mark, you agree to the collection and use of information as described in this policy.

---

## 2. Information We Collect

### 2.1 Biometric Data (Face Embeddings)
- **What we collect:** Mathematical representations (embeddings) of your face derived from photos taken during enrollment. We do **not** store raw photographs of your face.
- **Why:** To uniquely identify students for attendance marking without manual roll calls.
- **Consent:** Face enrollment is entirely voluntary and requires explicit consent before any facial data is captured. You may withdraw consent at any time by deleting your enrollment through the App.

### 2.2 Student Profile Data
- Full name, student ID / roll number, email address (if provided), enrolled class/section.

### 2.3 Attendance Records
- Date, time, class session, and attendance status (Present / Absent / Late) for each session.

### 2.4 Device Information
- Android OS version, device model (for crash diagnostics and compatibility only). We do **not** collect IMEI, phone number, or advertising identifiers.

### 2.5 Camera Permission
- The App requires camera access **only** during face enrollment and attendance capture. Camera data is processed on-device and is not continuously recorded or streamed.

---

## 3. How We Use Your Information

| Purpose | Data Used |
|---|---|
| Attendance marking via face recognition | Face embeddings |
| Displaying attendance history to students & teachers | Attendance records |
| Sending attendance notifications | Attendance records, student profile |
| Teacher dashboards and reports | Aggregated attendance records |
| App stability and bug fixes | Device information (anonymised) |

We do **not** use your data for advertising, profiling, or any purpose unrelated to classroom attendance.

---

## 4. Data Storage and Security

- **On-Device:** Face embeddings and local attendance records are stored in an encrypted SQLite database (Android Room) on your device.
- **Cloud (Supabase):** Attendance records and student profiles are synced to a Supabase (PostgreSQL) backend protected by Row-Level Security (RLS) policies. Only authenticated users can access their own records.
- **Encryption in transit:** All data is transmitted over HTTPS/TLS.
- **Access control:** Teachers can only view data for students enrolled in their classes. Students can only view their own records.

---

## 5. Data Retention

| Data Type | Retention Period |
|---|---|
| Face embeddings | Until you delete your enrollment or uninstall the App |
| Attendance records | For the duration of the academic term, or until deleted by the institution |
| Device diagnostic data | 30 days, anonymised |

Institutions (schools/colleges) deploying Swiff Mark are responsible for setting their own data retention policies in compliance with applicable laws.

---

## 6. Your Rights

Depending on your jurisdiction, you may have the right to:

- **Access** your personal data and face embeddings stored by the App.
- **Correct** inaccurate data (e.g., wrong attendance status) — request corrections through your teacher or institution.
- **Delete** your face enrollment at any time via the App settings (Student > Profile > Delete Enrollment).
- **Object** to biometric processing — you may opt out of facial recognition and request manual attendance marking from your institution.
- **Data portability** — your attendance records can be exported as a CSV file.

To exercise any of these rights, contact your institution's administrator or reach out to us at the contact details below.

---

## 7. Children's Privacy

Swiff Mark may be used in educational institutions that include users under the age of 18. We do not knowingly collect data from children under 13 without verifiable parental consent. Institutions deploying this App are responsible for obtaining appropriate consents from students and/or their guardians as required by applicable law (e.g., COPPA, DPDP Act 2023).

---

## 8. Compliance

This App is designed to comply with:
- **India's Digital Personal Data Protection (DPDP) Act, 2023**
- **EU General Data Protection Regulation (GDPR)** — where applicable
- **Google Play Sensitive Permissions Policy** (biometric data)

---

## 9. Third-Party Services

| Service | Purpose | Privacy Policy |
|---|---|---|
| Supabase | Cloud database and authentication | [supabase.com/privacy](https://supabase.com/privacy) |
| Google ML Kit | On-device face detection | [policies.google.com](https://policies.google.com/privacy) |
| TensorFlow Lite | On-device face embedding | Runs entirely on-device, no data shared |

---

## 10. Changes to This Policy

We may update this Privacy Policy from time to time. We will notify users of significant changes by posting a notice within the App. The "Last Updated" date at the top of this page will reflect the most recent revision.

---

## 11. Contact Us

If you have questions, concerns, or data requests related to this Privacy Policy, please contact:

**Developer:** Vaibhav  
**GitHub:** [github.com/vaavgit/Swiff-Mark](https://github.com/vaavgit/Swiff-Mark)  
**Issues:** [github.com/vaavgit/Swiff-Mark/issues](https://github.com/vaavgit/Swiff-Mark/issues)

---

*This privacy policy was last reviewed and updated on October 13, 2026.*
