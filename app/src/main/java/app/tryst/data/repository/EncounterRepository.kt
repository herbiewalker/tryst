// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.data.repository

import app.tryst.core.session.SessionManager
import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.relation.EncounterWithDetails
import app.tryst.data.media.EncryptedMediaStore
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class EncounterRepository @Inject constructor(
    private val session: SessionManager,
    private val mediaStore: EncryptedMediaStore,
) {
    private val encounterDao get() = session.database().encounterDao()
    private val mediaDao get() = session.database().mediaDao()

    fun observeAll(): Flow<List<EncounterWithDetails>> = encounterDao.observeAllWithDetails()

    suspend fun get(id: String): EncounterWithDetails? = encounterDao.getWithDetails(id)

    suspend fun save(
        encounter: EncounterEntity,
        partnerIds: List<String> = emptyList(),
        positionIds: List<String> = emptyList(),
        tagIds: List<String> = emptyList(),
    ) = encounterDao.upsertWithRelations(encounter, partnerIds, positionIds, tagIds)

    suspend fun delete(encounter: EncounterEntity) {
        // Encrypted blobs are not removed by the DB cascade; clean them up explicitly.
        mediaDao.getForEncounter(encounter.id).forEach { mediaStore.delete(it.id) }
        encounterDao.delete(encounter)
    }

    suspend fun attachMedia(
        encounterId: String,
        mimeType: String,
        source: InputStream,
        now: Long = System.currentTimeMillis(),
    ): MediaEntity {
        val id = UUID.randomUUID().toString()
        val file = mediaStore.save(id, source)
        val media = MediaEntity(
            id = id,
            encounterId = encounterId,
            encFilePath = file.absolutePath,
            mimeType = mimeType,
            createdAt = now,
        )
        mediaDao.upsert(media)
        return media
    }

    fun openMedia(media: MediaEntity): InputStream = mediaStore.open(media.id)

    suspend fun deleteMedia(media: MediaEntity) {
        mediaStore.delete(media.id)
        mediaDao.delete(media)
    }

    /** Toggles/sets a photo's favourite mark (GAL-3). */
    suspend fun setFavorite(mediaId: String, favorite: Boolean) = mediaDao.setFavorite(mediaId, favorite)

    /** Marks/unmarks many photos at once — the gallery's bulk favourite action (GAL-4). */
    suspend fun setFavorite(mediaIds: List<String>, favorite: Boolean) {
        if (mediaIds.isNotEmpty()) mediaDao.setFavorite(mediaIds, favorite)
    }

    /** Deletes many photos (blob + row) in one bulk action (GAL-4). */
    suspend fun deleteMedia(media: List<MediaEntity>) {
        for (m in media) {
            mediaStore.delete(m.id)
            mediaDao.delete(m)
        }
    }

    /** Moves photos to another tryst — the gallery's bulk reassign (GAL-4). No blob movement; only the row's owner. */
    suspend fun reassignMedia(mediaIds: List<String>, encounterId: String) {
        if (mediaIds.isNotEmpty()) mediaDao.reassign(mediaIds, encounterId)
    }
}
