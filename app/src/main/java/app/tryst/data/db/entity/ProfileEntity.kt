package app.tryst.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The user's own profile — a single row (id [SELF_ID]) holding their photo + demographics, mirroring
 * the partner fields so "you" and a partner read the same way. Lives in the encrypted DB (the data is
 * sensitive); the photo is an encrypted blob referenced by [photoMediaId], like a partner avatar.
 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String = SELF_ID,
    val displayName: String? = null,
    /** Media id of an encrypted profile photo; null if none. */
    val photoMediaId: String? = null,
    val sex: Sex? = null,
    val gender: Gender? = null,
    /** Date of birth as epoch millis (date only); age is derived for display. */
    val birthDate: Long? = null,
    val ethnicity: Ethnicity? = null,
    /** Free-text height (e.g. "5'10\"" or "178 cm"). */
    val height: String? = null,
    val bodyType: BodyType? = null,
    /** Free-text location / city. */
    val location: String? = null,
    /** Optional free-text "about you" note. */
    val note: String? = null,
    val updatedAt: Long = 0L,
) {
    companion object {
        /** The single profile row's fixed primary key. */
        const val SELF_ID = "self"
    }
}
