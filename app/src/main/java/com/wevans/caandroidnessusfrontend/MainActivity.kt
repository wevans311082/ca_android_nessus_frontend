package com.wevans.caandroidnessusfrontend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wevans.caandroidnessusfrontend.ui.NessusFrontendApp
import com.wevans.caandroidnessusfrontend.ui.NessusViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: NessusViewModel = viewModel(factory = NessusViewModel.factory(applicationContext))
            NessusFrontendApp(vm)
        }
    }
}
