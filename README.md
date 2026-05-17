# Prayer Wheel Counter

A devotional Android app that counts mantra recitations through physical prayer wheel practice. Supports capacity from personal mala (108) to stupa-class (1,000,000,000,000) with lifetime tracking, session history, and a practice calculator for projecting accumulation.

## Features

### The Wheel
- 3D-feeling rotating drum with Tibetan mantra text (Om Mani Padme Hum and others)
- Drag-to-spin with momentum physics and configurable friction decay
- Multiple spin modes: manual drag, two-handed, auto-spin, and wind mode (microphone-driven)
- Clockwise by default (counter-clockwise opt-in)

### Capacity Settings
- Logarithmic slider from 1 to 1,000,000,000,000 mantras per rotation
- Presets: Personal Mala (108), Pocket Wheel (10K), Hand Wheel (1M), Standing Wheel (100M), Stupa-class (1T)
- Each real prayer wheel has different physical mantra capacity — the app mirrors that flexibility

### Sessions & Dedication
- Session start/end with traditional Tibetan dedication prompt (nyer chö)
- Session stats: duration, average RPM, peak RPM, rotation count, mantras accumulated
- Customizable dedication text

### Statistics & History
- Lifetime totals: rotations, mantras, sessions, total practice time
- 7-day and 30-day practice comparisons
- Heat map calendar showing daily practice intensity
- Session history with full stats per session

### Practice Calculator
- **Personal tab**: Project future accumulation over 1 month to 10 years
- **Monastery tab**: Compare your output to verbal recitation; see how long a community of monks would take to reach the same milestones
- Both tabs show formulas and assumptions so you understand the math

### Settings
- Theme: system/light/dark/sepia/dawn-dusk
- Number format: standard (K/M/B/T), exact (with commas), scientific, long-form
- Haptics: rotation ticks, milestone pulses, configurable intensity
- Time goals: session and daily practice targets
- Audio: mantra recitation playback, bell at milestones, ambient sounds

## Architecture

```
app/src/main/java/com/prayerwheel/app/
├── data/
│   ├── datastore/       UserPreferences.kt — DataStore-backed settings (theme, haptics, etc.)
│   ├── db/              Room database (sessions, lifetime stats)
│   └── model/           LifetimeStats, Session, Mantra, WheelSkin, etc.
├── ui/
│   ├── components/      CounterDisplay, CapacitySlider, NumberFormatter, etc.
│   ├── navigation/      Navigation.kt — NavHost with all routes
│   ├── theme/           Material 3 theming (sepia, dawn-dusk, etc.)
│   └── wheel/           WheelScreen, SettingsScreen, CalculatorScreen, etc.
└── viewmodel/           WheelViewModel.kt — central state holder (~1600 lines)
```

## Data Model

- **Session**: single practice period with start/end time, rotation count, mantras, average/peak RPM, dedication
- **LifetimeStats**: singleton row tracking cumulative totals (uses BigInteger for mantras)
- **UserPreferences**: DataStore-based settings for all user choices

## Building

```bash
./gradlew assembleDebug
```

Requires:
- Android SDK 35 (compileSdk / targetSdk)
- Gradle 9.4+ (wrapper included)
- JDK 17+

## Privacy

No network, no accounts, no telemetry. All data local. Audio permissions only if wind mode is enabled.

## Design Philosophy

No XP bars, no achievement badges, no streak-shaming. Numbers are reported plainly — the app is a tool for serious practitioners, not a gamified wellness app. The tone is devotional: traditional dedication formulas, singing bowl sounds on milestones, no exclamation points or "Awesome!" affirmations.