# Tryst — Release & F-Droid submission

Tryst ships through **F-Droid only** (decision D-32). F-Droid builds the app from source on its own
infrastructure and signs the result with its key, so we never ship a binary ourselves and never commit a
signing key. This document is the checklist for cutting a release and getting it into F-Droid.

## Prerequisites (one-time)

- [ ] **The source repository must be public.** F-Droid builds from a public git repo; it cannot ingest a
      private one. The repo is currently **private** (`origin` = the GitHub `herbiewalker/tryst` remote) —
      make it public (or mirror to a public host) before submitting. This is a hard blocker for F-Droid.
- [ ] All dependencies are FOSS and there are no proprietary blobs (verified in pre-release Pass 10; the
      `OssLicenses` list + the banned-SDK grep guard this). F-Droid will reject non-free dependencies or
      `anti-features` we haven't declared. Tryst has **no anti-features** (no tracking, no non-free deps,
      no non-free network services — it has no network at all).
- [ ] The build is clean from source with no Google-proprietary Gradle plugins (no
      `com.google.gms`, no Firebase, no Play Services). Confirmed.

## Per-release steps

1. **Bump the version** in [`app/build.gradle.kts`](../app/build.gradle.kts):
   - `versionCode` — integer, +1 every release (currently `1`).
   - `versionName` — human string (currently `0.1.0`).
2. **Add a changelog** for the new `versionCode` at
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (e.g. `2.txt`). F-Droid shows this as the
   "What's New" text. Keep the existing `1.txt` for the first release.
3. **Update the store copy** if needed:
   `fastlane/metadata/android/en-US/{title,short_description,full_description}.txt`.
   (`short_description` ≤ 80 chars; `full_description` ≤ 4000 chars; plain text, blank lines for paragraphs,
   `* ` for bullets — no HTML.)
4. **Sanity-build** the release artifact locally to be sure it compiles and shrinks cleanly under R8
   (F-Droid will do its own build, but catch breakage early):
   ```powershell
   $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
   .\gradlew.bat assembleRelease checkNoNetworkRelease
   ```
   This emits `app/build/outputs/apk/release/app-release-unsigned.apk` — F-Droid signs its own build, so
   leaving it unsigned is correct. Do **not** add a committed signing config; `checkNoNetworkRelease` must
   stay green (no `INTERNET` permission in the merged release manifest).
5. **Commit, tag, and push the tag.** F-Droid builds from an annotated git tag:
   ```powershell
   git commit -am "Release 0.1.0 (versionCode 1)"
   git tag -a v0.1.0 -m "Tryst 0.1.0"
   git push; git push --tags
   ```
   (Pushing to `main` needs the user's explicit per-request authorization.)
6. **Archive the R8 mapping** for the release from `app/build/outputs/mapping/release/mapping.txt` (kept
   per-release for deobfuscating any locally-reported stack trace; never shipped).

## Getting it into F-Droid (first submission)

F-Droid metadata lives in F-Droid's **fdroiddata** repo, not ours. Submit a merge request to
<https://gitlab.com/fdroid/fdroiddata> adding `metadata/app.tryst.yml`. Starting template:

```yaml
Categories:
  - Connectivity        # adjust to the closest real F-Droid categories
License: GPL-3.0-only
AuthorName: Tryst
SourceCode: https://github.com/<public-owner>/tryst
IssueTracker: https://github.com/<public-owner>/tryst/issues

AutoName: Tryst
Description: |-
    See fastlane/metadata/android/en-US/full_description.txt in the source repo;
    F-Droid pulls the title, descriptions, and changelogs from there automatically.

RepoType: git
Repo: https://github.com/<public-owner>/tryst.git

Builds:
  - versionName: 0.1.0
    versionCode: 1
    commit: v0.1.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags
CurrentVersion: 0.1.0
CurrentVersionCode: 1
```

Notes:
- F-Droid reads the **fastlane metadata committed in this repo** (`fastlane/metadata/android/en-US/`) for
  the title / descriptions / per-version changelogs — keep those current; the `Description:` in the recipe
  is only a fallback.
- `UpdateCheckMode: Tags` + `AutoUpdateMode: Version v%v` means future releases are picked up automatically
  from new `vX.Y.Z` tags whose `versionCode` increased — no further fdroiddata MR needed for routine
  updates.
- Screenshots are optional and **not** included: `FLAG_SECURE` blanks captures, so producing real ones
  needs the temporary FLAG_SECURE-off procedure from pre-release Pass 5. Add them later under
  `fastlane/metadata/android/en-US/images/phoneScreenshots/` if desired.

## Why no signing config here

F-Droid signs builds with its own key; a self-distributed APK (outside F-Droid) would instead use a
**gitignored** `keystore.properties` + `zipalign`/`apksigner` — never a committed keystore. See the
pre-release Pass 11 notes in [ROADMAP.md](ROADMAP.md) for the debug-sign-for-smoke-test procedure.
