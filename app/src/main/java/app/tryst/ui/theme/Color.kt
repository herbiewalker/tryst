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
package app.tryst.ui.theme

import androidx.compose.ui.graphics.Color

// Tryst brand palette — "Warm Trio" (2026-09-30 refresh): purple warmed toward the launcher
// icon's violet, the icon's pink promoted to secondary, and a single green demoted to a minor
// tertiary accent (the app previously carried two greens; one was retired as redundant once pink
// took over the secondary role). Dark-mode background/surfaces are folded into the icon's own
// plum family (#56174D, see ic_launcher_background) rather than the previous cooler near-black.
// Used unless the user opts into Material You dynamic color.

// --- Light ---
val PurplePrimaryLight = Color(0xFF7A3FA8)
val OnPurplePrimaryLight = Color(0xFFFFFFFF)
val PurpleContainerLight = Color(0xFFF3DEFF)
val OnPurpleContainerLight = Color(0xFF3A0A5E)

val PinkSecondaryLight = Color(0xFFA81F4E)
val OnPinkSecondaryLight = Color(0xFFFFFFFF)
val PinkContainerLight = Color(0xFFFFD9E3)
val OnPinkContainerLight = Color(0xFF5E0F2E)

val GreenTertiaryLight = Color(0xFF2E6A4E)
val OnGreenTertiaryLight = Color(0xFFFFFFFF)
val GreenTertiaryContainerLight = Color(0xFFB4F1CE)
val OnGreenTertiaryContainerLight = Color(0xFF00210F)

val BackgroundLight = Color(0xFFFBF6FA)
val OnBackgroundLight = Color(0xFF241A28)
val SurfaceLight = Color(0xFFFBF6FA)
val OnSurfaceLight = Color(0xFF241A28)
val SurfaceVariantLight = Color(0xFFEDE1EA)
val OnSurfaceVariantLight = Color(0xFF544A52)
val OutlineLight = Color(0xFF8A7A87)
val OutlineVariantLight = Color(0xFFD7C8D3)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF7EEF6)
val SurfaceContainerLight = Color(0xFFF2E7EF)
val SurfaceContainerHighLight = Color(0xFFEDE0EA)
val SurfaceContainerHighestLight = Color(0xFFE7D9E4)

// --- Dark (the moody default) ---
val PurplePrimaryDark = Color(0xFFD6B4F2)
val OnPurplePrimaryDark = Color(0xFF3A1A5E)
val PurpleContainerDark = Color(0xFF5F3489)
val OnPurpleContainerDark = Color(0xFFF0DFFF)

val PinkSecondaryDark = Color(0xFFFB8FA8)
val OnPinkSecondaryDark = Color(0xFF4A0F26)
val PinkContainerDark = Color(0xFF7A1F4A)
val OnPinkContainerDark = Color(0xFFFFD3E0)

val GreenTertiaryDark = Color(0xFF99D9B3)
val OnGreenTertiaryDark = Color(0xFF00391F)
val GreenTertiaryContainerDark = Color(0xFF1F5138)
val OnGreenTertiaryContainerDark = Color(0xFFB4F1CE)

val BackgroundDark = Color(0xFF170B1C)
val OnBackgroundDark = Color(0xFFEFE3EE)
val SurfaceDark = Color(0xFF170B1C)
val OnSurfaceDark = Color(0xFFEFE3EE)
val SurfaceVariantDark = Color(0xFF34213F)
val OnSurfaceVariantDark = Color(0xFFD0C0D3)
val OutlineDark = Color(0xFF5A4462)
val OutlineVariantDark = Color(0xFF2E1E38)
val SurfaceContainerLowestDark = Color(0xFF0F0814)
val SurfaceContainerLowDark = Color(0xFF1C1024)
val SurfaceContainerDark = Color(0xFF241530)
val SurfaceContainerHighDark = Color(0xFF2E1A3B)
val SurfaceContainerHighestDark = Color(0xFF3A2246)

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
