# Tryst — Video Roadmap

> **Status:** Extracted 2026-08-31 from [ROADMAP_FUTURE.md](ROADMAP_FUTURE.md) as its own file
> because video is a distinct scope (new runtime dependency, on-device playback plumbing, and a
> real hit on backup container size) — not the sort of small change that fits between photo
> features. Deliberately out of scope for the v0.5.x line; revisit as its own theme once the
> photo work has closed out.

## Why this is its own roadmap

Every other post-v0.1 feature has been dependency-free (charts hand-drawn, no chart lib; every
transform in-process). **Video is the first item that requires a runtime media dependency** —
Media3 / ExoPlayer — and playback of encrypted-at-rest video needs a custom `DataSource` over
Tink's seekable channel so plaintext never touches disk. That plumbing plus the backup-size
implications make it a coherent bundle on its own, rather than one row inside the general
future roadmap.

## Hard constraints (unchanged)

The video work has to preserve every existing invariant:
- **No network permission ever.** Media3 modules pulled in must be the local-playback ones only
  — no HLS / DASH / Smooth-Streaming / cast / DRM extensions. The `checkNoNetwork*` Gradle guard
  + CI job stay green with the new dep on the classpath.
- **Encrypted at rest.** Video bytes live in `EncryptedMediaStore` alongside photos; playback
  streams through Tink's decrypting channel — no decrypted-to-disk temp file ever exists on the
  playback path.
- **FLAG_SECURE.** Already covers the surface + app-switcher preview; nothing to add for video.
- **FOSS.** Media3 ExoPlayer is Apache-2.0; add to `THIRD_PARTY_NOTICES.md` + the in-app
  About/OSS licenses list on land.

## Items

### MED-1 — Video attachments *(originally added 2026-06-20)*

Let an encounter attach **video** alongside photos.

**Storage model already fits.** `MediaEntity` carries a `mimeType` (the table is media-generic,
not photo-specific), and `MediaCrypto` is Tink `AesGcmHkdfStreaming` — which supports a
**seekable decrypting channel**, so video can be decrypted-and-seeked on the fly rather than
decrypted fully into memory (photos can decrypt in-memory; videos can't).

**Pieces:**

1. **Capture / import.** Extend `ImagePicker` to accept video MIME types, and add a
   video-record mode to the in-app camera (same FileProvider-temp → encrypt → delete pattern;
   temps already land in `cacheDir/captures`, already swept on unlock + `deleteAllData`).
2. **Playback.** Media3 / ExoPlayer with a **custom `DataSource`** backed by Tink's seekable
   channel over `EncryptedMediaStore` (no plaintext ever hits disk; `FLAG_SECURE` already
   blanks the surface + app-switcher).
3. **Thumbnails.** Extract a frame via `MediaMetadataRetriever` over the decrypting stream for
   the gallery grid + a play-badge overlay.

**No schema change.**

**Dependency vet (Pass-10 style)** — required before any code lands:
- FOSS (Apache-2.0), verified in `THIRD_PARTY_NOTICES.md`.
- Pull **only** the core + UI playback modules — never `media3-exoplayer-dash`,
  `media3-exoplayer-hls`, `media3-exoplayer-smoothstreaming`, `media3-exoplayer-rtsp`,
  `media3-exoplayer-ima`, `media3-cast`, or the DRM (`media3-exoplayer-drm`) extensions. Every
  one of those has a legitimate reason for the network or third-party code we don't want.
- Confirm `checkNoNetworkDebug` + `checkNoNetworkRelease` still pass with the module on the
  classpath (merged manifest must still declare **no** `INTERNET`).
- Confirm the CI banned-SDK grep still clears with the new coordinate.

**Watch-outs:**
- **Backup container size balloons.** The `.tryst` ZIP holds decrypted bytes re-encrypted under
  the backup password; one 60-second 1080p clip already dwarfs the entire photo library today.
  Consider a per-file size cap and an export-size warning before landing.
- **Larger temp files raise the orphaned-plaintext stakes.** The `cacheDir/captures` sweep
  already covers it — re-verify with the video capture path on top.

### GAL-2 — Gallery includes video *(originally added 2026-06-20)*

Once **MED-1** lands, the Photos tab surfaces videos beside photos: frame thumbnails with a
**play badge**, tap → inline encrypted playback via the same `PhotoViewer` (renamed to
`MediaViewer` or given a video branch). Pure UI on top of MED-1 + the existing filter layer;
no schema change.

**Watch-outs:**
- The current `PhotoViewer` reuses `DecodedImage` + `HorizontalPager`; a video page needs an
  ExoPlayer surface instead. The pager stays intact; the per-page composable branches on mime.
- The **slideshow** (GAL-5) skips videos or plays them once each (design call at build time).
- **Filmstrip** thumbnails need to come from the video-frame extractor, not the same photo
  decoder — a separate loader keyed by mime.

## What lands with MED-1 + GAL-2

Bundle these as a distinct release (probably **v0.6.0** or later, once photo close-out is
done and QOL-5 / any other v0.6-theme items settle), because the dep + backup-size change
deserves its own headline in the CHANGELOG and its own "here's what's new" popup — mixing
it into a smaller QoL release would bury the change.

## Cross-links

- [ROADMAP_FUTURE.md](ROADMAP_FUTURE.md) — the general post-1.0 roadmap (photo close-out,
  QOL-5, INS-1, DEL-1, engineering backlog).
- [ARCHITECTURE.md](ARCHITECTURE.md) — where `EncryptedMediaStore` + `MediaCrypto` sit.
- [SECURITY_DESIGN.md](SECURITY_DESIGN.md) §2 — the encryption model the video path must honour.
- [EXPORT_FORMAT.md](EXPORT_FORMAT.md) — the `.tryst` container that video would ride inside.
