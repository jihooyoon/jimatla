package ji.minitvlauncher.ui

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
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
import androidx.compose.ui.test.pressKey
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import ji.minitvlauncher.AppEntry
import ji.minitvlauncher.AppImage
import ji.minitvlauncher.buildRows
import ji.minitvlauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import java.util.Calendar

/** Renders the home screen at the TV's 1920×1080 to build/outputs/roborazzi for visual review. */
@OptIn(ExperimentalTestApi::class)
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
    }

    private val fixedTime = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 22, 20, 45) }.time

    private fun show(favoriteApps: List<AppEntry>) {
        favorites = favoriteApps.map { it.component }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LauncherTheme {
                HomeScreen(buildRows(apps, favorites), images, actions, now = { fixedTime })
            }
        }
        settle()
    }

    /** Popups attach their window through the main looper, which the compose clock doesn't drive. */
    private fun settle() {
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
}
