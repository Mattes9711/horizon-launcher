package app.horizon.launcher.ui

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.horizon.launcher.data.AppEntry
import app.horizon.launcher.data.AppRepository
import app.horizon.launcher.data.LayoutStore
import app.horizon.launcher.data.RecentApps
import app.horizon.launcher.data.INPUT_PREFIX
import app.horizon.launcher.data.SETTINGS_ID
import app.horizon.launcher.data.SavedLayout
import app.horizon.launcher.data.ShelfRepository
import app.horizon.launcher.data.ShelfSlide
import app.horizon.launcher.data.Photo
import app.horizon.launcher.data.PhotoRepository
import kotlinx.coroutines.Job
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
    /** Apps kept off the home screen ("Weitere Apps", yellow key). */
    val drawer: List<AppEntry> = emptyList(),
    val hiddenCount: Int = 0,
    val loaded: Boolean = false,
)

/** Top Shelf content for the focused top-row app. Slides with image == null use generated art. */
data class Hero(val app: AppEntry, val slides: List<ShelfSlide>, val art: ImageBitmap, val artIsPhoto: Boolean)

/** Current background photograph: sharp for the Top Shelf, frosted for behind the grid. */
data class PhotoArt(val sharp: ImageBitmap, val frosted: ImageBitmap, val caption: String?)

data class SwitcherState(
    val apps: List<Pair<AppEntry, ImageBitmap?>> = emptyList(),
    val hasAccess: Boolean = true,
    val loading: Boolean = true,
)

/** Row of the focused tile: -1 = top row (dock), 0.. = grid rows. */
const val DOCK_ROW = -1
const val COLUMNS = 6

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(app)
    private val store = LayoutStore(app)
    private val shelf = ShelfRepository(app)
    private val photos = PhotoRepository(app)
    private val recents = RecentApps(app)
    private val apps = MutableStateFlow<List<AppEntry>?>(null)

    val state: StateFlow<HomeState> = combine(apps, store.layout) { list, layout ->
        if (list == null) HomeState() else arrange(list, layout)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    private var hiddenNow: Set<String> = emptySet()
    private var pinnedNow: Set<String> = emptySet()

    private val _airplay = MutableStateFlow(false)
    val airplay: StateFlow<Boolean> = _airplay.asStateFlow()

    fun openAirPlay() {
        _controlCenter.value = false
        _airplay.value = true
    }

    fun closeAirPlay() {
        if (_airplay.value) {
            _airplay.value = false
            _restoreTick.value++
        }
    }

    private val _drawer = MutableStateFlow(false)
    val drawer: StateFlow<Boolean> = _drawer.asStateFlow()

    fun toggleDrawer() {
        _controlCenter.value = false
        _switcher.value = null
        _drawer.value = !_drawer.value
        if (!_drawer.value) _restoreTick.value++
    }

    fun closeDrawer() {
        if (_drawer.value) toggleDrawer()
    }

    /** Puts a drawer app on the home screen. */
    fun showOnHome(pkg: String) {
        viewModelScope.launch { store.setHidden(hiddenNow - pkg, pinnedNow + pkg) }
    }

    // ------------------------------------------------------------------ focus / page
    private val topRowApp = MutableStateFlow<AppEntry?>(null)
    private val _focusRow = MutableStateFlow(DOCK_ROW)
    val focusRow: StateFlow<Int> = _focusRow.asStateFlow()

    var lastFocusedPkg: String? = null
        private set

    /** Bumped whenever Home is pressed while we are already on the home screen. */
    private val _homeTick = MutableStateFlow(0)
    val homeTick: StateFlow<Int> = _homeTick.asStateFlow()

    /** Bumped when focus should go back to [lastFocusedPkg]. */
    private val _restoreTick = MutableStateFlow(0)
    val restoreTick: StateFlow<Int> = _restoreTick.asStateFlow()

    // ------------------------------------------------------------------ hero / top shelf
    private val _hero = MutableStateFlow<Hero?>(null)
    val hero: StateFlow<Hero?> = _hero.asStateFlow()

    private val _slide = MutableStateFlow(0)
    val slide: StateFlow<Int> = _slide.asStateFlow()

    private val _photo = MutableStateFlow<PhotoArt?>(null)
    val photo: StateFlow<PhotoArt?> = _photo.asStateFlow()

    private val _bing = MutableStateFlow(true)
    val bing: StateFlow<Boolean> = _bing.asStateFlow()

    private var photoList: List<Photo> = emptyList()
    private var photoIndex = 0
    private var photoJob: Job? = null

    private val slideCache = LruCache<String, List<ShelfSlide>>(4)
    private val artCache = LruCache<String, ImageBitmap>(10)
    private var imageStamp = ""

    // ------------------------------------------------------------------ overlays
    private val _immersive = MutableStateFlow(false)
    val immersive: StateFlow<Boolean> = _immersive.asStateFlow()

    private val _controlCenter = MutableStateFlow(false)
    val controlCenter: StateFlow<Boolean> = _controlCenter.asStateFlow()

    private val _switcher = MutableStateFlow<SwitcherState?>(null)
    val switcher: StateFlow<SwitcherState?> = _switcher.asStateFlow()

    private val _editing = MutableStateFlow<String?>(null)
    val editing: StateFlow<String?> = _editing.asStateFlow()

    private val _sounds = MutableStateFlow(true)
    val sounds: StateFlow<Boolean> = _sounds.asStateFlow()

    init {
        reload()
        viewModelScope.launch {
            store.sounds.collect { _sounds.value = it; Sounds.enabled = it }
        }
        viewModelScope.launch {
            store.bing.collect { on ->
                _bing.value = on
                refreshPhotos(fetch = true)
            }
        }
        // a new photo every 15 minutes, like CleanFrame
        viewModelScope.launch {
            while (true) {
                delay(15 * 60 * 1000L)
                showPhoto(photoIndex + 1)
            }
        }
        viewModelScope.launch {
            topRowApp.collectLatest { app ->
                if (app == null) return@collectLatest
                delay(70) // don't swap artwork while the user scrolls fast
                val (art, isPhoto) = heroArt(app)
                val cached = slideCache.get(app.pkg)
                _slide.value = 0
                _hero.value = Hero(app, cached.orEmpty(), art, isPhoto)
                if (cached == null) {
                    val slides = withContext(Dispatchers.IO) { shelf.load(app.pkg) }
                    slideCache.put(app.pkg, slides)
                    if (topRowApp.value?.pkg == app.pkg) _hero.value = Hero(app, slides, art, isPhoto)
                }
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

    /** Called on every resume: picks up photos / hero images pushed with tv.sh and new photos of the day. */
    fun refreshImages() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = getApplication<Application>().getExternalFilesDir(null)
            val stamp = dir?.walkTopDown()?.filter { it.isFile }?.joinToString { "${it.name}:${it.lastModified()}" }.orEmpty()
            if (stamp != imageStamp) {
                imageStamp = stamp
                slideCache.evictAll()
                refreshPhotos(fetch = false)
                val app = topRowApp.value
                if (app != null) {
                    val slides = shelf.load(app.pkg)
                    slideCache.put(app.pkg, slides)
                    _hero.value?.let { _hero.value = it.copy(slides = slides) }
                }
            }
            refreshPhotos(fetch = true)
        }
    }

    fun setBing(on: Boolean) {
        viewModelScope.launch { store.setBing(on) }
    }

    /** Photo or (no photos yet / offline) the app-coloured generated art. */
    private suspend fun heroArt(app: AppEntry): Pair<ImageBitmap, Boolean> {
        _photo.value?.let { return it.sharp to true }
        val art = artCache.get(app.pkg) ?: withContext(Dispatchers.Default) { Art.aurora(app.accent) }
            .also { artCache.put(app.pkg, it) }
        return art to false
    }

    private suspend fun refreshPhotos(fetch: Boolean) = withContext(Dispatchers.IO) {
        val bingOn = _bing.value
        if (fetch && bingOn && !photos.refreshBing() && photoList.isNotEmpty()) return@withContext
        val list = photos.list(bingOn)
        if (list.map { it.file.path } == photoList.map { it.file.path } && _photo.value != null) return@withContext
        photoList = list
        if (list.isEmpty()) {
            _photo.value = null
            topRowApp.value?.let { app -> _hero.value?.let { _hero.value = it.copy(art = heroArt(app).first, artIsPhoto = false) } }
        } else {
            showPhoto(0)
        }
    }

    private fun showPhoto(index: Int) {
        val list = photoList
        if (list.isEmpty()) return
        photoJob?.cancel()
        photoJob = viewModelScope.launch(Dispatchers.Default) {
            val i = ((index % list.size) + list.size) % list.size
            val p = list[i]
            val bmp = PhotoRepository.decode(p.file, 1280) ?: return@launch
            val art = PhotoArt(bmp.asImageBitmap(), Art.frosted(bmp), p.caption)
            photoIndex = i
            _photo.value = art
            _hero.value?.let { _hero.value = it.copy(art = art.sharp, artIsPhoto = true) }
        }
    }

    fun nextPhoto(delta: Int) = showPhoto(photoIndex + delta)

    fun onFocus(app: AppEntry, row: Int) {
        lastFocusedPkg = app.pkg
        _focusRow.value = row
        if (row == DOCK_ROW) topRowApp.value = app
    }

    fun requestRestore() {
        _restoreTick.value++
    }

    fun nextSlide(delta: Int = 1) {
        val n = _hero.value?.slides?.size ?: 0
        if (n > 1) _slide.value = (_slide.value + delta + n) % n
    }

    fun setImmersive(on: Boolean) {
        _immersive.value = on
        if (!on) _restoreTick.value++
    }

    fun goHome() {
        // (no photo change here: decoding a new photo right when Home is pressed costs a visible stutter)
        _immersive.value = false
        _controlCenter.value = false
        _switcher.value = null
        _editing.value = null
        _drawer.value = false
        _airplay.value = false
        lastFocusedPkg = null
        _homeTick.value++
    }

    // ------------------------------------------------------------------ control center
    fun toggleControlCenter() {
        _switcher.value = null
        _controlCenter.value = !_controlCenter.value
        if (!_controlCenter.value) _restoreTick.value++
    }

    fun closeControlCenter() {
        if (_controlCenter.value) toggleControlCenter()
    }

    fun setSounds(on: Boolean) {
        viewModelScope.launch { store.setSounds(on) }
    }



    // ------------------------------------------------------------------ app switcher
    fun toggleSwitcher() {
        if (_switcher.value != null) {
            closeSwitcher(); return
        }
        _controlCenter.value = false
        _switcher.value = SwitcherState()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val byPkg = apps.value.orEmpty().associateBy { it.pkg }
                val list = recents.load().mapNotNull { byPkg[it] }.take(10)
                SwitcherState(
                    apps = list.map { a -> a to a.source?.let { AppRepository.renderDrawable(it, 480, 270).asImageBitmap() } },
                    hasAccess = recents.hasAccess(),
                    loading = false,
                )
            }
            if (_switcher.value != null) _switcher.value = result
        }
    }

    fun closeSwitcher() {
        if (_switcher.value != null) {
            _switcher.value = null
            _restoreTick.value++
        }
    }

    /** "Swipe up" on a card: stop the app (only works for apps in the background) and drop the card. */
    fun closeApp(pkg: String) {
        val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        runCatching { am.killBackgroundProcesses(pkg) }
        _switcher.value = _switcher.value?.let { s -> s.copy(apps = s.apps.filter { it.first.pkg != pkg }) }
    }

    // ------------------------------------------------------------------ editing
    fun startEdit(pkg: String) {
        if (pkg != SETTINGS_ID) _editing.value = pkg
    }

    fun endEdit() {
        _editing.value = null
        _restoreTick.value++
    }

    fun editMove(pkg: String, dir: Dir) = edit { favs, others ->
        if (pkg in favs) {
            val i = favs.indexOf(pkg)
            when (dir) {
                Dir.LEFT -> if (i > 0) { favs.removeAt(i); favs.add(i - 1, pkg) }
                Dir.RIGHT -> if (i < favs.size - 1) { favs.removeAt(i); favs.add(i + 1, pkg) }
                Dir.DOWN -> { favs.removeAt(i); others.add(i.coerceAtMost(others.size), pkg); keepOnHome(pkg) }
                Dir.UP -> Unit
            }
        } else {
            val i = others.indexOf(pkg)
            if (i < 0) return@edit
            when (dir) {
                Dir.LEFT -> if (i > 0) { others.removeAt(i); others.add(i - 1, pkg) }
                Dir.RIGHT -> if (i < others.size - 1) { others.removeAt(i); others.add(i + 1, pkg) }
                Dir.UP -> if (i < COLUMNS) {
                    others.removeAt(i); favs.add(i.coerceAtMost(favs.size), pkg)
                } else { others.removeAt(i); others.add(i - COLUMNS, pkg) }
                Dir.DOWN -> { val j = (i + COLUMNS).coerceAtMost(others.size - 1); others.removeAt(i); others.add(j, pkg) }
            }
        }
        lastFocusedPkg = pkg
    }

    fun toggleFavorite(pkg: String) = edit { favs, others ->
        if (favs.remove(pkg)) {
            others.add(0, pkg)
            keepOnHome(pkg)
        } else {
            others.remove(pkg); favs.add(pkg)
        }
    }

    /** An app leaving the top row stays on the home screen even without a TV banner. */
    private fun keepOnHome(pkg: String) {
        if (pkg !in pinnedNow) viewModelScope.launch { store.setHidden(hiddenNow, pinnedNow + pkg) }
    }

    /** Moves an app into "Weitere Apps". */
    fun hide(pkg: String) {
        viewModelScope.launch { store.setHidden(hiddenNow + pkg, pinnedNow - pkg) }
        edit { favs, others -> favs.remove(pkg); others.remove(pkg) }
    }

    private fun edit(change: (MutableList<String>, MutableList<String>) -> Unit) {
        val s = state.value
        val favs = s.favorites.map { it.pkg }.toMutableList()
        val others = s.others.map { it.pkg }.filter { it != SETTINGS_ID }.toMutableList()
        change(favs, others)
        viewModelScope.launch { store.save(favs, others) }
    }

    private fun arrange(list: List<AppEntry>, layout: SavedLayout): HomeState {
        hiddenNow = layout.hidden
        pinnedNow = layout.pinned
        val favSet = (layout.favorites ?: DEFAULT_FAVORITES).toSet()
        // Home shows only apps that look good on a TV: real TV banner, HDMI inputs, or explicitly pinned.
        // Everything else (Android helpers, file managers, ...) lives in "Weitere Apps".
        fun onHome(a: AppEntry) = a.pkg != SETTINGS_ID && a.pkg !in layout.hidden &&
            (a.banner != null && a.icon == null || a.pkg.startsWith(INPUT_PREFIX) || a.pkg in layout.pinned || a.pkg in favSet)
        val visible = list.filter { onHome(it) }
        val drawer = list.filter { !onHome(it) }.sortedWith(compareBy<AppEntry>({ it.pkg == SETTINGS_ID }, { it.label.lowercase() }))
        val byPkg = visible.associateBy { it.pkg }
        var favIds = (layout.favorites ?: DEFAULT_FAVORITES).filter { it in byPkg && it != SETTINGS_ID }.distinct()
        if (layout.favorites == null && favIds.isEmpty()) {
            favIds = visible.map { it.pkg }.filter { it != SETTINGS_ID }.take(6)
        }
        val rank = layout.order.withIndex().associate { it.value to it.index }
        val others = visible
            .filter { it.pkg !in favIds && it.pkg != SETTINGS_ID }
            .sortedWith(compareBy<AppEntry>({ rank[it.pkg] ?: Int.MAX_VALUE }, { it.label.lowercase() }))
        return HomeState(favIds.mapNotNull { byPkg[it] }, others, drawer, drawer.size, loaded = true)
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
