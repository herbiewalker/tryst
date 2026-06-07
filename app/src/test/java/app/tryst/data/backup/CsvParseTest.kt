package app.tryst.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvParseTest {

    @Test
    fun parsesHeadersQuotedCommasAndNewlines() {
        val csv = "date,partner,note\n2026-01-02,\"Sam, Jr.\",\"line1\nline2\"\n2026-01-03,Alex,ok"
        val rows = Csv.parse(csv)
        assertEquals(3, rows.size)
        assertEquals(listOf("date", "partner", "note"), rows[0])
        assertEquals("Sam, Jr.", rows[1][1]) // embedded comma inside quotes
        assertEquals("line1\nline2", rows[1][2]) // embedded newline inside quotes
        assertEquals("Alex", rows[2][1])
    }

    @Test
    fun handlesEscapedQuotesAndCrlf() {
        val rows = Csv.parse("a,b\r\n\"he said \"\"hi\"\"\",2")
        assertEquals(2, rows.size)
        assertEquals("he said \"hi\"", rows[1][0])
        assertEquals("2", rows[1][1])
    }

    @Test
    fun dropsBlankLines() {
        val rows = Csv.parse("a,b\n\n1,2\n\n")
        assertEquals(2, rows.size)
    }
}
