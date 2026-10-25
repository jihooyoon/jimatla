package ji.minitvlauncher.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Turns a picture into the home screen backdrop, once, when it is chosen (see the wallpaper spec
 * note in docs/dev/design/canvas.json). The launcher then only paints a color and draws a bitmap.
 *
 * The result covers just the frame in the top-right corner. Its left and bottom edges fade fully
 * into the backdrop color, so the rest of the screen is that color and need not be stored.
 */
object WallpaperPipeline {
    /** Bump when the output changes, so stored wallpapers are rebuilt from their originals. */
    const val VERSION = 1

    // Frame in 1920×1080 screen pixels: from the middle of the big clock to the right edge.
    const val FRAME_LEFT = 290
    const val FRAME_WIDTH = 1920 - FRAME_LEFT
    const val FRAME_HEIGHT = 760

    /** Used when the picture is too grey to suggest a hue. */
    const val NEUTRAL = 0xFF0E0F11.toInt()

    private const val MASK_SOLID = 0.42f
    private const val SCRIM_HEIGHT = 170
    private const val SCRIM_ALPHA = 0.45f

    class Result(val frame: Bitmap, val color: Int)

    fun process(source: Bitmap, random: Random = Random(0)): Result {
        val frame = Bitmap.createBitmap(FRAME_WIDTH, FRAME_HEIGHT, Bitmap.Config.ARGB_8888)
        drawCover(source, frame)
        val color = backdropColor(frame)
        compose(frame, color, random)
        return Result(frame, color)
    }

    /** Scales [source] to cover [target] and centers it, like CSS object-fit: cover. */
    private fun drawCover(source: Bitmap, target: Bitmap) {
        val scale = max(target.width / source.width.toFloat(), target.height / source.height.toFloat())
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((target.width - source.width * scale) / 2f, (target.height - source.height * scale) / 2f)
        }
        Canvas(target).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /**
     * Mean hue of the visible picture weighted by saturation × brightness, at 7.5 % lightness and
     * 38 % saturation. Hue is an angle, so it is averaged as a vector.
     */
    fun backdropColor(frame: Bitmap): Int {
        val step = 8
        val hsv = FloatArray(3)
        var x = 0.0
        var y = 0.0
        var weight = 0.0
        var samples = 0
        for (py in 0 until frame.height step step) for (px in 0 until frame.width step step) {
            Color.colorToHSV(frame.getPixel(px, py), hsv)
            val w = (hsv[1] * hsv[2]).toDouble()
            val rad = Math.toRadians(hsv[0].toDouble())
            x += w * cos(rad)
            y += w * sin(rad)
            weight += w
            samples++
        }
        // Weak or cancelling hues (grey pictures, or opposite colors in equal measure) give no tint.
        if (samples == 0 || weight / samples < 0.03 || sqrt(x * x + y * y) / weight < 0.15) return NEUTRAL
        val hue = ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
        return ColorUtils.HSLToColor(floatArrayOf(hue, 0.38f, 0.075f))
    }

    /**
     * In place: the picture shows through an ellipse centered on the frame's top-right corner
     * (solid to 0.42 of the radius, then a smoothstep fade to nothing), the top edge darkens toward
     * the backdrop, and a little noise keeps the dark gradients from banding.
     */
    private fun compose(frame: Bitmap, color: Int, random: Random) {
        val w = frame.width
        val h = frame.height
        val px = IntArray(w * h)
        frame.getPixels(px, 0, w, 0, 0, w, h)
        val br = Color.red(color).toFloat()
        val bg = Color.green(color).toFloat()
        val bb = Color.blue(color).toFloat()
        for (yy in 0 until h) {
            val dy = yy / h.toFloat()
            val scrim = if (yy < SCRIM_HEIGHT) SCRIM_ALPHA * (1f - yy / SCRIM_HEIGHT.toFloat()) else 0f
            for (xx in 0 until w) {
                val dx = (w - xx) / w.toFloat()
                val d = sqrt(dx * dx + dy * dy)
                val t = ((d - MASK_SOLID) / (1f - MASK_SOLID)).coerceIn(0f, 1f)
                val a = 1f - t * t * (3f - 2f * t)
                val i = yy * w + xx
                val p = px[i]
                // Picture over backdrop through the mask, then the backdrop-colored scrim on top.
                var r = br + (Color.red(p) - br) * a
                var g = bg + (Color.green(p) - bg) * a
                var b = bb + (Color.blue(p) - bb) * a
                r += (br - r) * scrim
                g += (bg - g) * scrim
                b += (bb - b) * scrim
                // Triangular noise of about one step either way.
                val n = random.nextFloat() - random.nextFloat()
                px[i] = Color.rgb(dither(r, n), dither(g, n), dither(b, n))
            }
        }
        frame.setPixels(px, 0, w, 0, 0, w, h)
    }

    private fun dither(v: Float, noise: Float) = (v + noise).roundToInt().coerceIn(0, 255)
}
