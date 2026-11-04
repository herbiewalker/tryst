// SPDX-License-Identifier: GPL-3.0-or-later
// ╭──────────────────────────────────────────────────────────────────╮
// │  ┌●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┐  │
// │  │                                                            │  │
// │  ●   ██╗  ██╗    ██╗    ██╗                                   ●  │
// │  │   ██║  ██║    ██║    ██║           herbiewalker            │  │
// │  │   ███████║    ██║ █╗ ██║──●──●──●──┐                       │  │
// │  │   ██╔══██║    ██║███╗██║           │                       │  │
// │  ●   ██║  ██║    ╚███╔███╔╝           ●───⏣  code · tools     ●  │
// │  │   ╚═╝  ╚═╝     ╚══╝╚══╝                    homelab         │  │
// │  │                                                            │  │
// │  └●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┘  │
// ╰──────────────────────────────────────────────────────────────────╯
package app.tryst.data.backup

import java.io.IOException

/** Where an export or import is up to, for the progress sheet (QOL-9). [total] is null when it is not known yet. */
data class BackupProgress(val stage: BackupStage, val done: Int = 0, val total: Int? = null)

enum class BackupStage { PREPARING, DATABASE, PHOTOS, FINISHING }

/** Why a backup operation failed, so the screen can say something useful instead of one generic line (QOL-9). */
enum class BackupError {
    /** The header decrypted nothing: wrong password, or the file is damaged right at the start. */
    WRONG_PASSWORD,

    /** Not a Tryst backup file at all. */
    NOT_A_BACKUP,

    /** Made by a newer Tryst than this one. */
    NEWER_VERSION,

    /** Decrypted at first, then failed part way: the file is truncated or corrupted. */
    DAMAGED,

    /** The phone ran out of storage. */
    DISK_FULL,

    OTHER,
}

class BackupException(val kind: BackupError, cause: Throwable? = null) : IOException(cause?.message ?: kind.name, cause)

/** What an export wrote. */
data class ExportResult(val bytes: Long, val trysts: Int, val partners: Int, val photos: Int)

/** What an import brought in. */
data class ImportResult(val trysts: Int, val partners: Int, val photos: Int, val withSettings: Boolean)

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
