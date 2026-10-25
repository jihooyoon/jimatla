package ji.minitvlauncher.ui

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.github.takahirom.roborazzi.captureRoboImage
import ji.minitvlauncher.AppEntry
import ji.minitvlauncher.AppImage
import ji.minitvlauncher.buildRows
import ji.minitvlauncher.ui.theme.LauncherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/** Renders the home screen at the TV's 1920×1080 to build/outputs/roborazzi for visual review. */
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

    private val fixedTime = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 22, 20, 45) }.time

    private fun show(favorites: List<AppEntry>) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LauncherTheme {
                HomeScreen(buildRows(apps, favorites.map { it.component }), images, onLaunch = {}, now = { fixedTime })
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
    }

    @Test
    fun withFavorites() {
        show(apps.filterIndexed { i, _ -> i in listOf(0, 1, 4, 8, 11) })
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home_favorites.png")
    }

    @Test
    fun emptyFavorites() {
        show(emptyList())
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home_empty_favorites.png")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun scrolledDown() {
        show(apps.take(3))
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home_scrolled.png")
    }
}
