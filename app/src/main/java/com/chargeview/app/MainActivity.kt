package com.chargeview.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: BatteryViewModel = viewModel()
            val theme by vm.theme.collectAsState()
            ChargeViewTheme(theme) {
                Surface(color = MaterialTheme.colorScheme.background) { ChargeViewApp(vm) }
            }
        }
    }
}
