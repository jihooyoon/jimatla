package ji.minitvlauncher

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
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
import ji.minitvlauncher.data.Settings
import ji.minitvlauncher.ui.HomeScreen
import ji.minitvlauncher.ui.theme.LauncherTheme

class MainActivity : ComponentActivity() {
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
                HomeScreen(rows, repository, ::launch, homePresses)
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

    private fun launch(entry: AppEntry) {
        try {
            startActivity(repository.launchIntent(entry))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.launch_failed, entry.label), Toast.LENGTH_SHORT).show()
            reload()
        } catch (_: SecurityException) {
            Toast.makeText(this, getString(R.string.launch_failed, entry.label), Toast.LENGTH_SHORT).show()
        }
    }
}
