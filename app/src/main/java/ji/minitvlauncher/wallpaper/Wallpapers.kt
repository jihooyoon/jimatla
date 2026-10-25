package ji.minitvlauncher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import ji.minitvlauncher.R
import ji.minitvlauncher.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class WallpaperChoice(val key: String, val asset: String?, @StringRes val label: Int) {
    Dusk("dusk", "wallpapers/dusk.jpg", R.string.wall_dusk),
    Teal("teal", "wallpapers/teal.jpg", R.string.wall_teal),
    Desert("desert", "wallpapers/desert.jpg", R.string.wall_desert),
    None("none", null, R.string.wall_none),
    Custom("custom", null, R.string.wall_custom);

    companion object {
        val Default = Dusk
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Default
    }
}

/** What the home screen paints behind everything: a color, and maybe a picture in the corner. */
data class Backdrop(val color: Int, val frame: ImageBitmap? = null)

/**
 * Wallpapers as files: bundled samples and one custom picture, each processed once by
 * [WallpaperPipeline] and kept as a PNG next to its backdrop color in [Settings].
 */
class Wallpapers(private val context: Context, private val settings: Settings) {
    private val dir = File(context.filesDir, "wallpaper").apply { mkdirs() }
    private val customOriginal = File(dir, "custom_source.jpg")
    private val lock = Mutex()

    val choice: Flow<WallpaperChoice> = settings.wallpaper.map { key ->
        WallpaperChoice.of(key).let { if (it == WallpaperChoice.Custom && !customOriginal.exists()) WallpaperChoice.Default else it }
    }

    val hasCustom: Boolean get() = customOriginal.exists()

    /** Changes whenever a new custom picture is stored, including from the share target. */
    val customStamp: Long get() = customOriginal.lastModified()

    suspend fun select(choice: WallpaperChoice) = settings.setWallpaper(choice.key)

    /** The stored backdrop color, to paint before the picture is decoded. */
    suspend fun storedColor(choice: WallpaperChoice): Int =
        if (choice == WallpaperChoice.None) WallpaperPipeline.NEUTRAL
        else settings.wallpaperColor(choice.key, WallpaperPipeline.VERSION) ?: WallpaperPipeline.NEUTRAL

    /** Color first (cheap, stored), then the processed picture, rebuilding it if missing or outdated. */
    suspend fun backdrop(choice: WallpaperChoice): Backdrop = lock.withLock {
        withContext(Dispatchers.IO) {
            if (choice == WallpaperChoice.None) return@withContext Backdrop(WallpaperPipeline.NEUTRAL)
            val file = File(dir, "${choice.key}.png")
            var color = settings.wallpaperColor(choice.key, WallpaperPipeline.VERSION)
            if (color == null || !file.exists()) {
                val source = loadSource(choice) ?: return@withContext Backdrop(WallpaperPipeline.NEUTRAL)
                val result = WallpaperPipeline.process(source)
                source.recycle()
                file.outputStream().use { result.frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
                settings.setWallpaperColor(choice.key, WallpaperPipeline.VERSION, result.color)
                return@withContext Backdrop(result.color, result.frame.asImageBitmap())
            }
            Backdrop(color, BitmapFactory.decodeFile(file.path)?.asImageBitmap())
        }
    }

    /**
     * Makes [uri] the custom wallpaper: a downscaled copy replaces the previous one (only one
     * custom slot), it is processed, and selected. False if the picture can't be read.
     */
    suspend fun setCustom(uri: Uri): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            val bitmap = decodeScaled(uri) ?: return@withContext false
            File(dir, "${WallpaperChoice.Custom.key}.png").delete()
            customOriginal.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            bitmap.recycle()
            settings.setWallpaperColor(WallpaperChoice.Custom.key, -1, 0) // forces reprocessing
            true
        }
    }.also { if (it) select(WallpaperChoice.Custom) }

    /** Small preview for the wallpaper panel. */
    suspend fun thumbnail(choice: WallpaperChoice): ImageBitmap? = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        runCatching {
            when {
                choice.asset != null -> context.assets.open(choice.asset).use { BitmapFactory.decodeStream(it, null, options) }
                choice == WallpaperChoice.Custom -> BitmapFactory.decodeFile(customOriginal.path, options)
                else -> null
            }
        }.getOrNull()?.asImageBitmap()
    }

    private fun loadSource(choice: WallpaperChoice): Bitmap? = runCatching {
        when {
            choice.asset != null -> context.assets.open(choice.asset).use(BitmapFactory::decodeStream)
            choice == WallpaperChoice.Custom -> BitmapFactory.decodeFile(customOriginal.path)
            else -> null
        }
    }.getOrNull()

    /** Decodes no larger than needed to cover the frame twice over, to bound memory. */
    private fun decodeScaled(uri: Uri): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= WallpaperPipeline.FRAME_WIDTH &&
            bounds.outHeight / (sample * 2) >= WallpaperPipeline.FRAME_HEIGHT
        ) sample *= 2
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }.getOrNull()
}
