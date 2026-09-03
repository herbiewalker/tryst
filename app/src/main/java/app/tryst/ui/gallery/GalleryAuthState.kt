// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.ui.gallery

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks when the Photos gallery was last re-authenticated (SEC-2 tier 2), so a quick tab switch
 * doesn't re-prompt for biometric/device credential — only after a grace window. Parallel to
 * [GalleryRevealState] (which does the same thing for the blur tier). In-memory, per-process, not
 * persisted: a fresh app launch always requires a re-auth if the pref is on, and it's deliberately
 * not reset on app-lock because the app lock is itself the gate for that case.
 */
@Singleton
class GalleryAuthState @Inject constructor() {
    private var lastAuthedAt: Long? = null

    fun markAuthed() {
        lastAuthedAt = SystemClock.elapsedRealtime()
    }

    /** True if the user re-authed within [graceMs] — i.e. a tab switch shouldn't re-prompt yet. */
    fun isWithinGrace(graceMs: Long): Boolean = lastAuthedAt?.let { SystemClock.elapsedRealtime() - it < graceMs } ?: false
}
