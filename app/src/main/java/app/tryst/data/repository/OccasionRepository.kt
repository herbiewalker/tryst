// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.data.repository

import app.tryst.core.session.SessionManager
import app.tryst.data.db.entity.OccasionEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Manages user-defined (custom) occasions. Built-in occasions come from the [app.tryst.data.db.entity.Occasion] enum. */
@Singleton
class OccasionRepository @Inject constructor(
    private val session: SessionManager,
) {
    private val dao get() = session.database().occasionDao()

    fun observeCustom(): Flow<List<OccasionEntity>> = dao.observeCustom()

    /** Adds a custom occasion. Returns its new id (or null when the label was blank). */
    suspend fun addCustom(label: String): String? {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return null
        val id = UUID.randomUUID().toString()
        dao.upsert(OccasionEntity(id = id, label = trimmed, isBuiltIn = false))
        return id
    }

    /** Renames a custom occasion in place (id — and so every encounter ref — is untouched). A label that collides with an existing entry (unique index) is silently rejected. */
    suspend fun rename(id: String, label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        runCatching { dao.rename(id, trimmed) }
    }

    suspend fun delete(id: String) = dao.deleteById(id)
}
