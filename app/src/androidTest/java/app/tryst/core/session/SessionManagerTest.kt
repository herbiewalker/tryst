package app.tryst.core.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tryst.core.security.BiometricVault
import app.tryst.core.security.Vault
import app.tryst.core.security.WrongPinException
import app.tryst.data.db.TrystDatabase
import app.tryst.data.db.TrystDatabaseFactory
import app.tryst.data.db.entity.PartnerEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pin = "246810"

    private fun newManager() =
        SessionManager(Vault(context), TrystDatabaseFactory(context), BiometricVault(context))

    @Before
    fun setUp() {
        Vault(context).wipe()
        BiometricVault(context).disable()
        context.deleteDatabase(TrystDatabase.NAME)
    }

    @After
    fun tearDown() {
        Vault(context).wipe()
        BiometricVault(context).disable()
        context.deleteDatabase(TrystDatabase.NAME)
    }

    @Test
    fun setupUnlockLock_andPersistsAcrossRestart() = runBlocking {
        val manager = newManager()
        assertEquals(LockState.NeedsSetup, manager.state.value)

        manager.setupPin(pin)
        assertEquals(LockState.Unlocked, manager.state.value)

        val now = System.currentTimeMillis()
        manager.database().partnerDao().upsert(
            PartnerEntity("p", "Sam", isAnonymous = false, color = null, note = null, archivedAt = null, createdAt = now, updatedAt = now),
        )

        manager.lock()
        assertEquals(LockState.Locked, manager.state.value)
        assertThrows(IllegalStateException::class.java) { manager.database() }

        // Fresh instance simulates an app restart: starts Locked, unlocks, data persists.
        val restarted = newManager()
        assertEquals(LockState.Locked, restarted.state.value)
        restarted.unlockWithPin(pin)
        assertEquals("Sam", restarted.database().partnerDao().getById("p")?.displayName)
    }

    @Test
    fun wrongPin_staysLocked() = runBlocking {
        val manager = newManager()
        manager.setupPin("111111")
        manager.lock()
        assertThrows(WrongPinException::class.java) {
            runBlocking { manager.unlockWithPin("000000") }
        }
        assertEquals(LockState.Locked, manager.state.value)
    }
}
