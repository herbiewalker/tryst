# Tryst — Future Roadmap (post-v0.1.0)

> **Status: DRAFT for prioritization (2026-06-14).** Organized from a raw idea dump. Version groupings
> and ordering below are a *proposal* — reshuffle freely. Nothing here is committed scope yet.

Every item preserves the **hard constraints** (no network ever, encrypted at rest, `FLAG_SECURE`,
local-only, additive/nullable migrations only). Anything that would violate them is out of scope by
definition.

---

## The one architectural decision that shapes everything: a shared filter/query layer

Most of the big asks — **Search**, the **page-wide Insights scope (INS-2)**, the **Insights Explorer**,
the **Photo Gallery**, and **Granular erase** — are all the same primitive underneath: *"select a
subset of encounters by date range(s), partner, act/position/place, time-of-day, rating, etc."* The
**date range** in particular is the single most-reused filter (INS-2 makes it the default lens on the
whole Insights tab).

Rather than hand-roll filtering four times, build **one reusable filter/query layer** first (a
`EncounterFilter` data model + a pure-Kotlin query function over the log, mirroring how
`InsightsEngine` is a stateless derive). Then Search, the Explorer, the Gallery, and selective-delete
are each a thin UI on top of it. This is the "foundations before features" sequencing — it's why
v0.3.0 below is deliberately the filter layer, before the features that consume it.

**Industry-standard filter options** to support in that layer:
- **Date:** presets (this week / month / year / last 30/90/365 days / all time) **and** custom range,
  **and** multiple ranges / compare-two-periods.
- **Partner(s):** multi-select (incl. "solo").
- **Acts / positions / places / occasions / kinks / toys / protection:** multi-select.
- **Time-of-day** bucket, **day-of-week**, **rating** range, **duration** range, **has-photo**,
  **has-note / note contains**.
- Combinable (AND across categories), with a visible "active filters" chip row and a clear-all.

---

## Proposed releases

> **Tracking IDs** are stable handles for each item (they survive reordering); the **version** is just
> what ships to users. A whole theme ships as one minor release. Reserve `v0.2.1`/`.2` etc. for actual
> post-release **fixes**, not planned features.

### v0.2.0 — QOL & polish *(small, low-risk, high-felt-value; no schema change)*
| ID | Item | Current state | Proposed |
|----|------|---------------|----------|
| **QOL-1** | **Calendar: swipe to change month** | Month changes via ◀ ▶ arrow buttons only (`MonthHeader`). | Add horizontal swipe (wrap the month header+grid in a `HorizontalPager`, or a swipe gesture that maps to `month.minus/plusMonths`). Keep the arrows for a11y. |
| **QOL-2** | **Calendar: today indicator** | ✅ **DONE (2026-06-14).** | Verified it rendered (bold + primary text + "today" a11y label) but found it too subtle vs logged days (which also use primary), so added an **outline ring** around today's cell when it isn't the selected day (`DayCell` `border`). |
| **QOL-3** | **Partner editor → its own page** | Partner add/edit is an `AlertDialog` popout; the **self-profile is already a full-screen page** (`ProfileScreen`). | Convert the partner editor to a full-screen route to match. Reuse `DemographicFields`/`OptionalChips`; carry over the existing discard-changes guard (D-33). Net effect: "you" and "a partner" edit identically. |
| **QOL-4** | **Updated icon set** | Launcher icon + per-act vector icons (`ic_act_*.xml`). | New launcher icon (adaptive + **themed/monochrome** for Material You), refreshed act icons. Largely a design/drawable swap — act icons are already a drawable-swap-only seam (`PracticeVisuals`). Design brief: [design/ICON_PROJECT_PROMPT.md](../design/ICON_PROJECT_PROMPT.md). Can land in any release. |
| **QOL-5** | **App settings in the backup** (survive reinstall / new phone) | The encrypted backup restores DB + media but **deliberately excludes** the three settings stores — `tryst_appearance` (theme), `tryst_insights` (Insights customization), `tryst_general` (haptics/auto-lock/week-start) — the prefs classes' own comments say "excluded from backup/transfer". Note: a same-device **"Delete all data" does *not* clear these prefs** (`SessionManager.deleteAllData` wipes DB/keys/media only), so settings survive an in-app reset; they're lost on **reinstall / new phone / OS clear** (`allowBackup=false` → no cloud backup). | Add a non-sensitive `settings.json` (the three prefs as key→value) to the encrypted backup container so **theme + Insights layout + general prefs restore on a new device / reinstall**. The Insights customization (reorder/hide cards, per-card chart styles) is the highest-value thing to preserve — it's real user effort. Restore reapplies **known keys only** (skip-unknown, forward-compatible). Bump the export-format version (additive/optional section; older apps ignore it). **Never** include the PIN / vault / biometric config. Optional: an "include settings" toggle on export, and a separate *"Reset preferences to default"* action on the reset page (since data-reset currently leaves them). |

### v0.3.0 — Search & the filter foundation *(the enabling layer)*
| ID | Item | Proposed |
|----|------|----------|
| **FILT-1** | **Shared `EncounterFilter` query layer** | The reusable filter model + stateless query described above. Pure-Kotlin, JVM-testable, no schema change (it queries the existing log). |
| **SRCH-1** | **Global search** | A search entry (on Trysts, and/or app-wide) that filters encounters by free text (notes, partner names, act/position/place labels) on top of the filter layer. First consumer of the layer. |

### v0.4.0 — Insights Explorer & scope *(the big one)*
| ID | Item | Current state | Proposed |
|----|------|---------------|----------|
| **INS-2** | **Page-wide time scope** *(do this first — simpler, high-value)* | All Insights stats are computed over the **entire** log; no time selector. | A scope selector at the top of the Insights tab that **re-computes every stat/chart/tile** for the chosen window. **Default = current calendar year**, with options: pick a **specific year**, **all time**, or a **custom date range** (+ the standard presets). Implementation is light: feed a date-bounded subset of encounters into the existing `InsightsEngine.compute()`; everything downstream already reflects its input. |
| **INS-1** | **Clickable category cards → drill-down page** | Insights cards are display-only; the engine already computes a rich **orgasm drill-down** (you-vs-partner, per-partner, over-time, finish). | Tapping a category card (e.g. Orgasms, Acts, Partners) opens a **dedicated detail page**: all stats for that category, with the **full filter set** (multi/range dates, people, time-of-day, …), adjustable chart style (reuse the per-card Bars/Line/Donut), and searchable. Generalize the existing orgasm drill-down into a per-category pattern, fed by the v0.3.0 layer. The page inherits the **INS-2 scope** as its starting filter, then refines. |

**Product rules / design notes for the Insights scope (INS-2):**
- **Achievements are exempt — always all-time / cumulative.** They represent lifetime progress, so the
  scope selector must **not** apply to the Achievements section (keep feeding the full log to
  `AchievementEngine`). Make this visually clear (the scope chip sits on the stats sections, not the
  Achievements teaser/page).
- **Streaks need a decision:** "longest streak" naturally respects the window, but "current streak" is
  inherently all-time. Proposal: show *longest-in-range* under a scope, and only show *current* streak
  in the all-time view (or label it explicitly).
- **Persistence:** decide whether the chosen scope is remembered (persist last choice in
  `InsightsPreferences`) or resets to "current year" each visit. Default-to-current-year on launch is
  the safest, most predictable behaviour.
- **Empty windows — DECIDED: empty-state, do NOT auto-hide cards.** A scoped window with no data
  keeps its card and shows a compact empty state (e.g. *"No encounters in 2021"*), rather than the card
  vanishing. Rationale: (1) **layout stability** — cards popping in/out as the scope changes feels
  broken and loses the user's place; (2) **a zero is signal** in a tracker (a real example: the
  dataset's 2021 gap — auto-hide would blank the page into confusion); (3) it respects the **existing
  manual show/hide** (`InsightsPreferences`) instead of overriding it on data state; (4) **empty is
  teachable** — *"No toys logged in 2024"* shows what's trackable and invites action.
  - *List items within a breakdown* already omit zeros (a per-act breakdown lists only acts used) —
    keep that; the rule is about whole **cards/sections**, which persist.
  - Optional **opt-in** toggle *"Hide empty cards for this range"* (default **off**) for users who
    prefer the tidy view — keeps the predictable default while offering the choice.
- INS-2 shares the date primitive from **FILT-1** (v0.3.0) — same reason the filter layer is built
  first. It could even land in v0.3.0 if you want the scope before the full Explorer.

### v0.5.0 — Photo gallery *(reuses decrypt-in-memory + the filter layer)*
| ID | Item | Proposed |
|----|------|----------|
| **GAL-1** | **Gallery by person / date** | A browsing view aggregating all encrypted photos (encounter photos, partner avatars, profile), grouped/filtered **by partner or by date** via the filter layer. Reuses `MediaImages` (decrypt in-memory only); `FLAG_SECURE` already protects it. Watch list-scroll performance with many photos (thumbnail sampling/caching). No schema change — photos already link to encounters/partners. |

### v0.6.0 — Granular data management *(privacy feature; reuses the filter layer)*
| ID | Item | Current state | Proposed |
|----|------|---------------|----------|
| **DEL-1** | **Selective erase** | Settings → only **"Delete all data"** (`ResetDataScreen`, type-DELETE gated). Single-entry delete exists in the editor. | Add scoped deletes: **by date range**, **by partner**, and **bulk-select** entries — choosing the set via the filter layer, each behind a confirm gate. Tie into the deferred **VACUUM-on-delete / secure-delete** hardening so removed rows don't linger in the encrypted DB pages. |

---

## Relationship to the existing deferred backlog (ROADMAP.md / DECISIONS.md)
- **Search** and **history filters** were already noted as deferred → now formalized as v0.3.0.
- **Selective erase** pairs with the deferred **VACUUM-on-delete secure-delete** hardening.
- **Today indicator** appears to already be implemented — verify before scheduling.
- New user-facing strings from all of the above fold into the deferred **Localization** milestone
  (English identity keys already in `strings.xml`).

## Cross-cutting considerations
- **Schema impact:** most items are **read-side** and need **no migration** (search, explorer, gallery
  are derived; selective-erase is deletes). Icons = none. **QOL-5** touches the **backup/export format**
  (not the DB schema) — version that format additively (`EXPORT_FORMAT.md`), and restore must skip
  unknown keys for forward-compat. If any item later needs a column, follow the standard rule (version
  bump + additive migration + migration test).
- **Privacy invariants:** all items stay local, no-network, encrypted, `FLAG_SECURE` — no new
  permissions, no new external surface.
- **Testability:** keep the filter/query logic pure-Kotlin (like `InsightsEngine`) so it's JVM-tested
  without Robolectric.

## Open questions for you to decide
1. **Ordering** — is the QOL batch (v0.2.0) first, or do you want the Insights Explorer sooner? (It's
   the most work but the highest-value; it just needs the filter layer first.)
2. **Icon set** — do you have the new art, or is producing it part of the work?
3. **Selective erase scope** — by date + partner is clear; do you also want "delete everything *with*
   X act/place" (full filter-driven delete), or keep it simpler?
4. **Version cadence** — happy with one feature theme per minor version, or bundle more per release?
