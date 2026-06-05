package app.tryst.data.repository

import app.tryst.core.session.SessionManager
import app.tryst.data.db.entity.PartnerEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PartnerRepository @Inject constructor(
    private val session: SessionManager,
) {
    private val dao get() = session.database().partnerDao()

    fun observeActive(): Flow<List<PartnerEntity>> = dao.observeActive()

    suspend fun upsert(partner: PartnerEntity) = dao.upsert(partner)

    suspend fun getById(id: String): PartnerEntity? = dao.getById(id)

    suspend fun archive(id: String, now: Long = System.currentTimeMillis()) = dao.archive(id, now)
}
