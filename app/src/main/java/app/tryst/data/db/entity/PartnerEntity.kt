package app.tryst.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "partners")
data class PartnerEntity(
    @PrimaryKey val id: String,
    /** Null or blank means an anonymous partner. */
    val displayName: String?,
    val isAnonymous: Boolean,
    /** Optional UI accent color (e.g. "#FF8800"). Local only. */
    val color: String?,
    val note: String?,
    // --- added in schema v5 ---
    val sex: Sex? = null,
    val gender: Gender? = null,
    val relationshipType: RelationshipType? = null,
    /** Media id of an encrypted partner photo (avatar); null if none. */
    val photoMediaId: String? = null,
    // --- added in schema v7 (demographics) ---
    /** Date of birth as epoch millis (date only); age is derived for display. */
    val birthDate: Long? = null,
    val ethnicity: Ethnicity? = null,
    /** Free-text height (e.g. "5'10\"" or "178 cm") — units are the user's choice. */
    val height: String? = null,
    val bodyType: BodyType? = null,
    /** Free-text location / city. */
    val location: String? = null,
    // ---------------------------
    /** Non-null when soft-archived. */
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)
