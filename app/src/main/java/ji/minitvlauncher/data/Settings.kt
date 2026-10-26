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

    /** Last weather result as JSON, so the line shows before the next fetch. */
    suspend fun weather(): String? = context.dataStore.data.map { it[WEATHER] }.first()

    suspend fun setWeather(json: String) {
        context.dataStore.edit { it[WEATHER] = json }
    }

    /** Whether the location permission was asked once already; the launcher never nags. */
    suspend fun askedLocation(): Boolean = context.dataStore.data.map { it[ASKED_LOCATION] == 1 }.first()

    suspend fun setAskedLocation() {
        context.dataStore.edit { it[ASKED_LOCATION] = 1 }
    }

    /**
     * Input last chosen from the launcher. Android has no public "current input" API, so this is
     * the best guess the input panel can mark; null if none yet.
     */
    val lastInput: Flow<String?> = context.dataStore.data.map { it[LAST_INPUT] }

    suspend fun setLastInput(id: String) {
        context.dataStore.edit { it[LAST_INPUT] = id }
    }

    private companion object {
        val LAST_INPUT = stringPreferencesKey("last_input")
        val WEATHER = stringPreferencesKey("weather")
        val ASKED_LOCATION = intPreferencesKey("asked_location")
        val FAVORITES = stringPreferencesKey("favorites")
        val WALLPAPER = stringPreferencesKey("wallpaper")
        fun color(key: String) = intPreferencesKey("wallpaper_color_$key")
        fun colorVersion(key: String) = intPreferencesKey("wallpaper_version_$key")
    }
}
