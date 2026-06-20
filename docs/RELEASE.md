# Tryst — Release & F-Droid submission

Tryst ships through **F-Droid only** (decision D-32). F-Droid builds the app from source on its own
infrastructure and signs the result with its key, so we never ship a binary ourselves and never commit a
signing key. This document is the checklist for cutting a release and getting it into F-Droid.

## Prerequisites (one-time)

- [ ] **The source repository must be public.** F-Droid builds from a public git repo; it cannot ingest a
      private one. `herbiewalker/tryst` is **deliberately kept private until release** (it stays private
      while the app is unfinished). **Making it public is the final pre-submission step** — at that point
      GitHub auto-detects the license as GPL-3.0. (It was briefly flipped public on 2026-06-12, then set
      back to private; current state is **private** — confirmed via the GitHub API on 2026-06-13.)
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
2. **Add the release notes in all three synced places** (kept in lock-step on purpose — see D-35):
   - `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (e.g. `2.txt`) — F-Droid's "What's New"
     text. Keep the existing `1.txt` for the first release.
   - `ReleaseNotes.all` in [`app/src/main/java/app/tryst/ui/whatsnew/ReleaseNotes.kt`](../app/src/main/java/app/tryst/ui/whatsnew/ReleaseNotes.kt)
     — **prepend** a new `ReleaseNote` (newest first) with the bumped `versionName`/`versionCode`. This
     drives the in-app **What's new** screen and the one-time post-update popup.
   - [`CHANGELOG.md`](../CHANGELOG.md) at the repo root — add a dated section under the version heading.
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
  - Sports & Health     # closest fit for a wellness/intimacy tracker (no network category — the app has no network)
License: GPL-3.0-only
AuthorName: Tryst
SourceCode: https://github.com/herbiewalker/tryst
IssueTracker: https://github.com/herbiewalker/tryst/issues

AutoName: Tryst
# No Description: — the source repo carries fastlane metadata, so F-Droid pulls the
# title/description/changelogs from there. A "see fastlane" Description draws a review nit.

RepoType: git
Repo: https://github.com/herbiewalker/tryst.git

Builds:
  - versionName: 0.1.0
    versionCode: 1
    commit: 7ba63acab7b044deccb615ef4215da96147d5a76   # FULL commit hash of the v0.1.0 tag — reviewers reject a tag/branch name here
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version    # NOT "Version v%v" — that fails schema validation; the leading "v" on tags is handled automatically
UpdateCheckMode: Tags
CurrentVersion: 0.1.0
CurrentVersionCode: 1
```
(The file **must** end with a trailing newline or `fdroid rewritemeta` fails.)

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

### MR runbook (must be done by a human on GitLab — F-Droid is GitLab-hosted)

The submission cannot be automated from this repo's tooling (no `glab`, no GitLab credential; F-Droid is
on GitLab, not GitHub). Pass 12 has passed (GO, conditional — see [ROADMAP.md](ROADMAP.md)), so this is
now unblocked; do it once, by hand:

1. Sign in / register at <https://gitlab.com> and **fork** <https://gitlab.com/fdroid/fdroiddata>.
2. Clone your fork and create a branch:
   ```bash
   git clone https://gitlab.com/<you>/fdroiddata.git && cd fdroiddata
   git checkout -b add-app.tryst
   ```
3. Save the YAML block above as `metadata/app.tryst.yml` (verify `commit: v0.1.0` matches the pushed tag,
   and that `versionCode`/`versionName` match `app/build.gradle.kts`).
4. Optionally lint locally with F-Droid's tooling: `fdroid readmeta && fdroid lint app.tryst` (needs
   `fdroidserver` installed; F-Droid CI will lint it regardless).
5. Commit and push to your fork:
   ```bash
   git commit -am "New app: Tryst (app.tryst)"
   git push -u origin add-app.tryst
   ```
6. Open a merge request from your branch into `fdroid/fdroiddata:master`. Suggested MR description:
   > New app: **Tryst** (`app.tryst`) — a private, local-only, fully offline intimacy tracker. GPL-3.0,
   > no `INTERNET` permission, no trackers/ads/analytics, encrypted at rest. Source:
   > https://github.com/herbiewalker/tryst, release tag `v0.1.0`. No anti-features.
7. Respond to the F-Droid reviewers' feedback on the MR until it's merged; the first build then appears in
   the repo within a build cycle.

### What the first submission actually required (2026-06-14/15, MR !40471)

The plan above was mostly right; F-Droid's CI + reviewer (`@linsui`) surfaced these concrete fixes — they're
now folded into the recipe above, but keep them in mind for any future recipe change:

- **Full commit hash in `Builds.commit`**, never the `vX.Y.Z` tag (reviewer requirement).
- **`AutoUpdateMode: Version`**, not `Version v%v` (the latter fails schema validation).
- **Trailing newline** on `app.tryst.yml` or `fdroid rewritemeta` fails. Editing the file via the GitLab
  API silently drops it — re-append every time.
- **No `Description:`** — we have fastlane metadata, so F-Droid uses that.
- **Removed the Gradle `org.gradle.toolchains.foojay-resolver-convention` plugin** from `settings.gradle.kts`
  — F-Droid's build scanner flags it (network JDK-toolchain fetch). It was dead `gradle init` scaffolding;
  this was a source fix, so **the `v0.1.0` tag was re-cut** onto the fix (`7ba63ac…`).
- **Empty-pipeline gotcha:** the first fork push / MR pipeline showed "failed" with **zero jobs** (a benign
  CI-rules artifact); one more commit on the open MR triggered the real multi-job pipeline.
- **Reproducible builds were declined** (see DECISIONS **D-39**) — F-Droid signs; **permanent**, can't switch.
- The whole MR (fork, branch, file commits, MR create/update, replies) was driven via the **GitLab REST API**
  with a PAT (`api` scope); reading the public MR's status needs no auth.

## Why no signing config here

F-Droid signs builds with its own key; a self-distributed APK (outside F-Droid) would instead use a
**gitignored** `keystore.properties` + `zipalign`/`apksigner` — never a committed keystore. See the
pre-release Pass 11 notes in [ROADMAP.md](ROADMAP.md) for the debug-sign-for-smoke-test procedure.
