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
import java.text.Collator

data class AppEntry(
    val component: ComponentName,
    val label: String,
    val isLeanback: Boolean,
)

class AppRepository(private val context: Context) {
    private val pm = context.packageManager

    /** Decoded card images, so scrolling back over a row doesn't re-render drawables. */
    private val images = object : LruCache<ComponentName, Pair<ImageBitmap, Boolean>>(16 * 1024 * 1024) {
        override fun sizeOf(key: ComponentName, value: Pair<ImageBitmap, Boolean>) =
            value.first.width * value.first.height * 4
    }

    fun cachedImage(entry: AppEntry): Pair<ImageBitmap, Boolean>? = images.get(entry.component)

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

    /** Banner (16:9) if the app has one, otherwise its icon; rendered at display size to keep memory low. */
    fun loadImage(entry: AppEntry, widthPx: Int, heightPx: Int): Pair<ImageBitmap, Boolean> =
        images.get(entry.component) ?: renderImage(entry, widthPx, heightPx).also { images.put(entry.component, it) }

    private fun renderImage(entry: AppEntry, widthPx: Int, heightPx: Int): Pair<ImageBitmap, Boolean> {
        val info = pm.getActivityInfo(entry.component, 0)
        val banner: Drawable? = info.loadBanner(pm) ?: info.applicationInfo.loadBanner(pm)
        return if (banner != null) {
            banner.toBitmap(widthPx, heightPx).asImageBitmap() to true
        } else {
            val side = heightPx / 2
            info.loadIcon(pm).toBitmap(side, side).asImageBitmap() to false
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
    )
}
