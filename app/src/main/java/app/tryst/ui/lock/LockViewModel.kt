package app.tryst.ui.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tryst.core.security.VaultWipedException
import app.tryst.core.security.WrongPinException
import app.tryst.core.session.LockState
import app.tryst.core.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val session: SessionManager,
) : ViewModel() {

    val state: StateFlow<LockState> = session.state

    var error by mutableStateOf<String?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    fun setupPin(pin: String) {
        viewModelScope.launch {
            busy = true
            error = null
            try {
                session.setupPin(pin)
            } catch (e: Exception) {
                error = "Couldn't set up the PIN: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun unlock(pin: String) {
        viewModelScope.launch {
            busy = true
            error = null
            try {
                session.unlockWithPin(pin)
            } catch (e: WrongPinException) {
                error = "Incorrect PIN — ${e.attemptsRemaining} attempt(s) left"
            } catch (e: VaultWipedException) {
                error = "Too many attempts — data was wiped."
            } catch (e: Exception) {
                error = "Unlock failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun lock() = session.lock()

    fun reportError(message: String?) {
        error = message
    }

    // --- Biometric ---------------------------------------------------------------------

    fun isBiometricEnabled(): Boolean = session.isBiometricEnabled()

    fun canUseBiometrics(): Boolean = session.canUseBiometrics()

    fun biometricEncryptCipher(): Cipher = session.biometricEncryptCipher()

    fun biometricDecryptCipher(): Cipher = session.biometricDecryptCipher()

    /** Called when the biometric key was invalidated (e.g. fingerprints changed). */
    fun onBiometricInvalidated() {
        session.disableBiometric()
        error = "Biometric was changed on this device — please use your PIN."
    }

    /** Store the DEK under a freshly authenticated cipher. Returns true on success. */
    fun enableBiometric(authenticatedCipher: Cipher): Boolean =
        try {
            session.enableBiometric(authenticatedCipher)
            error = null
            true
        } catch (e: Exception) {
            error = "Couldn't enable biometric unlock: ${e.message}"
            false
        }

    fun disableBiometric() = session.disableBiometric()

    fun deleteAllData() = session.deleteAllData()

    fun unlockWithBiometric(authenticatedCipher: Cipher) {
        viewModelScope.launch {
            busy = true
            error = null
            try {
                session.unlockWithBiometric(authenticatedCipher)
            } catch (e: Exception) {
                error = "Biometric unlock failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }
}
