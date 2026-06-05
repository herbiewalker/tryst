package app.tryst.core.security

/** The entered PIN was incorrect. [attemptsRemaining] before the vault self-wipes. */
class WrongPinException(val attemptsRemaining: Int) : Exception("Incorrect PIN")

/** Too many failed attempts: all key material has been destroyed. */
class VaultWipedException : Exception("Vault wiped after too many failed attempts")

/** Unlock/change attempted before the vault was set up. */
class VaultNotInitializedException : Exception("Vault not initialized")

/** Setup attempted when the vault already exists. */
class VaultAlreadyInitializedException : Exception("Vault already initialized")
