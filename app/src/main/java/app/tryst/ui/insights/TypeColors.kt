package app.tryst.ui.insights

import androidx.compose.ui.graphics.Color

/**
 * Maps a breakdown value's label to a stable, vivid color so the same type reads as the same color
 * across every card (e.g. "Masturbation" is always the same violet in Acts, Orgasms, etc.).
 *
 * The mapping is a pure function of the label's [String.hashCode] (stable across app runs) into a
 * curated dark-background palette, seeded with the reference colors (pink / blue / violet) and then
 * a spread of distinct hues. New values automatically pick up a color — the palette cycles, so very
 * large breakdowns may reuse a hue, which is acceptable for an unbounded set of categories.
 */
object TypeColors {

    // Vivid, high-contrast on near-black surfaces. First three match the reference screenshot.
    private val palette = listOf(
        Color(0xFFFF2D6F), // hot pink
        Color(0xFF2D9CFF), // azure
        Color(0xFFB14EF5), // violet
        Color(0xFF00C2A8), // teal
        Color(0xFFFFB02E), // amber
        Color(0xFF34D399), // emerald
        Color(0xFFFF7A45), // orange
        Color(0xFF22D3EE), // cyan
        Color(0xFFF472B6), // soft pink
        Color(0xFFA3E635), // lime
        Color(0xFFE879F9), // magenta
        Color(0xFF60A5FA), // blue
        Color(0xFFFBBF24), // gold
        Color(0xFF4ADE80), // green
        Color(0xFFFB7185), // rose
        Color(0xFF818CF8), // indigo
    )

    /** Neutral colour for an aggregated "Other" bucket. */
    val other = Color(0xFF6B7280)

    fun colorFor(label: String): Color {
        if (label == "Other") return other
        val h = label.hashCode()
        // Mix the bits a little so similar labels don't cluster onto the same hue.
        val mixed = h xor (h ushr 16)
        val idx = ((mixed % palette.size) + palette.size) % palette.size
        return palette[idx]
    }
}
