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
    /** Number of orgasms the partner(s) had this session. */
    val orgasmCountPartner: Int? = null,
    val ejaculationLocations: Set<EjaculationLocation>? = null,
    val practicesPerformed: Set<Practice>? = null,
    val practicesReceived: Set<Practice>? = null,
    // --- added in schema v3 ---
    /** Selected position IDs: a built-in [Position] name, or "custom:<uuid>" for user-defined. */
    val positions: Set<String>? = null,
    // --- added in schema v4 ---
    val kinks: Set<Kink>? = null,
    val contexts: Set<Setting>? = null,
    val toys: Set<ToyType>? = null,
    // ---------------------------
    val locationId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
