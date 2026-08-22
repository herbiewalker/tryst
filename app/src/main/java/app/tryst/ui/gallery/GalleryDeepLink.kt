// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.ui.gallery

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A one-shot request to open the Photos tab pre-filtered to a partner — the Partners → gallery deep link.
 *
 * The gallery is a bottom-nav tab whose ViewModel is cached (and whose state is restored) across tab
 * switches, so a nav argument can't reliably re-apply on every jump. Instead the Partners screen drops a
 * partner id here and navigates to the tab; [GalleryViewModel] collects it and applies the partner filter,
 * then clears it. In-memory, per-process — the same pattern as [GalleryRevealState].
 */
@Singleton
class GalleryDeepLink @Inject constructor() {
    private val _pendingPartnerId = MutableStateFlow<String?>(null)
    val pendingPartnerId: StateFlow<String?> = _pendingPartnerId.asStateFlow()

    /** Ask the gallery to open filtered to this partner on its next composition. */
    fun requestPartner(id: String) {
        _pendingPartnerId.value = id
    }

    /** Consumed by the gallery once applied, so it doesn't re-fire on the next tab switch. */
    fun consume() {
        _pendingPartnerId.value = null
    }
}
