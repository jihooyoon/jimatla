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
import ji.minitvlauncher.system.NetKind
import ji.minitvlauncher.system.NetStatus
import ji.minitvlauncher.system.NotificationItem
import ji.minitvlauncher.system.NotificationReader
import ji.minitvlauncher.system.TvInputItem
import ji.minitvlauncher.system.networkStatus
import ji.minitvlauncher.system.switchToInputIntent
import ji.minitvlauncher.system.tvInputs
import ji.minitvlauncher.ui.HomeActions
import ji.minitvlauncher.ui.HomeScreen
import ji.minitvlauncher.ui.SystemScreen
import ji.minitvlauncher.ui.SystemStatus
import ji.minitvlauncher.ui.theme.LauncherTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), HomeActions {
    private lateinit var repository: AppRepository
    private lateinit var settings: Settings
    private var apps by mutableStateOf<List<AppEntry>?>(null)
    private var homePresses by mutableIntStateOf(0)
    private var inputs by mutableStateOf(emptyList<TvInputItem>())
    private var notificationAccess by mutableStateOf(false)

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
                val network by remember { networkStatus(this@MainActivity) }.collectAsStateWithLifecycle(NetStatus(NetKind.Wifi))
                val notifications by NotificationReader.notifications.collectAsStateWithLifecycle()
                val listening by NotificationReader.isConnected.collectAsStateWithLifecycle()
                val status = SystemStatus(
                    network = network,
                    notifications = notifications.takeIf { listening || notificationAccess },
                    hasSystemNotificationPanel = remember { packageManager.resolveActivity(systemNotificationsIntent, 0) != null },
                    inputs = inputs,
                )
                HomeScreen(rows, repository, this@MainActivity, status, homePresses)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationAccess = NotificationReader.hasAccess(this)
        Thread { tvInputs(this).also { runOnUiThread { inputs = it } } }.start()
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

    override fun openSystem(screen: SystemScreen) {
        val candidates = when (screen) {
            SystemScreen.Bluetooth -> listOf(SystemSettings.ACTION_BLUETOOTH_SETTINGS)
            SystemScreen.Network -> listOf(SystemSettings.ACTION_WIFI_SETTINGS, SystemSettings.ACTION_WIRELESS_SETTINGS)
            SystemScreen.Settings -> emptyList()
        } + SystemSettings.ACTION_SETTINGS
        val opened = candidates.any { action -> tryStart(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        if (!opened) Toast.makeText(this, getString(R.string.launch_failed, getString(R.string.settings)), Toast.LENGTH_SHORT).show()
    }

    override fun openSystemNotifications(): Boolean = tryStart(systemNotificationsIntent)

    override fun switchInput(input: TvInputItem) {
        if (!tryStart(switchToInputIntent(input))) {
            Toast.makeText(this, getString(R.string.launch_failed, input.label), Toast.LENGTH_SHORT).show()
        }
    }

    override fun openNotification(item: NotificationItem) = NotificationReader.open(item.key)

    override fun clearNotifications() = NotificationReader.dismissAll()

    private fun tryStart(intent: Intent): Boolean =
        packageManager.resolveActivity(intent, 0) != null &&
            runCatching { startActivity(intent) }.isSuccess

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

/** Android TV's system notification panel, on versions that have one. */
private val systemNotificationsIntent =
    Intent("com.android.tv.action.OPEN_NOTIFICATIONS_PANEL").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
