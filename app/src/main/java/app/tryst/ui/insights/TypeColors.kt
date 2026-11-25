// SPDX-License-Identifier: GPL-3.0-or-later
// ╭──────────────────────────────────────────────────────────────────╮
// │  ┌●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┐  │
// │  │                                                            │  │
// │  ●   ██╗  ██╗    ██╗    ██╗                                   ●  │
// │  │   ██║  ██║    ██║    ██║           herbiewalker            │  │
// │  │   ███████║    ██║ █╗ ██║──●──●──●──┐                       │  │
// │  │   ██╔══██║    ██║███╗██║           │                       │  │
// │  ●   ██║  ██║    ╚███╔███╔╝           ●───⏣  code · tools     ●  │
// │  │   ╚═╝  ╚═╝     ╚══╝╚══╝                    homelab         │  │
// │  │                                                            │  │
// │  └●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┘  │
// ╰──────────────────────────────────────────────────────────────────╯
package app.tryst.ui.insights

import androidx.compose.ui.graphics.Color

/** One palette entry: its stored form ("#RRGGBB"), an English name for screen readers, and the color. */
data class NamedColor(val hex: String, val name: String, val color: Color)

/**
 * Maps a breakdown value's label to a stable, vivid color so the same type reads as the same color
 * across every card (e.g. "Masturbation" is always the same violet in Acts, Orgasms, etc.).
 *
 * The mapping is a pure function of the label's [String.hashCode] (stable across app runs) into a
 * curated dark-background palette, seeded with the reference colors (pink / blue / violet) and then
 * a spread of distinct hues. New values automatically pick up a color — the palette cycles, so very
 * large breakdowns may reuse a hue, which is acceptable for an unbounded set of categories.
 *
 * People can also have a color picked from the same palette (stored as "#RRGGBB" on the partner or
 * profile row); [personColor] prefers that and falls back to the name-based color.
 */
object TypeColors {

    // Vivid, high-contrast on near-black surfaces. First three match the reference screenshot.
    val palette: List<NamedColor> = listOf(
        NamedColor("#FF2D6F", "Hot pink", Color(0xFFFF2D6F)),
        NamedColor("#2D9CFF", "Azure", Color(0xFF2D9CFF)),
        NamedColor("#B14EF5", "Violet", Color(0xFFB14EF5)),
        NamedColor("#00C2A8", "Teal", Color(0xFF00C2A8)),
        NamedColor("#FFB02E", "Amber", Color(0xFFFFB02E)),
        NamedColor("#34D399", "Emerald", Color(0xFF34D399)),
        NamedColor("#FF7A45", "Orange", Color(0xFFFF7A45)),
        NamedColor("#22D3EE", "Cyan", Color(0xFF22D3EE)),
        NamedColor("#F472B6", "Soft pink", Color(0xFFF472B6)),
        NamedColor("#A3E635", "Lime", Color(0xFFA3E635)),
        NamedColor("#E879F9", "Magenta", Color(0xFFE879F9)),
        NamedColor("#60A5FA", "Blue", Color(0xFF60A5FA)),
        NamedColor("#FBBF24", "Gold", Color(0xFFFBBF24)),
        NamedColor("#4ADE80", "Green", Color(0xFF4ADE80)),
        NamedColor("#FB7185", "Rose", Color(0xFFFB7185)),
        NamedColor("#818CF8", "Indigo", Color(0xFF818CF8)),
    )

    /** Neutral colour for an aggregated "Other" bucket. */
    val other = Color(0xFF6B7280)

    fun colorFor(label: String): Color {
        if (label == "Other") return other
        val h = label.hashCode()
        // Mix the bits a little so similar labels don't cluster onto the same hue.
        val mixed = h xor (h ushr 16)
        val idx = ((mixed % palette.size) + palette.size) % palette.size
        return palette[idx].color
    }

    /** "#RRGGBB" or "#AARRGGBB" to a color; null for null, blank or malformed input. */
    fun parse(hex: String?): Color? {
        val digits = hex?.trim()?.removePrefix("#") ?: return null
        if (digits.length != RGB_DIGITS && digits.length != ARGB_DIGITS) return null
        val value = digits.toLongOrNull(HEX_RADIX) ?: return null
        return Color(if (digits.length == RGB_DIGITS) value or OPAQUE else value)
    }

    /** A person's picked color when they have a valid one, else the stable color for their name. */
    fun personColor(name: String, pickedHex: String?): Color = parse(pickedHex) ?: colorFor(name)

    private const val RGB_DIGITS = 6
    private const val ARGB_DIGITS = 8
    private const val HEX_RADIX = 16
    private const val OPAQUE = 0xFF000000L
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
