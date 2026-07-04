# Tryst — Logic Flowcharts

> **Status:** Live — v0.3.2. Visual maps of how the app's core logic works, so a change can be
> reasoned about before touching code. Diagrams are [Mermaid](https://mermaid.js.org/) — they render on
> GitHub and in most Markdown viewers. Keep these in sync when the corresponding code changes.

Contents: [Lock lifecycle](#1-app-lock-lifecycle-state-machine) ·
[Key model](#2-key-model-dek-double-wrap) · [Unlock sequence](#3-unlock-sequence) ·
[Layered data flow](#4-layered-architecture--data-flow) · [Encounter save](#5-encounter-save-flow) ·
[Insights pipeline](#6-insights-pipeline) · [Backup export/import](#7-backup-export--import) ·
[Auto-lock handoff](#8-auto-lock--picker-handoff) · [Achievements](#9-achievements-derivation).

---

## 1. App lock lifecycle (state machine)

`SessionManager.state: StateFlow<LockState>` drives `MainActivity`, which renders the setup screen,
the lock screen, or the unlocked app. While **Unlocked** the DEK is in memory and the SQLCipher DB is
open; every other state has the DEK zeroed and the DB closed.

```mermaid
stateDiagram-v2
    [*] --> NeedsSetup: vault not initialized
    [*] --> Locked: vault initialized
    NeedsSetup --> Unlocked: setupPin(pin)
    Locked --> Unlocked: unlockWithPin(pin)
    Locked --> Unlocked: unlockWithBiometric(cipher)
    Locked --> NeedsSetup: 10 wrong PINs → vault self-wipe
    Unlocked --> Locked: lock() / app backgrounded (auto-lock)
    Unlocked --> NeedsSetup: deleteAllData()

    note right of Unlocked
        DEK in memory · SQLCipher DB open
    end note
    note right of Locked
        DEK zeroed · DB closed
    end note
```

## 2. Key model (DEK double-wrap)

One random 256-bit **Data Encryption Key (DEK)** protects everything. It is persisted on disk only
**double-wrapped**: an inner layer from the app PIN, an outer layer from a hardware-backed Android
Keystore key. The DB and media keys are *derived* from the DEK (HMAC subkeys via `SessionKeys`), so
they never need separate storage. See [SECURITY_DESIGN.md](SECURITY_DESIGN.md).

```mermaid
flowchart TD
    PIN["6-digit app PIN"] -->|"PBKDF2-HMAC-SHA256<br/>600k iters + random salt"| pinKey["pinKey"]
    DEK["Random 256-bit DEK<br/>(generated once at setup)"]
    DEK -->|"AES-GCM encrypt with pinKey"| inner["inner-wrapped DEK"]
    KSkey["Android Keystore AES-256-GCM key<br/>(StrongBox if available · non-exportable)"]
    inner -->|"AES-GCM encrypt with Keystore key"| blob[("vault blob on disk<br/>+ salt · iter · attempt counter")]
    KSkey -.protects.-> blob

    DEK ==>|"SessionKeys.databaseKey (HMAC)"| dbk["SQLCipher DB key"]
    DEK ==>|"SessionKeys.mediaKey (HMAC)"| mk["Tink media key"]

    DEK -->|"copy wrapped by 2nd auth-gated Keystore key"| bio[("biometric blob<br/>(BiometricVault)")]
```

## 3. Unlock sequence

Unlock peels the two wrap layers (Keystore outer, PIN-derived inner). A wrong PIN fails the inner
AEAD authentication, increments the attempt counter, and after 10 failures the vault self-wipes.

```mermaid
sequenceDiagram
    actor User
    participant Lock as LockScreen / LockViewModel
    participant SM as SessionManager
    participant V as Vault
    participant KS as Android Keystore
    participant DBF as TrystDatabaseFactory

    User->>Lock: enter PIN
    Lock->>SM: unlockWithPin(pin)
    SM->>V: unlock(pin)
    V->>KS: strip outer layer (Keystore key)
    V->>V: PBKDF2(pin, salt, iter) → pinKey;<br/>strip inner layer
    alt wrong PIN
        V-->>SM: WrongPinException (attempt++)
        Note over V: 10 fails → wipe() → NeedsSetup
    else correct PIN
        V-->>SM: DEK
        SM->>DBF: create(databaseKey(DEK)) + force-open
        Note over SM: a bad key fails here, not later
        SM-->>Lock: state = Unlocked
    end
```

## 4. Layered architecture & data flow

MVVM + repository, unidirectional. UI never touches crypto or the DB directly; everything sensitive
flows through `SessionManager` (which only yields a DB/keys while unlocked).

```mermaid
flowchart TD
    subgraph ui["ui/* — Compose screens + ViewModels + nav + theme"]
        Screen["Screen (Compose)"] --> VM["ViewModel (StateFlow)"]
    end
    subgraph data["data/*"]
        Repo["Repositories"]
        DAO["Room DAOs"]
        MediaStore["EncryptedMediaStore"]
        Engine["stats/InsightsEngine (pure Kotlin)"]
    end
    subgraph core["core/*"]
        Session["session/SessionManager"]
        Vault["security/Vault + BiometricVault"]
        Crypto["crypto/MediaCrypto · BackupCrypto"]
    end

    VM --> Repo
    VM --> Engine
    Repo --> Session
    Repo --> DAO
    Repo --> MediaStore
    Session --> Vault
    DAO --> SQL[("SQLCipher DB (encrypted)")]
    MediaStore --> Crypto
    Crypto --> Blobs[("Tink AES-GCM blobs<br/>app-internal storage")]
    Session -. opens/closes .-> SQL
```

## 5. Encounter save flow

Photos are *staged* on pick and only committed (encrypted) on Save; the encounter row and all its
M:N links are written in one transaction.

```mermaid
flowchart TD
    A["EncounterEditScreen: Save"] --> B["EncounterEditViewModel.save()"]
    B --> C{"staged photos?"}
    C -->|yes| D["repository.attachMedia()<br/>encrypt via Tink → app-internal"]
    C -->|no| E
    D --> E["build EncounterEntity"]
    E --> F["EncounterRepository.save()"]
    F --> G["EncounterDao.upsertWithRelations()<br/>(@Transaction: encounter + partner/position/tag refs)"]
    G --> H[("SQLCipher DB")]
```

## 6. Insights pipeline

Five reactive sources are combined and folded into an immutable `Insights` off the main thread; the
screen layers user customization (order/hidden/per-card chart style) on top, with stable per-type
colors. The engine is pure Kotlin (JVM-unit-tested, no Android).

```mermaid
flowchart LR
    R1["EncounterRepository.observeAll()"] --> CMB["combine"]
    R2["ActRepository.observeCustom()"] --> CMB
    R3["PositionRepository.observeCustom()"] --> CMB
    R4["KinkRepository.observeCustom()"] --> CMB
    R5["ToyRepository.observeCustom()"] --> CMB
    CMB -->|"map on Dispatchers.Default"| ENG["InsightsEngine.compute()"]
    ENG --> INS["Insights (totals, streaks, tallies, trends)"]
    INS --> SCR["InsightsScreen"]
    PREFS["InsightsPreferences<br/>(stat/section order · hidden · per-card style)"] --> SCR
    SCR --> CH["TrendChart / BreakdownChart<br/>+ TypeColors.colorFor(label)"]
```

## 7. Backup export / import

Live data is device-bound (Keystore-wrapped DEK), so backups are **re-encrypted** under a key derived
from the user's backup password — not a raw DB copy. See [EXPORT_FORMAT.md](EXPORT_FORMAT.md).

```mermaid
flowchart TD
    subgraph Export
        X1["BackupManager.export()"] --> X2["dump every table → data.json"]
        X1 --> X3["decrypt each media blob"]
        X2 --> X4["ZIP (data.json + media/&lt;id&gt;)"]
        X3 --> X4
        X4 --> X5["BackupCrypto: PBKDF2(password,salt,iter)<br/>→ Tink AES-GCM-HKDF stream"]
        X5 --> X6[(".tryst file via SAF")]
    end
    subgraph Import
        I1[(".tryst file")] --> I2["BackupCrypto: decrypt stream<br/>(wrong password → AEAD auth fail)"]
        I2 --> I3["unzip data.json + media"]
        I3 --> I4["INSERT OR REPLACE rows<br/>(defer_foreign_keys)"]
        I3 --> I5["re-encrypt media under this device's key"]
    end
```

## 8. Auto-lock & picker handoff

Backgrounding locks after the user's **auto-lock delay** (Settings → General; default **immediate**). A
non-zero delay schedules a process-scoped `lock()` that is cancelled if the app returns to the foreground
first (`ON_START` → `onAppForegrounded`). Handing off to the OS photo picker / camera unavoidably
backgrounds the app, so those launches arm a one-shot ~2-minute grace that skips exactly one auto-lock.

```mermaid
flowchart TD
    BG["App backgrounded (ProcessLifecycle ON_STOP)"] --> Q{"suppressNextAutoLock armed?<br/>(within 2-min grace)"}
    Q -->|"yes — picker/camera handoff"| R["consume grace · stay unlocked"]
    Q -->|"no"| T{"auto-lock timeout?"}
    T -->|"0 (immediate)"| L["lock(): close DB · zero DEK · state = Locked"]
    T -->|"> 0"| D["schedule delayed lock()"]
    D -->|"timeout elapses"| L
    D -->|"app foregrounded first (ON_START)"| C["cancel pending lock · stay unlocked"]
```

## 9. Achievements (derivation)

Like insights, achievements hold **no state** — `AchievementEngine` replays the chronologically-sorted
log against each static `AchievementDef` to derive progress and the date it unlocked. The Insights
screen shows a teaser; the trophy icon opens the full screen.

```mermaid
flowchart LR
    LOG["EncounterRepository.observeAll()"] -->|"map on Dispatchers.Default"| EV["AchievementEngine.evaluate()<br/>(sort by date, replay)"]
    CAT["Achievements.catalog (~67 static defs)"] --> EV
    EV -->|"per def, by Rule"| RULES["Count / Sum / Distinct / Streak"]
    RULES --> ST["AchievementStatus<br/>(current · unlocked · unlockedAt)"]
    ST --> SUM["summarize() → teaser rollup"]
    ST --> SCREEN["AchievementsScreen (grouped, progress bars)"]
    SUM --> TEASER["Insights teaser card → 'See all'"]
```

