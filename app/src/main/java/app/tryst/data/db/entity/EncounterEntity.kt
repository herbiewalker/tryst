package app.tryst.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "encounters",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("locationId"), Index("startAt")],
)
data class EncounterEntity(
    @PrimaryKey val id: String,
    /** Epoch millis of when the encounter started. */
    val startAt: Long,
    val durationMin: Int? = null,
    val note: String? = null,
    /** e.g. 1..5; null if unrated. */
    val satisfactionRating: Int? = null,
    /** Legacy (M3) — superseded by [orgasmCountSelf]/[orgasmCountPartner]; kept for migration. */
    val orgasm: Orgasm? = null,
    val mood: Mood? = null,
    val initiator: Initiator? = null,
    val protectionUsed: Set<Protection> = emptySet(),
    // --- added in schema v2 ---
    /** Number of orgasms the user had this session. */
    val orgasmCountSelf: Int? = null,
    /** Legacy single partner-orgasm count (M3); superseded by [partnerOrgasms]. Kept for migration. */
    val orgasmCountPartner: Int? = null,
    /**
     * Per-orgasm ejaculation: orgasm index (0-based) -> finish-location IDs (multi-select). Each id is
     * each a "custom:<id>" ref to an [EjaculationLocationEntity] row (id-based & user-owned since v12).
     */
    val ejaculationLocations: Map<Int, Set<String>>? = null,
    /** Act ids gave/received — each a "custom:<id>" ref to an [ActEntity] row (categories are user-owned since v12). */
    val practicesPerformed: Set<String>? = null,
    val practicesReceived: Set<String>? = null,
    // --- added in schema v3 ---
    /** Selected position ids — each a "custom:<id>" ref to a [PositionEntity] row (user-owned since v12). */
    val positions: Set<String>? = null,
    // --- added in schema v4 ---
    /** Selected kink ids — each a "custom:<id>" ref to a [KinkEntity] row (id-based since v9; user-owned since v12). */
    val kinks: Set<String>? = null,
    /** Place (built-in [Place]). */
    val contexts: Set<Place>? = null,
    /** Selected toy ids — each a "custom:<id>" ref to a [ToyEntity] row (id-based since v11; user-owned since v12). */
    val toys: Set<String>? = null,
    // --- added in schema v5 ---
    /** Selected occasion ids — each a "custom:<id>" ref to an [OccasionEntity] row (id-based & user-owned since v12). */
    val occasions: Set<String>? = null,
    // --- added in schema v6 ---
    /** Per-partner orgasm counts: partnerId -> count. */
    val partnerOrgasms: Map<String, Int>? = null,
    // ---------------------------
    val locationId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
