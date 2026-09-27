# Facial Attendance System — Complete UI/UX Design Specification

Design language: **Modern glassmorphism + Material Design 3**, cyan-accented, dark-first with full light theme parity.

---

## DESIGN TOKENS (Reference These Everywhere)

```kotlin
object AppColors {
    // Dark Theme
    val DarkBackground = Color(0xFF0F172A)
    val DarkSurface = Color(0xFF1E293B)
    val DarkCard = Color(0xFF1E2A3A)

    // Light Theme
    val LightBackground = Color(0xFFF8FAFC)
    val LightSurface = Color(0xFFFFFFFF)
    val LightCard = Color(0xFFF1F5F9)

    // Shared
    val Primary = Color(0xFF0284C7)
    val Success = Color(0xFF10B981)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)

    val TextPrimaryDark = Color(0xFFF8FAFC)
    val TextSecondaryDark = Color(0xFF94A3B8)
    val TextPrimaryLight = Color(0xFF0F172A)
    val TextSecondaryLight = Color(0xFF64748B)
}

object AppShapes {
    val CardRadius = 16.dp
    val ButtonRadius = 12.dp
    val PillRadius = 999.dp   // fully rounded badges
}

object AppSpacing {
    val ScreenPadding = 16.dp
    val CardGap = 16.dp
    val SectionGap = 20.dp
    val ItemGap = 12.dp
}
```

**Glassmorphism card modifier (reuse everywhere):**
```kotlin
fun Modifier.glassCard() = this
    .clip(RoundedCornerShape(AppShapes.CardRadius))
    .background(
        brush = Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            )
        )
    )
    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(AppShapes.CardRadius))
    .shadow(elevation = 8.dp, shape = RoundedCornerShape(AppShapes.CardRadius), clip = false)
```

---

## SCREEN 1 — Login / Sign Up

### Layout & Hierarchy
1. Full-bleed gradient/animated background (subtle radial cyan glow, low opacity, pulsing scan-line)
2. Logo/app name top-center
3. Segmented tab control: `Student Portal` | `Teacher Portal`
4. Glass card containing form fields
5. Primary CTA button
6. Toggle link: "Don't have an account? Sign up"

### ASCII Wireframe
```
┌─────────────────────────────────┐
│      (soft cyan glow bg)        │
│                                  │
│         🎓 AttendAI             │
│    Facial Attendance System     │
│                                  │
│  ┌────────────┬────────────┐    │
│  │  Student ●  │  Teacher   │    │  ← Segmented control
│  └────────────┴────────────┘    │
│                                  │
│  ╭──────────────────────────╮   │
│  │  Email                   │   │
│  │  ┌──────────────────────┐│   │
│  │  └──────────────────────┘│   │
│  │  Password                │   │
│  │  ┌──────────────────────┐│   │
│  │  └──────────────────────┘│   │
│  │  (Signup only)            │   │
│  │  Name / Roll Number       │   │
│  │                           │   │
│  │  ┌──────────────────────┐│   │
│  │  │      LOGIN           ││   │  ← Primary CTA
│  │  └──────────────────────┘│   │
│  ╰──────────────────────────╯   │
│                                  │
│     New here? Create Account    │
└─────────────────────────────────┘
```

### Compose Components
- `Scaffold` with `Box` background layer (animated `Brush.radialGradient` using `rememberInfiniteTransition` for slow pulse)
- `SegmentedButton` (M3) or custom `Row` with two `FilterChip`s for Student/Teacher
- Fields: `OutlinedTextField` with rounded shape, `leadingIcon` (Email, Lock icons), cyan focus border
- CTA: `Button` full-width, height 56.dp, `AppShapes.ButtonRadius`, `Primary` background, white bold text
- Bottom link: `TextButton` with `Primary` colored text

### Color Usage
| Element | Color |
|---|---|
| Background glow | `Primary` at 8–12% opacity radial gradient |
| Active tab | `Primary` fill, white text |
| Inactive tab | `Surface`, `TextSecondary` |
| Field border (focused) | `Primary` |
| CTA button | `Primary` bg → darker cyan on press |

### Micro-interactions
- Background glow slowly pulses (`infiniteRepeatable`, 4s, ease-in-out) — reinforces "face scan" theme without being literal
- Tab switch: animated `sharedBounds`/`AnimatedContent` slide between Student/Teacher field sets (Roll No. field slides in/out)
- Button press: subtle scale-down (0.97f) via `animateFloatAsState`

---

## SCREEN 2 — Student Dashboard

### Layout & Hierarchy
1. Top welcome banner (greeting + face-status chip)
2. Face Enrollment Status card
3. "My Classes" horizontal-scroll or vertical list with attendance % progress bars
4. Floating "Join Class by Code" button
5. Bottom nav: Dashboard | Classes | Profile

### ASCII Wireframe
```
┌─────────────────────────────────┐
│ (status bar padding + 28dp)     │
│  Hello, Arjun 👋                │
│  Roll No. 61 · CSE               │
│                                  │
│ ╭──────────────────────────────╮│
│ │ 🟢 Face Profile Active        ││  ← status card
│ │ Last enrolled: 3 days ago     ││
│ ╰──────────────────────────────╯│
│                                  │
│  My Classes                     │
│ ╭──────────────────────────────╮│
│ │ CSE301 - ML Fundamentals      ││
│ │ Prof. Sharma · Sec B1         ││
│ │ Attendance ████████░░ 82%     ││
│ ╰──────────────────────────────╯│
│ ╭──────────────────────────────╮│
│ │ CSE305 - Data Structures      ││
│ │ Attendance ██████░░░░ 64%     ││
│ ╰──────────────────────────────╯│
│                                  │
│            ➕ Join Class         │  ← FAB
│                                  │
│  🏠 Dashboard | 📚 Classes | 👤 │
└─────────────────────────────────┘
```

### Compose Components
- Header: `Column` with `Text` (headline, bold) + `Text` (secondary, roll no/dept)
- Status card: `Card` (glassCard modifier) + `Row` with status dot (`Box` circle) + label; color-coded pill (`Success`/`Warning`)
- Class list: `LazyColumn` of `Card`s, each with:
  - `Text` class name (titleMedium, bold)
  - `Text` professor/section (bodySmall, secondary)
  - `LinearProgressIndicator` custom-colored (green if >75%, amber if 50–75%, red if <50%) with % label
- FAB: `ExtendedFloatingActionButton` with icon + "Join Class"
- Bottom nav: `NavigationBar` (M3) with 3 `NavigationBarItem`s

### Color Usage
| Element | Color |
|---|---|
| Face status "Active" | `Success` dot + tinted `Success.copy(alpha=0.15f)` chip bg |
| Face status "Pending" | `Warning` dot + tinted bg |
| Attendance bar ≥75% | `Success` |
| Attendance bar 50–75% | `Warning` |
| Attendance bar <50% | `Error` |
| FAB | `Primary` bg, white icon/text |

### Micro-interactions
- Progress bars animate fill on screen entry (`animateFloatAsState` 0 → target over 600ms, easeOutCubic)
- Class cards: slight lift + scale on tap-down before navigation
- Join Class modal: slides up from bottom (`ModalBottomSheet`) with 6-box OTP-style code input, auto-focus next field

---

## SCREEN 3 — Student Face Enrollment

### Layout & Hierarchy
1. Step indicator top ("Step 2/6 — Look Left")
2. Full-bleed camera preview
3. Centered static oval overlay (canvas-drawn, non-tracking)
4. Instructional text above/below oval
5. Quality checkmark badge
6. Capture button
7. Post-capture: 6-thumbnail summary grid + save CTA

### ASCII Wireframe (Capture Step)
```
┌─────────────────────────────────┐
│  Step 2 / 6 — Look Left         │  ← step indicator
│  ●●○○○○  (dot progress)          │
│                                  │
│   Keep your face inside the oval│
│                                  │
│        ╭─────────────╮          │
│        │             │          │
│        │   (camera)  │  ✓        │ ← green check when aligned
│        │             │          │
│        ╰─────────────╯          │
│                                  │
│   Look straight, then rotate    │
│                                  │
│         ⬤ (capture button)       │
└─────────────────────────────────┘
```

### ASCII Wireframe (Summary Step)
```
┌─────────────────────────────────┐
│   Face Profile Complete! ✅      │
│                                  │
│  ┌────┬────┬────┐               │
│  │ 😐 │ ⬆️ │ ⬇️ │  (thumbnails) │
│  ├────┼────┼────┤               │
│  │ ⬅️ │ ➡️ │ 😊 │               │
│  └────┴────┴────┘               │
│                                  │
│  ┌──────────────────────────┐   │
│  │ Save Profile & Join Class│   │
│  └──────────────────────────┘   │
└─────────────────────────────────┘
```

### Compose Components
- Step indicator: `Row` of small `Box` dots, filled = `Primary`, unfilled = `TextSecondary.copy(alpha=0.3f)`
- Camera: `CameraX` `PreviewView` wrapped in `AndroidView`
- Oval overlay: `Canvas` composable drawn ON TOP of camera preview (separate layer, `drawOval` with `Stroke(width=3.dp)`, `Primary` color at 70% alpha)
- Checkmark badge: `AnimatedVisibility` (scale + fade in) showing a `Success`-colored circular badge with check icon, positioned top-right of oval
- Capture button: large circular `IconButton` (72.dp), white ring, `Primary` fill center
- Summary grid: `LazyVerticalGrid(columns = GridCells.Fixed(3))` of rounded `Image` thumbnails with small angle-label caption underneath

### Color Usage
| Element | Color |
|---|---|
| Oval stroke | `Primary` @ 70% opacity |
| Oval stroke (aligned) | `Success` @ 90% opacity (color shift on good alignment) |
| Checkmark badge | `Success` fill, white icon |
| Step dots (active) | `Primary` |
| Capture button | White ring + `Primary` fill |

### Micro-interactions
- **Oval pulse**: subtle scale breathing animation (1.0 → 1.02 → 1.0, 2s loop) to draw attention, freezes when face is aligned
- **Alignment feedback**: oval stroke color smoothly transitions cyan → green (`animateColorAsState`, 300ms) the instant face-in-region is detected
- **Checkmark**: scales in with spring bounce (`spring(dampingRatio = Spring.DampingRatioMediumBouncy)`)
- **Step transition**: `AnimatedContent` slide-left/fade between steps
- **Thumbnail grid**: staggered fade-in (each thumbnail delayed 60ms after previous)

---

## SCREEN 4 — Teacher Dashboard

### Layout & Hierarchy
1. Welcome banner
2. Class grid/list (cards with quick actions)
3. FAB to create class

### ASCII Wireframe
```
┌─────────────────────────────────┐
│  Good Morning, Prof. Sharma 👋   │
│                                  │
│  My Classes                     │
│ ╭──────────────────────────────╮│
│ │ CSE301 - ML Fundamentals      ││
│ │ Sec B1 · 58/60 students        ││
│ │ Code: ML-4821                 ││
│ │ [Mark Attendance] [View Roster]││
│ ╰──────────────────────────────╯│
│ ╭──────────────────────────────╮│
│ │ CSE305 - Data Structures      ││
│ │ ...                            ││
│ ╰──────────────────────────────╯│
│                                  │
│                          ➕       │  ← FAB create class
└─────────────────────────────────┘
```

### Compose Components
- Class cards (`glassCard`): `Column` with title (bold, titleLarge), subtitle Row (section · student count), Class Code as a pill `AssistChip`
- Two side-by-side action buttons: `OutlinedButton` ("View Roster") + filled `Button` ("Mark Attendance", `Primary`)
- FAB: standard M3 `FloatingActionButton`, `Primary` bg, plus icon

### Color Usage
| Element | Color |
|---|---|
| Class Code chip | `Primary.copy(alpha=0.15f)` bg, `Primary` text |
| Mark Attendance button | `Primary` filled |
| View Roster button | Outlined, `Primary` border/text |
| Student count "58/60" | `Success` if ≥90% enrolled, `Warning` otherwise |

---

## SCREEN 5 — Class Creation Dialog

### Layout
Modal dialog / bottom sheet, form fields, success state swap.

### ASCII Wireframe
```
┌─────────────────────────────────┐
│  Create New Class            ✕  │
│                                  │
│  Class Name                     │
│  [______________________]       │
│  Subject          Semester      │
│  [___________]     [_______]    │
│  Section          Room No.      │
│  [_______]         [_______]    │
│                                  │
│  [        Create Class        ] │
└─────────────────────────────────┘

  ↓ after save ↓

┌─────────────────────────────────┐
│      ✅ Class Created!           │
│                                  │
│     Your Class Code             │
│  ╭──────────────────╮           │
│  │     ML-4821        │ [Copy] │
│  ╰──────────────────╯           │
│                                  │
│  [        Done                ] │
└─────────────────────────────────┘
```

### Compose Components
- `AlertDialog` or `ModalBottomSheet` container
- Two-column fields via `Row` with `Modifier.weight(1f)` `OutlinedTextField`s
- Success state: `AnimatedContent` swaps form → code-reveal view
- Code display: large `Text` (headlineMedium, monospace, letter-spaced), `IconButton` (copy icon) triggering `ClipboardManager`

### Micro-interactions
- Code reveal: scale + fade in with spring bounce
- Copy button: brief "Copied ✓" tooltip/snackbar on tap

---

## SCREEN 6 — Class Roster (Teacher)

### Layout & Hierarchy
1. Header (class name, subject, section, semester)
2. Prominent Class Code banner with Copy + Sync buttons
3. Student list with status badges
4. Bottom sticky "Mark Attendance" button

### ASCII Wireframe
```
┌─────────────────────────────────┐
│ CSE301 - ML Fundamentals         │
│ Sec B1 · Semester 5              │
│                                  │
│ ╭──────────────────────────────╮│
│ │ Class Code: ML-4821            ││
│ │ [COPY CODE]      [SYNC ☁️]    ││
│ ╰──────────────────────────────╯│
│                                  │
│  Roster (58)                    │
│ ┌──────────────────────────────┐│
│ │ 61  Arjun Kumar     🟢 Active ││
│ │ 62  Priya Sharma    🟢 Active ││
│ │ 63  Raj Verma       🟡 Pending││
│ │ ...                            ││
│ └──────────────────────────────┘│
│                                  │
│ [      MARK ATTENDANCE        ] │ ← sticky bottom
└─────────────────────────────────┘
```

### Compose Components
- Code banner: `glassCard` with `Row` — code `Text` (bold, monospace) on left, two `IconButton`/`Button`s on right (Copy, Sync)
- Roster: `LazyColumn` of `ListItem`(M3) — leading: roll no. in circular badge; middle: name; trailing: status pill
- Sticky CTA: placed in `Scaffold`'s `bottomBar`, full-width `Button`, `Primary`

### Color Usage
| Element | Color |
|---|---|
| Code banner bg | `Primary.copy(alpha=0.1f)` |
| Sync button | Outlined cyan, cloud icon |
| Status "Active" | `Success` dot + pill |
| Status "Pending" | `Warning` dot + pill |
| Mark Attendance CTA | `Primary`, full-width, elevated |

### Micro-interactions
- Sync button: rotating cloud/refresh icon animation while syncing, then brief checkmark flash on completion
- Copy button: haptic tick + snackbar "Code copied"

---

## SCREEN 7 — Attendance Capture (Teacher) — 3-Step Flow

### Step 1: Camera Capture
```
┌─────────────────────────────────┐
│  Photo 1 · Start of Class    ✕  │
│                                  │
│                                  │
│         (full-screen             │
│          viewfinder)             │
│                                  │
│                                  │
│         ⬤ (shutter)              │
└─────────────────────────────────┘
```
- `CameraX` full-bleed preview, minimal chrome
- Top overlay: semi-transparent label "Photo 1 · Start of Class" / "Photo 2 · End of Class"
- Large circular shutter button, bottom-center

### Step 2: Photo Review with Detection Boxes
```
┌─────────────────────────────────┐
│  Review Detections               │
│  ╭──────────────────────────────╮│
│  │  [classroom photo]            ││
│  │   🟩Arjun 84%   🟨Raj 55%     ││
│  │        🟥 Unknown 38%          ││
│  ╰──────────────────────────────╯│
│  Detected 52 / 60 expected        │
│  [ Retake ]      [ Continue ]     │
└─────────────────────────────────┘
```
- Photo displayed full-width via `Image` with `Canvas` overlay drawing bounding boxes
- Each box: colored `Stroke` rect + floating label `Surface` (rounded pill) with name + confidence %, anchored above box
- Summary text: "Detected 52/60 expected" with `Warning` tint if mismatch >10%
- Two buttons: `OutlinedButton` (Retake) + `Button` (Continue), side-by-side

### Step 3: Final Attendance Summary
```
┌─────────────────────────────────┐
│  Attendance Summary               │
│                                  │
│  Present in both photos: 50      │
│  Only Photo 1: 2   Only Photo 2: 3│
│  Absent: 5                        │
│                                  │
│  ┌──────────────────────────────┐│
│  │ 61 Arjun     [Present ⚫️Absent]││ ← toggle
│  │ 62 Priya     [Present ⚫️Absent]││
│  │ 63 Raj       [⚫️Present Absent]││
│  └──────────────────────────────┘│
│                                  │
│  [     CONFIRM ATTENDANCE      ] │
└─────────────────────────────────┘
```
- Summary stat row: `Row` of small stat `Card`s (Present/Discrepancy/Absent counts), color-coded
- Per-student override: `ListItem` with a segmented `Present`/`Absent` toggle (custom two-state `SegmentedButton`)
- Final CTA: full-width `Button`, `Primary`, elevated, sticky bottom

### Color Usage (Confidence Boxes)
| Confidence | Box color | Label bg |
|---|---|---|
| ≥ 60% (High) | `Success` | `Success.copy(alpha=0.85f)` |
| 50–60% (Medium) | `Warning` | `Warning.copy(alpha=0.85f)` |
| < 50% (Low/Unrecognized) | `Error` | `Error.copy(alpha=0.85f)` |

### Micro-interactions
- Bounding boxes: fade + scale in sequentially (staggered 40ms) after detection completes, so results feel "discovered" rather than dumped all at once
- Confidence pill: number count-up animation (0% → actual %, 400ms)
- Toggle switches: smooth thumb-slide with color-crossfade (`Error`↔`Success`)
- Tapping a medium/low-confidence box opens a bottom sheet with dropdown to reassign student (searchable list)

---

## SCREEN 8 — Student Profile

### ASCII Wireframe
```
┌─────────────────────────────────┐
│         (Avatar circle)         │
│           AK                    │
│        Arjun Kumar              │
│     Roll No. 61 · CSE · Sec B1  │
│                                  │
│ ╭──────────────────────────────╮│
│ │ 🟢 Face Profile Active         ││
│ │ [Re-enroll Face]               ││
│ ╰──────────────────────────────╯│
│                                  │
│  Email      arjun@school.edu    │
│  Department CSE                 │
│  Section    B1                  │
│                                  │
│  Dark Theme          ⚫️ ⚪        │  ← toggle
│  [           Logout            ]│
└─────────────────────────────────┘
```

### Compose Components
- Avatar: `Box` circular, `Primary` gradient bg, initials `Text` (headlineMedium, white, centered)
- Info rows: `Row` label (secondary text) + value (primary text), separated by `HorizontalDivider` at 8% opacity
- Theme toggle: M3 `Switch`
- Logout: `OutlinedButton` with `Error` colored border/text

---

## SCREEN 9 — Teacher Profile

### ASCII Wireframe
```
┌─────────────────────────────────┐
│         (Avatar circle)         │
│           RS                    │
│      Prof. Ritu Sharma           │
│     ritu.sharma@school.edu       │
│                                  │
│ ╭─────────────╮ ╭──────────────╮│
│ │  6 Classes   │ │ 340 Students │|
│ ╰─────────────╯ ╰──────────────╯│
│                                  │
│  Department   Computer Science  │
│                                  │
│  Dark Theme          ⚫️ ⚪        │
│  [           Logout            ]│
└─────────────────────────────────┘
```

### Compose Components
- Stat cards: two side-by-side `glassCard` `Column`s, large bold number + small label underneath
- Rest identical pattern to Student Profile for consistency

---

## SHARED COMPONENT LIBRARY (Build These Once, Reuse Everywhere)

```kotlin
@Composable
fun StatusPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(AppShapes.PillRadius),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ConfidenceBadge(name: String, confidencePct: Int) {
    val color = when {
        confidencePct >= 60 -> AppColors.Success
        confidencePct >= 50 -> AppColors.Warning
        else -> AppColors.Error
    }
    StatusPill(text = "$name  $confidencePct%", color = color)
}

@Composable
fun AttendanceProgressBar(percent: Float) {
    val color = when {
        percent >= 0.75f -> AppColors.Success
        percent >= 0.5f -> AppColors.Warning
        else -> AppColors.Error
    }
    val animated by animateFloatAsState(percent, tween(600, easing = FastOutSlowInEasing))
    LinearProgressIndicator(
        progress = { animated },
        color = color,
        trackColor = color.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
    )
}
```

---

## NAVIGATION STRUCTURE

```kotlin
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object StudentDashboard : Screen("student_dashboard")
    object StudentEnrollment : Screen("student_enrollment")
    object StudentProfile : Screen("student_profile")
    object TeacherDashboard : Screen("teacher_dashboard")
    object ClassCreate : Screen("class_create")
    object ClassRoster : Screen("class_roster/{classId}")
    object AttendanceCapture : Screen("attendance_capture/{classId}")
    object TeacherProfile : Screen("teacher_profile")
}
```

Bottom nav (Student): Dashboard | Classes | Profile
Bottom nav (Teacher): Dashboard | Profile (simpler, since class actions live inside cards)

---

## GLOBAL MICRO-INTERACTION PRINCIPLES

1. **Confidence-driven color**: Any UI element tied to recognition confidence uses the same 3-tier color law everywhere (Success/Warning/Error) — never introduce a 4th color for this.
2. **Staggered reveals**: Lists/grids of async results (thumbnails, detected faces) fade in staggered, not all at once — communicates "the AI is working."
3. **State-color transitions**: Anything that changes state (oval alignment, sync status) animates its color/shape rather than snapping — feels alive, not jarring.
4. **Sticky primary actions**: The one "next step" button (Confirm Attendance, Save Profile, Mark Attendance) is always full-width, bottom-anchored, and the only `Primary`-filled button visible at once — keeps visual hierarchy unambiguous.

---

## PROMPT TO GIVE ANTIGRAVITY

```
Implement the UI redesign per this specification: 9 screens (Login/Signup, Student 
Dashboard, Student Face Enrollment, Teacher Dashboard, Class Creation, Class Roster, 
Attendance Capture 3-step flow, Student Profile, Teacher Profile).

Use the exact color tokens: Primary #0284C7, Success #10B981, Warning #F59E0B, 
Error #EF4444, Dark bg #0F172A, Light bg #F8FAFC. Apply glassmorphism card style 
(rounded 16dp corners, subtle border, soft shadow) across all cards.

Build shared composables: StatusPill, ConfidenceBadge, AttendanceProgressBar — reuse 
these across screens instead of duplicating badge/pill code.

Apply the confidence 3-tier color law consistently: >=60% green, 50-60% amber, <50% red.

Add micro-interactions: oval alignment color transition (cyan->green), staggered 
fade-in for detected face boxes, animated progress bar fills, spring-bounce on 
checkmark badges, sync button rotating icon.

Keep all existing ML/backend logic (AdaFace, cloud sync, dual-capture) untouched — 
this is UI/theming only.
```
