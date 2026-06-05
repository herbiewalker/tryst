package app.tryst.di

import app.tryst.core.crypto.DatabaseKeyProvider
import app.tryst.core.crypto.InsecureDevKeyProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the key provider. M1 uses [InsecureDevKeyProvider]; M2 will change this single
 * binding to the real passphrase/Keystore-backed implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {
    @Binds
    abstract fun bindDatabaseKeyProvider(impl: InsecureDevKeyProvider): DatabaseKeyProvider
}
