package ji.minitvlauncher.ui

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import ji.minitvlauncher.wallpaper.Backdrop
import ji.minitvlauncher.weather.Weather
import ji.minitvlauncher.wallpaper.WallpaperChoice
import ji.minitvlauncher.wallpaper.WallpaperPipeline
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.pressKey
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import ji.minitvlauncher.AppEntry
import ji.minitvlauncher.AppImage
import ji.minitvlauncher.buildRows
import ji.minitvlauncher.system.InputSignal
import ji.minitvlauncher.system.NetKind
import ji.minitvlauncher.system.NetStatus
import ji.minitvlauncher.system.NotificationItem
import ji.minitvlauncher.system.TvInputItem
import ji.minitvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import java.util.Calendar

/** Renders the home screen at the TV's 1920×1080 to build/outputs/roborazzi for visual review. */
@OptIn(ExperimentalTestApi::class, com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "vi-w960dp-h540dp-land-xhdpi")
class HomeScreenScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val apps = listOf(
        "Truyền hình" to ApplicationInfo.CATEGORY_VIDEO,
        "Phim" to ApplicationInfo.CATEGORY_VIDEO,
        "Video ngắn" to ApplicationInfo.CATEGORY_VIDEO,
        "Thể thao trực tiếp" to ApplicationInfo.CATEGORY_VIDEO,
        "Âm nhạc và podcast cho cả gia đình" to ApplicationInfo.CATEGORY_AUDIO,
        "Radio FM" to ApplicationInfo.CATEGORY_AUDIO,
        "Trò chơi giải trí dành cho thiếu nhi" to ApplicationInfo.CATEGORY_GAME,
        "Cờ tướng" to ApplicationInfo.CATEGORY_GAME,
        "Ảnh" to ApplicationInfo.CATEGORY_IMAGE,
        "Tin tức 24h" to ApplicationInfo.CATEGORY_NEWS,
        "Trình quản lý tệp và bộ nhớ" to ApplicationInfo.CATEGORY_PRODUCTIVITY,
        "Trình duyệt web" to ApplicationInfo.CATEGORY_UNDEFINED,
        "Cửa hàng ứng dụng" to ApplicationInfo.CATEGORY_UNDEFINED,
    ).mapIndexed { i, (label, cat) ->
        AppEntry(ComponentName("pkg.app$i", "pkg.app$i.Main"), label, isLeanback = true, category = cat)
    }

    /** Every other app gets a gradient "banner"; the rest show the icon-less fallback tile. */
    private val images = object : AppImageSource {
        override fun cachedImage(entry: AppEntry): AppImage? = null
        override suspend fun loadImage(entry: AppEntry, widthPx: Int, heightPx: Int): AppImage? {
            if (apps.indexOf(entry) % 2 == 1) return null
            val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val hue = (apps.indexOf(entry) * 47 % 360).toFloat()
            val paint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, widthPx.toFloat(), heightPx.toFloat(),
                    android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.6f, 0.8f)),
                    android.graphics.Color.HSVToColor(floatArrayOf((hue + 60) % 360, 0.7f, 0.4f)),
                    Shader.TileMode.CLAMP,
                )
            }
            Canvas(bmp).drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), paint)
            return AppImage(bmp.asImageBitmap(), isBanner = true)
        }
    }

    private var favorites by mutableStateOf(emptyList<ComponentName>())
    private val launched = mutableListOf<AppEntry>()
    private val actions = object : HomeActions {
        override fun launch(app: AppEntry) { launched += app }
        override fun setFavorites(favorites: List<ComponentName>) { this@HomeScreenScreenshotTest.favorites = favorites }
        override fun showAppInfo(app: AppEntry) {}
        override fun openSystem(screen: SystemScreen) { opened += screen.name }
        override fun openSystemNotifications() = false
        override fun switchInput(input: TvInputItem) { opened += input.label }
        override fun openNotification(item: NotificationItem) { opened += item.title }
        override fun clearNotifications() {}
        override fun selectWallpaper(choice: WallpaperChoice) { wallpaper = wallpaper.copy(choice = choice) }
        override fun pickWallpaperImage() { opened += "picker" }
        override val voiceSearch: ((onText: (String) -> Unit) -> Unit)? = { onText -> onText("phim") }
        override suspend fun wallpaperThumbnail(choice: WallpaperChoice) =
            choice.asset?.let { a -> context.assets.open(a).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 }) }?.asImageBitmap() }
    }
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private var wallpaper by mutableStateOf(WallpaperState())
    private var weather by mutableStateOf<Weather?>(null)
    private val opened = mutableListOf<String>()
    private var status by mutableStateOf(SystemStatus())

    private val fixedTime = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 22, 20, 45) }.time

    private fun show(favoriteApps: List<AppEntry>) {
        favorites = favoriteApps.map { it.component }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LauncherTheme {
                HomeScreen(buildRows(apps, favorites), images, actions, status, wallpaper, weather, now = { fixedTime })
            }
        }
        settle()
    }

    /** Popups attach their window through the main looper, which the compose clock doesn't drive. */
    private fun settle() {
        // State changed from the test thread must be announced before the clock moves.
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        repeat(3) {
            compose.mainClock.advanceTimeBy(500)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    /** Keys go to whichever window has focus: the menu popup while it is open. */
    private fun press(keys: List<Key>) = keys.forEach { key ->
        val popup = compose.onAllNodes(isPopup()).fetchSemanticsNodes().isNotEmpty()
        (if (popup) compose.onNode(isPopup()) else compose.onNode(isRoot())).performKeyInput { pressKey(key) }
        settle()
    }

    private fun shot(name: String) = compose.onNode(isRoot()).captureRoboImage("build/outputs/roborazzi/$name.png")

    @Test
    fun withFavorites() {
        show(apps.filterIndexed { i, _ -> i in listOf(0, 1, 4, 8, 11) })
        shot("home_favorites")
    }

    @Test
    fun emptyFavorites() {
        show(emptyList())
        shot("home_empty_favorites")
    }

    @Test
    fun scrolledDown() {
        show(apps.take(3))
        press(listOf(Key.DirectionDown, Key.DirectionRight))
        shot("home_scrolled")
    }

    @Test
    fun menuOnCategoryAppAddsFavorite() {
        show(apps.take(2))
        press(listOf(Key.DirectionDown, Key.Menu))
        captureScreenRoboImage("build/outputs/roborazzi/menu_category_app.png")

        press(listOf(Key.DirectionDown, Key.DirectionCenter)) // "Thêm vào ưa thích"
        assertEquals(apps.take(2).map { it.component } + apps[2].component, favorites)
        shot("menu_added_toast")
    }

    @Test
    fun arrangeMovesFavoriteAndBackFinishes() {
        show(apps.take(3))
        press(listOf(Key.Menu))
        captureScreenRoboImage("build/outputs/roborazzi/menu_favorite.png")

        press(listOf(Key.DirectionDown, Key.DirectionCenter)) // "Sắp xếp"
        press(listOf(Key.DirectionRight))
        assertEquals(listOf(apps[1], apps[0], apps[2]).map { it.component }, favorites)
        shot("arranging")

        press(listOf(Key.Back))
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf(apps[0]), launched)
    }

    @Test
    fun removingFavoriteKeepsFocusOnTheApp() {
        show(apps.take(2))
        compose.onNodeWithText("Phim").assertExists()
        press(listOf(Key.Menu, Key.DirectionDown, Key.DirectionDown, Key.DirectionCenter)) // "Xóa ưa thích"
        assertEquals(listOf(apps[1].component), favorites)
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf(apps[0]), launched)
    }

    private val sampleNotifications = listOf(
        NotificationItem("a", "Hệ thống", fixedTime.time - 10 * 60_000, "Có bản cập nhật phần mềm", "Cài đặt khi TV không sử dụng", true),
        NotificationItem("b", "Phim", fixedTime.time - 60 * 60_000, "Đã có tập mới", "Tên phim · Tập 12", true),
    )

    @Test
    fun topBarTooltipAndBadge() {
        status = SystemStatus(
            network = NetStatus(NetKind.Wifi, "Nha_Minh_5G"),
            notifications = sampleNotifications,
            inputs = listOf(TvInputItem("hdmi1", "HDMI 1", true)),
        )
        show(apps.take(2))
        press(listOf(Key.DirectionUp))
        shot("topbar_focused")
    }

    @Test
    fun topBarOfflineWithoutNotificationAccess() {
        status = SystemStatus(network = NetStatus(NetKind.Offline))
        show(apps.take(2))
        press(listOf(Key.DirectionUp))
        shot("topbar_offline")
    }

    @Test
    fun notificationPanelOpensAndForwardsClicks() {
        status = SystemStatus(notifications = sampleNotifications)
        show(apps.take(2))
        // Up reaches the search pill; Right enters the top bar at Bluetooth (no inputs here); the bell is two further.
        press(listOf(Key.DirectionUp, Key.DirectionRight, Key.DirectionRight, Key.DirectionRight))
        compose.onNodeWithText("2 thông báo mới").assertExists()
        press(listOf(Key.DirectionCenter))
        captureScreenRoboImage("build/outputs/roborazzi/panel_notifications.png")
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf("Có bản cập nhật phần mềm"), opened)
    }

    @Test
    fun inputPanel() {
        status = SystemStatus(
            inputs = listOf(
                TvInputItem("h1", "HDMI 1", true),
                TvInputItem("h2", "HDMI 2", true, InputSignal.None),
                TvInputItem("tv", "Ăng-ten", false),
            ),
            lastInput = "h1",
        )
        show(apps.take(2))
        press(listOf(Key.DirectionUp, Key.DirectionRight)) // Inputs is the first top-bar button
        press(listOf(Key.DirectionCenter))
        captureScreenRoboImage("build/outputs/roborazzi/panel_inputs.png")
        press(listOf(Key.DirectionDown, Key.DirectionCenter))
        assertEquals(listOf("HDMI 2"), opened)
    }

    private fun dusk(): WallpaperState {
        val src = context.assets.open("wallpapers/dusk.jpg").use(BitmapFactory::decodeStream)
        val result = WallpaperPipeline.process(src)
        return WallpaperState(WallpaperChoice.Dusk, false, Backdrop(result.color, result.frame.asImageBitmap()))
    }

    @Test
    fun wallpaperBehindHome() {
        wallpaper = dusk()
        show(apps.filterIndexed { i, _ -> i in listOf(0, 1, 4, 8, 11) })
        shot("wallpaper_home")
        press(listOf(Key.DirectionDown))
        shot("wallpaper_scrolled")
    }

    @Test
    fun wallpaperPanelSelects() {
        wallpaper = dusk()
        show(apps.take(2))
        press(listOf(Key.DirectionUp))
        repeat(7) { press(listOf(Key.DirectionRight)) }
        press(listOf(Key.DirectionLeft, Key.DirectionCenter)) // Wallpaper sits left of Settings
        captureScreenRoboImage("build/outputs/roborazzi/panel_wallpaper.png")
        press(listOf(Key.DirectionRight, Key.DirectionCenter))
        assertEquals(WallpaperChoice.Teal, wallpaper.choice)
    }

    @Test
    fun weatherLineFreshAndStale() {
        wallpaper = dusk()
        weather = Weather(24, 27, 21, 2, isDay = true, place = "Hà Nội", fetchedAt = fixedTime.time - 5 * 60_000)
        show(apps.take(3))
        shot("weather_fresh")
        weather = weather!!.copy(code = 61, fetchedAt = fixedTime.time - 3 * 60 * 60_000)
        settle()
        shot("weather_stale")
    }

    @Test
    fun searchByTypingWithoutAccents() {
        wallpaper = dusk()
        show(apps)
        press(listOf(Key.DirectionUp)) // the pill is above the first tile
        press(listOf(Key.DirectionCenter))
        captureScreenRoboImage("build/outputs/roborazzi/search_all.png")
        compose.onNode(hasSetTextAction()).performTextInput("tro choi")
        settle()
        captureScreenRoboImage("build/outputs/roborazzi/search_typed.png")
        compose.onNodeWithText("1 ứng dụng phù hợp").assertExists()
    }

    @Test
    fun voiceResultFillsTheQuery() {
        show(apps)
        press(listOf(Key.DirectionUp, Key.DirectionCenter))
        press(listOf(Key.DirectionLeft, Key.DirectionCenter)) // mic sits left of the field
        compose.onNodeWithText("phim").assertExists()
        captureScreenRoboImage("build/outputs/roborazzi/search_voice.png")
    }


    /** French has no translation yet, so the launcher falls back to English; dates still follow the system. */
    @Test
    @Config(qualifiers = "fr-w960dp-h540dp-land-xhdpi")
    fun untranslatedLanguageFallsBackToEnglish() {
        show(apps.take(2))
        compose.onNodeWithText("Favorites").assertExists()
        compose.onNodeWithText("Apps").assertExists()
        compose.onNodeWithText("jeudi 22 octobre").assertExists()
        shot("language_fallback_fr")
    }

    @Test
    fun bluetoothTooltipNamesAccessories() {
        status = SystemStatus(bluetoothDevices = listOf("Loa soundbar phòng khách"))
        show(apps.take(2))
        press(listOf(Key.DirectionUp, Key.DirectionRight))
        compose.onNodeWithText("Loa soundbar phòng khách").assertExists()
        status = SystemStatus(bluetoothDevices = listOf("Loa", "Tay cầm"))
        settle()
        compose.onNodeWithText("2 thiết bị đã kết nối").assertExists()
    }

    @Test
    fun voiceKeyThenPauseClosesSearchButAPlainPauseDoesNot() {
        // Drive the lifecycle the search screen observes through a test owner.
        val owner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
            override val lifecycle get() = registry
        }
        var open by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LauncherTheme {
                    if (open) SearchScreen(apps, images, voiceQuery = null, onVoice = null, onLaunch = {}, onDismiss = { open = false })
                }
            }
        }
        settle()
        // Something else takes over (e.g. a dialog): search stays.
        owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
        settle()
        assertEquals(true, open)
        owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
        settle()
        // The remote's voice key, then the Assistant covers us: search closes.
        compose.onNode(isPopup()).performKeyInput { pressKey(Key.Search) }
        owner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
        settle()
        assertEquals(false, open)
    }

    /** Long-pressing OK opens the menu with OK still held: its repeats and release must not pick "Open". */
    @Test
    fun heldOkThatOpenedTheMenuDoesNotSelectInIt() {
        show(apps.take(2))
        press(listOf(Key.Menu))
        // What reaches the menu from the press that opened it: auto-repeats, then the release.
        val code = android.view.KeyEvent.KEYCODE_DPAD_CENTER
        val popup = compose.onNode(isPopup())
        popup.performKeyPress(androidx.compose.ui.input.key.KeyEvent(android.view.KeyEvent(0, 600, android.view.KeyEvent.ACTION_DOWN, code, 12)))
        popup.performKeyPress(androidx.compose.ui.input.key.KeyEvent(android.view.KeyEvent(0, 650, android.view.KeyEvent.ACTION_UP, code, 0)))
        settle()
        assertEquals(emptyList<AppEntry>(), launched)
        compose.onNode(isPopup()).assertExists()
        // A deliberate press inside the menu still works.
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf(apps[0]), launched)
    }

    /** The tooltip's text node; the pill around it adds 16 px each side and 8 px above and below. */
    private fun boundsOf(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

    /** Design changelog 7: centred under the button, nudged only to stay 48 px from the screen edge. */
    @Test
    fun tooltipsCentreAndKeepTheSafeEdge() {
        val longName = "Nha_Minh_5G_Tang_3_Phong_Khach_Ten_Wifi_Rat_Rat_Rat_Dai_Qua"
        status = SystemStatus(network = NetStatus(NetKind.Wifi, longName))
        show(apps.take(2))
        press(listOf(Key.DirectionUp, Key.DirectionRight)) // pill → Bluetooth
        val bt = boundsOf("Bluetooth và phụ kiện")
        val btButtonCentre = 1484f + 32f // first of four 64 px buttons ending at 1800, 20 apart
        assertEquals(btButtonCentre, bt.center.x, 1.5f)
        assertEquals(64f + 80f + 8f, bt.top, 1.5f)
        press(listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionRight)) // → Settings
        val settings = boundsOf("Cài đặt")
        assertEquals(1736f + 32f, settings.center.x, 1.5f)
        press(listOf(Key.DirectionLeft, Key.DirectionLeft)) // → network, long Wi‑Fi name
        val net = boundsOf(longName)
        shot("tooltip_long_wifi")
        // Its centred position would run past the edge; it stops exactly 48 px short of it.
        val pillRight = net.right + 16f
        assertTrue("right edge $pillRight", pillRight <= 1920f - 48f + 0.5f && pillRight >= 1920f - 48f - 1.5f)
    }

    @Test
    fun searchButtonsHaveTooltips() {
        show(apps.take(2))
        press(listOf(Key.DirectionUp, Key.DirectionCenter, Key.DirectionLeft)) // search → mic
        compose.onNodeWithText("Tìm bằng giọng nói").assertExists()
        val mic = boundsOf("Tìm bằng giọng nói")
        // Centred on the mic (x 120..184) unless that would cross the 48 px edge; either way inside it.
        assertTrue("left ${mic.left - 16f}", mic.left - 16f >= 48f - 0.5f)
        captureScreenRoboImage("build/outputs/roborazzi/search_tooltip.png")
    }

    private fun topBarTop() = compose.onNode(androidx.compose.ui.test.hasContentDescription("Bluetooth và phụ kiện")).fetchSemanticsNode().boundsInRoot.top

    /** Design changelog 9c: hides scrolling down; scrolling up shows it only while Favorites is clear of it. */
    @Test
    fun topBarHidesOnScrollDownAndReturns() {
        show(apps.take(3)) // rows: Favorites, Apps, Games
        assertEquals(64f, topBarTop(), 1f)
        press(listOf(Key.DirectionDown)) // Apps row: scrolled down
        assertTrue("hidden at ${topBarTop()}", topBarTop() < 0f)
        press(listOf(Key.DirectionDown)) // Games row: further down, still hidden
        assertTrue(topBarTop() < 0f)
        press(listOf(Key.DirectionUp)) // back to Apps: Favorites' title is 158 px from the top, clear of the bar
        assertEquals(64f, topBarTop(), 1f)
        press(listOf(Key.DirectionUp)) // top of the page
        assertEquals(64f, topBarTop(), 1f)
    }

    private fun focusedBounds() =
        compose.onAllNodes(androidx.compose.ui.test.isFocused(), useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot

    /** The rows fill the screen behind the top bar; Down from any of its buttons must return to them. */
    @Test
    fun downFromTopBarReturnsToTheTileFocusCameFrom() {
        show(apps.take(3))
        press(listOf(Key.DirectionRight)) // second favorite
        val tile = focusedBounds()
        press(listOf(Key.DirectionUp)) // into the top bar
        assertTrue(focusedBounds().top < 130f)
        press(listOf(Key.DirectionDown))
        assertEquals(tile, focusedBounds())
        press(listOf(Key.DirectionUp, Key.DirectionRight, Key.DirectionRight, Key.DirectionDown)) // from a right-hand button
        assertEquals(tile, focusedBounds())
    }

    /** Five tiles fit across: focusing the fifth must not scroll the row. */
    @Test
    fun fifthTileFocusDoesNotScrollTheRow() {
        show(apps.take(6))
        fun left(label: String) = compose.onNode(androidx.compose.ui.test.hasContentDescription(label)).fetchSemanticsNode().boundsInRoot.left
        val first = apps[0].label
        assertEquals(120f, left(first), 1f)
        repeat(4) { press(listOf(Key.DirectionRight)) }
        shot("fifth_tile_focused")
        assertEquals("row scrolled when focusing the fifth tile", 120f, left(first), 1f)
    }
}
