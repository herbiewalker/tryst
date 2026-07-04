package app.tryst.data.db

import androidx.room.TypeConverter
import app.tryst.data.db.entity.BodyType
import app.tryst.data.db.entity.Ethnicity
import app.tryst.data.db.entity.Gender
import app.tryst.data.db.entity.Initiator
import app.tryst.data.db.entity.Mood
import app.tryst.data.db.entity.Orgasm
import app.tryst.data.db.entity.Place
import app.tryst.data.db.entity.Protection
import app.tryst.data.db.entity.RelationshipType
import app.tryst.data.db.entity.Sex

/**
 * Room type converters. Enum-set parsing skips unrecognized names so values that move between
 * categories (or are renamed) never crash older rows — they're simply dropped on read.
 */
class Converters {

    @TypeConverter fun initiatorToString(value: Initiator?): String? = value?.name

    @TypeConverter fun stringToInitiator(value: String?): Initiator? = value?.let { runCatching { Initiator.valueOf(it) }.getOrNull() }

    @TypeConverter fun moodToString(value: Mood?): String? = value?.name

    @TypeConverter fun stringToMood(value: String?): Mood? = value?.let { runCatching { Mood.valueOf(it) }.getOrNull() }

    @TypeConverter fun orgasmToString(value: Orgasm?): String? = value?.name

    @TypeConverter fun stringToOrgasm(value: String?): Orgasm? = value?.let { runCatching { Orgasm.valueOf(it) }.getOrNull() }

    @TypeConverter fun sexToString(value: Sex?): String? = value?.name

    @TypeConverter fun stringToSex(value: String?): Sex? = value?.let { runCatching { Sex.valueOf(it) }.getOrNull() }

    @TypeConverter fun genderToString(value: Gender?): String? = value?.name

    @TypeConverter fun stringToGender(value: String?): Gender? = value?.let { runCatching { Gender.valueOf(it) }.getOrNull() }

    @TypeConverter fun relationshipToString(value: RelationshipType?): String? = value?.name

    @TypeConverter fun stringToRelationship(value: String?): RelationshipType? = value?.let { runCatching { RelationshipType.valueOf(it) }.getOrNull() }

    @TypeConverter fun ethnicityToString(value: Ethnicity?): String? = value?.name

    @TypeConverter fun stringToEthnicity(value: String?): Ethnicity? = value?.let { runCatching { Ethnicity.valueOf(it) }.getOrNull() }

    @TypeConverter fun bodyTypeToString(value: BodyType?): String? = value?.name

    @TypeConverter fun stringToBodyType(value: String?): BodyType? = value?.let { runCatching { BodyType.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun protectionSetToString(value: Set<Protection>): String = value.joinToString(SEP) { it.name }

    @TypeConverter
    fun stringToProtectionSet(value: String): Set<Protection> = value.split(SEP).mapNotNull { runCatching { Protection.valueOf(it) }.getOrNull() }.toSet()

    // orgasm-index -> finish-location id(s), encoded as "idx=ID1|ID2,idx=ID3" in the same TEXT column.
    // Ids within one orgasm join on '|' (SEP=',' separates entries); each id is a built-in
    // EjaculationLocation name or "custom:<uuid>". Ids never contain '|' or ',', so this round-trips.
    @TypeConverter
    fun ejaculationMapToString(value: Map<Int, Set<String>>?): String? = value?.entries
        ?.filter { it.value.isNotEmpty() }
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(SEP) { (idx, ids) -> "$idx=${ids.joinToString("|")}" }

    @TypeConverter
    fun stringToEjaculationMap(value: String?): Map<Int, Set<String>>? = value?.takeIf { it.isNotBlank() }
        ?.split(SEP)
        ?.mapNotNull { token ->
            val parts = token.split("=", limit = 2)
            val idx = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val ids = parts.getOrNull(1)?.split("|")?.filter { it.isNotBlank() }?.toSet().orEmpty()
            if (ids.isEmpty()) return@mapNotNull null
            idx to ids
        }?.toMap()

    @TypeConverter
    fun partnerOrgasmsToString(value: Map<String, Int>?): String? = value?.entries?.joinToString(SEP) { "${it.key}=${it.value}" }

    @TypeConverter
    fun stringToPartnerOrgasms(value: String?): Map<String, Int>? = value?.takeIf { it.isNotBlank() }
        ?.split(SEP)
        ?.mapNotNull { token ->
            val parts = token.split("=")
            val count = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            (parts.getOrNull(0) ?: return@mapNotNull null) to count
        }?.toMap()

    @TypeConverter
    fun placeSetToString(value: Set<Place>?): String? = value?.joinToString(SEP) { it.name }

    @TypeConverter
    fun stringToPlaceSet(value: String?): Set<Place>? = value?.toEnumSet { Place.valueOf(it) }

    // Shared by every string-id set column — positions, acts (performed/received), kinks, toys, and
    // occasions (id-based since schema v12). Each id
    // is a built-in enum name or "custom:<uuid>", and ids never contain commas, so a plain comma-join
    // round-trips. (Room selects one converter per type, so this single pair serves all of them.)
    @TypeConverter
    fun positionSetToString(value: Set<String>?): String? = value?.joinToString(SEP)

    @TypeConverter
    fun stringToPositionSet(value: String?): Set<String>? = when {
        value == null -> null
        value.isBlank() -> emptySet()
        else -> value.split(SEP).toSet()
    }

    private inline fun <T> String.toEnumSet(parse: (String) -> T): Set<T> = if (isBlank()) {
        emptySet()
    } else {
        split(SEP).mapNotNull { runCatching { parse(it) }.getOrNull() }.toSet()
    }

    private companion object {
        const val SEP = ","
    }
}
