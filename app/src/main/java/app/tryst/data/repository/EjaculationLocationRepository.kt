package app.tryst.data.repository

import app.tryst.core.session.SessionManager
import app.tryst.data.db.entity.EjaculationLocationEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Manages user-defined (custom) finish locations. Built-ins come from the [app.tryst.data.db.entity.EjaculationLocation] enum. */
@Singleton
class EjaculationLocationRepository @Inject constructor(
    private val session: SessionManager,
) {
    private val dao get() = session.database().ejaculationLocationDao()

    fun observeCustom(): Flow<List<EjaculationLocationEntity>> = dao.observeCustom()

    suspend fun addCustom(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        dao.upsert(EjaculationLocationEntity(id = UUID.randomUUID().toString(), label = trimmed, isBuiltIn = false))
    }

    /** Renames a custom finish location in place (id — and so every encounter ref — is untouched). A label that collides with an existing entry (unique index) is silently rejected. */
    suspend fun rename(id: String, label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        runCatching { dao.rename(id, trimmed) }
    }

    suspend fun delete(id: String) = dao.deleteById(id)
}
