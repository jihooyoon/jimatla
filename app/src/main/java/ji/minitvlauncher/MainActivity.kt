package ji.minitvlauncher

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as SystemSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ji.minitvlauncher.data.Settings
import ji.minitvlauncher.ui.HomeActions
import ji.minitvlauncher.ui.HomeScreen
import ji.minitvlauncher.ui.theme.LauncherTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), HomeActions {
    private lateinit var repository: AppRepository
    private lateinit var settings: Settings
    private var apps by mutableStateOf<List<AppEntry>?>(null)
    private var homePresses by mutableIntStateOf(0)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AppRepository(applicationContext)
        settings = Settings(applicationContext)
        reload()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent {
            LauncherTheme {
                BackHandler { /* Home stays put */ }
                val favorites by settings.favorites.collectAsStateWithLifecycle(emptyList())
                val loaded = apps ?: return@LauncherTheme
                val rows = remember(loaded, favorites) { buildRows(loaded, favorites) }
                HomeScreen(rows, repository, this@MainActivity, homePresses)
            }
        }
    }

    /** Home pressed while already home: back to the top. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    private fun reload() {
        Thread { repository.loadApps().also { runOnUiThread { apps = it } } }.start()
    }

    override fun setFavorites(favorites: List<ComponentName>) {
        lifecycleScope.launch { settings.setFavorites(favorites) }
    }

    override fun showAppInfo(app: AppEntry) {
        val intent = Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.component.packageName, null))
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.launch_failed, getString(R.string.menu_app_info)), Toast.LENGTH_SHORT).show()
        }
    }

    override fun launch(app: AppEntry) {
        try {
            startActivity(repository.launchIntent(app))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.launch_failed, app.label), Toast.LENGTH_SHORT).show()
            reload()
        } catch (_: SecurityException) {
            Toast.makeText(this, getString(R.string.launch_failed, app.label), Toast.LENGTH_SHORT).show()
        }
    }
}
