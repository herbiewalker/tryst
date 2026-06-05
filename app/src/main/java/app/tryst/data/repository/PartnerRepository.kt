package app.tryst.data.repository

import app.tryst.data.db.dao.PartnerDao
import app.tryst.data.db.entity.PartnerEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PartnerRepository @Inject constructor(
    private val partnerDao: PartnerDao,
) {
    fun observeActive(): Flow<List<PartnerEntity>> = partnerDao.observeActive()

    suspend fun upsert(partner: PartnerEntity) = partnerDao.upsert(partner)

    suspend fun getById(id: String): PartnerEntity? = partnerDao.getById(id)

    suspend fun archive(id: String, now: Long = System.currentTimeMillis()) =
        partnerDao.archive(id, now)
}
