package app.tryst.data.backup

/** Minimal RFC-4180-ish CSV parser (handles quoted fields, embedded commas/newlines, "" escapes). */
object Csv {

    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        val s = text.replace("\r\n", "\n").replace('\r', '\n')
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < s.length && s[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { row.add(field.toString()); field.clear() }
                c == '\n' -> { row.add(field.toString()); rows.add(row); row = mutableListOf(); field.clear() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows.filter { cells -> cells.any { it.isNotBlank() } } // drop blank lines
    }
}
