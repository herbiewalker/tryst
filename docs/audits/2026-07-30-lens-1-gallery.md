# Lens 1 — Gallery core correctness

**Status:** 2 findings (1 needs-fix, 1 needs-fix). Scope reviewed: `data/gallery/GalleryModels.kt`,
`data/gallery/GalleryPhotos.kt`, `ui/gallery/GalleryViewModel.kt`, and
`test/java/app/tryst/data/gallery/GalleryPhotosTest.kt`. Cross-checks in
`data/filter/EncounterFilter.kt`, `data/repository/PersonPhotoRepository.kt`, and the
`PersonPhotoDao` in `data/db/dao/SupportingDaos.kt`.

The two findings share a root cause: portrait rows are folded into the pipeline separately
from encounters (per design), but the code that mimics `EncounterFilter.matches` for portraits
has two gaps where a portrait passes when it should have been dropped. Both are user-visible
today: any user who drills into "You" from the People tab, or types anything into the search
bar, hits at least one of them.

---

## Finding 1 — Self-drill leaks every partner's portrait album

**Severity:** needs-fix

**Location:** `app/src/main/java/app/tryst/data/gallery/GalleryPhotos.kt:143-152`
(the partner block of `personPhotoPassesFilter`), interacting with
`app/src/main/java/app/tryst/ui/gallery/GalleryViewModel.kt:250-254` (the self-drill
`filter` mapping).

**Claim:** When `filter.partnerIds` is empty and `filter.includeSolo` is true (the exact
shape the VM assembles for a People → "You" drill), the guard
`if (!byPartner && !filter.includeSolo) return false` reduces to
`if (!byPartner && false) return false`, so **every portrait — partner and self alike —
passes**. The self drill therefore surfaces every partner's portrait album instead of
only the self profile's.

**Concrete failure scenario:**
- `person_photo` rows: `("partner","alex","blob-a")`, `("partner","sam","blob-b")`,
  `("profile","self","blob-s")`.
- User taps the People tab and taps the "You" avatar → `drillIntoSelf()` →
  `drilledPartnerId = "self"` → the VM's `filter` flow emits
  `EncounterFilter(partnerIds = emptySet(), includeSolo = true, …)`.
- `GalleryPhotos.build` narrows encounters correctly (only solo trysts survive
  `EncounterFilter.matches`), but for portraits `personPhotoPassesFilter` runs on each of
  the three rows:
  - `blob-s` (self): `byPartner=false`, `!byPartner && !includeSolo` = `true && false` = `false` → **passes** (correct).
  - `blob-a` (Alex): `byPartner=false`, same result → **passes** (WRONG).
  - `blob-b` (Sam): `byPartner=false`, same result → **passes** (WRONG).
- Expected: photos = [self portrait + solo-encounter photos].
  Actual: photos = [self portrait, Alex portrait, Sam portrait, + solo-encounter photos].

The exact same bug fires whenever a user toggles the "Include solo" chip on the More
Filters sheet without also selecting any partner — every partner's portrait album shows
up alongside solo-encounter photos.

**Suggested remediation:** In `personPhotoPassesFilter`, treat `includeSolo` for portraits
as "match the self-profile portrait only" (that's the intent of the VM mapping — self isn't
a partner id, so the self portrait is the portrait analogue of a solo encounter):

```kotlin
val bySelf = filter.includeSolo && ownerId == SELF_PARTNER_ID
if (!byPartner && !bySelf) return false
```

Add a regression test with the three-portrait setup above and
`filter = EncounterFilter(includeSolo = true)`; assert `ids == setOf("blob-s")`.

---

## Finding 2 — Text query does not narrow portraits

**Severity:** needs-fix

**Location:** `app/src/main/java/app/tryst/data/gallery/GalleryPhotos.kt:52-56` (the
`build` pipeline runs `applyQuery` only on `withPhotos`; `portraitPhotos` bypass it) and
`personPhotoPassesFilter:143-172` (no query hook).

**Claim:** Portraits skip the free-text search entirely. Searching for a partner's name
in the Photos tab returns that partner's encounter photos plus **all** other partners'
portraits and the self portrait — the query has no effect on the portrait subset.

**Concrete failure scenario:**
- `person_photo` rows: `("partner","alex","blob-a")`, `("partner","sam","blob-b")`,
  `("profile","self","blob-s")`.
- Encounters: one with Alex (media `enc-alex`), one solo with note `"beach"` (media
  `enc-solo`).
- User types `alex` into the gallery search bar. `applyQuery` narrows encounters to
  `[enc-alex]` (Alex's tryst). Portraits go through `personPhotoPassesFilter` with a
  default `EncounterFilter()` (partnerIds empty, includeSolo false) → the partner block
  is skipped, the `excludingCategorySet` is false, so all three portraits pass.
- Expected: `["enc-alex", "blob-a"]` (Alex's encounter + Alex's portrait).
  Actual: `["enc-alex", "blob-a", "blob-b", "blob-s"]`.

Same shape for `query = "beach"`: user gets the solo encounter's photo plus every
partner's portrait, which reads as a search bug to the user.

**Suggested remediation:** Give portraits a token-based filter parallel to
`EncounterSearch.tokenize`. Cheapest correct behavior: when `tokens.isNotEmpty()`, keep
a portrait only if any token is a substring (case-insensitive) of its partner display
name (or of "you"/self displayName for the profile portrait). Add a test that mirrors
`textQueryMatchesPartnerName` for portraits — with the setup above and
`query = "alex"`, assert `ids == setOf("enc-alex", "blob-a")`.

---

## What else I looked at and did not flag

- **Empty gallery / single photo / duplicate blob ids / missing partner:** no NPE, OOB,
  or off-by-one under any of these. `build(emptyList(), …)` returns `GalleryResult()`;
  `groupByPartner`'s `Solo` bucket is unreachable (encounter photos always carry a Self
  stub, portraits always carry exactly one partner) — dead but harmless; a portrait
  whose owning partner is missing from `partnerNamesById` renders anonymous rather than
  crashing.
- **Sort determinism:** `sortPhotos` is a stable Kotlin sort keyed on `takenAt` with a
  `media?.createdAt ?: takenAt` tiebreaker; the portrait DAO's `ORDER BY addedAt DESC`
  feeds it in a deterministic order per query. The only theoretical non-determinism is
  two portraits with the exact same `addedAt` millisecond — SQLite doesn't guarantee a
  tiebreaker there — but that's an outside-the-bar edge case.
- **`groupByPartner` vs the v15 person-photo merge:** a partner's portraits and their
  encounter photos correctly collapse into a single partner section (LinkedHashMap keyed
  by partner id).
- **`assignablePeople` + `addPhotoToPerson`:** starts as `[Self(name=null)]` (initial
  StateFlow value) and updates when partners/profile arrive — no NPE. `addPhotoToPerson`
  swallows failures with `runCatching { … }` (that's a UX concern for Lens 5/6, not a
  correctness bug).
- **Partner drill (non-self) filter:** `partnerIds = {drilledId}`, `includeSolo = false`
  correctly keeps only that partner's portraits and drops the profile portrait.
- **`onlyFavorites` dropping portraits:** documented and consistent with the code —
  portraits carry `favorite = false` unconditionally, so a favourites-only view has
  none, matching the doc on `portraitPhoto`.
- **Selection bulk actions:** `_selectedIds` keys by blobId (unique per photo), and
  `favoriteSelected` / `reassignSelected` correctly filter to `media != null` so
  portraits are ignored; `deleteSelected` splits the selection into
  `encounterRepository.deleteMedia` and `personPhotoRepository.deleteByBlobId` — no
  double-delete or leaked blob.
