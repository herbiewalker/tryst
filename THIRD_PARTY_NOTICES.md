# Third-Party Notices

Tryst is licensed under the **GNU General Public License v3.0** (see [`LICENSE`](LICENSE)).
It ships with the open-source components listed below. Each is used under its own license,
and **every one is compatible with GPLv3**. This file is the source-tree counterpart of the
in-app **Settings → About → open-source licenses** screen; keep the two in sync when
dependencies change.

Audited in pre-release **Pass 10** (dependency vulnerabilities & license compliance). No
component carries a known CVE at the audited versions, and there are no proprietary blobs.

## Components

| Component | Copyright | License |
|-----------|-----------|---------|
| AndroidX / Jetpack — Core, Activity, Compose, Material 3, Lifecycle, Navigation, Room, Biometric, Window, SQLite, … | © The Android Open Source Project | Apache-2.0 |
| Kotlin standard library & kotlinx (Coroutines, Serialization) | © JetBrains s.r.o. and contributors | Apache-2.0 |
| Dagger & Hilt | © Google LLC | Apache-2.0 |
| Google Tink (`tink-android`) | © Google LLC | Apache-2.0 |
| Gson | © Google LLC | Apache-2.0 |
| Error Prone annotations · Guava `ListenableFuture` | © Google LLC | Apache-2.0 |
| JSpecify annotations | © The JSpecify Authors | Apache-2.0 |
| Jakarta / `javax` Inject API | © Eclipse Foundation / contributors | Apache-2.0 |
| JSR-305 annotations (`com.google.code.findbugs:jsr305`) | © FindBugs project contributors | BSD-3-Clause |
| SQLCipher for Android (`net.zetetic:sqlcipher-android`) | © Zetetic LLC | BSD-style (Zetetic) |

### Note on SQLCipher

SQLCipher for Android bundles **SQLite** (public domain) and **OpenSSL**. SQLCipher 4.6.x
ships OpenSSL 3.x, which is licensed under **Apache-2.0** and is therefore GPLv3-compatible.
(The historical OpenSSL-license / GPL "advertising clause" conflict applied to GPLv2 against
pre-3.0 OpenSSL and does not apply here.)

## License texts

- **Apache-2.0:** https://www.apache.org/licenses/LICENSE-2.0
- **BSD-3-Clause:** https://opensource.org/license/bsd-3-clause
- **SQLCipher:** https://github.com/sqlcipher/sqlcipher/blob/master/LICENSE
- **GPL-3.0 (Tryst itself):** [`LICENSE`](LICENSE)
