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
package app.tryst.data.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tryst.core.prefs.GeneralPreferences
import app.tryst.core.security.BiometricVault
import app.tryst.core.security.Vault
import app.tryst.core.session.SessionManager
import app.tryst.data.db.TrystDatabase
import app.tryst.data.db.TrystDatabaseFactory
import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.repository.EncounterRepository
import app.tryst.data.repository.PartnerRepository
import app.tryst.data.repository.PersonPhotoRepository
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An avatar may point straight at a photo that also lives in an album or on a tryst. Replacing or deleting that
 * avatar must never destroy the photo, and deleting the photo must never leave the avatar dangling.
 */
@RunWith(AndroidJUnit4::class)
class AvatarSharingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pin = "135790"
    private lateinit var manager: SessionManager
    private lateinit var store: EncryptedMediaStore
    private lateinit var partners: PartnerRepository
    private lateinit var albums: PersonPhotoRepository
    private lateinit var encounters: EncounterRepository

    @Before
    fun setUp() {
        reset()
        manager = SessionManager(context, Vault(context), TrystDatabaseFactory(context), BiometricVault(context), GeneralPreferences(context))
        runBlocking { manager.setupPin(pin) }
        store = EncryptedMediaStore(context, manager)
        partners = PartnerRepository(manager, store)
        albums = PersonPhotoRepository(manager, store)
        encounters = EncounterRepository(manager, store)
    }

    @After
    fun tearDown() {
        if (::manager.isInitialized) manager.lock()
        reset()
    }

    private fun reset() {
        Vault(context).wipe()
        BiometricVault(context).disable()
        context.deleteDatabase(TrystDatabase.NAME)
        File(context.filesDir, "media").deleteRecursively()
    }

    private suspend fun partner(photo: String? = null): PartnerEntity {
        val p = PartnerEntity(id = "p1", displayName = "Sam", isAnonymous = false, color = null, note = null, archivedAt = null, createdAt = 1, updatedAt = 1, photoMediaId = photo)
        partners.upsert(p)
        return p
    }

    private fun bytes(tag: String) = ByteArrayInputStream(tag.repeat(500).toByteArray())

    @Test
    fun replacingAnAvatarKeepsAnAlbumPhotoItPointedAt() = runBlocking {
        val album = albums.add("partner", "p1", bytes("A"), now = 1_000L)
        val p = partner(photo = album.mediaBlobId)

        partners.deletePhoto(p.photoMediaId!!) // what the editor does when the avatar is replaced

        assertTrue("an album photo must survive losing its avatar role", store.fileFor(album.mediaBlobId).exists())
        assertEquals(1_000L, albums.getForOwner("partner", "p1").single().addedAt)
    }

    @Test
    fun replacingAnAvatarKeepsATrystPhotoItPointedAt() = runBlocking {
        val now = System.currentTimeMillis()
        encounters.save(EncounterEntity(id = "e1", startAt = now, createdAt = now, updatedAt = now))
        val media = encounters.attachMedia("e1", "image/jpeg", bytes("B"))
        partner(photo = media.id)

        partners.deletePhoto(media.id)

        assertTrue(store.fileFor(media.id).exists())
    }

    @Test
    fun anOrphanAvatarBlobIsStillCleanedUp() = runBlocking {
        val orphan = partners.savePhoto(bytes("C"))
        partners.deletePhoto(orphan)
        assertFalse(store.fileFor(orphan).exists())
    }

    @Test
    fun deletingTheAlbumPhotoClearsTheAvatarPointer() = runBlocking {
        val album = albums.add("partner", "p1", bytes("D"))
        partner(photo = album.mediaBlobId)

        albums.delete(album)

        assertNull(partners.getById("p1")!!.photoMediaId)
    }

    @Test
    fun deletingATrystPhotoClearsTheAvatarPointer() = runBlocking {
        val now = System.currentTimeMillis()
        encounters.save(EncounterEntity(id = "e1", startAt = now, createdAt = now, updatedAt = now))
        val media = encounters.attachMedia("e1", "image/jpeg", bytes("E"))
        partner(photo = media.id)

        encounters.deleteMedia(media)

        assertNull(partners.getById("p1")!!.photoMediaId)
    }

    @Test
    fun copyingAPhotoIntoAnAlbumKeepsItsDateFavouriteAndCaption() = runBlocking {
        val copy = albums.add("partner", "p1", bytes("F"), now = 1_700_000_000_000L, favorite = true, caption = "Lakeside")
        val row = albums.getForOwner("partner", "p1").single { it.id == copy.id }
        assertEquals(1_700_000_000_000L, row.addedAt)
        assertTrue(row.favorite)
        assertEquals("Lakeside", row.caption)
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
