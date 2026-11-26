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
import app.tryst.data.color.PersonPalette

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

    // The shared people palette (D-70): twenty-four hues, readable on both themes.
    val palette: List<NamedColor> = PersonPalette.entries.map { NamedColor(it.hex, it.name, parse(it.hex) ?: Color.Gray) }

    // Categories only use the bold first tier, so charts never get the darker or paler shades.
    private val categories: List<NamedColor> = palette.take(PersonPalette.FIRST_TIER)

    /** Neutral colour for an aggregated "Other" bucket. */
    val other = Color(0xFF6B7280)

    fun colorFor(label: String): Color {
        if (label == "Other") return other
        val h = label.hashCode()
        // Mix the bits a little so similar labels don't cluster onto the same hue.
        val mixed = h xor (h ushr 16)
        val idx = ((mixed % categories.size) + categories.size) % categories.size
        return categories[idx].color
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
