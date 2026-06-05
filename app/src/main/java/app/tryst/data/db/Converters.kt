package app.tryst.data.db

import androidx.room.TypeConverter
import app.tryst.data.db.entity.Initiator
import app.tryst.data.db.entity.Mood
import app.tryst.data.db.entity.Orgasm
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

    private companion object {
        const val SEP = ","
    }
}
