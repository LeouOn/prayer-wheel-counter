# Accessibility & Empty States Polish — Design

**Status:** Approved (auto, continuation directive)
**Date:** 2026-07-13
**Scope:** Four screens — `WheelScreen.kt`, `StatsScreen.kt`, `HistoryScreen.kt`, `CalendarScreen.kt`. Plus the related component `WheelCustomizer.kt` for touch-target review.

## 1. Goal

Polish two facets of the existing Compose UI without changing visual identity, physics, audio, or settings:

1. **Accessibility** — every icon-only `IconButton` has a meaningful `contentDescription`; every `IconButton` meets the Material 48dp minimum touch target.
2. **Empty states** — `StatsScreen` shows a devotional empty block with guidance, and `WheelScreen` offers a quiet first-spin hint for brand-new users.

## 2. Constraints

- **Do not change hardcoded colors.** Palette changes are a separate follow-up.
- **Do not change physics or audio.**
- **Do not change `SettingsScreen`.**
- **Do not add new files.**
- Devotional tone: plain, calm, no exclamation points, no gamification language.
- No changes to `WheelViewModel` state surface.

## 3. Existing patterns honored

- `WheelScreen` / `HistoryScreen` / `CalendarScreen` use Material 3 `IconButton` defaults (48dp tap area).
- `HistoryScreen` already shows a lotus-emoji empty state at lines 388–414 — this is the template for the new `StatsScreen` empty block (minus the emoji, see §6).
- `WheelViewModel` exposes `rotationCount: StateFlow<Long>` and `angularVelocity: StateFlow<Float>` via `collectAsState()` — both are zero on a fresh install, and either going non-zero is a reliable "user has spun" signal without any new state.

## 4. Approach

Delta-only edits inside four existing Kotlin files. No state additions, no new composables, no ViewModel changes.

### Why local edits

| Option | Decision |
|---|---|
| A. Local edits only (this design) | **Pick.** Smallest diff, satisfies constraints. |
| B. Add `hasInteractedOnce` `StateFlow` + DataStore flag | Rejected — out of scope; persistence not required (the hint only matters during the very first session on a brand-new install). |
| C. Extract a reusable `EmptyState` composable | Rejected — only one new instance; introducing a new component would couple to a `DESIGN.md` extraction that the user has not asked for. |

## 5. Change 1 — `contentDescription = null` audit

Verified in-scope `IconButton` instances in the four target files all already carry descriptions (`"Calculator"`, `"Customize"`, `"Share"`, `"Statistics"`, `"Settings"`, `"History"`, `"Back"`, `"Calendar"`, `"Delete session"`, `"Toggle Filters"`, `"Dismiss"`).

The remaining `contentDescription = null` instances in the codebase are **decorative** and stay null per the task's own rule:

- `SettingsScreen.kt:550` — `ListItem` leading icon next to "Practice Calculator" label.
- `SettingsScreen.kt:1019` — `ListItem` leading icon next to "Reset all data" label.
- `LogPracticeDialog.kt:254` — icon inside a `FilterChip` label "Custom Date".
- `ShareIntentionDialog.kt:130` — icon inside `Button` paired with `Text("Share")`.
- `ShareIntentionDialog.kt:147` — icon inside `OutlinedButton` paired with `Text("Send Light")`.

All are accompanied by a sibling text label. **No edits required for Change 1.**

## 6. Change 2 — Touch targets

| File:line | Current | Action |
|---|---|---|
| `HistoryScreen.kt:716–727` | `IconButton(modifier = Modifier.size(24.dp)) { Icon(... Modifier.size(20.dp)) }` | Remove the outer `size(24.dp)`; enlarge the `Icon` glyph to `Modifier.size(24.dp)` so the default 48dp tap area is restored while the glyph remains legible. |
| `CalendarScreen.kt:843–853` | `IconButton(modifier = Modifier.size(20.dp)) { Icon(... Modifier.size(14.dp)) }` | Replace the outer `size(20.dp)` with `Modifier.minimumInteractiveComponentSize()` so the tap area is the standard 48dp even inside the dense day-detail row; bump glyph to `Modifier.size(18.dp)`. |
| `WheelScreen.kt:3267–3276` (counter-clockwise banner dismiss) | `IconButton(modifier = Modifier.size(24.dp)) { Icon(... Modifier.size(24.dp)) }` | Drop the outer `size(24.dp)`; keep glyph `Modifier.size(24.dp)`. |

`WheelCustomizer.kt` `Icon`s at 20dp/24dp (lines 180, 223) are **non-clickable indicators** inside a `Card.clickable` — not `IconButton`s — so they fall outside this rule and stay as-is.

## 7. Change 3 — `StatsScreen` empty state

Replace the bare `Text("No practice sessions recorded yet.", ...)` block at `StatsScreen.kt:153–160` with a centered two-line block that mirrors `HistoryScreen`'s devotional empty state (but without the lotus emoji — the Stats screen never uses one):

```kotlin
if (mantraStats.isEmpty()) {
    item {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No practice sessions yet",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Your sessions and lifetime stats will appear here once you start spinning.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}
```

No CTA button is added: the path forward is one tap on the wheel tab, and the existing pattern in `HistoryScreen` also lacks a button. Adding one would break the calm tone.

The wheel-specific empty row at `StatsScreen.kt:184–191` keeps its current one-line form — both being empty reads correctly for a brand-new user.

## 8. Change 4 — `WheelScreen` first-spin guidance

Add a quiet hint directly below the wheel `Box` (around `WheelScreen.kt:628`), conditional on a fresh install. Track "has spun" with a screen-local `remember { mutableStateOf(false) }`; flip it inside a small `LaunchedEffect` observing `currentRpm` and `rotationCount`.

```kotlin
var hasSpunOnce by remember { mutableStateOf(false) }
LaunchedEffect(currentRpm, rotationCount) {
    if (currentRpm > 0f || rotationCount > 0L) hasSpunOnce = true
}
val showFirstSpinHint = !hasSpunOnce && rotationCount == 0L
```

Render under the wheel:

```kotlin
if (showFirstSpinHint) {
    Text(
        text = "Drag the wheel to begin your practice",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
```

**Behavior**

- Visible only when `rotationCount == 0L` AND the user hasn't yet spun.
- Disappears on the first drag — `currentRpm > 0f` or `rotationCount > 0L` flips the flag permanently for this screen lifetime.
- Re-entering the screen on a fresh install (after killing the process): counter resets to zero, the hint reappears. Acceptable — the condition is genuinely "never spun yet".
- No overlay, no animation, no persistence to disk.

## 9. Verification

1. **Build.** `./gradlew.bat :app:compileDebugKotlin` exits 0.
2. **Null-contentDescription grep.**
   ```
   grep -nE "contentDescription\s*=\s*null" \
     app/src/main/java/com/prayerwheel/app/ui/wheel/{WheelScreen,StatsScreen,HistoryScreen,CalendarScreen}.kt
   ```
   Expect no `IconButton` hits. (The decorative `contentDescription = null` in `SettingsScreen` / `LogPracticeDialog` / `ShareIntentionDialog` is outside this spec and not changed.)
3. **Touch-target grep.**
   ```
   grep -nE "IconButton\([^)]*Modifier\.size\((20|24)\.dp\)" \
     app/src/main/java/com/prayerwheel/app/ui/wheel/{WheelScreen,StatsScreen,HistoryScreen,CalendarScreen}.kt
   ```
   Expect zero matches after edits.
4. **Visual smoke.** Manual install on an emulator with a fresh DB:
   - `StatsScreen` shows the two-line empty block.
   - `WheelScreen` shows "Drag the wheel to begin your practice" beneath the wheel.
   - A single drag hides the hint permanently for this screen lifetime.
   - Delete buttons in `HistoryScreen` and `CalendarScreen` accept taps comfortably with one finger.

## 10. Out of scope

- Hardcoded color cleanup (`Color(0xFF…)` literals across components).
- Adding a `DESIGN.md` design-system file.
- New state in `WheelViewModel` (e.g. `hasInteractedOnce`).
- Reusable `EmptyState` composable.
- Localization of new strings (they are plain English to match the existing tone).
- Any change to `SettingsScreen`, audio, or physics.

## 11. Files touched

- `app/src/main/java/com/prayerwheel/app/ui/wheel/HistoryScreen.kt`
- `app/src/main/java/com/prayerwheel/app/ui/wheel/CalendarScreen.kt`
- `app/src/main/java/com/prayerwheel/app/ui/wheel/StatsScreen.kt`
- `app/src/main/java/com/prayerwheel/app/ui/wheel/WheelScreen.kt`

Zero new files. Zero deletions.
