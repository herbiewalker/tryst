# Changelog

All notable changes to Tryst are recorded here. The format is loosely based on
[Keep a Changelog](https://keepachangelog.com/). Each released version must stay in sync across
three places:

- this file,
- `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (F-Droid release notes), and
- `ReleaseNotes.all` in `app/src/main/java/app/tryst/ui/whatsnew/ReleaseNotes.kt` (in-app "What's new").

On every release: bump `versionCode`/`versionName` in `app/build.gradle.kts`, add the new fastlane
`<versionCode>.txt`, prepend a `ReleaseNote` (newest first), and add a section below.

## [Unreleased]

## [0.1.0] — 2026-06-13 (versionCode 1)

First public release.

- Everything stays on this device — no account, no sync, and no internet access at all.
- Encrypted SQLCipher database and encrypted photo storage, locked behind your PIN with optional
  biometric unlock and auto-lock on background.
- Rich encounter and partner logging, with on-device Insights and Achievements.
- Manual, password-encrypted backup/restore for moving to a new phone.
