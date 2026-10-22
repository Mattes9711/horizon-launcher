package app.horizon.launcher.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import java.io.File

data class HomeState(
    val favorites: List<AppEntry> = emptyList(),
    val others: List<AppEntry> = emptyList(),
    val loaded: Boolean = false,
)

/** Full-screen artwork shown while a top-row app is focused. */
data class Hero(val pkg: String, val title: String, val image: ImageBitmap?, val accent: Color)

/** Row of the focused tile: -1 = top row (dock), 0.. = grid rows. */
const val DOCK_ROW = -1

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(app)
    private val store = LayoutStore(app)
    private val apps = MutableStateFlow<List<AppEntry>?>(null)

    val state: StateFlow<HomeState> = combine(apps, store.layout) { list, layout ->
        if (list == null) HomeState() else arrange(list, layout)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    private val topRowApp = MutableStateFlow<AppEntry?>(null)

    private val _focusRow = MutableStateFlow(DOCK_ROW)
    val focusRow: StateFlow<Int> = _focusRow.asStateFlow()

    private val _hero = MutableStateFlow<Hero?>(null)
    val hero: StateFlow<Hero?> = _hero.asStateFlow()

    private val _wallpaper = MutableStateFlow<ImageBitmap?>(null)
    val wallpaper: StateFlow<ImageBitmap?> = _wallpaper.asStateFlow()

    private val _immersive = MutableStateFlow(false)
    val immersive: StateFlow<Boolean> = _immersive.asStateFlow()

    /** Bumped whenever Home is pressed while we are already on the home screen. */
    private val _homeTick = MutableStateFlow(0)
    val homeTick: StateFlow<Int> = _homeTick.asStateFlow()

    /** Bumped when focus should be put back on [lastFocusedPkg] (window regained focus, list reloaded). */
    private val _restoreTick = MutableStateFlow(0)
    val restoreTick: StateFlow<Int> = _restoreTick.asStateFlow()

    var lastFocusedPkg: String? = null
        private set

    private val heroCache = LruCache<String, ImageBitmap>(4)
    private var imageStamp = ""

    init {
        reload()
        viewModelScope.launch {
            topRowApp.collectLatest { app ->
                if (app == null) return@collectLatest
                delay(120) // don't swap artwork while the user scrolls fast
                val image = withContext(Dispatchers.IO) { userHero(app.pkg) }
                _hero.value = Hero(app.pkg, app.label, image, app.accent)
            }
        }
    }

    fun reload() {
        viewModelScope.launch {
            apps.value = repo.load()
            Log.d(TAG, "apps reloaded")
            _restoreTick.value++
        }
    }

    /** Called on every resume: picks up wallpaper / hero images pushed with tv.sh. */
    fun refreshImages() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = getApplication<Application>().getExternalFilesDir(null) ?: return@launch
            val stamp = dir.walkTopDown().filter { it.isFile }.joinToString { "${it.name}:${it.lastModified()}" }
            if (stamp == imageStamp) return@launch
            imageStamp = stamp
            heroCache.evictAll()
            _wallpaper.value = findImage(dir, "wallpaper")?.let { decode(it) }
            topRowApp.value?.let { app -> _hero.value = Hero(app.pkg, app.label, userHero(app.pkg), app.accent) }
        }
    }

    fun onFocus(app: AppEntry, row: Int) {
        lastFocusedPkg = app.pkg
        _focusRow.value = row
        if (row == DOCK_ROW) topRowApp.value = app
    }

    fun requestRestore() {
        _restoreTick.value++
    }

    fun setImmersive(on: Boolean) {
        _immersive.value = on
    }

    fun goHome() {
        _immersive.value = false
        lastFocusedPkg = null
        _homeTick.value++
    }

    // ------------------------------------------------------------------ images

    private fun userHero(pkg: String): ImageBitmap? {
        heroCache.get(pkg)?.let { return it }
        val dir = getApplication<Application>().getExternalFilesDir("hero") ?: return null
        val img = findImage(dir, pkg)?.let { decode(it) } ?: return null
        heroCache.put(pkg, img)
        return img
    }

    private fun findImage(dir: File, name: String): File? =
        listOf("jpg", "jpeg", "png", "webp").map { File(dir, "$name.$it") }.firstOrNull { it.isFile }

    /** Decodes at most ~1280 px wide in RGB_565 to keep memory low on the TV. */
    private fun decode(f: File): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1280) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(f.path, opts)?.asImageBitmap()
    }.getOrNull()

    // ------------------------------------------------------------------ editing

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
        const val TAG = "SmartTV"
        val DEFAULT_FAVORITES = listOf(
            "com.google.android.youtube.tv", "com.netflix.ninja", "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom", "com.apple.atve.androidtv.appletv",
            "com.spotify.tv.android", "de.swr.avp.ard.tv",
        )
    }
}
