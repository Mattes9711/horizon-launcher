package app.horizon.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.layoutDataStore: DataStore<Preferences> by preferencesDataStore(name = "layout")

/** Saved home-screen layout: which apps sit in the top row and the order of everything else. */
data class SavedLayout(val favorites: List<String>?, val order: List<String>)

class LayoutStore(private val context: Context) {

    private val favKey = stringPreferencesKey("favorites")
    private val orderKey = stringPreferencesKey("order")

    val layout: Flow<SavedLayout> = context.layoutDataStore.data.map { p ->
        SavedLayout(
            favorites = p[favKey]?.let { split(it) } ?: legacyFavorites(),
            order = p[orderKey]?.let { split(it) } ?: emptyList(),
        )
    }

    suspend fun save(favorites: List<String>, order: List<String>) {
        context.layoutDataStore.edit {
            it[favKey] = favorites.joinToString(",")
            it[orderKey] = order.joinToString(",")
        }
    }

    /** Favourites saved by the first (Java) version of the launcher. */
    private fun legacyFavorites(): List<String>? =
        context.getSharedPreferences("horizon", Context.MODE_PRIVATE)
            .getString("favs", null)?.let { split(it) }

    private fun split(s: String): List<String> = s.split(',').filter { it.isNotBlank() }
}
