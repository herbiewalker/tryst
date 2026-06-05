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
    /** Non-null when soft-archived. */
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)
