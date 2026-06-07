package app.tryst.data.repository

import app.tryst.core.session.SessionManager
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.media.EncryptedMediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PartnerRepository @Inject constructor(
    private val session: SessionManager,
    private val mediaStore: EncryptedMediaStore,
) {
    private val dao get() = session.database().partnerDao()

    fun observeActive(): Flow<List<PartnerEntity>> = dao.observeActive()

    suspend fun upsert(partner: PartnerEntity) = dao.upsert(partner)

    suspend fun getById(id: String): PartnerEntity? = dao.getById(id)

    suspend fun getAll(): List<PartnerEntity> = dao.getAll()

    suspend fun archive(id: String, now: Long = System.currentTimeMillis()) = dao.archive(id, now)

    // --- Partner photo (encrypted blob in app-internal storage; id stored as Partner.photoMediaId) ---

    /** Encrypts [source] to a new blob and returns its media id. */
    suspend fun savePhoto(source: InputStream): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        mediaStore.save(id, source)
        id
    }

    fun openPhoto(id: String): InputStream = mediaStore.open(id)

    fun deletePhoto(id: String) {
        mediaStore.delete(id)
    }
}
