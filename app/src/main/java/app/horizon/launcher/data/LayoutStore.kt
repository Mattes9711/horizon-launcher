package app.horizon.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.layoutDataStore: DataStore<Preferences> by preferencesDataStore(name = "layout")

/** Saved home-screen layout: top row, order of everything else, hidden apps. */
data class SavedLayout(val favorites: List<String>?, val order: List<String>, val hidden: Set<String>)

class LayoutStore(private val context: Context) {

    private val favKey = stringPreferencesKey("favorites")
    private val orderKey = stringPreferencesKey("order")
    private val hiddenKey = stringPreferencesKey("hidden")
    private val soundsKey = booleanPreferencesKey("sounds")

    val layout: Flow<SavedLayout> = context.layoutDataStore.data.map { p ->
        SavedLayout(
            favorites = p[favKey]?.let { split(it) } ?: legacyFavorites(),
            order = p[orderKey]?.let { split(it) } ?: emptyList(),
            hidden = p[hiddenKey]?.let { split(it).toSet() } ?: emptySet(),
        )
    }

    val sounds: Flow<Boolean> = context.layoutDataStore.data.map { it[soundsKey] ?: true }

    suspend fun save(favorites: List<String>, order: List<String>) {
        context.layoutDataStore.edit {
            it[favKey] = favorites.joinToString(",")
            it[orderKey] = order.joinToString(",")
        }
    }

    suspend fun setHidden(hidden: Set<String>) {
        context.layoutDataStore.edit { it[hiddenKey] = hidden.joinToString(",") }
    }

    suspend fun setSounds(on: Boolean) {
        context.layoutDataStore.edit { it[soundsKey] = on }
    }

    /** Favourites saved by the first (Java) version of the launcher. */
    private fun legacyFavorites(): List<String>? =
        context.getSharedPreferences("horizon", Context.MODE_PRIVATE)
            .getString("favs", null)?.let { split(it) }

    private fun split(s: String): List<String> = s.split(',').filter { it.isNotBlank() }
}
