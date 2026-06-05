package app.tryst.core.session

/** Top-level lock state that drives what the UI shows. */
sealed interface LockState {
    /** Vault not yet created — first run; show PIN setup. */
    data object NeedsSetup : LockState

    /** Vault exists but the DEK is not in memory; show the lock screen. */
    data object Locked : LockState

    /** DEK in memory, database open; show the app. */
    data object Unlocked : LockState
}
