package app.horizon.launcher.ui

import android.app.Application
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.horizon.launcher.data.AppEntry
import app.horizon.launcher.data.AppRepository
import app.horizon.launcher.data.LayoutStore
import app.horizon.launcher.data.SETTINGS_ID
import app.horizon.launcher.data.SavedLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeState(
    val favorites: List<AppEntry> = emptyList(),
    val others: List<AppEntry> = emptyList(),
    val loaded: Boolean = false,
)

/** Background for the hero area: a blurred banner, or a colour wash for apps without one. */
data class Backdrop(val pkg: String, val image: ImageBitmap?, val accent: Color)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(app)
    private val store = LayoutStore(app)
    private val apps = MutableStateFlow<List<AppEntry>?>(null)

    val state: StateFlow<HomeState> = combine(apps, store.layout) { list, layout ->
        if (list == null) HomeState() else arrange(list, layout)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    private val focusedApp = MutableStateFlow<AppEntry?>(null)
    private val _focusInTopRow = MutableStateFlow(true)
    val focusInTopRow: StateFlow<Boolean> = _focusInTopRow.asStateFlow()

    private val _backdrop = MutableStateFlow<Backdrop?>(null)
    val backdrop: StateFlow<Backdrop?> = _backdrop.asStateFlow()

    private val _immersive = MutableStateFlow(false)
    val immersive: StateFlow<Boolean> = _immersive.asStateFlow()

    /** Bumped whenever Home is pressed while we are already on the home screen. */
    private val _homeTick = MutableStateFlow(0)
    val homeTick: StateFlow<Int> = _homeTick.asStateFlow()

    private val blurCache = LruCache<String, ImageBitmap>(12)

    init {
        reload()
        viewModelScope.launch {
            focusedApp.collectLatest { app ->
                if (app == null) return@collectLatest
                delay(140) // don't rebuild the background while the user scrolls fast
                val image = app.source?.let { src ->
                    blurCache.get(app.pkg) ?: withContext(Dispatchers.Default) {
                        AppRepository.backdropFrom(src)
                    }.also { blurCache.put(app.pkg, it) }
                }
                _backdrop.value = Backdrop(app.pkg, image, app.accent)
            }
        }
    }

    fun reload() {
        viewModelScope.launch { apps.value = repo.load() }
    }

    val focusedPkg: String? get() = focusedApp.value?.pkg

    fun onFocus(app: AppEntry, inTopRow: Boolean) {
        _focusInTopRow.value = inTopRow
        // tvOS-style: only the top row drives the big background
        if (inTopRow) focusedApp.value = app
    }

    fun setImmersive(on: Boolean) {
        _immersive.value = on
    }

    fun goHome() {
        _immersive.value = false
        _homeTick.value++
    }

    // ------------------------------------------------------------------ editing

    fun isFavorite(pkg: String) = state.value.favorites.any { it.pkg == pkg }

    fun toggleFavorite(pkg: String) = edit { favs, others ->
        if (favs.remove(pkg)) others.add(0, pkg) else {
            others.remove(pkg); favs.add(pkg)
        }
    }

    fun move(pkg: String, delta: Int) = edit { favs, others ->
        val list = if (favs.contains(pkg)) favs else others
        val i = list.indexOf(pkg)
        val j = i + delta
        if (i >= 0 && j in list.indices) {
            list.removeAt(i); list.add(j, pkg)
        }
    }

    private fun edit(change: (MutableList<String>, MutableList<String>) -> Unit) {
        val s = state.value
        val favs = s.favorites.map { it.pkg }.toMutableList()
        val others = s.others.map { it.pkg }.filter { it != SETTINGS_ID }.toMutableList()
        change(favs, others)
        viewModelScope.launch { store.save(favs, others) }
    }

    private fun arrange(list: List<AppEntry>, layout: SavedLayout): HomeState {
        val byPkg = list.associateBy { it.pkg }
        var favIds = (layout.favorites ?: DEFAULT_FAVORITES).filter { it in byPkg && it != SETTINGS_ID }.distinct()
        if (layout.favorites == null && favIds.isEmpty()) {
            favIds = list.map { it.pkg }.filter { it != SETTINGS_ID }.take(6)
        }
        val rank = layout.order.withIndex().associate { it.value to it.index }
        val others = list
            .filter { it.pkg !in favIds && it.pkg != SETTINGS_ID }
            .sortedWith(compareBy<AppEntry>({ rank[it.pkg] ?: Int.MAX_VALUE }, { it.label.lowercase() }))
        val settings = listOfNotNull(byPkg[SETTINGS_ID])
        return HomeState(favIds.mapNotNull { byPkg[it] }, others + settings, loaded = true)
    }

    companion object {
        val DEFAULT_FAVORITES = listOf(
            "com.google.android.youtube.tv", "com.netflix.ninja", "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom", "com.apple.atve.androidtv.appletv",
            "com.spotify.tv.android", "de.swr.avp.ard.tv",
        )
    }
}
