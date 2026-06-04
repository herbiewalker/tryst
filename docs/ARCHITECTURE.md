# Ember — Architecture

Status: **Draft v0.1**

## Stack (defaults — see [CLAUDE.md](../CLAUDE.md))
Kotlin (JDK 17) · Jetpack Compose + Material 3 · Room+SQLCipher · Hilt · Coroutines/Flow ·
Tink (media crypto) · Argon2id (KDF) · AndroidX Biometric · Gradle KTS + version catalog.

`minSdk 29` · `compileSdk`/`targetSdk 36`.

## Pattern
- **MVVM + Repository**, unidirectional data flow.
- UI (Compose screens) → ViewModel (state + events) → Repository → DAO/crypto/file sources.
- Domain logic (stats, achievements, validation) kept off the UI thread and unit-testable.
- State via immutable UI-state data classes exposed as `StateFlow`.

## Layering
```
presentation/  Compose screens, ViewModels, navigation, theming
domain/        use-cases, stats engine, achievement rules (pure Kotlin, testable)
data/          repositories, Room DAOs/entities, SQLCipher setup, media crypto, export/import
core/          crypto primitives (KDF, AEAD, keystore), app-lock, security utils, common
```

## Modules
Start single-module `:app` (package-by-feature). Split into `:core`, `:data`, `:feature-*`
later only if build time or boundaries demand it. Don't over-modularize early.

## Security touchpoints (cross-cutting — see [SECURITY_DESIGN.md](SECURITY_DESIGN.md))
- DB factory injects the DEK into SQLCipher; key lifecycle tied to lock state.
- An `AppLockManager` owns lock/unlock, auto-lock timer, and key zeroization.
- All `Activity`/windows set `FLAG_SECURE`.
- A single audited crypto module; feature code never rolls its own crypto.

## Navigation
Compose Navigation. Top destinations (tentative): History, Add/Edit Encounter, Partners,
Insights, Settings. A lock screen gates the whole graph.

## Testing strategy
- Unit: domain (stats/achievements), repositories (with in-memory/encrypted test DB), crypto.
- DB: Room migration tests.
- UI: Compose tests for key flows (add encounter, unlock).
- CI guard: assert merged manifest has **no `INTERNET` permission** and no banned SDKs.

## Quality gates
Detekt/ktlint, Android Lint, dependency-license check (FOSS only).
