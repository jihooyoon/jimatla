package ji.minitvlauncher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import ji.minitvlauncher.wallpaper.WallpaperPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperPipelineTest {
    private fun solid(color: Int) = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test
    fun `grey pictures get the neutral backdrop`() {
        val result = WallpaperPipeline.process(solid(Color.rgb(128, 128, 128)))
        assertEquals(WallpaperPipeline.NEUTRAL, result.color)
    }

    @Test
    fun `backdrop keeps the picture's hue at low lightness`() {
        val result = WallpaperPipeline.process(solid(Color.rgb(200, 40, 40)))
        val hsl = FloatArray(3).also { androidx.core.graphics.ColorUtils.colorToHSL(result.color, it) }
        assertTrue("hue ${hsl[0]}", hsl[0] < 2f || hsl[0] > 358f)
        assertEquals(0.075f, hsl[2], 0.01f)
    }

    @Test
    fun `frame fades to the backdrop at its left and bottom edges and shows the picture top right`() {
        val result = WallpaperPipeline.process(solid(Color.rgb(230, 200, 60)))
        val f = result.frame
        assertEquals(WallpaperPipeline.FRAME_WIDTH, f.width)
        assertEquals(WallpaperPipeline.FRAME_HEIGHT, f.height)
        fun near(a: Int, b: Int) = abs(Color.red(a) - Color.red(b)) <= 2 && abs(Color.green(a) - Color.green(b)) <= 2 && abs(Color.blue(a) - Color.blue(b)) <= 2
        assertTrue(near(f.getPixel(0, f.height / 2), result.color))
        assertTrue(near(f.getPixel(f.width / 2, f.height - 1), result.color))
        assertTrue(near(f.getPixel(f.width - 10, 300), Color.rgb(230, 200, 60)))
    }

    /** Renders the bundled samples for a visual check in build/outputs/wallpapers. */
    @Test
    fun `render bundled samples`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val out = File("build/outputs/wallpapers").apply { mkdirs() }
        for (name in listOf("dusk", "teal", "desert")) {
            val src = context.assets.open("wallpapers/$name.jpg").use(BitmapFactory::decodeStream)
            val result = WallpaperPipeline.process(src)
            val full = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888).apply { eraseColor(result.color) }
            android.graphics.Canvas(full).drawBitmap(result.frame, WallpaperPipeline.FRAME_LEFT.toFloat(), 0f, null)
            File(out, "$name.png").outputStream().use { full.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
