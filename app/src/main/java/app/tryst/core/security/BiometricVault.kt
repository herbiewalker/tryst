package app.tryst.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * Optional biometric unlock. A separate Android Keystore key (`KEK_bio`) that *requires
 * biometric authentication to use* wraps a copy of the DEK. Enabling and unlocking both go
 * through a [androidx.biometric.BiometricPrompt] with a [Cipher] CryptoObject:
 *
 *  - enable:  [encryptCipher] → BiometricPrompt → [store] the DEK
 *  - unlock:  [decryptCipher] → BiometricPrompt → [recover] the DEK
 *
 * The key is invalidated automatically if the device's biometrics change.
 */
@Singleton
class BiometricVault @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val file: File get() = File(appContext.filesDir, FILE_NAME)

    fun isEnabled(): Boolean = file.exists()

    /** True if the device has usable strong biometrics enrolled. */
    fun canUseBiometrics(): Boolean = BiometricManager.from(appContext)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        BiometricManager.BIOMETRIC_SUCCESS

    /** Cipher for enabling biometric unlock. Authenticate it, then call [store]. */
    fun encryptCipher(): Cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }

    /** Cipher for biometric unlock, using the stored IV. Authenticate it, then call [recover]. */
    fun decryptCipher(): Cipher {
        val key = existingKey() ?: error("Biometric not enabled")
        val iv = Base64.getDecoder().decode(JSONObject(file.readText()).getString(KEY_IV))
        return Cipher.getInstance(TRANSFORMATION)
            .apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv)) }
    }

    /** Persist the DEK using a [Cipher] already authenticated via [encryptCipher]. */
    fun store(dek: ByteArray, authenticatedCipher: Cipher) {
        val ct = authenticatedCipher.doFinal(dek)
        val json = JSONObject()
            .put(KEY_IV, b64(authenticatedCipher.iv))
            .put(KEY_CT, b64(ct))
        file.writeText(json.toString())
    }

    /** Recover the DEK using a [Cipher] already authenticated via [decryptCipher]. */
    fun recover(authenticatedCipher: Cipher): ByteArray {
        val ct = Base64.getDecoder().decode(JSONObject(file.readText()).getString(KEY_CT))
        return authenticatedCipher.doFinal(ct)
    }

    fun disable() {
        if (file.exists()) file.delete()
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
    }

    private fun existingKey(): SecretKey? = (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun getOrCreateKey(): SecretKey {
        existingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "tryst_vault_kek_bio"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val FILE_NAME = "bio.json"
        const val KEY_IV = "iv"
        const val KEY_CT = "ct"
    }
}
