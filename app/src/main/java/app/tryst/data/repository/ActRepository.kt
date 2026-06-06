package app.tryst.data.repository

import app.tryst.core.session.SessionManager
import app.tryst.data.db.entity.ActEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Manages user-defined (custom) acts. Built-in acts come from the [app.tryst.data.db.entity.Practice] enum. */
@Singleton
class ActRepository @Inject constructor(
    private val session: SessionManager,
) {
    private val dao get() = session.database().actDao()

    fun observeCustom(): Flow<List<ActEntity>> = dao.observeCustom()

    suspend fun addCustom(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        dao.upsert(ActEntity(id = UUID.randomUUID().toString(), label = trimmed, isBuiltIn = false))
    }

    suspend fun delete(id: String) = dao.deleteById(id)
}
