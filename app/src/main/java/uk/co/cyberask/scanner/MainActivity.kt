package uk.co.cyberask.scanner

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import uk.co.cyberask.scanner.ui.NessusFrontendApp
import uk.co.cyberask.scanner.ui.NessusViewModel
import uk.co.cyberask.scanner.ui.theme.NessusFrontendTheme

/**
 * Must be a [FragmentActivity]: [androidx.biometric.BiometricPrompt] requires it.
 * Using ComponentActivity makes the lock screen cast fail and previously skipped auth.
 */
class MainActivity : FragmentActivity() {
    private val viewModel: NessusViewModel by viewModels { NessusViewModel.factory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()
        setContent {
            NessusFrontendTheme {
                NessusFrontendApp(viewModel)
            }
        }
    }
}
