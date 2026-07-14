# Accessibility & Empty States Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish accessibility and empty states in the four core Compose screens without changing visual identity, physics, audio, or settings.

**Architecture:** Delta-only edits inside four existing Kotlin files. Zero new files, zero `WheelViewModel` changes. Uses existing `rotationCount` and `currentRpm` state to drive the WheelScreen first-spin hint, and replaces bare empty-state text in `StatsScreen` with a devotional two-line block that mirrors `HistoryScreen`'s pattern.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3.

## Global Constraints

- No new files.
- No changes to `WheelViewModel`, `SettingsScreen`, physics, audio, or hardcoded colors.
- Devotional tone: plain, calm, no exclamation points, no gamification language.
- Material 3 `IconButton` default tap area is 48dp; never shrink with `Modifier.size(<48.dp)` on an `IconButton`.
- Build command (Windows): `./gradlew.bat :app:compileDebugKotlin` must exit 0.
- Working directory for all commands: `C:\Users\Y\proj\prayer-wheel-counter`.

---

### Task 1: Fix HistoryScreen delete button touch target

**Files:**
- Modify: `app/src/main/java/com/prayerwheel/app/ui/wheel/HistoryScreen.kt:716-727`

**Context:**
`SessionCard` deletes a session with this `IconButton`:

```kotlin
IconButton(
    onClick = onDeleteClick,
    modifier = Modifier.size(24.dp)
) {
    Icon(
        imageVector = Icons.Default.Delete,
        contentDescription = "Delete session",
        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
        modifier = Modifier.size(20.dp)
    )
}
```

The `Modifier.size(24.dp)` shrinks the tap area to 24dp — well below the 48dp Material minimum. The icon already has a proper contentDescription ("Delete session"), so only the touch target needs fixing.

**Interfaces:**
- Consumes: existing `onDeleteClick: () -> Unit` from `SessionCard`.
- Produces: the same callback, with a restored 48dp tap area and a slightly larger glyph (24dp) for legibility.

- [ ] **Step 1: Edit the delete IconButton in SessionCard**

Replace the entire `IconButton { ... }` block at `HistoryScreen.kt:716-727` with:

```kotlin
IconButton(
    onClick = onDeleteClick
) {
    Icon(
        imageVector = Icons.Default.Delete,
        contentDescription = "Delete session",
        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
        modifier = Modifier.size(24.dp)
    )
}
```

Two precise changes:
1. Drop `modifier = Modifier.size(24.dp)` from the `IconButton` (default tap area is 48dp).
2. Bump the inner `Icon`'s `modifier = Modifier.size(20.dp)` to `Modifier.size(24.dp)` so the glyph stays visible inside the larger tap area.

The `contentDescription = "Delete session"` already meets the accessibility rule and stays unchanged.

- [ ] **Step 2: Verify the file builds**

Run:
```bash
./gradlew.bat :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL` with no errors in `HistoryScreen.kt`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/prayerwheel/app/ui/wheel/HistoryScreen.kt
git -c user.name=opencode -c user.email=opencode@local commit -m "fix(history): restore 48dp tap target on session delete"
```

---

### Task 2: Fix CalendarScreen per-session delete button touch target

**Files:**
- Modify: `app/src/main/java/com/prayerwheel/app/ui/wheel/CalendarScreen.kt:843-853`

**Context:**
Inside the day-detail card, per-session delete uses `Modifier.size(20.dp)` on the `IconButton` itself. This lives inside a tight row with `Modifier.size(44.dp)` cells, so the safe fix is to wrap with `minimumInteractiveComponentSize()` — keeps the dense layout but gives the tap area the standard 48dp.

```kotlin
IconButton(
    onClick = { onDeleteClick(session) },
    modifier = Modifier.size(20.dp)
) {
    Icon(
        imageVector = Icons.Default.Delete,
        contentDescription = "Delete",
        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
        modifier = Modifier.size(14.dp)
    )
}
```

**Interfaces:**
- Consumes: existing `onDeleteClick: (Session) -> Unit` from `DayDetailCard`.
- Produces: the same callback, with a 48dp tap area and a larger glyph.

- [ ] **Step 1: Verify minimumInteractiveComponentSize is already imported**

Run:
```bash
grep -n "minimumInteractiveComponentSize" app/src/main/java/com/prayerwheel/app/ui/wheel/CalendarScreen.kt
```
Expected: no match (the symbol is not imported).

If the symbol is not already imported, add this line to the imports block near the top of `CalendarScreen.kt` (after the existing `import androidx.compose.material3.IconButton` line at line 39):

```kotlin
import androidx.compose.material3.minimumInteractiveComponentSize
```

- [ ] **Step 2: Edit the delete IconButton in DayDetailCard**

Replace the `IconButton { ... }` block at `CalendarScreen.kt:843-853` with:

```kotlin
IconButton(
    onClick = { onDeleteClick(session) },
    modifier = Modifier.minimumInteractiveComponentSize()
) {
    Icon(
        imageVector = Icons.Default.Delete,
        contentDescription = "Delete",
        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
        modifier = Modifier.size(18.dp)
    )
}
```

Three precise changes:
1. Replace `modifier = Modifier.size(20.dp)` on the `IconButton` with `modifier = Modifier.minimumInteractiveComponentSize()`.
2. Bump the inner `Icon`'s `Modifier.size(14.dp)` to `Modifier.size(18.dp)` so the glyph stays visible.
3. Leave `contentDescription = "Delete"` unchanged.

- [ ] **Step 3: Verify the file builds**

Run:
```bash
./gradlew.bat :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL` with no errors in `CalendarScreen.kt`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/prayerwheel/app/ui/wheel/CalendarScreen.kt
git -c user.name=opencode -c user.email=opencode@local commit -m "fix(calendar): restore 48dp tap target on day-session delete"
```

---

### Task 3: Fix WheelScreen counter-clockwise banner dismiss touch target

**Files:**
- Modify: `app/src/main/java/com/prayerwheel/app/ui/wheel/WheelScreen.kt:3267-3276`

**Context:**
The counter-clockwise reminder banner has a dismiss `IconButton` shrunk to 24dp:

```kotlin
IconButton(
    onClick = onDismiss,
    modifier = Modifier.size(24.dp)
) {
    Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "Dismiss",
        tint = MaterialTheme.colorScheme.onTertiaryContainer
    )
}
```

`contentDescription` is already correct. Drop the `size(24.dp)` modifier on the `IconButton`.

**Interfaces:**
- Consumes: existing `onDismiss: () -> Unit` from `CounterClockwiseReminderBanner`.
- Produces: same callback, default 48dp tap area.

- [ ] **Step 1: Edit the dismiss IconButton**

Replace the `IconButton { ... }` block at `WheelScreen.kt:3267-3276` with:

```kotlin
IconButton(
    onClick = onDismiss
) {
    Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "Dismiss",
        tint = MaterialTheme.colorScheme.onTertiaryContainer
    )
}
```

One precise change: drop `modifier = Modifier.size(24.dp)` from the `IconButton`. The glyph has no explicit `Modifier.size` so it uses the `IconButton`'s default.

- [ ] **Step 2: Verify the file builds**

Run:
```bash
./gradlew.bat :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL` with no errors in `WheelScreen.kt`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/prayerwheel/app/ui/wheel/WheelScreen.kt
git -c user.name=opencode -c user.email=opencode@local commit -m "fix(wheel): restore 48dp tap target on counter-clockwise dismiss"
```

---

### Task 4: Replace StatsScreen bare empty state with devotional block

**Files:**
- Modify: `app/src/main/java/com/com/prayerwheel/app/ui/wheel/StatsScreen.kt:153-160`

> **Note on path:** the existing file is at `app/src/main/java/com/prayerwheel/app/ui/wheel/StatsScreen.kt` (single `com/prayerwheel`, not `com/com`). Use the correct path below.

**Files (corrected):**
- Modify: `app/src/main/java/com/prayerwheel/app/ui/wheel/StatsScreen.kt:153-168`

**Context:**
Today the file renders a single bare text line when `mantraStats.isEmpty()`:

```kotlin
if (mantraStats.isEmpty()) {
    item {
        Text(
            text = "No practice sessions recorded yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
} else {
    items(mantraStats) { stats ->
        MantraStatsCard(
            stats = stats,
            lifetimeStats = lifetimeStats
        )
    }
}
```

`TextAlign` and `Arrangement` are already imported at lines 35 and 25 — no new imports needed.

**Interfaces:**
- Consumes: existing `mantraStats: List<MantraStats>` and `lifetimeStats: LifetimeStats?` parameters in scope.
- Produces: an empty-state block matching `HistoryScreen`'s calm two-line pattern (no lotus emoji — Stats never uses one).

- [ ] **Step 1: Edit the mantraStats.isEmpty() block**

Replace the `if (mantraStats.isEmpty()) { ... } else { ... }` block at `StatsScreen.kt:153-168` with:

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
} else {
    items(mantraStats) { stats ->
        MantraStatsCard(
            stats = stats,
            lifetimeStats = lifetimeStats
        )
    }
}
```

Three precise changes inside the empty branch:
1. Wrap in a `Column` with `fillMaxWidth().padding(vertical = 32.dp)`.
2. First `Text` uses `titleMedium` with the calm heading "No practice sessions yet".
3. Second `Text` uses `bodySmall` with the devotional guidance copy, centered with `TextAlign.Center`.

The wheel-specific empty row at lines 184–191 stays as-is — both empty reads correctly for a brand-new install.

- [ ] **Step 2: Verify the file builds**

Run:
```bash
./gradlew.bat :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL` with no errors in `StatsScreen.kt`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/prayerwheel/app/ui/wheel/StatsScreen.kt
git -c user.name=opencode -c user.email=opencode@local commit -m "feat(stats): devotional empty state for no practice sessions"
```

---

### Task 5: Add WheelScreen first-spin guidance

**Files:**
- Modify: `app/src/main/java/com/prayerwheel/app/ui/wheel/WheelScreen.kt`

**Context:**
The wheel `Box` ends at line 628 with `}`. Immediately below it (line 630) is `Spacer(modifier = Modifier.height(16.dp))` followed by `ViewModeBottomBar(...)`. Insert the hint + the `hasSpunOnce` state between the wheel closing brace and the `Spacer`.

State to introduce (screen-local):

```kotlin
var hasSpunOnce by remember { mutableStateOf(false) }
LaunchedEffect(currentRpm, rotationCount) {
    if (currentRpm > 0f || rotationCount > 0L) hasSpunOnce = true
}
val showFirstSpinHint = !hasSpunOnce && rotationCount == 0L
```

Hint to render directly under the wheel `Box`:

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

`rotationCount` is already collected as `val rotationCount by viewModel.rotationCount.collectAsState()` (search: it appears in this file at the `collectAsState` block near the top). `currentRpm` is likewise already collected. `remember`/`mutableStateOf`/`LaunchedEffect`/`Text`/`Modifier`/`dp` are all already imported.

**Interfaces:**
- Consumes: existing `rotationCount` (Long) and `currentRpm` (Float) state holders in `WheelScreen`.
- Produces: a quiet one-line `Text` under the wheel, visible only on first launch with `rotationCount == 0L` and before any drag.

- [ ] **Step 1: Add the local `hasSpunOnce` state**

Find a good spot near the top of `WheelScreen`, after the existing `var starSpawnTimer by remember { mutableFloatStateOf(0f) }` line (~line 275). Add immediately below that line:

```kotlin
// T17: first-spin guidance hint — visible only on fresh installs, hides on first drag
var hasSpunOnce by remember { mutableStateOf(false) }
LaunchedEffect(currentRpm, rotationCount) {
    if (currentRpm > 0f || rotationCount > 0L) hasSpunOnce = true
}
val showFirstSpinHint = !hasSpunOnce && rotationCount == 0L
```

- [ ] **Step 2: Render the hint under the wheel**

Find the closing `}` of the wheel `Box` block (around line 628). The line just before it ends with:

```kotlin
                    }
                }
```

The `Spacer(modifier = Modifier.height(16.dp))` follows on the next line (line 630). Insert the hint between the wheel `}` and the `Spacer`:

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

Exact location: directly above the existing `Spacer(modifier = Modifier.height(16.dp))` at line 630.

- [ ] **Step 3: Verify the file builds**

Run:
```bash
./gradlew.bat :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL` with no errors in `WheelScreen.kt`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/prayerwheel/app/ui/wheel/WheelScreen.kt
git -c user.name=opencode -c user.email=opencode@local commit -m "feat(wheel): subtle first-spin guidance for new users"
```

---

### Task 6: Final verification sweep

**Files:** none (read-only)

- [ ] **Step 1: Re-run the full compile**

Run:
```bash
./gradlew.bat :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL` with no errors.

- [ ] **Step 2: Verify no undersized IconButton remains in the four target files**

Run:
```bash
grep -nE "IconButton\([^)]*Modifier\.size\((20|24)\.dp\)" \
  app/src/main/java/com/prayerwheel/app/ui/wheel/{WheelScreen,StatsScreen,HistoryScreen,CalendarScreen}.kt
```
Expected: zero matches. (Any decorative `contentDescription = null` that is *inside* an `IconButton` would also be flagged by this grep.)

- [ ] **Step 3: Verify no `contentDescription = null` on clickable IconButtons in the four target files**

Run:
```bash
grep -nE "contentDescription\s*=\s*null" \
  app/src/main/java/com/prayerwheel/app/ui/wheel/{WheelScreen,StatsScreen,HistoryScreen,CalendarScreen}.kt
```
Expected: zero matches. (Decorative nulls live in `SettingsScreen`, `LogPracticeDialog`, `ShareIntentionDialog` — outside this spec.)

- [ ] **Step 4: Confirm the new hint state and import surface are clean**

Run:
```bash
git diff --stat HEAD~5 HEAD
```
Expected: only the four target files touched, total ~30-40 lines changed.

- [ ] **Step 5: Final commit if any verification artifacts**

If Step 4 surfaced an unexpected touch (e.g. an unrelated fix during compilation), commit it with a focused message. Otherwise no commit.

```bash
git status
```
Expected: clean working tree.
