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
package app.tryst.data.repository

import app.tryst.core.session.SessionManager
import app.tryst.data.db.entity.OrgasmTypeEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** The user-built orgasm types (ORG-1, 0.7.7, D-76). Nothing is preset; trysts refer to them as "custom:<id>". */
@Singleton
class OrgasmTypeRepository @Inject constructor(
    private val session: SessionManager,
) {
    private val dao get() = session.database().orgasmTypeDao()

    fun observeAll(): Flow<List<OrgasmTypeEntity>> = dao.observeAll()

    /** Adds a type. Returns its new id, or null when the label was blank or already taken. */
    suspend fun add(label: String): String? {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return null
        val id = UUID.randomUUID().toString()
        return runCatching { dao.upsert(OrgasmTypeEntity(id = id, label = trimmed)) }.map { id }.getOrNull()
    }

    /** Renames a type in place, so every tryst that uses it keeps it. A label already taken is ignored. */
    suspend fun rename(id: String, label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        runCatching { dao.rename(id, trimmed) }
    }

    suspend fun delete(id: String) = dao.deleteById(id)
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
