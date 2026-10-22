package app.horizon.launcher.ui

import android.app.Application
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.horizon.launcher.data.AppEntry
import app.horizon.launcher.data.AppRepository
import app.horizon.launcher.data.LayoutStore
import app.horizon.launcher.data.Photo
import app.horizon.launcher.data.PhotoRepository
import app.horizon.launcher.data.RecentApps
import app.horizon.launcher.data.SETTINGS_ID
import app.horizon.launcher.data.SavedLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, APPS, INPUTS, AIRPLAY, SETTINGS, PHOTO }

data class HomeState(
    /** Your home apps (small row on the start page). */
    val favorites: List<AppEntry> = emptyList(),
    /** Every app that looks good on a TV ("Alle Apps"). */
    val apps: List<AppEntry> = emptyList(),
    /** Everything kept out of sight ("Weitere Apps", yellow key). */
    val drawer: List<AppEntry> = emptyList(),
    val inputs: List<AppEntry> = emptyList(),
    val loaded: Boolean = false,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(app)
    private val store = LayoutStore(app)
    private val photos = PhotoRepository(app)
    private val recentsRepo = RecentApps(app)

    private val apps = MutableStateFlow<List<AppEntry>?>(null)
    private val inputs = MutableStateFlow<List<AppEntry>>(emptyList())
    private var hiddenNow: Set<String> = emptySet()
    private var pinnedNow: Set<String> = emptySet()

    val state: StateFlow<HomeState> = combine(apps, inputs, store.layout) { list, ins, layout ->
        if (list == null) HomeState() else arrange(list, ins, layout)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    /** "Zuletzt": recently used apps (usage access), else your first home apps. */
    private val _recents = MutableStateFlow<List<AppEntry>>(emptyList())
    val recents: StateFlow<List<AppEntry>> = _recents.asStateFlow()

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _drawer = MutableStateFlow(false)
    val drawer: StateFlow<Boolean> = _drawer.asStateFlow()

    /** Bumped to pull focus into the sidebar (blue key, holding Home). */
    private val _sidebarTick = MutableStateFlow(0)
    val sidebarTick: StateFlow<Int> = _sidebarTick.asStateFlow()

    // ------------------------------------------------------------------ focus memory
    /** Key of the element that should have focus ("home:recent:<pkg>", "apps:<pkg>", ...). */
    var focusKey: String? = null
    private val _restoreTick = MutableStateFlow(0)
    val restoreTick: StateFlow<Int> = _restoreTick.asStateFlow()

    fun focus(key: String?) {
        focusKey = key
        _restoreTick.value++
    }

    fun requestRestore() {
        _restoreTick.value++
    }

    // ------------------------------------------------------------------ settings
    private val _sounds = MutableStateFlow(true)
    val sounds: StateFlow<Boolean> = _sounds.asStateFlow()

    private val _bing = MutableStateFlow(false)
    val bing: StateFlow<Boolean> = _bing.asStateFlow()

    private val _photoBackground = MutableStateFlow(false)
    val photoBackground: StateFlow<Boolean> = _photoBackground.asStateFlow()

    // ------------------------------------------------------------------ photo (only loaded when shown)
    private val _photo = MutableStateFlow<ImageBitmap?>(null)
    val photo: StateFlow<ImageBitmap?> = _photo.asStateFlow()
    private var photoList: List<Photo> = emptyList()
    private var photoIndex = 0
    private var photoJob: Job? = null

    private val bigArt = LruCache<String, ImageBitmap>(6) // ~3 MB at most

    init {
        reload()
        viewModelScope.launch { store.sounds.collect { _sounds.value = it; Sounds.enabled = it } }
        viewModelScope.launch { store.bing.collect { _bing.value = it; refreshPhotos(fetch = true) } }
        viewModelScope.launch { store.photoBackground.collect { _photoBackground.value = it; updatePhotoNeed() } }
        // a new photo every 15 minutes, only while photos are shown
        viewModelScope.launch {
            while (true) {
                delay(15 * 60 * 1000L)
                if (photoNeeded()) showPhoto(photoIndex + 1)
            }
        }
    }

    fun reload() {
        viewModelScope.launch {
            apps.value = repo.load()
            inputs.value = withContext(Dispatchers.IO) { repo.inputs() }
            refreshRecents()
            Log.d(TAG, "apps reloaded")
            _restoreTick.value++
        }
    }

    fun onResume() {
        viewModelScope.launch { refreshRecents() }
        viewModelScope.launch(Dispatchers.IO) { refreshPhotos(fetch = true) }
    }

    private suspend fun refreshRecents() {
        val byPkg = apps.value.orEmpty().associateBy { it.pkg }
        val list = withContext(Dispatchers.IO) { recentsRepo.load() }
            .mapNotNull { byPkg[it] }.filter { it.banner != null && it.pkg !in hiddenNow }.take(6)
        _recents.value = list.ifEmpty { state.value.favorites.filter { it.banner != null }.take(3) }
    }

    suspend fun bigArtFor(app: AppEntry): ImageBitmap? {
        bigArt.get(app.pkg)?.let { return it }
        val img = withContext(Dispatchers.IO) { repo.bigArt(app) } ?: return null
        bigArt.put(app.pkg, img)
        return img
    }

    // ------------------------------------------------------------------ navigation
    fun show(s: Screen) {
        _drawer.value = false
        _screen.value = s
        updatePhotoNeed()
    }

    fun goHome() {
        _drawer.value = false
        _screen.value = Screen.HOME
        updatePhotoNeed()
        focus(null)
    }

    fun openSidebar() {
        _drawer.value = false
        if (_screen.value == Screen.PHOTO) show(Screen.HOME)
        _sidebarTick.value++
    }

    fun toggleDrawer() {
        _drawer.value = !_drawer.value
        if (!_drawer.value) _restoreTick.value++
    }

    fun closeDrawer() {
        if (_drawer.value) toggleDrawer()
    }

    fun setSounds(on: Boolean) {
        viewModelScope.launch { store.setSounds(on) }
    }

    fun setBing(on: Boolean) {
        viewModelScope.launch { store.setBing(on) }
    }

    fun setPhotoBackground(on: Boolean) {
        viewModelScope.launch { store.setPhotoBackground(on) }
    }

    // ------------------------------------------------------------------ photos
    private fun photoNeeded() = _photoBackground.value || _screen.value == Screen.PHOTO

    /** Loads a photo only while one is on screen; frees it otherwise. */
    private fun updatePhotoNeed() {
        if (photoNeeded()) {
            if (_photo.value == null) viewModelScope.launch(Dispatchers.IO) { refreshPhotos(fetch = false, force = true) }
        } else {
            photoJob?.cancel()
            _photo.value = null
        }
    }

    fun nextPhoto(delta: Int) = showPhoto(photoIndex + delta)

    private suspend fun refreshPhotos(fetch: Boolean, force: Boolean = false) {
        withContext(Dispatchers.IO) {
            val bingOn = _bing.value
            val added = fetch && bingOn && photos.refreshBing()
            val list = photos.list(bingOn)
            val changed = list.map { it.file.path } != photoList.map { it.file.path }
            photoList = list
            if ((changed || added || force) && photoNeeded()) {
                if (list.isEmpty()) _photo.value = null else showPhoto(0)
            }
        }
    }

    private fun showPhoto(index: Int) {
        val list = photoList
        if (list.isEmpty() || !photoNeeded()) return
        photoJob?.cancel()
        photoJob = viewModelScope.launch(Dispatchers.Default) {
            val i = ((index % list.size) + list.size) % list.size
            // 1280 px wide, RGB_565, shade baked in: one ~1.8 MB bitmap, one draw call
            val bmp = PhotoRepository.decode(list[i].file, 1280, mutable = true) ?: return@launch
            Art.shade(Canvas(bmp), bmp.width, bmp.height, Paint(Paint.DITHER_FLAG), strength = 0.92f)
            photoIndex = i
            if (photoNeeded()) _photo.value = bmp.asImageBitmap()
        }
    }

    // ------------------------------------------------------------------ editing
    fun addToHome(pkg: String) = edit { favs, _ -> if (pkg !in favs) favs.add(pkg) }
    fun removeFromHome(pkg: String) = edit { favs, _ -> favs.remove(pkg) }
    fun moveOnHome(pkg: String, delta: Int) = edit { favs, _ ->
        val i = favs.indexOf(pkg)
        val j = i + delta
        if (i >= 0 && j in favs.indices) {
            favs.removeAt(i); favs.add(j, pkg)
        }
    }

    /** Puts an app from "Weitere Apps" into "Alle Apps". */
    fun showInApps(pkg: String) {
        viewModelScope.launch { store.setHidden(hiddenNow - pkg, pinnedNow + pkg) }
    }

    /** Moves an app into "Weitere Apps". */
    fun hide(pkg: String) {
        viewModelScope.launch { store.setHidden(hiddenNow + pkg, pinnedNow - pkg) }
        edit { favs, _ -> favs.remove(pkg) }
    }

    private fun edit(change: (MutableList<String>, MutableList<String>) -> Unit) {
        val s = state.value
        val favs = s.favorites.map { it.pkg }.toMutableList()
        val order = s.apps.map { it.pkg }.toMutableList()
        change(favs, order)
        viewModelScope.launch { store.save(favs, order) }
    }

    private fun arrange(list: List<AppEntry>, ins: List<AppEntry>, layout: SavedLayout): HomeState {
        hiddenNow = layout.hidden
        pinnedNow = layout.pinned
        val favSet = (layout.favorites ?: DEFAULT_FAVORITES).toSet()
        val inputIds = ins.map { it.pkg }.toSet()
        // "Alle Apps" shows only apps that look good on a TV: a real banner, or pinned by you
        fun nice(a: AppEntry) = a.pkg != SETTINGS_ID && a.pkg !in layout.hidden &&
            (a.banner != null || a.pkg in layout.pinned || a.pkg in favSet)
        val byPkg = (list + ins).associateBy { it.pkg }
        val visible = list.filter { nice(it) }
        val drawer = list.filter { !nice(it) }.sortedWith(compareBy<AppEntry>({ it.pkg == SETTINGS_ID }, { it.label.lowercase() }))
        var favIds = (layout.favorites ?: DEFAULT_FAVORITES)
            .filter { it in byPkg && it !in layout.hidden && it != SETTINGS_ID && (it in inputIds || nice(byPkg.getValue(it))) }
            .distinct()
        if (layout.favorites == null && favIds.isEmpty()) favIds = visible.map { it.pkg }.take(6)
        val rank = layout.order.withIndex().associate { it.value to it.index }
        val sorted = visible.sortedWith(compareBy<AppEntry>({ rank[it.pkg] ?: Int.MAX_VALUE }, { it.label.lowercase() }))
        return HomeState(favIds.mapNotNull { byPkg[it] }, sorted, drawer, ins, loaded = true)
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
