# Pass 5 (Adaptive Layouts) — Handoff for local verification

> Scratch/handoff note. **Delete this file once Pass 5 is verified and merged.** It exists because
> Pass 5 was implemented in a Claude-on-the-web session that has **no Android SDK** — so nothing here
> was compiled or run. This afternoon's local session (Android Studio + emulator) is where it gets
> verified.

## TL;DR

- Branch: **`claude/tryst-pass-5-fph081`** · Draft PR: **herbiewalker/tryst#1**.
- A **full two-pane adaptive refactor** is implemented (the "Full two-pane refactor" option you chose).
- **Not built, not run, not seen on a device.** CI (`assembleDebug testDebugUnitTest lint
  checkNoNetworkDebug`) is the only compile check, and it took 3 tries just to get past pre-build
  issues (see "CI history" below). Treat the code as *compiles-on-paper* until you build it.
- You have two clean options this afternoon (see "Pick your path").

## Pick your path

**A. Verify & finish this branch (recommended if CI is green).**
Pull the branch, build, run on phone + tablet/fold emulators, work the checklist below, fix whatever
the emulator reveals, then mark the PR ready. Fastest route to a done Pass 5.

**B. Redo the pass fresh.**
If you'd rather re-audit with clean eyes: start a fresh local session, paste the **Pass 5** prompt
from [PRERELEASE_PROMPT_PACK.md](PRERELEASE_PROMPT_PACK.md) (§"Pass 5 — Adaptive Layouts"). Use this
branch as a reference/diff, or `git reset` it away. The design notes below still apply.

## What was changed (files)

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | + `androidx-material3-windowsizeclass` (BOM-managed; alias avoids the reserved word `class`) |
| `app/build.gradle.kts` | + `implementation(libs.androidx.material3.windowsizeclass)` |
| `MainActivity.kt` | computes `calculateWindowSizeClass(this).widthSizeClass` → `TrystApp(widthSizeClass)` |
| `ui/TrystApp.kt` | **rewritten** — adaptive shell (bar↔rail), two-pane `HistoryPane` / `InsightsPane`, `CenteredPane`, `DetailPlaceholder`, `navigateTopLevel` |
| `ui/history/HistoryScreen.kt` | `sharedScope`/`animatedScope` now **nullable** (morph guarded); `calendarMode` → `rememberSaveable` |
| `ui/encounter/EncounterEditScreen.kt` | `sharedScope`/`animatedScope` now **nullable** (morph guarded) |
| `ui/insights/InsightsScreen.kt` | + `twoPane` flag (hides redundant Achievements action + teaser); `editMode` → `rememberSaveable` |
| `ui/achievements/AchievementsScreen.kt` | + `showBack` flag (false = permanent detail pane, no back arrow) |
| `gradlew` | restored the executable bit (was committed `100644`) — unrelated repo fix CI forced |

Design rationale lives in the ROADMAP Pass 5 entry (`docs/ROADMAP.md`, "Pre-release audit passes").

## Behaviour by width

- **Compact** (phone portrait): unchanged — bottom `NavigationBar`, single-pane, card/FAB→editor
  container-transform morph intact.
- **Medium** (phone landscape / small tablet): side `NavigationRail`, single-pane, content centred in
  an 840dp `CenteredPane` lane (no stretch).
- **Expanded** (tablet / unfolded): side `NavigationRail` **+ two-pane**:
  - Trysts list ↔ encounter editor (`HistoryPane`).
  - Insights dashboard ↔ full Achievements list (`InsightsPane`).

## Emulator verification checklist

Build/run per [SETUP_WINDOWS.md](SETUP_WINDOWS.md) / CLAUDE.md. Remember **screenshots are black by
design** (`FLAG_SECURE`) — you must eyeball the device, screen capture won't help.

Suggested AVDs to exercise all three size classes + fold:
- A phone (e.g. Pixel 8) — compact (portrait) and medium (landscape).
- **Resizable (Experimental)** AVD — flip Phone / Unfolded / Tablet live; the cleanest way to watch
  the bar→rail and single→two-pane transitions and **state preservation across the change**.
- Optional: Pixel Fold / Pixel Tablet for a realistic expanded layout.

Check:
- [ ] **Phone unchanged:** bottom bar; tap a card and the **+** FAB → still morphs into the editor.
- [ ] **Landscape / medium:** bottom bar becomes a left **rail**; screens are centred, not stretched.
- [ ] **Tablet / expanded:** rail + **two panes**. Trysts list on the left, editor on the right.
- [ ] Two-pane: tap different entries → right pane swaps to each; **+** opens a **blank** new form
      (this is the keyed-VM path — if a stale form shows, the `hiltViewModel(key=…)` keying regressed).
- [ ] Two-pane: Save / Delete in the detail pane → returns to the `DetailPlaceholder`; list refreshes.
- [ ] Insights expanded: dashboard + Achievements side by side; **no** duplicate Achievements
      affordance (top-bar trophy + in-list teaser are hidden), Achievements pane has **no** back arrow.
- [ ] Partners / Settings on tablet: centred lane, not edge-to-edge stretched.
- [ ] **Rotate / fold–unfold mid-task:** open the editor (or set calendar/edit toggles), rotate or
      fold. Expected: no crash; toggles + selection survive (`rememberSaveable`). *Known gap:* folding
      expanded→compact with the detail pane open drops to the list (compact can't show two panes).
- [ ] **Insets:** with the rail shown, status bar / nav bar / display cutout aren't overlapped; the
      rail sits clear of the system bars on a gesture-nav device.
- [ ] Anti-leak still green (`gradlew checkNoNetworkDebug`); no new permission.

## Risk hot-spots to watch first

1. **`calculateWindowSizeClass(activity)` import/availability** (`material3-windowsizeclass`,
   `androidx.compose.material3.windowsizeclass.*`). Most likely single point of compile failure if the
   API moved in the pinned Compose BOM (2026.04.01). If it's gone, the modern replacement is
   `currentWindowAdaptiveInfo().windowSizeClass` from `material3-adaptive`, or compute from
   `LocalConfiguration.screenWidthDp` (compact <600 / medium <840 / expanded ≥840).
2. **`this` capture for `animatedScope`** inside `composable { CenteredPane { EncounterEditScreen(
   animatedScope = this) } }` — should resolve to the `composable` lambda's `AnimatedContentScope`.
   Verify the morph still runs on phone; if it broke, hoist the scope into a `val` above `CenteredPane`.
3. **Keyed editor VM accumulation** — each two-pane selection creates a `hiltViewModel(key=sel)` under
   the History back-stack entry. Bounded per session; fine for a single user, but if you ever see
   stale form state, that's the place to look.
4. **`NavigationRail` + per-screen `Scaffold` insets** — double-padding or a gap at the top is the
   thing to watch on a cutout/gesture-nav device.

## CI history (why the first runs failed — not the refactor)

1. `3be5ea4` — ❌ `./gradlew: Permission denied` → wrapper was committed non-executable. Fixed `3035f9b`.
2. `3035f9b` — ❌ catalog alias `androidx-material3-window-size-class` rejected (`class` is reserved).
   Fixed `1953adc` (alias → `androidx-material3-windowsizeclass`).
3. `1953adc` — ⏳ first run to reach actual Kotlin compile. **Check its result before trusting the code.**
   (If it failed, the error + fix should be recorded here or in a follow-up commit.)
