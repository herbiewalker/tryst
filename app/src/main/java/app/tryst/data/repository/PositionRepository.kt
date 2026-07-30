package app.tryst.data.repository

import app.tryst.core.session.SessionManager
import app.tryst.data.db.entity.PositionEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Manages user-defined (custom) positions. Built-in positions come from the [app.tryst.data.db.entity.Position] enum. */
@Singleton
class PositionRepository @Inject constructor(
    private val session: SessionManager,
) {
    private val dao get() = session.database().positionDao()

    fun observeCustom(): Flow<List<PositionEntity>> = dao.observeCustom()

    /** Adds a custom position. Returns its new id (or null when the label was blank). */
    suspend fun addCustom(label: String): String? {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return null
        val id = UUID.randomUUID().toString()
        dao.upsert(PositionEntity(id = id, label = trimmed, isBuiltIn = false))
        return id
    }

    /** Renames a custom position in place (id — and so every encounter ref — is untouched). A label that collides with an existing entry (unique index) is silently rejected. */
    suspend fun rename(id: String, label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        runCatching { dao.rename(id, trimmed) }
    }

    suspend fun delete(id: String) = dao.deleteById(id)
}
