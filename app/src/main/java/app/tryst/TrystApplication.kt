package app.tryst

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.tryst.core.session.SessionManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class TrystApplication : Application() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SessionEntryPoint {
        fun sessionManager(): SessionManager
    }

    override fun onCreate() {
        super.onCreate()

        // Auto-lock: clear the in-memory key and close the DB whenever the app goes to the
        // background (default = immediate). ProcessLifecycle (not Activity) so rotation/config
        // changes don't trigger a lock. See docs/DECISIONS.md D-14.
        val session = EntryPointAccessors
            .fromApplication(this, SessionEntryPoint::class.java)
            .sessionManager()

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    session.onAppBackgrounded()
                }
            },
        )
    }
}
