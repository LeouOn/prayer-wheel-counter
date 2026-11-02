# Prayer Wheel Counter

A devotional Android app that counts mantra recitations through physical prayer wheel practice. Supports capacity from personal mala (108) to stupa-class (1,000,000,000,000) with lifetime tracking, session history, and a practice calculator for projecting accumulation.

## Features

### The Wheel
- 3D-feeling rotating drum with Tibetan mantra text (Om Mani Padme Hum and others)
- Drag-to-spin with momentum physics and configurable friction decay
- Multiple spin modes: manual drag, two-handed, auto-spin, and breath mode (microphone-driven "wind")
- Six view modes: side, top-down, abstract, table-top, globe, and dual wheels
- Clockwise by default (counter-clockwise opt-in)
- Saved named wheels (capacity + mantra presets)

### Capacity Settings
- Logarithmic slider from 1 to 1,000,000,000,000 mantras per rotation
- Presets: Personal Mala (108), Pocket Wheel (10K), Hand Wheel (1M), Standing Wheel (100M), Stupa-class (1T)
- Each real prayer wheel has different physical mantra capacity — the app mirrors that flexibility

### Sessions & Dedication
- Sessions start on the first spin and end with a traditional dedication prompt, with an optional je-nyer (rejoicing) step
- Session intention, morning/evening auto-labels, and manual "Log Practice" for off-app sessions
- Session stats: duration, average RPM, peak RPM, rotation count, mantras accumulated
- Customizable dedication text

### Statistics & History
- Lifetime totals: rotations, mantras, sessions, total practice time
- 7-day and 30-day practice comparisons
- Heat map calendar showing daily practice intensity, with current/best practice-day runs
- Session history with full stats per session, filters by time, mode and wheel
- Today card with daily mantra/time goals; home-screen widget
- Milestones (108 → 1T) that quietly unlock wheel skins
- Export sessions as CSV or JSON

### Practice Calculator
- **Personal tab**: Project future accumulation over 1 month to 10 years
- **Monastery tab**: Compare your output to verbal recitation; see how long a community of monks would take to reach the same milestones
- Both tabs show formulas and assumptions so you understand the math

### Settings
- Theme: system/light/dark/sepia/dawn-dusk
- Number format: standard (K/M/B/T), exact (with commas), scientific, long-form; notation beyond trillions (Qa, Qi… or aa, ab…)
- Haptics: rotation ticks, milestone pulses, configurable intensity
- Time goals: session and daily practice targets
- Audio: synthesized singing bowl at milestones, RPM-following ambient drone
- Reminders: gentle morning/evening reminders and an optional end-of-day summary

## Architecture

```
app/src/main/java/com/prayerwheel/app/
├── audio/               AudioEngine (synthesized bowl + drone), BreathModeController
├── data/
│   ├── datastore/       UserPreferences (DataStore), PreferenceEnums, SavedWheelsJson
│   ├── db/              Room database, migrations, DAOs
│   ├── model/           LifetimeStats, Session, Mantra, WheelSkin, Achievement, …
│   └── stats/           LifetimeStatsMath — pure add/subtract of sessions to lifetime totals
├── notification/        Reminders, end-of-day summary, session foreground service, re-arm receiver
├── ui/
│   ├── components/      CounterDisplay, CapacitySlider, NumberFormatter, dialogs, …
│   ├── navigation/      NavHost with all routes
│   ├── theme/           Material 3 theming (sepia, dawn-dusk, …)
│   └── wheel/           Screens; subpackages:
│       ├── views/       one file per wheel view mode
│       ├── render/      Canvas drawing helpers and glow math
│       ├── calculator/  Personal + Monastery calculators and pure CalculatorMath
│       ├── calendar/    calendar components, Streaks
│       ├── history/     session card
│       ├── settings/    settings helpers
│       └── export/      CSV / JSON builders
├── viewmodel/           WheelViewModel (central state holder) plus pure, unit-tested logic:
│   ├── physics/         SpinPhysics
│   ├── milestones/      Milestones
│   ├── session/         SessionRules
│   └── haptics/         HapticPatterns
├── widget/              Glance home-screen widget
└── work/                SessionSaveWorker (WorkManager)
```

Pure logic is kept free of Android dependencies so it can be covered by JVM unit tests in `app/src/test/`.

## Data Model

- **Session**: single practice period with start/end time, rotation count, mantras, average/peak RPM, dedication
- **LifetimeStats**: singleton row tracking cumulative totals (uses BigInteger for mantras)
- **UserPreferences**: DataStore-based settings for all user choices

## Building

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew lintDebug              # lint (fails on errors)
./gradlew assembleRelease        # minified release; unsigned unless signing is configured
```

Requires:
- Android SDK 36 (compileSdk / targetSdk 36, minSdk 26)
- Gradle 9.4+ (wrapper included), AGP 9 with built-in Kotlin
- JDK 17+

Release signing is optional and read from Gradle properties or environment variables:
`PRAYERWHEEL_KEYSTORE_FILE`, `PRAYERWHEEL_KEYSTORE_PASSWORD`, `PRAYERWHEEL_KEY_ALIAS`, `PRAYERWHEEL_KEY_PASSWORD`.
Never commit a keystore.

## Privacy

No network, no accounts, no telemetry. All data local. Microphone permission only if breath mode is enabled. Sessions, lifetime totals and settings are included in Android backup / device transfer (Room database and DataStore only).

## Design Philosophy

No XP bars, no leaderboards, no streak-shaming. Milestones exist but are quiet: they unlock wheel skins, never nag. Numbers are reported plainly — the app is a tool for serious practitioners, not a gamified wellness app. The tone is devotional: traditional dedication formulas, singing bowl sounds on milestones, no exclamation points or "Awesome!" affirmations.

## Status & roadmap

See [`docs/2027-readiness.md`](docs/2027-readiness.md) for the current assessment: what's built vs. planned, known issues, and the 2027 ideas backlog.
