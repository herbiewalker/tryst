package app.tryst.ui.common

import app.tryst.data.db.entity.PartnerEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Display-formatting helpers for the UI layer. */
object Format {

    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    fun dateTime(epochMillis: Long): String = zoned(epochMillis).format(dateTimeFormatter)

    fun date(epochMillis: Long): String = zoned(epochMillis).format(dateFormatter)

    fun time(epochMillis: Long): String = zoned(epochMillis).format(timeFormatter)

    /** Title-cases an enum constant: PREP -> "Prep", BIRTH_CONTROL -> "Birth control". */
    fun enumLabel(value: Enum<*>): String =
        value.name.lowercase(Locale.getDefault())
            .replace('_', ' ')
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }

    fun partnerName(partner: PartnerEntity): String =
        partner.displayName?.takeIf { it.isNotBlank() } ?: "Anonymous"

    private fun zoned(epochMillis: Long) =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
}
