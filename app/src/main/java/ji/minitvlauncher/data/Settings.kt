package ji.minitvlauncher.data

import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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

    private companion object {
        val FAVORITES = stringPreferencesKey("favorites")
    }
}
