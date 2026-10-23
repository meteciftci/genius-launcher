package dev.evim.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dev.evim.tv.ui.EvimTheme
import dev.evim.tv.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EvimTheme {
                HomeScreen(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startHomeUpdates()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopHomeUpdates()
    }

    override fun onResume() {
        super.onResume()
        // Uygulama yüklenip kaldırılmış olabilir, RAM değişmiştir
        viewModel.refresh()
    }
}
