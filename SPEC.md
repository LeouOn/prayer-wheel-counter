# Prayer Wheel App — Specification & Build Plan

*Android-first, vibe-coded, single-developer scope. Design target: feels devotional, not gamified; respects traditional conventions while leveraging modern UI affordances.*

---

## 1. Recommended Stack

| Layer | Choice | Rationale |
|---|---|---|
| Language | **Kotlin** | First-class Android, AI-tooling-friendly, concise |
| UI Framework | **Jetpack Compose + Material 3** | Declarative, animation-rich, fewer boilerplate files for vibe coding. Compose has matured to the point that an opinionated solo dev can ship something polished in days, not weeks. |
| Min SDK | API 26 (Android 8.0) | Covers ~98% of devices; lets you use modern animation APIs without compat shims |
| Target SDK | API 35 (Android 15) | Required by Play Store; enables predictive back, edge-to-edge |
| Persistence | **DataStore** (settings) + **Room** (sessions/stats) | DataStore for prefs, Room for queryable session history |
| DI | **Koin** (or skip entirely for v1) | Hilt is heavier; for a solo project, manual construction or Koin is fine |
| Async | **Coroutines + Flow** | Standard |
| Custom rendering | **Compose Canvas** + **Modifier.graphicsLayer** | Sufficient for 2.5D wheel; avoid full 3D engine |

**Why Compose over Flutter for this specific app:** You get richer haptics, Android sensor APIs natively, and the Material 3 motion specs out of the box. Flutter wins on cross-platform but you said Android-first. If you want iOS later, **KMP + Compose Multiplatform** is a credible bridge — but don't pre-architect for it; it tends to bleed scope.

**Vibe-coding note:** Compose is exceptionally well-represented in Claude / Cursor / Copilot training data. You'll get higher-quality completions for `@Composable` functions than for, say, custom React Native bridges.

---

## 2. Core Concept

A digital འཁོར་ལོ་ (*khor lo*) — prayer wheel — that:

1. Renders a 3D-feeling rotating cylinder (drum) inscribed with the chosen mantra
2. Counts rotations × mantras-per-revolution = accumulated mantra count
3. Persists lifetime totals across sessions
4. Offers spin modes: drag-to-spin (with momentum), continuous auto-spin, or two-handed gesture mode
5. Treats the practice respectfully: no XP bars, no achievements with garish badges, no streak-shaming

---

## 3. Feature Spec

### 3.1 The Wheel (Primary Surface)

**Visual:**
- Cylindrical drum, slight perspective tilt (~15°)
- Inscribed mantra text wraps around the drum surface (Tibetan དབུ་ཅན་ script preferred; rendered as repeating text with proper baseline alignment)
- Optional: glowing outline that intensifies with rotation speed
- Top finial (གཙུག་ — crowning ornament) — small flame or jewel motif
- Background: gradient that subtly shifts with cumulative session merit (very gentle — saturated gold only at major milestones)

**Interaction:**
- **Drag-to-spin:** Circular drag on the drum imparts angular velocity. Decays via configurable "friction" coefficient.
- **Flick-to-spin:** Quick gesture imparts higher initial velocity.
- **Default direction: clockwise** (viewed from above) — this is non-negotiable for orthodox practice. Make counter-clockwise an explicitly opt-in toggle buried in advanced settings (some practitioners may want it for specific Bön or experimental contexts; default protects the casual user from inadvertent error).
- **Long-press:** Pause spin instantly (for dedication moment).

**Physics model:**
- Angular velocity ω, angular friction μ, max ω cap
- `ω_{t+1} = ω_t × (1 - μ × Δt)`
- Each crossing of θ = 2π triggers a "rotation complete" event → increments counter
- Subtle haptic tick on each rotation completion (configurable: off / light / strong)

### 3.2 Capacity Slider — "Mantras per Rotation"

This is the conceptually rich part. Real prayer wheels contain physical mantras (microfilm or printed scrolls) — sometimes billions per wheel. Digital wheels need a configurable analog.

**Settings exposure:**
- Slider: 1 → 1,000,000,000,000 (logarithmic scale)
- Presets:
  - **Personal mala** (108)
  - **Pocket wheel** (10,000)
  - **Hand wheel** (1,000,000)
  - **Standing wheel** (100,000,000)
  - **Stupa-class** (1,000,000,000,000) — for those wanting Patrul-Rinpoche-scale accumulation
- Display the chosen number with proper grouping and scale label (lakh/crore optional for cultural fit if you want)

**Honesty layer:** A small info icon explains that mantra count efficacy in tradition depends on motivation (bodhicitta), concentration, and the lineage authorization of the wheel — not raw numbers. This protects against the "score-maxing" temptation without being preachy. One sentence, behind a tap.

### 3.3 Mantra Selection

Built-in mantras (with optional audio playback at adjustable volume):
- ཨོཾ་མ་ཎི་པདྨེ་ཧཱུྃ་ — *Om Mani Padme Hum* (Avalokiteśvara, default)
- ཨོཾ་ཨཱཿ་ཧཱུྃ་བཛྲ་གུ་རུ་པདྨ་སིདྡྷི་ཧཱུྃ་ — *Vajra Guru* (Padmasambhava — Nyingma-relevant)
- ཨོཾ་ཨ་ར་པ་ཙ་ན་དྷཱིཿ — *Arapacana* (Mañjuśrī)
- ཏདྱ་ཐཱ། ཨོཾ་གཏེ་གཏེ་པཱ་ར་གཏེ་པཱ་ར་སཾ་གཏེ་བོ་དྷི་སྭཱ་ཧཱ — Heart Sūtra mantra
- ཨོཾ་ཏཱ་རེ་ཏུཏྟཱ་རེ་ཏུ་རེ་སྭཱ་ཧཱ — Tārā
- ཨོཾ་ཨཱཿ་ཧཱུྃ་ — three vajra syllables
- **Custom**: user can paste any mantra (Tibetan or transliteration); store as user-defined entry

**Display fidelity:** Use a proper Tibetan font (e.g., Noto Sans Tibetan, Microsoft Himalaya, or Jomolhari) bundled with the app. Don't rely on system font fallback — many Android devices lack proper stacking glyph support and will mangle subscripts.

### 3.4 Spin Modes

| Mode | Description | UI |
|---|---|---|
| **Manual** | User drags. Wheel decays. Standard. | Default |
| **Two-handed** | Wheel only spins while two distinct touch points are maintained. The friction coefficient drops to near-zero while engaged — rewards committed, embodied attention. | Toggle |
| **Auto-spin** | Set RPM, wheel spins continuously. Useful for background accumulation during other practice (sitting, walking). | Toggle + RPM slider 1–60 |
| **Wind mode** | Phone's microphone detects breath/wind; ambient audio drives spin. Genuinely playful, gestures toward རླུང་ (wind/prāṇa) symbolism. | Optional / experimental |

### 3.5 Session Mechanics

- **Start session** button (large, centered, restrained typography — no "PLAY!")
- Live counters: this-session rotations | this-session mantras | lifetime mantras
- **End session** invokes a **dedication prompt** (configurable):
  - Default text: a short བསྔོ་བ་ — dedication of merit (e.g., "By this merit, may all beings attain the omniscient state of buddhahood")
  - User can edit or replace with their own
  - Presented as a quiet screen, not a popup; tap to confirm and write to history

### 3.6 Settings Architecture

```
Settings/
├── Wheel/
│   ├── Mantra (selection)
│   ├── Capacity (slider — mantras per rotation)
│   ├── Rotation direction (clockwise default)
│   ├── Wheel skin (color/material — copper, gold, silver, ivory)
│   └── Drum aspect ratio
├── Interaction/
│   ├── Spin mode (manual / two-handed / auto / wind)
│   ├── Friction coefficient
│   ├── Max RPM
│   ├── Haptics (off / light / strong)
│   └── Tilt-to-spin sensitivity (use accelerometer; physical phone tilt nudges wheel)
├── Audio/
│   ├── Mantra recitation (off / single voice / ensemble) — bundled audio
│   ├── Bell on milestones (off / soft / standard)
│   ├── Ambient (off / wind / stream / silence)
│   └── Master volume
├── Practice/
│   ├── Default dedication text
│   ├── Show/hide live counter (some prefer to spin without watching numbers)
│   ├── Milestone notifications (1M, 1B, 1T...)
│   └── Daily reminder (optional, gentle — not a "streak alert")
├── Display/
│   ├── Theme (light / dark / sepia / dawn-dusk auto)
│   ├── Background ambient animation (off / subtle / full)
│   └── Always-on while session active
└── Data/
    ├── Export sessions (CSV/JSON)
    ├── Backup (local file)
    └── Reset lifetime counter (with confirmation)
```

---

## 4. Animation Design

This is where the app earns its devotional feeling or fails as a toy.

**Core animations:**
1. **Drum rotation** — `Modifier.graphicsLayer { rotationZ = angle }` with skewed perspective. Use Compose's `animateFloatAsState` for momentum decay, or drive directly from a coroutine-managed angular velocity state.
2. **Mantra text wrap** — Render the mantra string repeated around the cylinder using `drawText` on a Canvas, with each glyph's position computed via `cos/sin` of its angular position on the drum surface, plus per-glyph rotation matching the surface tangent.
3. **Particle/glow on milestone** — When a power-of-ten threshold is crossed, emit subtle radiating glyphs or light particles. Use `Lottie` for hand-crafted complex animations; use Compose `Canvas` with particle simulation for procedural ones.
4. **Background gradient drift** — Slow `Brush.linearGradient` interpolation tied to session duration. Should be barely perceptible.
5. **Counter increment** — Crossfade digits with `AnimatedContent` rather than instant snap.

**Libraries:**
- **`androidx.compose.animation`** — built-in, sufficient for 90% of cases
- **`com.airbnb.android:lottie-compose`** — for the finial flame, milestone bursts, any hand-animated assets you commission or download from LottieFiles
- **`com.google.accompanist:accompanist-systemuicontroller`** — edge-to-edge polish (deprecated in favor of Compose's built-in APIs in latest versions; check current state)

**Anti-patterns to avoid:**
- Confetti on milestones (gauche)
- Bouncy / springy easings (too playful for the tone)
- Drop shadows everywhere (Material 3 prefers tonal elevation)
- Stock "celebration sound" (use a real recorded singing bowl or གཎྜཱི་ if you can source one with appropriate licensing — or omit entirely)

---

## 5. Data Model

```kotlin
@Entity
data class Session(
    @PrimaryKey val id: UUID,
    val startedAt: Instant,
    val endedAt: Instant,
    val rotationCount: Long,
    val mantrasPerRotation: Long,
    val totalMantras: Long,         // = rotationCount * mantrasPerRotation
    val mantraId: String,
    val dedication: String?,
    val mode: SpinMode
)

@Entity
data class LifetimeStats(
    @PrimaryKey val id: Int = 0,    // singleton row
    val totalRotations: Long,
    val totalMantras: BigInteger,   // can exceed Long.MAX_VALUE at stupa-class capacity!
    val sessionsCompleted: Int,
    val firstSessionAt: Instant?
)
```

**Critical:** Lifetime mantra totals will overflow `Long` if you're using stupa-class capacity. You mentioned quintillions in past practice. Use `BigInteger` for cumulative totals, store as TEXT in Room with a TypeConverter.

---

## 6. Permissions & Privacy

- **No network** for v1 (no analytics, no ads, no telemetry — this matters for a devotional app)
- **No account system**
- All data local
- Optional: `RECORD_AUDIO` permission only if user enables wind mode
- Optional: `VIBRATE` for haptics
- Optional: `WAKE_LOCK` for keep-screen-on during long sessions

State this clearly on the Play Store listing. It's a differentiator.

---

## 7. Build Phases

**Phase 0 — Skeleton (1 day)**
- New Compose project, Material 3, dark theme, edge-to-edge
- Single screen with a static circle that says "wheel here"
- Wire up DataStore for one setting (selected mantra)

**Phase 1 — Spinning wheel (2–3 days)**
- Drag-to-spin with momentum decay
- Rotation counter
- Capacity slider with logarithmic scale
- Lifetime persistence via Room

**Phase 2 — Polish pass (2–3 days)**
- Tibetan text rendering on cylinder surface (this is the fiddly part — budget extra time)
- Bundled font
- Material 3 theming refinement
- Haptics on rotation tick
- Three preset mantras

**Phase 3 — Modes & sessions (2 days)**
- Two-handed mode
- Auto-spin
- Session start/end with dedication
- Session history screen

**Phase 4 — Polish & release (2–3 days)**
- Milestone animations (Lottie or Canvas particles)
- Settings screen
- Onboarding (3 screens max — skippable)
- Play Store listing

**Total realistic solo timeline: 10–15 working days for v1.**

---

## 8. Key Implementation Gotchas

1. **Tibetan text on a curve** — Android's `Canvas.drawTextOnPath` does *not* properly handle Tibetan stacking characters in many implementations. You may need to render glyph clusters individually with manual positioning. Test early. If it's a bottleneck, fall back to romanized mantra text on the drum and full Tibetan elsewhere in the UI.

2. **Frame budget** — Hold 60fps minimum, target 90/120 on capable devices. Profile with Layout Inspector and Compose recomposition tracking; the wheel should be a single recomposing element, not redrawing the whole screen.

3. **Sensor noise in tilt mode** — Apply a low-pass filter to accelerometer data; raw values are jittery.

4. **State hoisting for the wheel** — Keep angular velocity in a `ViewModel` or top-level state holder, not inside the Composable. Otherwise rotation breaks across configuration changes.

5. **Audio licensing** — Recorded mantra audio: source from CC0 / public domain, get explicit permission from a teacher, or record yourself. Don't scrape YouTube.

---

## 9. Two-Handed Mode — Design Note

Worth elaborating because you flagged it. Three plausible interpretations:

| Interpretation | UX | Practice resonance |
|---|---|---|
| **Two simultaneous touches required** | Wheel only spins while both fingers touch screen | Embodied commitment; mirrors holding a real wheel with both hands |
| **Split UI** | Left half: spin. Right half: counter/mantra display. | Bimanual coordination |
| **Sustained-grip mode** | Two-finger pinch must be maintained throughout session; release ends session early | Highest commitment tier |

My recommendation: **Option 1**, with friction reduced to near-zero while both touches are maintained. This rewards sustained engagement without becoming punitive — release one finger and friction returns to normal, wheel decays. It's a soft constraint that mirrors the embodied attention of holding a real wheel.

---

## 10. Out of Scope for v1 (parking lot)

- Multi-user / shared wheels
- Cloud sync
- iOS port
- Apple Watch / Wear OS companion
- Video/AR rendering of large temple wheels
- Integration with mala counters (Bluetooth bead counters exist!)
- Community dedication boards

Resist scope creep. Ship v1 first.

---

## 11. Tone & Copy Principles

- No exclamation points anywhere in the app
- No "Awesome!" / "Great job!" affirmations
- Numbers are reported plainly; no "🎉 1 MILLION! 🎉"
- Dedication text defaults to a real, traditional formula — not motivational platitudes
- Settings use precise terms ("Mantras per rotation" not "Power level")
- No leaderboards, no social sharing prompts in v1

The app should feel like a tool a serious practitioner would tolerate, not a Calm-app spinoff. This is, paradoxically, also what makes it appealing to non-practitioners curious about the practice — earnestness reads as quality.

---

## Quick Library Cheat Sheet

```kotlin
// build.gradle.kts (app)
dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2025.04.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    
    // Animation
    implementation("androidx.compose.animation:animation")
    implementation("com.airbnb.android:lottie-compose:6.5.2")
    
    // Persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // Lifecycle / ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    
    // Optional DI
    implementation("io.insert-koin:koin-androidx-compose:4.0.0")
    
    // Audio (if bundling mantra recordings)
    implementation("androidx.media3:media3-exoplayer:1.5.0")
}
```

Verify versions at build time — these move fast. Use Android Studio's built-in dependency updater.
