package app.tryst

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import app.tryst.core.prefs.ThemePreferences
import app.tryst.core.session.LockState
import app.tryst.ui.TrystApp
import app.tryst.ui.lock.LockScreen
import app.tryst.ui.lock.LockViewModel
import app.tryst.ui.lock.SetupScreen
import app.tryst.ui.theme.TrystTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var themePreferences: ThemePreferences

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Privacy hardening: block screenshots and redact the app-switcher preview.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        enableEdgeToEdge()
        setContent {
            val themeMode by themePreferences.themeMode.collectAsState()
            val dynamicColor by themePreferences.dynamicColor.collectAsState()
            TrystTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val viewModel: LockViewModel = hiltViewModel()
                    val state by viewModel.state.collectAsState()

                    when (state) {
                        LockState.NeedsSetup -> SetupScreen(viewModel)
                        LockState.Locked -> LockScreen(viewModel)
                        LockState.Unlocked -> {
                            // Recomputed across fold/rotate/resize, so the shell re-lays itself out
                            // (bottom bar → rail, single-pane → two-pane) without losing state.
                            val widthSizeClass = calculateWindowSizeClass(this@MainActivity).widthSizeClass
                            TrystApp(widthSizeClass = widthSizeClass)
                        }
                    }
                }
            }
        }
    }
}
