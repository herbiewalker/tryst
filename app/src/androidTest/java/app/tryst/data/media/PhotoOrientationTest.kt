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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tryst.core.prefs.GeneralPreferences
import app.tryst.core.security.BiometricVault
import app.tryst.core.security.Vault
import app.tryst.core.session.SessionManager
import app.tryst.data.db.TrystDatabase
import app.tryst.data.db.TrystDatabaseFactory
import app.tryst.data.repository.PersonPhotoRepository
import app.tryst.ui.common.MediaImages
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Old photos store their pixels sideways with an EXIF orientation tag. Tryst must draw them upright, and rotating or
 * cropping must work on the upright picture and leave no stale tag behind.
 */
@RunWith(AndroidJUnit4::class)
class PhotoOrientationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var manager: SessionManager
    private lateinit var store: EncryptedMediaStore
    private lateinit var albums: PersonPhotoRepository

    @Before
    fun setUp() {
        reset()
        manager = SessionManager(context, Vault(context), TrystDatabaseFactory(context), BiometricVault(context), GeneralPreferences(context))
        runBlocking { manager.setupPin("135790") }
        store = EncryptedMediaStore(context, manager)
        albums = PersonPhotoRepository(manager, store)
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

    /** A 2 by 1 bitmap: red on the left, blue on the right. */
    private fun redBlue(): Bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888).apply {
        setPixel(0, 0, Color.RED)
        setPixel(1, 0, Color.BLUE)
    }

    private fun isRed(p: Int) = Color.red(p) > 200 && Color.blue(p) < 60
    private fun isBlue(p: Int) = Color.blue(p) > 200 && Color.red(p) < 60

    @Test
    fun everyOrientationPlacesPixelsWhereTheTagSays() {
        val src = redBlue()
        // Rotate 90 clockwise: a column, red on top.
        ExifOrientation.upright(src, ExifInterface.ORIENTATION_ROTATE_90).let {
            assertEquals(1 to 2, it.width to it.height)
            assertTrue(isRed(it.getPixel(0, 0)))
            assertTrue(isBlue(it.getPixel(0, 1)))
        }
        // Rotate 270 (90 counter-clockwise): a column, blue on top.
        ExifOrientation.upright(src, ExifInterface.ORIENTATION_ROTATE_270).let {
            assertEquals(1 to 2, it.width to it.height)
            assertTrue(isBlue(it.getPixel(0, 0)))
            assertTrue(isRed(it.getPixel(0, 1)))
        }
        // Rotate 180 and horizontal flip both swap the two pixels of a one-row image.
        for (o in listOf(ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_HORIZONTAL)) {
            val it = ExifOrientation.upright(src, o)
            assertEquals(2 to 1, it.width to it.height)
            assertTrue("orientation $o", isBlue(it.getPixel(0, 0)))
            assertTrue("orientation $o", isRed(it.getPixel(1, 0)))
        }
        // Flip vertical leaves a single row alone.
        ExifOrientation.upright(src, ExifInterface.ORIENTATION_FLIP_VERTICAL).let {
            assertTrue(isRed(it.getPixel(0, 0)))
            assertTrue(isBlue(it.getPixel(1, 0)))
        }
        // Transpose: a column, red on top. Transverse: a column, blue on top.
        ExifOrientation.upright(src, ExifInterface.ORIENTATION_TRANSPOSE).let {
            assertTrue(isRed(it.getPixel(0, 0)))
            assertTrue(isBlue(it.getPixel(0, 1)))
        }
        ExifOrientation.upright(src, ExifInterface.ORIENTATION_TRANSVERSE).let {
            assertTrue(isBlue(it.getPixel(0, 0)))
            assertTrue(isRed(it.getPixel(0, 1)))
        }
        // Normal and unknown values return the very same bitmap.
        assertTrue(ExifOrientation.upright(src, ExifInterface.ORIENTATION_NORMAL) === src)
        assertTrue(ExifOrientation.upright(src, 0) === src)
    }

    /** A wide 8 by 4 JPEG whose stored pixels need turning (orientation 6, 90 clockwise) to be upright. */
    private fun sidewaysJpeg(): ByteArray {
        val bmp = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888)
        for (x in 0 until 8) for (y in 0 until 4) bmp.setPixel(x, y, if (x < 4) Color.RED else Color.BLUE)
        val raw = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 100, it) }.toByteArray()
        val file = File.createTempFile("orient", ".jpg", context.cacheDir)
        try {
            file.writeBytes(raw)
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    @Test
    fun theDecoderDrawsASidewaysPhotoUpright() = runBlocking {
        val image = MediaImages.decodeSampled(0) { ByteArrayInputStream(sidewaysJpeg()) }
        assertNotNull(image)
        val bmp = image!!.asAndroidBitmap()
        // 8 by 4 stored, turned a quarter: 4 wide, 8 tall, with the red half on top.
        assertEquals(4 to 8, bmp.width to bmp.height)
        assertTrue(isRed(bmp.getPixel(2, 1)))
        assertTrue(isBlue(bmp.getPixel(2, 6)))
    }

    @Test
    fun rotatingAnAlbumPhotoWorksOnTheUprightPictureAndLeavesNoTag() = runBlocking {
        val photo = albums.add("partner", "p1", ByteArrayInputStream(sidewaysJpeg()))
        // Upright it is 4 wide by 8 tall; a quarter turn makes it 8 wide by 4 tall.
        assertTrue(albums.rotate(photo.mediaBlobId, 90))
        val bytes = albums.openBlob(photo.mediaBlobId).use { it.readBytes() }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(8 to 4, decoded.width to decoded.height)
        assertTrue("the edit bakes the orientation in, so no turning tag may remain", ExifOrientation.isUpright(ExifOrientation.read(ByteArrayInputStream(bytes))))
    }

    @Test
    fun rotatingSomethingThatIsNotAnImageFailsAndChangesNothing() = runBlocking {
        val junk = "not an image at all".toByteArray()
        val photo = albums.add("partner", "p1", ByteArrayInputStream(junk))
        assertTrue(!albums.rotate(photo.mediaBlobId, 90))
        assertEquals(junk.toList(), albums.openBlob(photo.mediaBlobId).use { it.readBytes() }.toList())
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
