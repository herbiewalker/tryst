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
package app.tryst.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.tryst.data.db.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    /** Clears the avatar pointer when the photo it points at is deleted. */
    @Query("UPDATE profile SET photoMediaId = NULL WHERE photoMediaId = :blobId")
    suspend fun clearPhotoRef(blobId: String)

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("SELECT * FROM profile WHERE id = :id")
    fun observe(id: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun get(id: String): ProfileEntity?
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
