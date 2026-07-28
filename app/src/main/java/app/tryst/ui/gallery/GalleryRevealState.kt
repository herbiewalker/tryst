package app.tryst.ui.gallery

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks when the Photos gallery was last revealed, so the blur gate (SEC-2) doesn't re-prompt on a quick
 * tab switch — only after a grace window. **In-memory, per-process** (not persisted): a fresh app launch
 * always starts gated, and it deliberately isn't reset on app-lock because the app lock is itself the gate
 * for that case.
 *
 * The timestamp is refreshed both when the user taps "Show photos" and when they leave the tab while
 * revealed, so the grace is measured from *last active in Photos*, not from the first reveal.
 */
@Singleton
class GalleryRevealState @Inject constructor() {
    private var lastRevealedAt: Long? = null

    fun markRevealed() {
        lastRevealedAt = SystemClock.elapsedRealtime()
    }

    /** True if Photos was revealed within [GRACE_MS] — i.e. a tab switch shouldn't re-blur it yet. */
    fun isWithinGrace(): Boolean = lastRevealedAt?.let { SystemClock.elapsedRealtime() - it < GRACE_MS } ?: false

    companion object {
        /** How long a reveal survives leaving the Photos tab before it re-blurs. */
        const val GRACE_MS = 30_000L
    }
}
