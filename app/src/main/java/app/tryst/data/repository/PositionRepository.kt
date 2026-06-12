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

    suspend fun addCustom(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        dao.upsert(PositionEntity(id = UUID.randomUUID().toString(), label = trimmed, isBuiltIn = false))
    }

    suspend fun delete(id: String) = dao.deleteById(id)
}
