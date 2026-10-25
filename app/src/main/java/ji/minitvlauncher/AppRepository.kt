package ji.minitvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import ji.minitvlauncher.ui.AppImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

data class AppEntry(
    val component: ComponentName,
    val label: String,
    val isLeanback: Boolean,
    /** `ApplicationInfo.category`, i.e. the app's `android:appCategory`. */
    val category: Int,
)

/** A card image: the app's 16:9 banner, or its icon to be drawn on a category-colored tile. */
data class AppImage(val bitmap: ImageBitmap, val isBanner: Boolean)

class AppRepository(private val context: Context) : AppImageSource {
    private val pm = context.packageManager

    /** Decoded card images, so scrolling back over a row doesn't re-render drawables. */
    private val images = object : LruCache<ComponentName, AppImage>(16 * 1024 * 1024) {
        override fun sizeOf(key: ComponentName, value: AppImage) = value.bitmap.width * value.bitmap.height * 4
    }

    override fun cachedImage(entry: AppEntry): AppImage? = images.get(entry.component)

    /** Apps with a TV entry point first; phone-only apps are included only if they have no TV one. */
    fun loadApps(): List<AppEntry> {
        images.evictAll()
        val leanback = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val leanbackPackages = leanback.mapTo(HashSet()) { it.activityInfo.packageName }
        val phoneOnly = query(Intent.CATEGORY_LAUNCHER)
            .filter { it.activityInfo.packageName !in leanbackPackages }

        val collator = Collator.getInstance()
        return (leanback.map { it.toEntry(isLeanback = true) } + phoneOnly.map { it.toEntry(isLeanback = false) })
            .filter { it.component.packageName != context.packageName }
            .distinctBy { it.component }
            .sortedWith(compareBy(collator) { it.label })
    }

    /** Rendered at display size to keep memory low; null if the app vanished meanwhile. */
    override suspend fun loadImage(entry: AppEntry, widthPx: Int, heightPx: Int): AppImage? =
        images.get(entry.component) ?: withContext(Dispatchers.IO) {
            runCatching { renderImage(entry, widthPx, heightPx) }.getOrNull()
        }?.also { images.put(entry.component, it) }

    private fun renderImage(entry: AppEntry, widthPx: Int, heightPx: Int): AppImage {
        val info = pm.getActivityInfo(entry.component, 0)
        val banner: Drawable? = info.loadBanner(pm) ?: info.applicationInfo.loadBanner(pm)
        return if (banner != null) {
            AppImage(banner.toBitmap(widthPx, heightPx).asImageBitmap(), isBanner = true)
        } else {
            val side = heightPx * 4 / 9
            AppImage(info.loadIcon(pm).toBitmap(side, side).asImageBitmap(), isBanner = false)
        }
    }

    fun launchIntent(entry: AppEntry): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(if (entry.isLeanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setComponent(entry.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    private fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    private fun ResolveInfo.toEntry(isLeanback: Boolean) = AppEntry(
        component = ComponentName(activityInfo.packageName, activityInfo.name),
        label = loadLabel(pm).toString(),
        isLeanback = isLeanback,
        category = activityInfo.applicationInfo.category,
    )
}
