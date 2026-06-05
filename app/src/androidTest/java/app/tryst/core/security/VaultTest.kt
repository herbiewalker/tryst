package app.tryst.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real Android Keystore on the emulator (StrongBox falls back to TEE there).
 */
@RunWith(AndroidJUnit4::class)
class VaultTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pin = "135790"

    private fun newVault() = Vault(context)

    @Before
    fun setUp() {
        newVault().wipe()
    }

    @After
    fun tearDown() {
        newVault().wipe()
    }

    @Test
    fun setupThenUnlock_returnsSameDek() {
        val vault = newVault()
        val created = vault.setup(pin)
        val unlocked = vault.unlock(pin)
        assertArrayEquals(created, unlocked)
        assertTrue(vault.isInitialized())
    }

    @Test
    fun unlock_wrongPin_throwsWithCountdown() {
        val vault = newVault()
        vault.setup(pin)
        val ex = assertThrows(WrongPinException::class.java) { vault.unlock("000000") }
        assertTrue("attemptsRemaining should be reported", ex.attemptsRemaining in 1..9)
        // The correct PIN still works afterwards, and resets the counter.
        assertArrayEquals(vault.unlock(pin), vault.unlock(pin))
    }

    @Test
    fun vaultPersistsAcrossInstances() {
        val created = newVault().setup(pin)
        // A fresh Vault instance (simulating an app restart) reads Keystore + file from disk.
        val unlocked = newVault().unlock(pin)
        assertArrayEquals(created, unlocked)
    }

    @Test
    fun changePin_reprotectsSameDek() {
        val vault = newVault()
        val created = vault.setup("111111")
        vault.changePin("111111", "222222")
        assertArrayEquals(created, vault.unlock("222222"))
        assertThrows(WrongPinException::class.java) { vault.unlock("111111") }
    }

    @Test
    fun tooManyWrongAttempts_wipesVault() {
        val vault = newVault()
        vault.setup(pin)
        // 9 wrong attempts report WrongPin...
        repeat(9) {
            assertThrows(WrongPinException::class.java) { vault.unlock("999999") }
        }
        // ...the 10th wipes the vault.
        assertThrows(VaultWipedException::class.java) { vault.unlock("999999") }
        assertFalse(vault.isInitialized())
        assertThrows(VaultNotInitializedException::class.java) { vault.unlock(pin) }
    }
}
