package app.tryst.ui.common

import app.tryst.data.db.entity.PartnerEntity
import java.time.Instant
import java.time.LocalDate
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

    fun dayOfMonth(epochMillis: Long): String = zoned(epochMillis).dayOfMonth.toString()

    fun monthShort(epochMillis: Long): String =
        zoned(epochMillis).format(monthFormatter).uppercase(Locale.getDefault())

    /** "Today" / "Yesterday" / "Monday, Jun 12" / "Jun 12, 2025" — used for list section headers. */
    fun relativeDay(epochMillis: Long): String {
        val date = zoned(epochMillis).toLocalDate()
        val today = LocalDate.now()
        return when {
            date.isEqual(today) -> "Today"
            date.isEqual(today.minusDays(1)) -> "Yesterday"
            date.year == today.year -> date.format(sameYearFormatter)
            else -> date.format(otherYearFormatter)
        }
    }

    private val monthFormatter = DateTimeFormatter.ofPattern("MMM")
    private val sameYearFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")
    private val otherYearFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    private fun zoned(epochMillis: Long) =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
}
