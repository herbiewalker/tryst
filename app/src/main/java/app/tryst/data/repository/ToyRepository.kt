package app.tryst.data.repository

import app.tryst.core.session.SessionManager
import app.tryst.data.db.entity.ToyEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Manages user-defined (custom) toys. Built-in toys come from the [app.tryst.data.db.entity.ToyType] enum. */
@Singleton
class ToyRepository @Inject constructor(
    private val session: SessionManager,
) {
    private val dao get() = session.database().toyDao()

    fun observeCustom(): Flow<List<ToyEntity>> = dao.observeCustom()

    suspend fun addCustom(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        dao.upsert(ToyEntity(id = UUID.randomUUID().toString(), label = trimmed, isBuiltIn = false))
    }

    /** Renames a custom toy in place (id — and so every encounter ref — is untouched). A label that collides with an existing entry (unique index) is silently rejected. */
    suspend fun rename(id: String, label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        runCatching { dao.rename(id, trimmed) }
    }

    suspend fun delete(id: String) = dao.deleteById(id)
}
