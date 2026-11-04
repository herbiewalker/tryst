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
import app.tryst.data.db.entity.ProfileEntity
import app.tryst.data.media.EncryptedMediaStore
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * The single self-profile row. Mirrors [PartnerRepository]'s photo handling — the profile avatar is an
 * encrypted blob whose id is stored in [ProfileEntity.photoMediaId].
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val session: SessionManager,
    private val mediaStore: EncryptedMediaStore,
) {
    private val dao get() = session.database().profileDao()

    fun observe(): Flow<ProfileEntity?> = dao.observe(ProfileEntity.SELF_ID)

    suspend fun get(): ProfileEntity? = dao.get(ProfileEntity.SELF_ID)

    suspend fun upsert(profile: ProfileEntity) = dao.upsert(profile)

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
