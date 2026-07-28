package app.tryst.data.media

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoMetadataTest {

    private val utc = ZoneOffset.UTC

    @Test
    fun parsesExifDateTimeToEpochMillis() {
        val expected = LocalDateTime.of(2026, 5, 1, 9, 30, 15).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(expected, PhotoMetadata.parseExifDateTime("2026:05:01 09:30:15", utc))
    }

    @Test
    fun badOrMissingDateTimeIsNull() {
        assertNull(PhotoMetadata.parseExifDateTime(null, utc))
        assertNull(PhotoMetadata.parseExifDateTime("", utc))
        assertNull(PhotoMetadata.parseExifDateTime("not a date", utc))
        assertNull(PhotoMetadata.parseExifDateTime("2026-05-01T09:30:15", utc)) // ISO, not EXIF format
    }

    @Test
    fun cameraJoinsMakeAndModelAndDropsRedundantMake() {
        assertEquals("Google Pixel 9", PhotoMetadata.camera("Google", "Pixel 9"))
        assertEquals("Pixel 9 Pro", PhotoMetadata.camera("Pixel", "Pixel 9 Pro")) // model already carries make
        assertEquals("Canon", PhotoMetadata.camera("Canon", null))
        assertEquals("SM-G998B", PhotoMetadata.camera(null, "SM-G998B"))
        assertNull(PhotoMetadata.camera(null, null))
        assertNull(PhotoMetadata.camera("  ", ""))
    }
}
