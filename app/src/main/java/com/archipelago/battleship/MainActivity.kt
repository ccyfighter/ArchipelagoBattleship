package com.archipelago.battleship

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.archipelago.battleship.core.ArchipelagoTheme
import com.archipelago.battleship.ui.AppRoot
import com.archipelago.battleship.ui.GameViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: GameViewModel = viewModel()
            val state by vm.ui.collectAsState()
            ArchipelagoTheme(state.theme) {
                AppRoot(vm, state, this)
            }
        }
    }
}
