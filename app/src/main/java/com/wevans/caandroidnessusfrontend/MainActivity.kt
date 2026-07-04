package com.wevans.caandroidnessusfrontend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.wevans.caandroidnessusfrontend.ui.NessusFrontendApp
import com.wevans.caandroidnessusfrontend.ui.NessusViewModel
import com.wevans.caandroidnessusfrontend.ui.theme.NessusFrontendTheme

class MainActivity : ComponentActivity() {
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
