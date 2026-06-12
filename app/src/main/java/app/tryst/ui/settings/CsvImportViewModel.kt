package app.tryst.ui.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.R
import app.tryst.core.session.SessionManager
import app.tryst.data.backup.Csv
import app.tryst.data.db.entity.EncounterEntity
import app.tryst.data.db.entity.PartnerEntity
import app.tryst.data.repository.EncounterRepository
import app.tryst.data.repository.PartnerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/** The Tryst fields a CSV column can be mapped to. */
enum class CsvField(val label: String, val required: Boolean = false) {
    DATE("Date", required = true),
    TIME("Time (if separate)"),
    PARTNER("Partner"),
    DURATION("Duration (min)"),
    RATING("Rating (1–5)"),
    NOTE("Note"),
}

@HiltViewModel
class CsvImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val partners: PartnerRepository,
    private val encounters: EncounterRepository,
) : ViewModel() {

    var headers by mutableStateOf<List<String>>(emptyList())
        private set
    var rowCount by mutableStateOf(0)
        private set
    var mapping by mutableStateOf<Map<CsvField, Int?>>(emptyMap())
        private set
    var showMapping by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    private var rows: List<List<String>> = emptyList()

    fun suppressAutoLock() = session.suppressNextAutoLock()

    fun parse(uri: Uri) {
        viewModelScope.launch {
            busy = true
            status = null
            try {
                val text = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: throw IllegalStateException("Couldn't open file")
                val table = Csv.parse(text)
                if (table.size < 2) {
                    status = context.getString(R.string.csv_status_no_rows)
                    return@launch
                }
                headers = table.first()
                rows = table.drop(1)
                rowCount = rows.size
                mapping = autoGuess(headers)
                showMapping = true
            } catch (e: Exception) {
                status = context.getString(R.string.csv_status_read_failed, e.message)
            } finally {
                busy = false
            }
        }
    }

    fun setMapping(field: CsvField, columnIndex: Int?) {
        mapping = mapping + (field to columnIndex)
    }

    fun cancel() {
        showMapping = false
        rows = emptyList()
        headers = emptyList()
    }

    fun import() {
        viewModelScope.launch {
            busy = true
            try {
                val (imported, skipped) = doImport()
                status = if (skipped > 0) {
                    context.getString(R.string.csv_import_done_skipped, imported, skipped)
                } else {
                    context.getString(R.string.csv_import_done, imported)
                }
                showMapping = false
            } catch (e: Exception) {
                status = context.getString(R.string.csv_status_import_failed, e.message)
            } finally {
                busy = false
            }
        }
    }

    private suspend fun doImport(): Pair<Int, Int> {
        val dateCol = mapping[CsvField.DATE] ?: return 0 to rowCount
        val now = System.currentTimeMillis()
        val nameToId = partners.getAll()
            .filter { !it.displayName.isNullOrBlank() }
            .associateTo(mutableMapOf()) { it.displayName!!.lowercase() to it.id }
        var imported = 0
        var skipped = 0
        for (r in rows) {
            fun cell(f: CsvField): String? = mapping[f]?.let { r.getOrNull(it)?.trim()?.ifBlank { null } }
            val startAt = parseDateTime(r.getOrNull(dateCol)?.trim().orEmpty(), cell(CsvField.TIME))
            if (startAt == null) {
                skipped++
                continue
            }
            val partnerIds = mutableListOf<String>()
            cell(CsvField.PARTNER)?.let { name ->
                val key = name.lowercase()
                val id = nameToId[key] ?: run {
                    val newId = UUID.randomUUID().toString()
                    partners.upsert(
                        PartnerEntity(
                            id = newId, displayName = name, isAnonymous = false, color = null,
                            note = null, archivedAt = null, createdAt = now, updatedAt = now,
                        ),
                    )
                    nameToId[key] = newId
                    newId
                }
                partnerIds.add(id)
            }
            encounters.save(
                EncounterEntity(
                    id = UUID.randomUUID().toString(),
                    startAt = startAt,
                    durationMin = cell(CsvField.DURATION)?.filter { it.isDigit() }?.toIntOrNull(),
                    satisfactionRating = cell(CsvField.RATING)?.toDoubleOrNull()?.toInt()?.coerceIn(1, 5),
                    note = cell(CsvField.NOTE),
                    createdAt = now,
                    updatedAt = now,
                ),
                partnerIds = partnerIds,
            )
            imported++
        }
        return imported to skipped
    }

    private fun autoGuess(headers: List<String>): Map<CsvField, Int?> {
        fun find(vararg keys: String): Int? =
            headers.indexOfFirst { h -> keys.any { h.trim().lowercase().contains(it) } }.takeIf { it >= 0 }
        return mapOf(
            CsvField.DATE to find("date", "day", "when"),
            CsvField.TIME to find("time"),
            CsvField.PARTNER to find("partner", "with", "person", "name"),
            CsvField.DURATION to find("duration", "length", "minutes", "mins"),
            CsvField.RATING to find("rating", "score", "stars", "rate"),
            CsvField.NOTE to find("note", "comment", "description", "detail"),
        )
    }

    private fun parseDateTime(dateStr: String, timeStr: String?): Long? {
        if (dateStr.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        // Epoch millis / seconds.
        dateStr.toLongOrNull()?.let { return if (dateStr.length >= 12) it else it * 1000L }
        val combined = if (!timeStr.isNullOrBlank()) "$dateStr ${timeStr.trim()}" else dateStr
        for (p in DATETIME_PATTERNS) {
            runCatching { LocalDateTime.parse(combined, DateTimeFormatter.ofPattern(p, Locale.US)) }
                .getOrNull()?.let { return it.atZone(zone).toInstant().toEpochMilli() }
        }
        for (p in DATE_PATTERNS) {
            runCatching { LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(p, Locale.US)) }
                .getOrNull()?.let { return it.atTime(12, 0).atZone(zone).toInstant().toEpochMilli() }
        }
        runCatching { Instant.parse(dateStr) }.getOrNull()?.let { return it.toEpochMilli() }
        return null
    }

    private companion object {
        val DATETIME_PATTERNS = listOf(
            "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm",
            "MM/dd/yyyy HH:mm", "M/d/yyyy H:mm", "MM/dd/yyyy hh:mm a", "M/d/yyyy h:mm a",
        )
        val DATE_PATTERNS = listOf(
            "yyyy-MM-dd", "MM/dd/yyyy", "M/d/yyyy", "yyyy/MM/dd", "dd-MM-yyyy", "MMM d, yyyy", "MMMM d, yyyy",
        )
    }
}
