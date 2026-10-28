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
        viewModel.openRequest = intent.getStringExtra(EXTRA_OPEN)
        watchFeeders(intent)
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
        // Kumanda tuşuyla istenen kilitli uygulama ya da Dosyalar; Home'un kapattıklarından sonra açılır
        intent.getStringExtra(EXTRA_OPEN)?.let { viewModel.openRequest = it }
        watchFeeders(intent)
    }

    // Yalnızca ADB'den: mama kaplarının DP'lerini loga yazar (GeniusHome etiketi)
    private fun watchFeeders(intent: Intent) {
        val seconds = intent.getIntExtra("watch_feeders", 0)
        if (seconds > 0) viewModel.watchFeeders(seconds)
    }

    companion object {
        const val EXTRA_OPEN = "dev.evim.tv.OPEN"
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
