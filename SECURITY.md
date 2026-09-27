# Security Policy

## Supported Versions

| Version | Supported          |
| :------ | :----------------- |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

---

## Reporting a Vulnerability

We take the security and privacy of student biometric data and academic attendance integrity very seriously. If you discover a security vulnerability, presentation attack (spoofing) bypass, or authorization flaw, please follow responsible disclosure:

1. **Do NOT disclose the issue publicly** on GitHub Issues, pull requests, or social media.
2. **Privately report the vulnerability** by creating a [GitHub Private Vulnerability Report](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-communicating-vulnerabilities/privately-reporting-a-security-vulnerability) directly on this repository.
3. Include detailed steps to reproduce the issue, sample payloads (if testing API endpoints), device specifications, and any suggested mitigations.

### What to Expect
- **Acknowledgement**: We will acknowledge receipt of your report within 48 hours.
- **Investigation & Patch**: We will investigate the root cause, determine the impact, and develop a patch in a private branch.
- **Public Disclosure**: Once a fix is verified and deployed in a new release, an advisory will be published crediting the researcher (if desired).

---

## Architectural Security Model

### Biometric Privacy
- **On-Device Vector Extraction**: Facial photos taken during class sessions or student onboarding are converted directly on the user's mobile device into non-reversible mathematical embedding vectors ($512$-dimensional vectors).
- **No Raw Face Images in Database**: The cloud database stores only mathematical embeddings, never raw facial photographs or biometric bitmap images.

### Presentation Attack Mitigation
- **Teacher-Operated Capture**: Classroom scans are taken exclusively by the instructor's device inside the physical lecture hall, preventing students from submitting attendance remotely.
- **Dual-Capture Verification**: Attendance requires two distinct captures (at lecture start and lecture conclusion) to ensure students were physically present throughout the class period.
- **Server-Side Enforcement**: Row Level Security (RLS) policies strictly prohibit student accounts from generating attendance sessions or marking records.
