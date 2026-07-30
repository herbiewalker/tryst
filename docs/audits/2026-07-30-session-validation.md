# 2026-07-30 session — consolidated on-device validation

**Everything you should walk through on the emulator before the next release cut.**
Supersedes the earlier `2026-07-30-validation-checklist.md` (kept for history — this
one is the merged rollup covering all of today's commits).

`main == origin == d2f880a`, fresh debug APK is on `emulator-5554` (PIN **123456**).
Your real v13 backup restored (Alex active partner, Sam self-profile,
~850 encounters, some photos).

Each check names the commit it validates. If anything reads as WORSE than pre-fix
behaviour, `git revert <sha>` on that commit rolls it back cleanly.

---

## Section 1 — Settings screen (UX pass + orphan cleanup)

**Validates:** `d87dc11` (Section reorder), `ab0c3c0` (verbiage), `4cf3fe1` (orphan strings removed).

Open **Settings**. Scroll top-to-bottom once and confirm the new section order:

- [ ] Top preamble paragraph (about-app blurb) — was under General, now sits above all sections.
- [ ] **You** — "Your profile" button.
- [ ] **Security** — Change PIN · Auto-lock chips (Immediately / 30s / 1m / 5m) · Enable/Disable biometric · Lock now.
- [ ] **Appearance** — Theme (System/Light/Dark) · Material You switch · Haptics switch · Calendar week start · "Open Trysts in calendar view" switch.
- [ ] **Customize tabs** — Customize Insights · **Customize Gallery** (label; was "Gallery layout" pre-verbiage-pass).
- [ ] **Categories** — the six Manage buttons (Acts / Kinks / Positions / Toys / Occasions / Finish locations).
- [ ] **Backup & restore** — Export encrypted backup · **Import backup** (was "Import / restore backup") · Import from CSV.
- [ ] **Danger zone** — Delete all data.
- [ ] **About** — What's new · **About & licenses** (was "About & open-source licenses").

Then tap each button and confirm the sub-screen opens cleanly (no NPE, no missing string).

## Section 2 — Gallery settings (blur merge)

**Validates:** `730e570`.

Settings → **Customize Gallery**. Scroll top-to-bottom:

- [ ] **Layout / Density / Sort** (unchanged).
- [ ] **Tile appearance** (unchanged).
- [ ] **When Photos opens** section now contains BOTH the "default to favourites only" switch AND the "Blur photos until tapped" switch. If blur is on, the grace-window chips (Immediate / 15s / 30s / 1m / 5m) appear right below.
- [ ] **Slideshow** section (unchanged).
- [ ] **Camera** section (unchanged, last).
- [ ] **No standalone "Blur gate" section at the bottom** — that was the pre-merge state.

## Section 3 — Encounter editor (semantic regroup)

**Validates:** `dc36c93`.

Trysts tab → tap any encounter to edit. Scroll top-to-bottom. Order should now be:

1. [ ] When (date + time)
2. [ ] Duration
3. [ ] Partners
4. [ ] Initiator ← moved from #16
5. [ ] Setting ← moved from #13
6. [ ] Occasion ← moved from #14
7. [ ] Positions
8. [ ] Acts — gave
9. [ ] Acts — received (hidden if solo)
10. [ ] Kinks
11. [ ] Toys ← moved from #15
12. [ ] Protection ← moved from #4
13. [ ] Your orgasms
14. [ ] Ejaculation (per orgasm)
15. [ ] Partner orgasms (per partner)
16. [ ] Mood ← moved from #5
17. [ ] Rating
18. [ ] Note
19. [ ] Photos
20. [ ] Delete (existing encounters only)

- [ ] Toggling Solo/multi-partner should still cleanly hide/show Initiator and "Acts received" in-place (no visual glitch).
- [ ] Save. Reload the tryst. Every field roundtrips unchanged.

## Section 4 — Photos gallery (Bundle A person-photo fixes)

**Validates:** `f966576`.

**Setup:** ensure both Sam (self) and Alex (partner) have at least one portrait each. If not, add one to each from their editor.

### N1 — Self-drill leaks partner portraits

- [ ] Photos → People → tap **You**. Expect: only Sam's portraits + solo tryst photos. **NOT** Alex's portraits.
- [ ] Photos → Filters sheet → toggle **"Include solo"** alone (no partner chip selected) → Show results. Expect: solo tryst photos + Sam's portraits only. **NOT** Alex's portraits.
- [ ] Drill into Alex from People. Expect: her tryst photos + her portraits. (Sanity — non-self drill still works.)

### N2 — Text query didn't filter portraits

- [ ] Photos → search bar → type `alex`. Expect: Alex's tryst photos + her portraits. **NOT** Sam's portraits.
- [ ] Search `sam`. Expect: solo tryst photos + Sam's portraits. **NOT** Alex's portraits.
- [ ] Search `ALEX` (all caps). Same match — fold is case+accent insensitive.
- [ ] Clear the search bar. Every photo returns.

### N3 — Set-as-partner-avatar created orphan blob

- [ ] Open any encounter photo of Alex in the viewer → tap **PersonPin** icon → **"Set avatar for Alex"**.
- [ ] Partners → Alex → edit. In PersonPhotoStrip:
  - [ ] The promoted photo appears as a portrait tile.
  - [ ] It shows a **checkmark** (current avatar).
  - [ ] Tap it → action sheet → "Delete" → tile disappears from the strip.
- [ ] Partners list — Alex's thumbnail updated to the new photo.

### N4 — Deleting current-avatar portrait left dangling ref

- [ ] Partners → Alex → edit → strip → pick a portrait → action sheet → **"Set as avatar"**. Checkmark moves.
- [ ] Same portrait → **Delete**. Tile gone.
- [ ] Back out → Partners list. Alex's avatar should be **cleared** (initial-letter fallback), not blank/broken.
- [ ] Same with You: Settings → Your profile → strip → set-as-avatar → delete → profile avatar clears.

## Section 5 — Backup restore (Bundle C atomicity + Bundle E wipe-first)

**Validates:** `24e7cfc` (atomic staging), `d2f880a` (wipe-first checkbox).

### E1 — Default path (replace)

- [ ] Export a fresh backup (Settings → Export encrypted backup). Save the file somewhere accessible.
- [ ] Settings → **Import backup** → pick the file. Dialog appears with:
  - [ ] **"Replace my current data with the backup (recommended)"** checkbox **checked** by default.
  - [ ] Explanation text visible below it.
  - [ ] Password field below.
- [ ] Enter password → Import. Expect: full restore, everything present. Partner list, encounter count, portraits, gallery photos.

### E2 — Merge path (checkbox off)

- [ ] Add a NEW partner "TestMerge" with no photos. Add one encounter with them.
- [ ] Settings → Import backup → pick the earlier backup → **uncheck** the box → password → Import.
- [ ] Expect: TestMerge + the earlier data BOTH present. Any partner id in the backup that matched a local one still hits the cascade (documented behaviour when off).

### E3 — Atomic rollback (force-stop mid-import)

- [ ] Wipe-first ON → Import → **immediately** `adb shell am force-stop app.tryst` from PowerShell within ~1 second of tapping Import.
- [ ] Relaunch, enter PIN. Expect:
  - [ ] Data intact (whatever state it was in BEFORE the import) — no partial wipe.
  - [ ] No decode-failed thumbnails.
  - [ ] No `.staging` files: `adb shell run-as app.tryst ls files/media/ | Select-String staging` → nothing.

### E4 — Orphan sweep

- [ ] Take a backup that has FEWER photos than currently exist locally (either restore an older one or export a fresh one after deleting some photos).
- [ ] Wipe-first ON → Import that backup.
- [ ] Count blobs: `adb shell run-as app.tryst ls files/media/` (or `... | Measure-Object -Line`). Count should match the backup's blob count, not the pre-import count. Old encrypted files gone.

### C1 — Regression check (happy path still works)

Already covered by E1 above.

## Section 6 — Lens 6 testing gaps (T1-T5)

These aren't specific bug repros — they're new v15 UI paths that adb tap can't reach under `FLAG_SECURE`, so **real finger validation** is needed once per flow.

### T1 — PhotoViewer "Add to person's photos"

- [ ] Open any tryst photo → viewer top-right → **AddPhotoAlternate** icon.
- [ ] Menu lists **You + every active partner**.
- [ ] Pick **You**. Menu closes without crash. Settings → Your profile → strip → the photo appears as a portrait.
- [ ] Repeat picking a partner. Photo lands in that partner's strip.
- [ ] Edge case: if profile has no name AND every partner is archived, the icon should be **disabled/dim**.

### T2 — PhotoViewer "Set avatar for {partner}"

Combine with the N3 checks above — same code path, stricter verification via the strip's checkmark.

### T3 — PersonPhotoStrip "+" menu (camera / multi-pick / single-pick)

- [ ] Partner editor → strip → **+** → 3 menu items appear.
- [ ] **Take photo** → camera opens → shoot → photo lands in the strip. Then check system Gallery/Photos app — **it should NOT be there** (encrypted, private store only).
- [ ] **Choose from gallery (multi)** → pick 3 → all 3 appear.
- [ ] **Choose from gallery (single)** → pick 1 → appears.
- [ ] Cancel any picker → strip unchanged, no stuck loader.

### T4 — EncounterEditScreen "+" AddPhotoTile

- [ ] New encounter → **+** → camera / gallery menu items.
- [ ] Camera capture → photo in tryst.
- [ ] If Settings → Customize Gallery → Camera → "keep capturing" is ON, camera should relaunch after each shot. Take 3.

### T5 — Filter sheets

- [ ] Photos tab → Filters button → sheet → toggle date scope + rating chips → "Show N results" → sheet closes, gallery narrows.
- [ ] Trysts tab → search → Filters button → same round trip.

## Section 7 — Bundle B verification (already automated)

**Validates:** `e4e5728`.

Nothing to do on-device — the R8-stripped release build was verified automatically: literal string `TRYSTIMPORT` is **absent** from `classes.dex` in the release APK. Debug builds intentionally still log it for dev diagnostics (see `adb logcat -s TRYSTIMPORT` if curious).

---

## Sign-off

When every checkbox above passes, we cut **v0.5.2** as the D-35 four-spot sync
bundling all of Bundle A + B + C + D + E over v0.5.1. That release will be the
first F-Droid-picked version — see the release-notes queue in `[Unreleased]`
in `CHANGELOG.md`.

If a specific bundle fails, `git revert <sha>` rolls it back cleanly and the rest ship:
- `d2f880a` — Bundle E (wipe-first)
- `4cf3fe1` — Bundle D (orphan strings)
- `24e7cfc` — Bundle C (atomic restore)
- `e4e5728` — Bundle B (log strip)
- `f966576` — Bundle A (person-photo cluster)
- `ab0c3c0` — verbiage polish
- `dc36c93` — encounter regroup
- `730e570` — gallery blur merge
- `d87dc11` — Settings resection
