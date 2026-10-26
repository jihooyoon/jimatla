package ji.minitvlauncher

import android.content.ComponentName
import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import java.text.Normalizer

/** Declared `android:appCategory`; only colors tiles that have no banner. */
@SuppressLint("InlinedApi") // CATEGORY_ACCESSIBILITY is a constant; older systems just never report it
enum class AppCategory(val code: Int, val fill: Color) {
    Video(ApplicationInfo.CATEGORY_VIDEO, Color(0xFF2B3A55)),
    Audio(ApplicationInfo.CATEGORY_AUDIO, Color(0xFF2F4A3C)),
    Game(ApplicationInfo.CATEGORY_GAME, Color(0xFF45305A)),
    Image(ApplicationInfo.CATEGORY_IMAGE, Color(0xFF54452A)),
    Social(ApplicationInfo.CATEGORY_SOCIAL, Color(0xFF5A2E44)),
    News(ApplicationInfo.CATEGORY_NEWS, Color(0xFF5A2E2E)),
    Productivity(ApplicationInfo.CATEGORY_PRODUCTIVITY, Color(0xFF2C4752)),
    Maps(ApplicationInfo.CATEGORY_MAPS, Color(0xFF2C4A47)),
    Accessibility(ApplicationInfo.CATEGORY_ACCESSIBILITY, Color(0xFF3F3A52)),
    Other(ApplicationInfo.CATEGORY_UNDEFINED, Color(0xFF3A3B40));

    companion object {
        fun of(code: Int): AppCategory = entries.firstOrNull { it.code == code } ?: Other
    }
}

val AppEntry.appCategory: AppCategory get() = AppCategory.of(category)

enum class RowKey(@StringRes val title: Int) {
    Favorites(R.string.row_favorites),
    Apps(R.string.row_apps),
    Games(R.string.row_games),
}

data class HomeRow(val key: RowKey, val apps: List<AppEntry>)

/**
 * Favorites first (always shown, even empty, so the user learns how to fill it), then Apps and
 * Games. A favorite appears only in Favorites; [apps] arrive sorted by name and keep that order.
 * Apps and Games are left out when empty.
 */
fun buildRows(apps: List<AppEntry>, favorites: List<ComponentName>): List<HomeRow> {
    val byComponent = apps.associateBy { it.component }
    val favoriteApps = favorites.mapNotNull { byComponent[it] }
    val favoriteSet = favoriteApps.mapTo(HashSet()) { it.component }
    val (games, others) = apps.filter { it.component !in favoriteSet }.partition { it.isGame }
    return listOf(HomeRow(RowKey.Favorites, favoriteApps)) +
        listOf(HomeRow(RowKey.Apps, others), HomeRow(RowKey.Games, games)).filter { it.apps.isNotEmpty() }
}

/** [item] swapped with its neighbor [delta] places away; unchanged at either end. */
fun List<ComponentName>.moved(item: ComponentName, delta: Int): List<ComponentName> {
    val from = indexOf(item)
    val to = from + delta
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** Lowercase, accents stripped and đ → d, so "phim" finds "Phim" and "dau" finds "Đầu". */
fun searchKey(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('đ', 'd').replace('Đ', 'D')
        .lowercase()
        .trim()

/** Every app matches an empty query; otherwise the name must contain it, accents ignored. */
fun AppEntry.matchesQuery(query: String): Boolean = searchKey(label).contains(searchKey(query))
