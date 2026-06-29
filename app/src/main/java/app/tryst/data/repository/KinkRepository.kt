package app.tryst.data.repository

import app.tryst.core.session.SessionManager
import app.tryst.data.db.entity.KinkEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Manages user-defined (custom) kinks. Built-in kinks come from the [app.tryst.data.db.entity.Kink] enum. */
@Singleton
class KinkRepository @Inject constructor(
    private val session: SessionManager,
) {
    private val dao get() = session.database().kinkDao()

    fun observeCustom(): Flow<List<KinkEntity>> = dao.observeCustom()

    suspend fun addCustom(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        dao.upsert(KinkEntity(id = UUID.randomUUID().toString(), label = trimmed, isBuiltIn = false))
    }

    suspend fun delete(id: String) = dao.deleteById(id)
}
