package app.tryst.data.db

/**
 * Loads the SQLCipher native library exactly once. `net.zetetic:sqlcipher-android` does not
 * auto-load it, so this must run before any SQLCipher [net.zetetic.database.sqlcipher]
 * usage (DI database provider and DB-backed tests both call it).
 */
object SqlCipherLibrary {
    @Volatile
    private var loaded = false

    @Synchronized
    fun ensureLoaded() {
        if (!loaded) {
            System.loadLibrary("sqlcipher")
            loaded = true
        }
    }
}
