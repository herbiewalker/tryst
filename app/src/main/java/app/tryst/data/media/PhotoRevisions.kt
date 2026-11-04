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
package app.tryst.data.media

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Counts how many times each photo blob has been edited (rotate or crop), shared by every screen. Image caches key on
 * [keyFor], so an edit made in the partner editor also refreshes the same photo's thumbnail in the Photos tab.
 */
@Singleton
class PhotoRevisions @Inject constructor() {
    private val state = MutableStateFlow<Map<String, Int>>(emptyMap())
    val revisions: StateFlow<Map<String, Int>> = state.asStateFlow()

    fun bump(blobId: String) = state.update { it + (blobId to ((it[blobId] ?: 0) + 1)) }

    fun keyFor(blobId: String): String = "$blobId#${state.value[blobId] ?: 0}"
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
