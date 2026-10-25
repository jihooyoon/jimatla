package ji.minitvlauncher

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color

/** A category row on the home screen; also gives tiles without a banner their color. */
enum class AppCategory(val code: Int, @StringRes val title: Int, val fill: Color) {
    Video(ApplicationInfo.CATEGORY_VIDEO, R.string.cat_video, Color(0xFF2B3A55)),
    Audio(ApplicationInfo.CATEGORY_AUDIO, R.string.cat_audio, Color(0xFF2F4A3C)),
    Game(ApplicationInfo.CATEGORY_GAME, R.string.cat_game, Color(0xFF45305A)),
    Image(ApplicationInfo.CATEGORY_IMAGE, R.string.cat_image, Color(0xFF54452A)),
    Social(ApplicationInfo.CATEGORY_SOCIAL, R.string.cat_social, Color(0xFF5A2E44)),
    News(ApplicationInfo.CATEGORY_NEWS, R.string.cat_news, Color(0xFF5A2E2E)),
    Productivity(ApplicationInfo.CATEGORY_PRODUCTIVITY, R.string.cat_productivity, Color(0xFF2C4752)),
    Maps(ApplicationInfo.CATEGORY_MAPS, R.string.cat_maps, Color(0xFF2C4A47)),
    Accessibility(ApplicationInfo.CATEGORY_ACCESSIBILITY, R.string.cat_accessibility, Color(0xFF3F3A52)),
    Other(ApplicationInfo.CATEGORY_UNDEFINED, R.string.cat_other, Color(0xFF3A3B40));

    companion object {
        fun of(code: Int): AppCategory = entries.firstOrNull { it.code == code } ?: Other
    }
}

val AppEntry.appCategory: AppCategory get() = AppCategory.of(category)

sealed interface RowKey {
    data object Favorites : RowKey
    data class Category(val category: AppCategory) : RowKey
}

data class HomeRow(val key: RowKey, val apps: List<AppEntry>)

/**
 * Favorites first (always shown, even empty, so the user learns how to fill it),
 * then one row per category in [AppCategory] order, skipping favorites and empty categories.
 */
fun buildRows(apps: List<AppEntry>, favorites: List<ComponentName>): List<HomeRow> {
    val byComponent = apps.associateBy { it.component }
    val favoriteApps = favorites.mapNotNull { byComponent[it] }
    val favoriteSet = favoriteApps.mapTo(HashSet()) { it.component }
    val rest = apps.filter { it.component !in favoriteSet }.groupBy { it.appCategory }
    return listOf(HomeRow(RowKey.Favorites, favoriteApps)) +
        AppCategory.entries.mapNotNull { cat -> rest[cat]?.let { HomeRow(RowKey.Category(cat), it) } }
}

/** [item] swapped with its neighbor [delta] places away; unchanged at either end. */
fun List<ComponentName>.moved(item: ComponentName, delta: Int): List<ComponentName> {
    val from = indexOf(item)
    val to = from + delta
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
