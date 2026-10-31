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
import app.tryst.data.db.entity.OrientationEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Manages the user-defined orientation labels (DEMO-1). There are no built-ins; every row is the user's. */
@Singleton
class OrientationRepository @Inject constructor(
    private val session: SessionManager,
) {
    private val dao get() = session.database().orientationDao()

    fun observeAll(): Flow<List<OrientationEntity>> = dao.observeAll()

    /** Adds a label. Returns its new id (or null when the label was blank). */
    suspend fun add(label: String): String? {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return null
        val id = UUID.randomUUID().toString()
        dao.upsert(OrientationEntity(id = id, label = trimmed))
        return id
    }

    /** Renames in place (the id, and so every partner/profile ref, is untouched). A label that collides with an existing one (unique index) is silently rejected. */
    suspend fun rename(id: String, label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        runCatching { dao.rename(id, trimmed) }
    }

    /** Deletes the label and clears it from every partner and the profile. */
    suspend fun delete(id: String) = dao.delete(id)
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
