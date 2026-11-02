# 2027 Readiness — State of the App

*Assessment date: 2026-11-01, updated after the `chore/2027-readiness` work. Covers what's built, what was planned, what was broken, and what's next.*

## TL;DR

- **The core works**: physics, counting, sessions + dedication, history, calendar heat map, stats, calculator, themes, haptics, breath (wind) mode, widget, reminders, achievements/skins.
- **Release blocker — fixed on this branch**: Google Play requires `targetSdk 36` for updates since 2026-08-31; the app now targets 36.
- **Data integrity — fixed on this branch**: a bug that discarded rotations from active sessions on every flick, session loss on process death, non-atomic lifetime writes, a `SUM()` overflow crash, broken Android 12+ backup, and unbounded destructive migration (§4).
- **Year/day boundaries (Dec 31 → Jan 1) — fixed**: date-picker day/year shift, calendar streak, stale "today" card, alarms after time-zone change, late end-of-day summary.
- **Maintainability**: god files split, pure logic extracted, 0 → 177 unit tests (§6).
- **Decided 2026-11-02**: merge threshold and dead settings removed, Number Notation wired up, SPEC updated to the as-built tone (see "Decisions").
- **Not yet done**: on-device smoke test, the compileSdk 37 dependency wave, and the breath-mode background question.

## 1. Planned vs. built

### SPEC.md (original v1 spec)

| Area | Status |
|---|---|
| Drag/flick/momentum physics, long-press pause, clockwise default | Done |
| Capacity slider (log scale, presets, honesty info) | Done |
| Spin modes: manual, two-handed, auto | Done (two parallel UI controls for the same thing — `SpinMode` enum in Settings and booleans in SpinModeSelector) |
| Wind mode | Done, named "Breath mode" |
| Session + dedication | Done; diverged — sessions auto-start on spin, dedication is an overlay card |
| Mantras: 6 + custom | Partial — 3 mantras, "Custom" is a "Coming soon" tile |
| Bundled Tibetan font | Not started — `res/font/noto_sans_tibetan.xml` is an empty placeholder |
| Tilt-to-spin, drum aspect ratio | Not started |
| Audio: recitation | Not started (toggle exists, no audio) |
| Audio: bell levels, ambient wind/stream | Partial — on/off synthesized bowl, RPM drone only |
| Export CSV/JSON | Done (share intent; JSON escaping is incomplete) |
| Backup / restore | Partial — writes to app-private external dir (deleted on uninstall), no import |
| Onboarding (≤3 screens, skippable) | Partial — no Skip |
| Reset lifetime counter | Done |

### Gamification/polish plan (`.kilo/plans/…nimble-comet.md`) — ~50% done

Done: achievements (10 tiers), 6 unlockable skins, milestone celebration overlay, achievements screen, glow speed tiers, RPM drone, background warmth, inspire-me intention button, RPM-scaled haptics.
Not done: enriched session summary (duration/RPM/monk comparison), time-goal ring around wheel (diverged into a separate display), idle breathing glow, incense/lotus particles, weekly insights, layout pass (collapsible panel, auto-hide top bar), live skin preview, non-skin achievement rewards that `Achievement.kt` *promises* (welcome animation, lotus particles, stupa on calendar).

### Accessibility & empty-states plan (2026-07-13)

All tasks done (commits c636af6…f77444a); the plan's checkboxes were never ticked.

### Built but in no plan

Home widget, foreground session notification, WorkManager session save, morning/evening/end-of-day reminders, saved named wheels, 5 view modes + dual-wheel view, calendar heat map with streaks, manual "Log Practice", cosmic/yuga comparisons, monastery calculator, today-progress card with goals, wheel mass / auto-label settings, je-nyer rejoicing, Share Intention / Send Light.

## 2. Dead settings and UI

Resolved: **Direction**, **Recitation** + **Recitation Volume**, **Ambient Volume**, **Milestone notifications** and **Session merge threshold** were removed from Settings and `UserPreferences`; a DataStore migration (`RetiredPreferencesMigration`) deletes their stored values on upgrade. **Number Notation** was kept and now applies to the wheel-screen counter and today card (STANDARD continues Qa, Qi… after T; EXTENDED continues aa, ab…).

Still open: `jeNyerEnabled` has a preference but no UI. Export CSV / Export JSON / Backup all open the same screen.

## 3. Tone (SPEC §11)

Resolved by updating the spec rather than the UI: SPEC §11 now allows quiet milestones (with "Unlocked / Reward / n of 10" labels), practice-day runs shown as plain facts, user-initiated "Send Light" sharing, and a small set of devotional emoji. Celebratory emoji, prompts to share and any run-breaking warnings remain out.

## 4. Correctness & data integrity

Status as of branch `chore/2027-readiness`. ✅ = fixed on this branch.

| # | Sev | Issue | Status |
|---|---|---|---|
| 0 | Critical | `needsNewSession` reset an *active* session on every flick once the last dedicated session was >5 min old — rotations silently discarded from the session record (present since the first commit) | ✅ only starts a new session when none is active |
| 1 | Critical | Live session only persisted from `onCleared()`; resume deleted the saved row before re-holding it in memory | ✅ `active_session` checkpoint every 15 s + on background, recovered on startup; resume replaces in place keyed by `session_key` |
| 2 | High | `SUM(totalMantras)` over TEXT → INT64 overflow → Stats crash | ✅ aggregated in Kotlin as BigInteger |
| 3 | High | Unbounded destructive migration; no exported schema | ✅ destructive only from v1; schemas exported to `app/schemas` (v4, v5) |
| 4 | High | Android 12+ backup misconfigured | ✅ separate `data_extraction_rules.xml` (DB + DataStore) |
| 5 | Med-high | Session insert and lifetime update not atomic; worker/reset bypassed mutex | ✅ all writes via `LedgerDao` `@Transaction` methods; reset moved into the ViewModel |
| 6 | Medium | Delete after lifetime reset subtracted pre-reset counts | ✅ `reset_at` / `recorded_at` |
| 7 | Medium | Manual-log date picker shifted day/year west of UTC; future dates allowed | ✅ |
| 8 | Medium | Calendar streak ≈ always 0; confined to displayed month | ✅ computed from all history across months/years |
| 9 | Medium | Today-progress stale overnight / across time zones | ✅ recomputed on foreground + 60 s tick |
| 10 | Medium | Session duration included idle wait and open dedication prompt | ✅ snapshot at session end |
| 11 | Low | Export JSON/CSV escaping, fake `Z` timestamp | ✅ org.json + RFC 4180 CSV; large exports via `EXTRA_TEXT` still fail >~1 MB (needs FileProvider) |
| 12 | Low | History wheel filter / stale "Today" | ✅ |
| 13 | Low | Calculator monthly = week × 4 | ✅ unified 365.25-day basis |
| 14 | Low | Alarms not re-armed on time/zone change; late end-of-day alarm skipped; `runBlocking` in receiver | ✅ |
| 15 | Low | DataStore corruption crash loop; one bad saved wheel dropped all | ✅ |
| — | Crash | Dedication screen loaded an empty font resource | ✅ uses `TibetanFont` |
| — | Low | Ghost "0 mantras" notification after process death; unused `WAKE_LOCK`; exported `EndOfDayReceiver`; breath-mode mic recording in background | ✅ |

An independent review of the ledger work found six further defects in the resume path (stale resume snapshot, resume re-pricing a session at the current wheel's capacity, v4-era resets invisible after migration, deleting the live row, resume time gap, Reset All racing the save worker) — all fixed, plus rotations lost during the first drag of a session.

Behavior changes to know about:
- A session keeps its own capacity / mantra / wheel. Changing them on the wheel screen mid-session saves the current session and starts a new one; changes from Settings apply from the next session.
- Resuming a session switches the wheel screen to that session's wheel.
- Recovered (crash-interrupted) sessions are saved as completed sessions without a dedication; the resume banner still offers to continue them.

Known residual risks: up to 15 s of practice can be lost on a hard kill; a save worker queued by a previous process can re-insert a row the user deleted before it ran; the widget and end-of-day summary compute time as end − start (includes a resume gap); ledger transactions are unit-tested against an in-memory fake (migration also checked against real SQLite), with no instrumented Room tests yet; nothing on this branch has been run on a device.

## 5. Build & platform

Done on this branch: targetSdk/compileSdk 36, version 1.1.0 (2); AGP 9 opt-outs removed (built-in Kotlin, new DSL); Kotlin 2.4.21, KSP 2.3.13, Compose BOM 2025.11.01, Room 2.8.5, coroutines 1.11, DataStore 1.3, Work 2.12, Glance 1.2, activity 1.13; optional release signing from env/Gradle properties; R8 keeps line numbers; lint passes with 0 errors.

Still open:
- Compose BOM 2026.x, core 1.19, navigation 2.10 need compileSdk 37 — next bump.
- `TabRow` deprecated (CalculatorScreen) → `PrimaryTabRow`.
- Lint baseline/CI; a Play Console declaration for the `specialUse` foreground service.
- Notification small icon is a system drawable; ship a monochrome vector.
- Localization: ~500 hardcoded user-facing strings.

**Manual smoke test before release (Android 16 device):** edge-to-edge on every screen (gesture + 3-button nav, IME), predictive back on every screen/dialog, calculator tabs + icons (material3 1.4), widget updates, end-of-day notification, upgrade over a 1.0.0 install keeps all sessions and lifetime totals (v4 → v5 migration), kill the app mid-session and confirm the session is recovered.

## 6. Refactor (done on this branch)

- `WheelScreen.kt` 3524 → 886 lines: one file per view mode in `ui/wheel/views/`, drawing in `ui/wheel/render/`.
- `WheelViewModel.kt`: pure logic extracted to `viewmodel/physics|milestones|session|haptics` and `data/stats`; session/lifetime writes moved to `LedgerDao` / `SessionLedger`.
- Calculator, Settings, History, Calendar, UserPreferences split into subpackages; exact-duplicate helpers merged.
- Tests: 0 → 177 JVM unit tests.

Deferred: unifying the 6 copy-pasted gesture handlers and divergent glow copies (visual change), merging formatters that only look alike, splitting the single ViewModel, DI, Robolectric/instrumented Room tests.

## Decisions

1. **Session merge threshold** — removed (2026-11-02).
2. **Dead settings** — removed, except Number Notation, which was implemented (2026-11-02).
3. **Tone** — SPEC §2, §3.6, §10, §11 updated to match the app (2026-11-02).
4. **Breath mode** (open) — stops when the app is backgrounded. If background breath spinning is wanted, it needs a `microphone` foreground service.

## 7. Ideas backlog for 2027 (not started)

Grouped from the spec parking lot, the kilo plan, and gaps above. Ordered roughly by value/effort.

- **Real backup & restore** — SAF export to a user-chosen file + import; the most important missing feature for a lifetime-count app.
- **Bundle Noto Sans Tibetan** (OFL) — proper stacking glyphs; spec called it out as essential.
- **Custom mantras** — the "Coming soon" tile.
- **Enriched dedication summary** — duration, RPM, comparison; show the session intention.
- **Losar awareness** — Tibetan New Year (2027-02-07, verify against an almanac) as a gentle calendar marker; optional lunar-calendar auspicious days (multiplier days are a traditional concept — treat with care).
- **Year-in-review** — a quiet annual summary on Jan 1 (no confetti, numbers plainly).
- **Weekly insights**, idle breathing glow, incense-mote particles.
- **Wheel-screen layout pass** — keep wheel + counter visible without scrolling.
- **Localization** — move strings to resources; Tibetan / Chinese / Hindi audiences are natural.
- **Parking lot** (unchanged): cloud sync, Wear OS, Bluetooth mala counters, iOS.
