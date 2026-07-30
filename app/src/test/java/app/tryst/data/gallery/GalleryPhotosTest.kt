package app.tryst.data.gallery

import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.MediaEntity
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.db.entity.PersonPhotoEntity
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

    private fun media(id: String, encounterId: String, createdAt: Long = 0, favorite: Boolean = false) = MediaEntity(id = id, encounterId = encounterId, encFilePath = id, mimeType = "image/jpeg", createdAt = createdAt, favorite = favorite)

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
        onlyFavorites: Boolean = false,
        personPhotos: List<PersonPhotoEntity> = emptyList(),
        partnerNamesById: Map<String, String?> = emptyMap(),
        profileDisplayName: String? = null,
    ) = GalleryPhotos.build(
        encounters, filter, query, CatalogLabels.EMPTY, layout, sort, zone, onlyFavorites,
        personPhotos, partnerNamesById, profileDisplayName,
    )

    private fun portrait(id: String, ownerKind: String, ownerId: String, blobId: String = id, addedAt: Long = 0L) = PersonPhotoEntity(id = id, ownerKind = ownerKind, ownerId = ownerId, mediaBlobId = blobId, addedAt = addedAt)

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
    fun byPartnerPutsAPhotoUnderEachPartnerAndSoloUnderSelf() {
        val alex = partner("alex", "Alex")
        val sam = partner("sam", "Sam")
        val log = listOf(
            encounter("threesome", LocalDateTime.of(2026, 5, 3, 12, 0), partners = listOf(alex, sam), media = listOf(media("t1", "threesome"))),
            encounter("solo", LocalDateTime.of(2026, 5, 2, 12, 0), media = listOf(media("s1", "solo"))),
        )
        // The threesome photo appears under both partners; the solo photo attributes to self ("You")
        // and shows under a self section — not a trailing generic Solo bucket.
        val sections = build(log, layout = GalleryLayout.BY_PARTNER, profileDisplayName = "You").sections
        assertEquals(GalleryGroup.Partner("alex", "Alex"), sections[0].group)
        assertEquals(GalleryGroup.Partner("sam", "Sam"), sections[1].group)
        assertEquals(GalleryGroup.Partner(GalleryPhotos.SELF_PARTNER_ID, "You"), sections.last().group)
        assertTrue(sections[0].photos.single().id == "t1" && sections[1].photos.single().id == "t1")
        assertEquals("s1", sections.last().photos.single().id)
        // The flat viewer list de-duplicates: two source photos, not three.
        assertEquals(listOf("t1", "s1"), build(log, layout = GalleryLayout.BY_PARTNER).photos.map { it.id })
    }

    @Test
    fun soloEncounterPhotoAttributesToSelfPartnerId() {
        val log = listOf(
            encounter("solo", LocalDateTime.of(2026, 5, 2, 12, 0), media = listOf(media("s1", "solo"))),
        )
        val photo = build(log, profileDisplayName = "Sam").photos.single()
        assertEquals(listOf(GalleryPhotos.SELF_PARTNER_ID), photo.partners.map { it.id })
        assertEquals(listOf("Sam"), photo.partnerNames)
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
    fun onlyFavoritesKeepsStarredPhotos() {
        val log = listOf(
            encounter(
                "e",
                LocalDateTime.of(2026, 5, 1, 12, 0),
                media = listOf(media("fav", "e", favorite = true), media("plain", "e")),
            ),
        )
        assertEquals(setOf("fav", "plain"), build(log).photos.map { it.id }.toSet())
        assertEquals(listOf("fav"), build(log, onlyFavorites = true).photos.map { it.id })
    }

    @Test
    fun peopleLayoutYieldsNoPhotos() {
        val log = listOf(
            encounter("e", LocalDateTime.of(2026, 5, 1, 12, 0), media = listOf(media("p", "e"))),
        )
        val result = build(log, layout = GalleryLayout.PEOPLE)
        assertTrue(result.photos.isEmpty())
        assertTrue(result.sections.isEmpty())
    }

    @Test
    fun mosaicGroupsByMonthLikeTheDateGrid() {
        val log = listOf(
            encounter("may", LocalDateTime.of(2026, 5, 10, 12, 0), media = listOf(media("m1", "may"))),
            encounter("jun", LocalDateTime.of(2026, 6, 10, 12, 0), media = listOf(media("j1", "jun"))),
        )
        val sections = build(log, layout = GalleryLayout.MOSAIC, sort = GallerySort.NEWEST).sections
        assertEquals(
            listOf(GalleryGroup.Month(2026, 6), GalleryGroup.Month(2026, 5)),
            sections.map { it.group },
        )
    }

    @Test
    fun portraitsMergeWithEncounterPhotosAndAttributeToTheOwner() {
        val alex = partner("alex", "Alex")
        val log = listOf(
            encounter("e", LocalDateTime.of(2026, 5, 1, 12, 0), partners = listOf(alex), media = listOf(media("enc1", "e"))),
        )
        val portraits = listOf(
            portrait(id = "pp1", ownerKind = "partner", ownerId = "alex", blobId = "blob-a", addedAt = epoch(LocalDateTime.of(2026, 5, 2, 9, 0))),
            portrait(id = "pp2", ownerKind = "profile", ownerId = "self", blobId = "blob-s", addedAt = epoch(LocalDateTime.of(2026, 5, 3, 9, 0))),
        )
        val photos = build(
            log,
            personPhotos = portraits,
            partnerNamesById = mapOf("alex" to "Alex"),
            profileDisplayName = "You",
        ).photos
        // Newest first: profile portrait (May 3) → partner portrait (May 2) → encounter (May 1).
        assertEquals(listOf("blob-s", "blob-a", "enc1"), photos.map { it.id })
        val self = photos.first { it.id == "blob-s" }
        val partnerPic = photos.first { it.id == "blob-a" }
        assertEquals(listOf("You"), self.partnerNames)
        assertEquals(listOf("Alex"), partnerPic.partnerNames)
        // Portraits carry no encounter link and can't be favourited.
        assertEquals(null, self.encounterId)
        assertTrue(!self.favorite && !partnerPic.favorite)
    }

    @Test
    fun partnerFilterPicksMatchingPortraits() {
        val alex = partner("alex", "Alex")
        val log = listOf(
            encounter("e", LocalDateTime.of(2026, 5, 1, 12, 0), partners = listOf(alex), media = listOf(media("enc1", "e"))),
        )
        val portraits = listOf(
            portrait("pp1", "partner", "alex", "blob-a"),
            portrait("pp2", "partner", "sam", "blob-b"),
            portrait("pp3", "profile", "self", "blob-s"),
        )
        val ids = build(
            log,
            personPhotos = portraits,
            partnerNamesById = mapOf("alex" to "Alex", "sam" to "Sam"),
            filter = EncounterFilter(partnerIds = setOf("alex")),
        ).photos.map { it.id }.toSet()
        // The encounter photo (Alex partner) and Alex's portrait qualify; Sam's + profile's do not.
        assertEquals(setOf("enc1", "blob-a"), ids)
    }

    @Test
    fun textQueryNarrowsPortraitsByOwnerName() {
        // Repro of Lens-1 N2: portraits used to bypass applyQuery entirely, so typing "alex"
        // returned Alex's tryst photo AND every other partner's portrait. After the fix, a
        // portrait keeps only if every query token is a substring of the (folded) owner name.
        val alex = partner("alex", "Alex")
        val log = listOf(
            encounter("withAlex", LocalDateTime.of(2026, 5, 1, 12, 0), partners = listOf(alex), media = listOf(media("enc-alex", "withAlex"))),
        )
        val portraits = listOf(
            portrait("pp1", "partner", "alex", "blob-a"),
            portrait("pp2", "partner", "sam", "blob-b"),
            portrait("pp3", "profile", "self", "blob-s"),
        )
        val partnerNames = mapOf("alex" to "Alex", "sam" to "Sam")
        val queryAlex = build(log, query = "alex", personPhotos = portraits, partnerNamesById = partnerNames, profileDisplayName = "You").photos.map { it.id }.toSet()
        assertEquals(setOf("enc-alex", "blob-a"), queryAlex)
        // Case + accent fold: "SAM" query hits Sam's portrait, self ("You") stays out.
        val querySam = build(log, query = "SAM", personPhotos = portraits, partnerNamesById = partnerNames, profileDisplayName = "You").photos.map { it.id }.toSet()
        assertEquals(setOf("blob-b"), querySam)
        // A portrait with no owner name (anonymous partner or unnamed profile) is dropped when a query is set.
        val portraitsAnon = listOf(portrait("pp4", "partner", "ghost", "blob-g"))
        val queryX = build(emptyList(), query = "x", personPhotos = portraitsAnon, partnerNamesById = mapOf("ghost" to null)).photos
        assertTrue(queryX.isEmpty())
    }

    @Test
    fun selfDrillIncludesSelfPortraitButNotPartnerPortraits() {
        // Repro of Lens-1 N1: drilling into "You" from People (which the VM emits as
        // includeSolo=true, partnerIds=empty) used to let every partner's portrait through
        // because the guard collapsed to `if (!byPartner && !true.not())` = always false.
        val portraits = listOf(
            portrait("pp1", "partner", "alex", "blob-a"),
            portrait("pp2", "partner", "sam", "blob-b"),
            portrait("pp3", "profile", "self", "blob-s"),
        )
        val ids = build(
            emptyList(),
            filter = EncounterFilter(partnerIds = emptySet(), includeSolo = true),
            personPhotos = portraits,
            partnerNamesById = mapOf("alex" to "Alex", "sam" to "Sam"),
            profileDisplayName = "You",
        ).photos.map { it.id }.toSet()
        assertEquals(setOf("blob-s"), ids)
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
