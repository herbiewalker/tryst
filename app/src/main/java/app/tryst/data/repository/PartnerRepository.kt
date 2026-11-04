// SPDX-License-Identifier: GPL-3.0-or-later
// ╭──────────────────────────────────────────────────────────────────╮
// │  ┌●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┐  │
// │  │                                                            │  │
// │  ●   ██╗  ██╗    ██╗    ██╗                                   ●  │
// │  │   ██║  ██║    ██║    ██║           herbiewalker            │  │
// │  │   ███████║    ██║ █╗ ██║──●──●──●──┐                       │  │
// │  │   ██╔══██║    ██║███╗██║           │                       │  │
// │  ●   ██║  ██║    ╚███╔███╔╝           ●───⏣  code · tools     ●  │
// │  │   ╚═╝  ╚═╝     ╚══╝╚══╝                    homelab         │  │
// │  │                                                            │  │
// │  └●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┘  │
// ╰──────────────────────────────────────────────────────────────────╯
package app.tryst.data.repository

import app.tryst.core.session.SessionManager
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.media.EncryptedMediaStore
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Singleton
class PartnerRepository @Inject constructor(
    private val session: SessionManager,
    private val mediaStore: EncryptedMediaStore,
) {
    private val dao get() = session.database().partnerDao()

    fun observeActive(): Flow<List<PartnerEntity>> = dao.observeActive()

    fun observeArchived(): Flow<List<PartnerEntity>> = dao.observeArchived()

    suspend fun upsert(partner: PartnerEntity) = dao.upsert(partner)

    suspend fun getById(id: String): PartnerEntity? = dao.getById(id)

    suspend fun getAll(): List<PartnerEntity> = dao.getAll()

    suspend fun archive(id: String, now: Long = System.currentTimeMillis()) = dao.archive(id, now)

    suspend fun unarchive(id: String, now: Long = System.currentTimeMillis()) = dao.unarchive(id, now)

    // --- Partner photo (encrypted blob in app-internal storage; id stored as Partner.photoMediaId) ---

    /** Encrypts [source] to a new blob and returns its media id. */
    suspend fun savePhoto(source: InputStream): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        mediaStore.save(id, source)
        id
    }

    fun openPhoto(id: String): InputStream = mediaStore.open(id)

    /**
     * Deletes an avatar blob, but only when nothing else owns it. An avatar can point at a photo that also lives in an
     * album or on a tryst, and replacing the avatar must never delete that photo (it keeps its date and details).
     */
    suspend fun deletePhoto(id: String) {
        val db = session.database()
        val shared = db.personPhotoDao().getByBlobId(id) != null || db.mediaDao().countById(id) > 0
        if (!shared) mediaStore.delete(id)
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
