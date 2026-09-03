// SPDX-License-Identifier: GPL-3.0-or-later
package app.tryst.ui.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.tryst.R
import javax.crypto.Cipher

/**
 * Thin wrapper around [BiometricPrompt] for a CryptoObject-based flow. On success the
 * authenticated [Cipher] is handed back so the caller can encrypt/decrypt the DEK.
 */
object BiometricPromptHelper {

    @Suppress("LongParameterList") // a biometric callback API: title/subtitle + 3 result callbacks.
    fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        subtitle: String,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authed = result.cryptoObject?.cipher
                    if (authed != null) onSuccess(authed) else onError(activity.getString(R.string.biometric_no_cipher))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED,
                        -> onCancel()
                        else -> onError(errString.toString())
                    }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(activity.getString(R.string.biometric_use_pin))
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    /**
     * Presence-only re-auth (SEC-2 tier 2): no CryptoObject, no DEK touch — just "prove the person
     * holding the phone is still the owner." Used to gate optional extra-sensitive surfaces (the
     * Photos tab) once the user has already unlocked the app. Falls back to the device credential
     * (screen-lock PIN/pattern/password) when biometrics aren't enrolled, so it works on every
     * device that has *any* lock method set up.
     *
     * Callers can query [canConfirmPresence] first to hide the gate's toggle on devices where no
     * authenticator is available at all.
     */
    @Suppress("LongParameterList")
    fun confirmPresence(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED,
                        -> onCancel()
                        else -> onError(errString.toString())
                    }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            // BIOMETRIC_STRONG + DEVICE_CREDENTIAL lets the OS chain to the phone's lock if no
            // biometric is enrolled — so the gate still works for PIN-only users. The negative
            // button is disallowed with DEVICE_CREDENTIAL, so no setNegativeButtonText here.
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        prompt.authenticate(info)
    }

    /**
     * Can the device do a presence re-auth at all? True when either a biometric is enrolled or the
     * device screen lock is set. Used by Settings to gate whether the SEC-2 tier-2 toggle is even
     * shown.
     */
    fun canConfirmPresence(context: Context): Boolean {
        val bm = BiometricManager.from(context)
        return bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }
}

/** Walks the context chain to find the hosting [FragmentActivity]. */
fun Context.findFragmentActivity(): FragmentActivity {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    error("No FragmentActivity in context chain")
}
