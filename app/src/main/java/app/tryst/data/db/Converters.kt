package app.tryst.data.db

import androidx.room.TypeConverter
import app.tryst.data.db.entity.EjaculationLocation
import app.tryst.data.db.entity.Gender
import app.tryst.data.db.entity.Initiator
import app.tryst.data.db.entity.Kink
import app.tryst.data.db.entity.Mood
import app.tryst.data.db.entity.Occasion
import app.tryst.data.db.entity.Orgasm
import app.tryst.data.db.entity.Protection
import app.tryst.data.db.entity.RelationshipType
import app.tryst.data.db.entity.Setting
import app.tryst.data.db.entity.Sex
import app.tryst.data.db.entity.ToyType

/**
 * Room type converters. Enum-set parsing skips unrecognized names so values that move between
 * categories (or are renamed) never crash older rows — they're simply dropped on read.
 */
class Converters {

    @TypeConverter fun initiatorToString(value: Initiator?): String? = value?.name
    @TypeConverter fun stringToInitiator(value: String?): Initiator? =
        value?.let { runCatching { Initiator.valueOf(it) }.getOrNull() }

    @TypeConverter fun moodToString(value: Mood?): String? = value?.name
    @TypeConverter fun stringToMood(value: String?): Mood? =
        value?.let { runCatching { Mood.valueOf(it) }.getOrNull() }

    @TypeConverter fun orgasmToString(value: Orgasm?): String? = value?.name
    @TypeConverter fun stringToOrgasm(value: String?): Orgasm? =
        value?.let { runCatching { Orgasm.valueOf(it) }.getOrNull() }

    @TypeConverter fun sexToString(value: Sex?): String? = value?.name
    @TypeConverter fun stringToSex(value: String?): Sex? =
        value?.let { runCatching { Sex.valueOf(it) }.getOrNull() }

    @TypeConverter fun genderToString(value: Gender?): String? = value?.name
    @TypeConverter fun stringToGender(value: String?): Gender? =
        value?.let { runCatching { Gender.valueOf(it) }.getOrNull() }

    @TypeConverter fun relationshipToString(value: RelationshipType?): String? = value?.name
    @TypeConverter fun stringToRelationship(value: String?): RelationshipType? =
        value?.let { runCatching { RelationshipType.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun protectionSetToString(value: Set<Protection>): String = value.joinToString(SEP) { it.name }

    @TypeConverter
    fun stringToProtectionSet(value: String): Set<Protection> =
        value.split(SEP).mapNotNull { runCatching { Protection.valueOf(it) }.getOrNull() }.toSet()

    @TypeConverter
    fun ejaculationSetToString(value: Set<EjaculationLocation>?): String? =
        value?.joinToString(SEP) { it.name }

    @TypeConverter
    fun stringToEjaculationSet(value: String?): Set<EjaculationLocation>? =
        value?.toEnumSet { EjaculationLocation.valueOf(it) }

    @TypeConverter
    fun kinkSetToString(value: Set<Kink>?): String? = value?.joinToString(SEP) { it.name }

    @TypeConverter
    fun stringToKinkSet(value: String?): Set<Kink>? = value?.toEnumSet { Kink.valueOf(it) }

    @TypeConverter
    fun settingSetToString(value: Set<Setting>?): String? = value?.joinToString(SEP) { it.name }

    @TypeConverter
    fun stringToSettingSet(value: String?): Set<Setting>? = value?.toEnumSet { Setting.valueOf(it) }

    @TypeConverter
    fun occasionSetToString(value: Set<Occasion>?): String? = value?.joinToString(SEP) { it.name }

    @TypeConverter
    fun stringToOccasionSet(value: String?): Set<Occasion>? = value?.toEnumSet { Occasion.valueOf(it) }

    @TypeConverter
    fun toyTypeSetToString(value: Set<ToyType>?): String? = value?.joinToString(SEP) { it.name }

    @TypeConverter
    fun stringToToyTypeSet(value: String?): Set<ToyType>? = value?.toEnumSet { ToyType.valueOf(it) }

    // Positions are stored as string IDs (built-in enum name, or "custom:<uuid>"); IDs never
    // contain commas.
    @TypeConverter
    fun positionSetToString(value: Set<String>?): String? = value?.joinToString(SEP)

    @TypeConverter
    fun stringToPositionSet(value: String?): Set<String>? = when {
        value == null -> null
        value.isBlank() -> emptySet()
        else -> value.split(SEP).toSet()
    }

    private inline fun <T> String.toEnumSet(parse: (String) -> T): Set<T> =
        if (isBlank()) emptySet()
        else split(SEP).mapNotNull { runCatching { parse(it) }.getOrNull() }.toSet()

    private companion object {
        const val SEP = ","
    }
}
