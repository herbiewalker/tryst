package app.tryst.data.repository

import app.tryst.core.session.SessionManager
import app.tryst.data.db.entity.PersonPhotoEntity
import app.tryst.data.media.EncryptedMediaStore
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * The portrait album per person — partner or self-profile — added in v15. Encrypted blobs live in
 * [EncryptedMediaStore] (same store as encounter media); this repo just adds/reads rows in the
 * `person_photo` table and hands the underlying blob io to the store.
 */
@Singleton
class PersonPhotoRepository @Inject constructor(
    private val session: SessionManager,
    private val mediaStore: EncryptedMediaStore,
) {
    private val dao get() = session.database().personPhotoDao()

    fun observeForOwner(kind: String, ownerId: String): Flow<List<PersonPhotoEntity>> = dao.observeForOwner(kind, ownerId)

    fun observeAll(): Flow<List<PersonPhotoEntity>> = dao.observeAll()

    suspend fun getForOwner(kind: String, ownerId: String): List<PersonPhotoEntity> = dao.getForOwner(kind, ownerId)

    /**
     * Encrypts [source] into a new blob and inserts a `person_photo` row pointing at it. Returns the
     * new entity so callers can offer to promote it as the person's active avatar right after.
     */
    suspend fun add(
        kind: String,
        ownerId: String,
        source: InputStream,
        now: Long = System.currentTimeMillis(),
    ): PersonPhotoEntity = withContext(Dispatchers.IO) {
        val blobId = UUID.randomUUID().toString()
        mediaStore.save(blobId, source)
        val row = PersonPhotoEntity(
            id = UUID.randomUUID().toString(),
            ownerKind = kind,
            ownerId = ownerId,
            mediaBlobId = blobId,
            addedAt = now,
        )
        dao.upsert(row)
        row
    }

    /**
     * Adopt an ALREADY-encrypted blob as a portrait row without re-encrypting it. Used to fold pre-v15
     * legacy avatars (stored in `partners.photoMediaId` / `profile.photoMediaId` via the old
     * `savePhoto` path) into the v15 person_photo album so PersonPhotoStrip can render + manage them
     * uniformly. Idempotent-friendly: callers should check `getForOwner` first to avoid duplicates.
     */
    suspend fun adoptExistingBlob(
        kind: String,
        ownerId: String,
        blobId: String,
        now: Long = System.currentTimeMillis(),
    ): PersonPhotoEntity = withContext(Dispatchers.IO) {
        val row = PersonPhotoEntity(
            id = UUID.randomUUID().toString(),
            ownerKind = kind,
            ownerId = ownerId,
            mediaBlobId = blobId,
            addedAt = now,
        )
        dao.upsert(row)
        row
    }

    /** Deletes both the row and its encrypted blob. */
    suspend fun delete(entity: PersonPhotoEntity) {
        mediaStore.delete(entity.mediaBlobId)
        dao.deleteById(entity.id)
    }

    /**
     * Find-and-delete by blob id. Returns `true` if a portrait row referenced [blobId] and was removed
     * (along with its encrypted blob), `false` if nothing was tracked — the caller can then decide
     * whether the raw blob still needs cleaning via [PartnerRepository.deletePhoto] or similar.
     */
    suspend fun deleteByBlobId(blobId: String): Boolean {
        val row = dao.getByBlobId(blobId) ?: return false
        delete(row)
        return true
    }

    fun openBlob(id: String): InputStream = mediaStore.open(id)

    companion object {
        const val KIND_PARTNER = "partner"
        const val KIND_PROFILE = "profile"
    }
}
