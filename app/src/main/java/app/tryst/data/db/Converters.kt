package app.tryst.data.db

import androidx.room.TypeConverter
import app.tryst.data.db.entity.EjaculationLocation
import app.tryst.data.db.entity.Initiator
import app.tryst.data.db.entity.Mood
import app.tryst.data.db.entity.Orgasm
import app.tryst.data.db.entity.Practice
import app.tryst.data.db.entity.Protection

/** Room type converters for the domain enums and the protection set. */
class Converters {

    @TypeConverter fun initiatorToString(value: Initiator?): String? = value?.name

    @TypeConverter fun stringToInitiator(value: String?): Initiator? =
        value?.let { Initiator.valueOf(it) }

    @TypeConverter fun moodToString(value: Mood?): String? = value?.name

    @TypeConverter fun stringToMood(value: String?): Mood? = value?.let { Mood.valueOf(it) }

    @TypeConverter fun orgasmToString(value: Orgasm?): String? = value?.name

    @TypeConverter fun stringToOrgasm(value: String?): Orgasm? = value?.let { Orgasm.valueOf(it) }

    @TypeConverter
    fun protectionSetToString(value: Set<Protection>): String =
        value.joinToString(SEP) { it.name }

    @TypeConverter
    fun stringToProtectionSet(value: String): Set<Protection> =
        if (value.isBlank()) emptySet()
        else value.split(SEP).map { Protection.valueOf(it) }.toSet()

    // Nullable sets (added in schema v2). null = not recorded (e.g. pre-migration rows).

    @TypeConverter
    fun ejaculationSetToString(value: Set<EjaculationLocation>?): String? =
        value?.joinToString(SEP) { it.name }

    @TypeConverter
    fun stringToEjaculationSet(value: String?): Set<EjaculationLocation>? = when {
        value == null -> null
        value.isBlank() -> emptySet()
        else -> value.split(SEP).map { EjaculationLocation.valueOf(it) }.toSet()
    }

    @TypeConverter
    fun practiceSetToString(value: Set<Practice>?): String? =
        value?.joinToString(SEP) { it.name }

    @TypeConverter
    fun stringToPracticeSet(value: String?): Set<Practice>? = when {
        value == null -> null
        value.isBlank() -> emptySet()
        else -> value.split(SEP).map { Practice.valueOf(it) }.toSet()
    }

    // Positions are stored as string IDs (built-in enum name, or "custom:<uuid>") so the set can
    // include user-defined custom positions. IDs never contain commas, so SEP is safe here.
    @TypeConverter
    fun positionSetToString(value: Set<String>?): String? =
        value?.joinToString(SEP)

    @TypeConverter
    fun stringToPositionSet(value: String?): Set<String>? = when {
        value == null -> null
        value.isBlank() -> emptySet()
        else -> value.split(SEP).toSet()
    }

    private companion object {
        const val SEP = ","
    }
}
