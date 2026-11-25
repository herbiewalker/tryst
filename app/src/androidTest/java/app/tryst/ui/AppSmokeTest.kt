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
package app.tryst.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tryst.MainActivity
import app.tryst.R
import app.tryst.TrystApplication
import app.tryst.core.session.LockState
import app.tryst.core.session.SessionManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TEST-1: the happy path through the real app, end to end. First-run setup, lock, a wrong PIN is
 * refused, unlock, log a tryst, and it lands in the database and on the Trysts tab. FLAG_SECURE
 * doesn't get in the way: Compose tests read the semantics tree, not pixels.
 *
 * Uses the app's own singleton SessionManager so the activity and the test see the same state, and
 * wipes everything before and after. Like every instrumented test here, emulator only.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val session: SessionManager = EntryPointAccessors
        .fromApplication(context, TrystApplication.SessionEntryPoint::class.java)
        .sessionManager()

    private val pin = "135790"

    @Before
    fun resetToFreshInstall() = session.deleteAllData()

    @After
    fun wipe() = session.deleteAllData()

    private fun str(@StringRes id: Int) = context.getString(id)

    /** Waits for a node with [text]; PIN key-derivation is deliberately slow, so allow plenty. */
    private fun awaitText(text: String, timeoutMs: Long = 30_000) = compose.waitUntilAtLeastOneExists(hasText(text), timeoutMs)

    private fun enterPin(value: String) = value.forEach { compose.onNodeWithText(it.toString()).performClick() }

    @Test
    fun setupLockUnlockAndLogATryst() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // First run: welcome, then create + confirm the PIN.
            awaitText(str(R.string.welcome_get_started))
            compose.onNodeWithText(str(R.string.welcome_get_started)).performClick()
            awaitText(str(R.string.setup_create_title))
            enterPin(pin)
            awaitText(str(R.string.setup_confirm_title))
            enterPin(pin)

            // Unlocked into an empty Trysts tab.
            awaitText(str(R.string.history_empty))
            assertEquals(LockState.Unlocked, session.state.value)

            // Lock, refuse a wrong PIN, then unlock with the right one.
            compose.runOnIdle { session.lock() }
            awaitText(str(R.string.lock_pin_title))
            enterPin("000000")
            // The error carries the attempts left; match the fixed part before the count.
            val wrongPinPrefix = str(R.string.lock_error_wrong_pin).substringBefore("%")
            compose.waitUntilAtLeastOneExists(hasText(wrongPinPrefix, substring = true), 30_000)
            assertEquals(LockState.Locked, session.state.value)
            enterPin(pin)
            awaitText(str(R.string.history_empty))
            assertEquals(LockState.Unlocked, session.state.value)

            // Log a tryst with the defaults and save it.
            compose.waitUntilAtLeastOneExists(hasContentDescription(str(R.string.cd_log_encounter)), 10_000)
            compose.onNodeWithContentDescription(str(R.string.cd_log_encounter)).performClick()
            awaitText(str(R.string.action_save))
            compose.onNodeWithText(str(R.string.action_save)).performClick()

            // It's stored, and the empty state is gone.
            compose.waitUntilDoesNotExist(hasText(str(R.string.history_empty)), 10_000)
            val saved = runBlocking { session.database().encounterDao().observeAllWithDetails().first() }
            assertEquals(1, saved.size)
        }
    }
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
