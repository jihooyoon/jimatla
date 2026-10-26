package ji.minitvlauncher

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ji.minitvlauncher.data.Settings
import ji.minitvlauncher.system.NetKind
import ji.minitvlauncher.system.NetStatus
import ji.minitvlauncher.system.NotificationItem
import ji.minitvlauncher.system.NotificationReader
import ji.minitvlauncher.system.TvInputItem
import ji.minitvlauncher.system.connectedAccessories
import ji.minitvlauncher.system.networkStatus
import ji.minitvlauncher.system.switchToInputIntent
import ji.minitvlauncher.system.tvInputs
import ji.minitvlauncher.ui.HomeActions
import ji.minitvlauncher.ui.HomeScreen
import ji.minitvlauncher.ui.ImagePicker
import ji.minitvlauncher.ui.SystemScreen
import ji.minitvlauncher.ui.SystemStatus
import ji.minitvlauncher.ui.WallpaperState
import ji.minitvlauncher.wallpaper.Backdrop
import ji.minitvlauncher.wallpaper.WallpaperChoice
import ji.minitvlauncher.wallpaper.Wallpapers
import ji.minitvlauncher.wallpaper.mediaImages
import ji.minitvlauncher.wallpaper.mediaThumbnail
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.collectLatest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import ji.minitvlauncher.weather.Weather
import ji.minitvlauncher.weather.WeatherSource
import kotlinx.coroutines.delay
import ji.minitvlauncher.ui.theme.LauncherTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), HomeActions {
    private lateinit var repository: AppRepository
    private lateinit var settings: Settings
    private var apps by mutableStateOf<List<AppEntry>?>(null)
    private var homePresses by mutableIntStateOf(0)
    private var inputs by mutableStateOf(emptyList<TvInputItem>())
    private var notificationAccess by mutableStateOf(false)
    private lateinit var wallpapers: Wallpapers
    private var wallpaper by mutableStateOf(WallpaperState())
    private var pickerImages by mutableStateOf<List<Uri>?>(null)
    private var shownCustomStamp = 0L
    private lateinit var weatherSource: WeatherSource
    private var weather by mutableStateOf<Weather?>(null)
    private var onVoiceText: ((String) -> Unit)? = null
    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { onVoiceText?.invoke(it) }
        onVoiceText = null
    }
    private val speechIntent
        get() = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, getString(R.string.search_voice_prompt))

    override val voiceSearch: ((onText: (String) -> Unit) -> Unit)? by lazy {
        if (packageManager.resolveActivity(speechIntent, 0) == null) null
        else { onText -> onVoiceText = onText; runCatching { speech.launch(speechIntent) } }
    }

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) refreshWeather()
    }

    private val systemPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(::useAsWallpaper) }
    private val photoPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openOwnPicker() else Toast.makeText(this, R.string.picker_permission, Toast.LENGTH_LONG).show()
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AppRepository(applicationContext)
        settings = Settings(applicationContext)
        wallpapers = Wallpapers(applicationContext, settings)
        weatherSource = WeatherSource(applicationContext, settings)
        lifecycleScope.launch { weather = weatherSource.cached() }
        // Hourly while the home screen is showing; nothing runs while an app is in front.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    refreshWeather()
                    delay(15 * 60 * 1000L)
                }
            }
        }
        reload()
        lifecycleScope.launch { wallpapers.choice.collectLatest(::showWallpaper) }
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
                val lastInput by settings.lastInput.collectAsStateWithLifecycle(null)
                val bluetooth by remember { connectedAccessories(this@MainActivity) }.collectAsStateWithLifecycle(emptyList())
                val status = SystemStatus(
                    network = network,
                    notifications = notifications.takeIf { listening || notificationAccess },
                    hasSystemNotificationPanel = remember { packageManager.resolveActivity(systemNotificationsIntent, 0) != null },
                    inputs = inputs,
                    bluetoothDevices = bluetooth,
                    lastInput = lastInput,
                )
                HomeScreen(rows, repository, this@MainActivity, status, wallpaper, weather, homePresses)
                pickerImages?.let { images ->
                    ImagePicker(
                        images,
                        thumbnail = { mediaThumbnail(this@MainActivity, it) },
                        onPick = { pickerImages = null; useAsWallpaper(it) },
                        onDismiss = { pickerImages = null },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationAccess = NotificationReader.hasAccess(this)
        // A picture shared to the launcher while it was in the background.
        if (wallpaper.choice == WallpaperChoice.Custom && wallpapers.customStamp != shownCustomStamp) {
            lifecycleScope.launch { showWallpaper(WallpaperChoice.Custom) }
        }
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
        if (tryStart(switchToInputIntent(input))) {
            lifecycleScope.launch { settings.setLastInput(input.id) }
        } else {
            Toast.makeText(this, getString(R.string.launch_failed, input.label), Toast.LENGTH_SHORT).show()
        }
    }

    override fun openNotification(item: NotificationItem) = NotificationReader.open(item.key)

    /** Asks for location once, ever; without it the weather line simply stays hidden. */
    private fun refreshWeather() {
        lifecycleScope.launch {
            if (!weatherSource.hasLocationPermission()) {
                if (!settings.askedLocation()) {
                    settings.setAskedLocation()
                    locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                return@launch
            }
            weatherSource.refresh(System.currentTimeMillis(), resources.configuration.locales[0])?.let { weather = it }
        }
    }

    override fun selectWallpaper(choice: WallpaperChoice) {
        lifecycleScope.launch { wallpapers.select(choice) }
    }

    override fun pickWallpaperImage() {
        val getContent = Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        if (packageManager.resolveActivity(getContent, 0) != null) {
            runCatching { systemPicker.launch("image/*") }.onSuccess { return }
        }
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES
        else android.Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) openOwnPicker()
        else photoPermission.launch(permission)
    }

    override suspend fun wallpaperThumbnail(choice: WallpaperChoice): ImageBitmap? = wallpapers.thumbnail(choice)

    private fun openOwnPicker() {
        lifecycleScope.launch { pickerImages = mediaImages(this@MainActivity) }
    }

    private fun useAsWallpaper(uri: Uri) {
        Toast.makeText(this, R.string.wall_processing, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            if (wallpapers.setCustom(uri)) showWallpaper(WallpaperChoice.Custom)
            else Toast.makeText(this@MainActivity, R.string.wall_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** Paints the stored color at once, then the picture once it is decoded (or rebuilt). */
    private suspend fun showWallpaper(choice: WallpaperChoice) {
        if (choice == WallpaperChoice.Custom) shownCustomStamp = wallpapers.customStamp
        wallpaper = WallpaperState(choice, wallpapers.hasCustom, Backdrop(wallpapers.storedColor(choice), wallpaper.backdrop.frame.takeIf { wallpaper.choice == choice }))
        wallpaper = wallpaper.copy(backdrop = wallpapers.backdrop(choice))
    }

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
