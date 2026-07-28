package app.tryst.data.gallery

import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.relation.EncounterWithDetails
import app.tryst.data.filter.EncounterFilter
import app.tryst.data.search.CatalogLabels
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryPhotosTest {

    private val zone = ZoneOffset.UTC

    private fun epoch(dateTime: LocalDateTime): Long = dateTime.toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun partner(id: String, name: String? = id) = PartnerEntity(id, name, isAnonymous = name == null, color = null, note = null, archivedAt = null, createdAt = 0, updatedAt = 0)

    private fun media(id: String, encounterId: String, createdAt: Long = 0) = MediaEntity(id = id, encounterId = encounterId, encFilePath = id, mimeType = "image/jpeg", createdAt = createdAt)

    private fun encounter(
        id: String,
        at: LocalDateTime,
        rating: Int? = null,
        note: String? = null,
        partners: List<PartnerEntity> = emptyList(),
        media: List<MediaEntity> = emptyList(),
    ) = EncounterWithDetails(
        encounter = EncounterEntity(
            id = id,
            startAt = epoch(at),
            durationMin = null,
            note = note,
            satisfactionRating = rating,
            mood = null,
            initiator = null,
            protectionUsed = emptySet(),
            practicesPerformed = null,
            practicesReceived = null,
            positions = null,
            contexts = null,
            occasions = null,
            kinks = null,
            toys = null,
            createdAt = 0,
            updatedAt = 0,
        ),
        partners = partners,
        positions = emptyList(),
        tags = emptyList(),
        media = media,
        location = null,
    )

    private fun build(
        encounters: List<EncounterWithDetails>,
        layout: GalleryLayout = GalleryLayout.SQUARE_GRID,
        sort: GallerySort = GallerySort.NEWEST,
        filter: EncounterFilter = EncounterFilter(),
        query: String = "",
    ) = GalleryPhotos.build(encounters, filter, query, CatalogLabels.EMPTY, layout, sort, zone)

    @Test
    fun onlyEncountersWithMediaContributeAndEachPhotoIsOneItem() {
        val log = listOf(
            encounter("a", LocalDateTime.of(2026, 5, 1, 12, 0), media = listOf(media("a1", "a"), media("a2", "a"))),
            encounter("b", LocalDateTime.of(2026, 5, 2, 12, 0)), // no media → contributes nothing
        )
        val photos = build(log).photos
        assertEquals(2, photos.size)
        assertEquals(setOf("a1", "a2"), photos.map { it.id }.toSet())
    }

    @Test
    fun newestAndOldestOrderByTrystDate() {
        val log = listOf(
            encounter("old", LocalDateTime.of(2026, 1, 1, 12, 0), media = listOf(media("p_old", "old"))),
            encounter("new", LocalDateTime.of(2026, 6, 1, 12, 0), media = listOf(media("p_new", "new"))),
        )
        assertEquals(listOf("p_new", "p_old"), build(log, sort = GallerySort.NEWEST).photos.map { it.id })
        assertEquals(listOf("p_old", "p_new"), build(log, sort = GallerySort.OLDEST).photos.map { it.id })
    }

    @Test
    fun justifiedDateGroupsByMonthInOrder() {
        val log = listOf(
            encounter("may", LocalDateTime.of(2026, 5, 10, 12, 0), media = listOf(media("m1", "may"))),
            encounter("jun", LocalDateTime.of(2026, 6, 10, 12, 0), media = listOf(media("j1", "jun"))),
        )
        val sections = build(log, layout = GalleryLayout.JUSTIFIED_DATE, sort = GallerySort.NEWEST).sections
        assertEquals(
            listOf(GalleryGroup.Month(2026, 6), GalleryGroup.Month(2026, 5)),
            sections.map { it.group },
        )
    }

    @Test
    fun byPartnerPutsAPhotoUnderEachPartnerAndSoloLast() {
        val alex = partner("alex", "Alex")
        val sam = partner("sam", "Sam")
        val log = listOf(
            encounter("threesome", LocalDateTime.of(2026, 5, 3, 12, 0), partners = listOf(alex, sam), media = listOf(media("t1", "threesome"))),
            encounter("solo", LocalDateTime.of(2026, 5, 2, 12, 0), media = listOf(media("s1", "solo"))),
        )
        val sections = build(log, layout = GalleryLayout.BY_PARTNER).sections
        // The threesome photo appears under both partners; the solo photo lands in a trailing Solo section.
        assertEquals(GalleryGroup.Partner("alex", "Alex"), sections[0].group)
        assertEquals(GalleryGroup.Partner("sam", "Sam"), sections[1].group)
        assertEquals(GalleryGroup.Solo, sections.last().group)
        assertTrue(sections[0].photos.single().id == "t1" && sections[1].photos.single().id == "t1")
        // The flat viewer list de-duplicates: two source photos, not three.
        assertEquals(listOf("t1", "s1"), build(log, layout = GalleryLayout.BY_PARTNER).photos.map { it.id })
    }

    @Test
    fun structuredFilterNarrowsPhotos() {
        val alex = partner("alex", "Alex")
        val log = listOf(
            encounter("withAlex", LocalDateTime.of(2026, 5, 1, 12, 0), partners = listOf(alex), media = listOf(media("wa", "withAlex"))),
            encounter("solo", LocalDateTime.of(2026, 5, 2, 12, 0), media = listOf(media("so", "solo"))),
        )
        val photos = build(log, filter = EncounterFilter(partnerIds = setOf("alex"))).photos
        assertEquals(listOf("wa"), photos.map { it.id })
    }

    @Test
    fun textQueryMatchesPartnerName() {
        val alex = partner("alex", "Alex")
        val log = listOf(
            encounter("withAlex", LocalDateTime.of(2026, 5, 1, 12, 0), partners = listOf(alex), media = listOf(media("wa", "withAlex"))),
            encounter("solo", LocalDateTime.of(2026, 5, 2, 12, 0), note = "beach", media = listOf(media("so", "solo"))),
        )
        assertEquals(listOf("wa"), build(log, query = "alex").photos.map { it.id })
        assertEquals(listOf("so"), build(log, query = "beach").photos.map { it.id })
    }

    @Test
    fun photoCarriesTrystContext() {
        val alex = partner("alex", "Alex")
        val log = listOf(
            encounter("e", LocalDateTime.of(2026, 5, 1, 9, 30), rating = 5, partners = listOf(alex), media = listOf(media("p", "e"))),
        )
        val photo = build(log).photos.single()
        assertEquals("e", photo.encounterId)
        assertEquals(5, photo.rating)
        assertEquals(listOf("Alex"), photo.partnerNames)
        assertEquals(LocalDate.of(2026, 5, 1), java.time.Instant.ofEpochMilli(photo.takenAt).atZone(zone).toLocalDate())
    }
}
