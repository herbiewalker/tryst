// SPDX-License-Identifier: GPL-3.0-or-later
//
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
//
package app.tryst.ui.brand

object BrandMark {
    const val SIGIL: String = "⏣"
    const val NAME: String = "herbiewalker"
    const val TAGLINE: String = "code · tools · homelab"
    const val MARK: String = "⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣"

    val BANNER_MODERN: String = """
        ┌─────────────────────────────────────────────────────────────────┐
        │                                                                 │
        │    █ █    █ █                                                   │
        │    █▀█    █▄█ ──●──●──⏣   herbiewalker                          │
        │    █ █    ▀ ▀                        code · tools · homelab     │
        │                                                                 │
        └─────────────────────────────────────────────────────────────────┘
    """.trimIndent()

    private val EGG_QUERIES: Set<String> = setOf("⏣", "herbiewalker")

    fun isEasterEggQuery(query: String): Boolean = query.trim().lowercase() in EGG_QUERIES
}
