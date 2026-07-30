# Lens 6 — adb-untestable UI paths (Compose popups under FLAG_SECURE)

**Base:** `6706500` (HEAD on `main`, ahead of `v0.4.0` = `245d56e`).
**Status:** 2 code findings (both needs-fix, both in avatar/portrait plumbing); 4 flows the user needs to run manually on-device before cutting v0.5.0.

`FLAG_SECURE` means every `DropdownMenu` `onClick` introduced since v0.4.0 has only ever been reached by real fingers — which for most hasn't happened this cycle. Fixes and validations both need a device tap.

---

## Code findings

### F1. Gallery "Set as partner avatar" creates an orphan avatar blob — needs-fix

`GalleryViewModel.setAsPartnerAvatar` (line 447) re-encrypts the source into a **fresh** UUID blob via `partnerRepository.savePhoto(...)` and writes that id into `partners.photoMediaId`. That id is not tracked by any `PersonPhotoEntity`.

Consequences after tapping `PersonPin` → "Set avatar for Alex" in the viewer:
- `PartnerEditScreen` passes `ui.existing?.photoMediaId` as `currentAvatarBlobId` to `PersonPhotoStrip`. The strip compares that to `photo.mediaBlobId` (`PersonPhotoStrip.kt:120`). The gallery-set avatar has no matching portrait, so **no checkmark renders** — the strip claims no avatar is set even though one is.
- The avatar blob is unreachable from the portrait album, so it can't be deleted from the strip. Only replacing removes it.
- `PartnerEditViewModel.setAsAvatar` (line 199) writes `photo.mediaBlobId` directly, so the checkmark works from that path. The inconsistency is the bug.

Suggested fix (deferred): the gallery path should also insert a `PersonPhotoEntity` for the fresh avatar blob, OR skip the fresh copy entirely when the source is already a portrait.

### F2. Deleting the current-avatar portrait leaves `photoMediaId` dangling — needs-fix

`PartnerEditViewModel.deletePhoto` (line 194) and `ProfileViewModel.deletePhoto` (line 82) call `personPhotoRepository.delete(photo)` and stop. Neither checks whether the deleted portrait's `mediaBlobId` is the owner's `photoMediaId`. If it is, the row keeps pointing at a deleted blob.

Reproduces: pick a portrait, tap "Set as avatar" in the strip action sheet, then "Delete" the same portrait. Partner avatar and every consumer of `photoMediaId` fail to decode until a new avatar is set.

Suggested fix (deferred): if `existing.photoMediaId == photo.mediaBlobId`, `upsert(existing.copy(photoMediaId = null))` in the same coroutine.

---

## Testing gaps

None of the callbacks below have been reached under adb tap this cycle. All are new since `v0.4.0`. Each needs one manual pass on the emulator before v0.5.0 ships.

### T1. `PhotoViewer` → `AddToPersonMenu` — "Add to person's photos"

- Open a gallery photo of a tryst with Alex. Tap top-bar `AddPhotoAlternate` icon.
- Expect: menu lists "You" + all active partners. Pick "You". Expect: menu closes, no crash, and the same blob shows up in Profile → portrait strip when the settings screen is reopened.
- Repeat with a partner target — new portrait must appear in that partner's editor strip.
- Sanity: menu button is disabled when `assignablePeople` is empty (i.e. profile has no name AND no partners exist). Confirm the icon dims.

### T2. `PhotoViewer` → `AvatarPartnerMenu` — "Set avatar for Alex"

- Same photo, tap `PersonPin`. Menu lists only the tryst's partners.
- After picking a partner, verify F1's checkmark bug: reopen that partner's editor, expect the strip **not** to show a checkmark on any portrait, and expect the partner-row header thumbnail to have changed. Both observations = F1 reproduced.

### T3. `PersonPhotoStrip` "+" `DropdownMenu` — camera / multi-pick / single-pick

- Partner editor and profile editor both host the strip. Tap "+" → three menu items. Each item's `onClick` sets `addMenu = false` and launches a picker/camera. Verify:
  - Camera path: shot lands in the strip, encrypted, not in system gallery. `pending` in `rememberCameraCapture` is cleared on cancel (temp file deleted).
  - Gallery multi-pick: selecting several items returns them all in one `onAdd`. Cancelling returns nothing and does not spin the loader.
  - Single-pick fallback: on emulators without the photo picker, `GetContent` fallback fires (see `rememberImagePicker`) — worth verifying at least once on a stock AVD.
- `rememberMultiImagePicker` (`ImagePicker.kt:49`) silently drops empty results — the caller never sees a cancel. Not a bug, but if any future caller needs to distinguish, note it.

### T4. `EncounterEditScreen.AddPhotoTile` `DropdownMenu`

- New encounter → "+" tile → menu items camera / gallery. Same shape as T3. Verify camera-keep-capturing pref causes a relaunch after a successful shot (`relaunchTick` mechanism, line 148).

### T5. Filter sheets (ModalBottomSheet)

- `GalleryFiltersSheet` and `MoreFiltersSheet`. Callbacks here are chip taps + a bottom-bar button, not `DropdownMenuItem`, so they should work under adb. Still worth one manual pass because they were rewired for the shared advanced-filter column and the "Show N results" button is the sole dismiss path.

---

## Not findings

- The `PersonPhotoStrip` action `AlertDialog` (Set as avatar / Delete) is not a `DropdownMenu` — `AlertDialog` fires under adb. No testing gap there.
- `PhotoViewer` filmstrip taps and favourite pill are plain `clickable` — already exercised.
