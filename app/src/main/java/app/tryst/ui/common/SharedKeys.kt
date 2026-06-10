package app.tryst.ui.common

/**
 * Stable keys for shared-element (container-transform) transitions, shared by the source (a history
 * card, or the "+" FAB for a brand-new entry) and the destination (the encounter editor). Both ends
 * must agree on the exact string, so derive it here in one place.
 */
fun encounterSharedKey(encounterId: String?): String = "encounter-container-${encounterId ?: "new"}"
