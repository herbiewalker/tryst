# v0.5.0 audit fixes — on-device validation checklist

**Commits in scope:** `f966576` Bundle A · `e4e5728` Bundle B · `24e7cfc` Bundle C.
Base: `24e7cfc`. Fresh debug APK is installed on `emulator-5554`; PIN **123456**.
Your real v13 backup is already restored (Alex active partner, Sam self-profile, ~850 encounters).

If anything reads as WORSE than pre-fix behaviour, `git revert <sha>` on that commit rolls it back cleanly — each bundle is one commit.

---

## Bundle A — Person-photo cluster

### N1 — Self-drill leaks partner portraits

**Setup:** make sure both Sam (self) and Alex (partner) have at least one portrait each. If not, add a portrait to each from their editor.

- [ ] **Photos tab → People layout → tap the You avatar.** Expect: only Sam's portraits + solo tryst photos. **Expect NOT to see Alex's portraits.**
- [ ] **Photos tab → open Filters sheet → toggle "Include solo" (leave partners empty) → Show results.** Expect: solo tryst photos + Sam's self portraits only. **Expect NOT to see Alex's portraits.**
- [ ] **Drill into Alex from People.** Expect: Alex's tryst photos + Alex's portraits — same as before. (Sanity check that non-self drill still works.)

### N2 — Text query doesn't filter portraits

- [ ] **Photos tab → search bar → type `alex`.** Expect: Alex's tryst photos + Alex's portraits only. **Expect NOT to see Sam's portraits.**
- [ ] **Same search bar → type `sam`.** Expect: solo tryst photos (attributed to Sam) + Sam's portraits only. **Expect NOT to see Alex's portraits.**
- [ ] **Search with all-caps / accented variants** (e.g. `ALEX` or an accented letter if any partner has one). Expect: same matches — fold is case+accent insensitive.
- [ ] **Clear the search bar.** Expect: every photo returns.

### N3 — Set-as-partner-avatar creates orphan blob

- [ ] **Open any encounter photo of Alex in the viewer.** Tap the PersonPin (👤📌) icon → menu shows partners on that tryst → tap **"Set avatar for Alex"**.
- [ ] Menu closes without crash. Go to **Partners → Alex → tap to edit**. In PersonPhotoStrip:
  - [ ] The photo you just promoted appears as a portrait tile.
  - [ ] It shows a **checkmark** (indicating it's the current avatar). ← this is the N3 fix; pre-fix it never showed a checkmark.
  - [ ] Tap it → action sheet includes "Delete". Tap Delete → tile disappears from the strip.
- [ ] Also verify the Partners list thumbnail for Alex changed to the new photo.

### N4 — Deleting the current-avatar portrait leaves dangling ref

- [ ] **Partners → Alex → edit.** In PersonPhotoStrip: pick a portrait → action sheet → **"Set as avatar"**. The tile checkmark should move to it.
- [ ] **Long-press or tap the same portrait → Delete.** Expect: tile disappears from the strip.
- [ ] Back out of the editor → open Alex's card in the Partners list. Expect: **avatar is cleared** (initial-letter fallback). Pre-fix, it stayed pointing at the deleted blob → decode failure / blank thumbnail.
- [ ] Repeat with **profile (You)**: Settings → Your profile → PersonPhotoStrip → set-as-avatar → delete → back out → profile avatar clears.

## Bundle B — TRYSTIMPORT log strip

Automated verification already done: the literal string `TRYSTIMPORT` was confirmed **absent** from `classes.dex` in the release APK (R8 stripped it). No on-device test needed for this one — it's a release-build guarantee that debug builds don't exercise.

If you want to spot-check: on a **debug** build, force an import error (import a random non-backup file) and run `adb logcat -s TRYSTIMPORT` — you should still see the log line (debug isn't stripped, that's intentional). On the release build the line does not exist.

## Bundle C — Atomic restore

This one is inherently a "fail mid-restore" test. Two scenarios:

### C1 — Happy path (regression check that restore still works normally)

- [ ] Export a fresh backup from the current state (Settings → Backup & restore → Export encrypted backup). Save the file somewhere accessible.
- [ ] Settings → **Delete all data** → confirm. App returns to first-run.
- [ ] Set a fresh PIN → skip through onboarding → Settings → **Import backup** → pick the file → enter password.
- [ ] Expect: full restore — partners, encounters, portraits, encounter photos. Same as pre-fix; just proving the new staging path didn't regress the happy path.

### C2 — Simulated mid-restore failure (harder to fake)

This is the actual N5 scenario. On the emulator, the easiest proxy:

- [ ] Note the current media count: Photos tab → count what you see, or Files app → `/data/data/app.tryst/files/media/` (needs root or `adb shell run-as app.tryst ls files/media/ | wc -l`).
- [ ] Start an import of a legitimate backup. **Immediately** after the password prompt, force-stop the app (`adb shell am force-stop app.tryst`) — do this within about a second so you catch it mid-loop.
- [ ] Relaunch the app, enter PIN. Expect: the app is in whatever state it was in BEFORE the import started (either empty post-wipe, or the previous restored state). Verify:
  - [ ] Encounter count matches pre-import.
  - [ ] No blank/decode-failed thumbnails in the gallery.
  - [ ] Media dir contains no `.enc.staging` leftovers (`adb shell run-as app.tryst ls files/media/ | grep staging` should return nothing).

Pre-fix, C2 would have shown a fully-populated DB (from the backup's data.json committed on entry 1) with blank thumbnails wherever a media blob hadn't been extracted yet.

---

## Lens 6 testing gaps (T1-T5) — new v15 UI paths that adb can't tap

These aren't bug repros; they're flows that only real fingers can validate under `FLAG_SECURE`.

### T1 — PhotoViewer "Add to person's photos"

- [ ] Open any tryst photo → viewer's top action row → **AddPhotoAlternate** icon.
- [ ] Menu lists **You + every active partner**. Tap "You".
- [ ] Expect: menu closes, no crash. Go to Settings → Your profile → strip → the same photo appears as a portrait.
- [ ] Repeat picking a partner target → the photo lands in that partner's editor strip.
- [ ] Sanity: if all partners are archived AND profile has no name, the icon should be disabled (dim).

### T2 — PhotoViewer "Set avatar for {partner}" (already validated as part of N3 above)

Combine T2 with the N3 checks. Same flow; the N3 verification is stricter.

### T3 — PersonPhotoStrip "+" menu (camera / multi-pick / single-pick)

- [ ] Partner editor → strip → tap **+** → menu shows 3 items.
- [ ] **Take photo** → camera opens → capture → photo appears in the strip. Verify it did NOT land in the system Gallery (Photos app on device → look; should be absent).
- [ ] **Choose from gallery (multi)** → pick 3 photos → all appear in the strip.
- [ ] **Choose from gallery (single)** → pick 1 → appears in strip.
- [ ] Cancel any picker → strip unchanged, no loader stuck spinning.

### T4 — EncounterEditScreen "+" AddPhotoTile

- [ ] New encounter → **+** → camera / gallery menu.
- [ ] Camera capture → photo appears as thumbnail in the tryst.
- [ ] If **Settings → Gallery → Camera → "keep capturing"** is ON, the camera should relaunch after each successful shot; take 3 in a row.

### T5 — Filter sheets

- [ ] Photos tab → Filters button → sheet opens → toggle date scope + rating chips → tap "Show N results" → sheet closes and gallery narrows.
- [ ] Trysts tab → search → Filters button → same round trip.

---

## Sign-off

When every checkbox above passes, the v0.5.1 patch is validated behaviourally and I can cut the D-35 four-spot sync (build.gradle vc8/0.5.1 + ReleaseNotes.kt + fastlane 8.txt + CHANGELOG). If anything fails, note the bundle it belongs to and we roll that one commit back before continuing.

Deferred (not fixed this session, not on this checklist):
- **Q1** (INSERT OR REPLACE cascade on restore-over-existing) — behavior change, needs DECISIONS entry first.
- **Q2/Q3** orphan strings `settings_general` + `settings_insights` — trivial cleanup, batch with the 9 other pre-existing orphans.
- **Lens 4 REFAC** (BaseFilterViewModel extraction) — no bug, pick up when a filter change would otherwise touch both VMs.
