package dev.evim.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dev.evim.tv.data.CrashGuard
import dev.evim.tv.ui.EvimTheme
import dev.evim.tv.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashGuard.install(this)
        if (intent.getBooleanExtra("demo", false)) viewModel.enterDemo()
        setContent {
            EvimTheme {
                HomeScreen(viewModel)
            }
        }
    }

    // Launcher Home olduğu için çoğu zaman zaten açıktır; yeni istek buraya gelir
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("demo", false)) viewModel.enterDemo()
        // Home tuşu: Dosyalar ya da ayarlar açıkken de ana ekrana dönülür
        if (intent.hasCategory(Intent.CATEGORY_HOME)) viewModel.homePressed()
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
