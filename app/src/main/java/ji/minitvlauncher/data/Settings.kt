package ji.minitvlauncher.data

import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

/** User choices that must survive restarts. Everything else is derived from the system. */
class Settings(private val context: Context) {

    /** Favorite apps in the user's order. */
    val favorites: Flow<List<ComponentName>> = context.dataStore.data.map { prefs ->
        prefs[FAVORITES].orEmpty().lines().mapNotNull { ComponentName.unflattenFromString(it) }
    }

    suspend fun setFavorites(favorites: List<ComponentName>) {
        context.dataStore.edit { it[FAVORITES] = favorites.joinToString("\n") { c -> c.flattenToString() } }
    }

    /** Key of the chosen wallpaper; null until the user picks one. */
    val wallpaper: Flow<String?> = context.dataStore.data.map { it[WALLPAPER] }

    suspend fun setWallpaper(key: String) {
        context.dataStore.edit { it[WALLPAPER] = key }
    }

    /** Backdrop color computed for a wallpaper, and the pipeline version that computed it. */
    suspend fun wallpaperColor(key: String, version: Int): Int? =
        context.dataStore.data.map { if (it[colorVersion(key)] == version) it[color(key)] else null }.first()

    suspend fun setWallpaperColor(key: String, version: Int, color: Int) {
        context.dataStore.edit {
            it[color(key)] = color
            it[colorVersion(key)] = version
        }
    }

    private companion object {
        val FAVORITES = stringPreferencesKey("favorites")
        val WALLPAPER = stringPreferencesKey("wallpaper")
        fun color(key: String) = intPreferencesKey("wallpaper_color_$key")
        fun colorVersion(key: String) = intPreferencesKey("wallpaper_version_$key")
    }
}
