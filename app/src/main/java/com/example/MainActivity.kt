package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.lifecycleScope
import com.example.data.db.ZenDatabaseHelper
import com.example.data.model.CategorySummary
import com.example.data.model.Channel
import com.example.data.model.Playlist
import com.example.data.parser.M3uParser
import com.example.data.settings.SettingsManager
import com.example.ui.adapter.CategoryAdapter
import com.example.ui.adapter.ChannelAdapter
import com.example.ui.adapter.PlayerAdapter
import com.example.ui.adapter.PlaylistAdapter
import com.example.ui.dialog.AddPlaylistDialog
import com.example.ui.dialog.FilePickerDialog
import com.example.ui.leanback.LeanbackUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : FragmentActivity() {

    private lateinit var db: ZenDatabaseHelper
    private lateinit var settingsManager: SettingsManager

    // UI elements
    private lateinit var contentFlipper: ViewFlipper
    private lateinit var navTabPlaylists: LinearLayout
    private lateinit var navTabFavorites: LinearLayout
    private lateinit var navTabSettings: LinearLayout

    // Playlists Panel UI
    private lateinit var tvPlaylistTitle: TextView
    private lateinit var tvBreadcrumb: TextView
    private lateinit var btnPlaylistBack: LinearLayout
    private lateinit var btnSearchToggle: Button
    private lateinit var btnAddPlaylist: Button
    private lateinit var searchBarLayout: LinearLayout
    private lateinit var etSearchQuery: EditText
    private lateinit var btnClearSearch: Button
    private lateinit var progressLoading: ProgressBar
    private lateinit var tvPlaylistEmpty: TextView

    private lateinit var rvPlaylists: VerticalGridView
    private lateinit var rvCategories: VerticalGridView
    private lateinit var rvChannels: VerticalGridView

    // Favorites Panel UI
    private lateinit var tvFavoritesEmpty: TextView
    private lateinit var rvFavorites: VerticalGridView

    // Settings Panel UI
    private lateinit var rvPlayers: VerticalGridView

    // Adapters
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var favoritesAdapter: ChannelAdapter
    private lateinit var playerAdapter: PlayerAdapter

    // Navigation and state tracking
    private var currentTab = 0 // 0 = Playlists, 1 = Favorites, 2 = Settings
    private var browseLevel = 0 // 0 = Playlists, 1 = Categories, 2 = Channels
    private var activePlaylist: Playlist? = null
    private var activeCategory: String? = null
    private var hasCategories = false
    private var currentSearchQuery = ""
    private var searchJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = ZenDatabaseHelper.getInstance(this)
        settingsManager = SettingsManager(this)

        initViews()
        setupAdapters()
        setupNavigation()
        setupBackHandler()

        checkAndRequestStoragePermission()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val playlists = db.getPlaylists()
                for (p in playlists) {
                    if (p.name == "ZenTV Demo Channels" || p.sourcePath == "sample_zentv.m3u") {
                        db.deletePlaylist(p.id)
                    }
                }
                val sampleFile = File(filesDir, "sample_zentv.m3u")
                if (sampleFile.exists()) {
                    sampleFile.delete()
                }
            }
            loadPlaylists()
        }
    }

    private fun initViews() {
        contentFlipper = findViewById(R.id.content_flipper)
        navTabPlaylists = findViewById(R.id.nav_tab_playlists)
        navTabFavorites = findViewById(R.id.nav_tab_favorites)
        navTabSettings = findViewById(R.id.nav_tab_settings)

        // Playlists views
        tvPlaylistTitle = findViewById(R.id.tv_playlist_title)
        tvBreadcrumb = findViewById(R.id.tv_breadcrumb)
        btnPlaylistBack = findViewById(R.id.btn_playlist_back)
        btnSearchToggle = findViewById(R.id.btn_search_toggle)
        btnAddPlaylist = findViewById(R.id.btn_add_playlist)
        searchBarLayout = findViewById(R.id.search_bar_layout)
        etSearchQuery = findViewById(R.id.et_search_query)
        btnClearSearch = findViewById(R.id.btn_clear_search)
        progressLoading = findViewById(R.id.progress_loading)
        tvPlaylistEmpty = findViewById(R.id.tv_playlist_empty)

        rvPlaylists = findViewById(R.id.rv_playlists)
        rvCategories = findViewById(R.id.rv_categories)
        rvChannels = findViewById(R.id.rv_channels)

        // Favorites views
        tvFavoritesEmpty = findViewById(R.id.tv_favorites_empty)
        rvFavorites = findViewById(R.id.rv_favorites)

        // Settings views
        rvPlayers = findViewById(R.id.rv_players)

        // Setup official Leanback VerticalGridViews with TV alignment
        LeanbackUtils.setupVerticalGrid(rvPlaylists, numColumns = 1)
        LeanbackUtils.setupVerticalGrid(rvCategories, numColumns = 1)
        LeanbackUtils.setupVerticalGrid(rvChannels, numColumns = 1)
        LeanbackUtils.setupVerticalGrid(rvFavorites, numColumns = 1)
        LeanbackUtils.setupVerticalGrid(rvPlayers, numColumns = 1)

        // Setup Leanback focus scaling for navigation and actions
        LeanbackUtils.applyFocusHighlight(navTabPlaylists, scale = 1.03f, elevationDp = 6f)
        LeanbackUtils.applyFocusHighlight(navTabFavorites, scale = 1.03f, elevationDp = 6f)
        LeanbackUtils.applyFocusHighlight(navTabSettings, scale = 1.03f, elevationDp = 6f)

        LeanbackUtils.applyFocusHighlight(btnPlaylistBack, scale = 1.04f, elevationDp = 4f)
        LeanbackUtils.applyFocusHighlight(btnSearchToggle, scale = 1.04f, elevationDp = 4f)
        LeanbackUtils.applyFocusHighlight(btnAddPlaylist, scale = 1.04f, elevationDp = 4f)
        LeanbackUtils.applyFocusHighlight(btnClearSearch, scale = 1.04f, elevationDp = 4f)
    }

    private fun setupAdapters() {
        // Playlists adapter
        playlistAdapter = PlaylistAdapter(
            onPlaylistClick = { playlist -> openPlaylist(playlist) },
            onPlaylistDelete = { playlist -> deletePlaylist(playlist) }
        )
        rvPlaylists.adapter = playlistAdapter

        // Categories adapter
        categoryAdapter = CategoryAdapter { category ->
            openCategory(category.name)
        }
        rvCategories.adapter = categoryAdapter

        // Channel adapter
        channelAdapter = ChannelAdapter(
            onChannelClick = { channel -> playChannel(channel) },
            onChannelToggleFavorite = { channel, pos -> toggleFavorite(channel, pos, isFavoritesTab = false) }
        )
        rvChannels.adapter = channelAdapter

        // Favorites adapter
        favoritesAdapter = ChannelAdapter(
            onChannelClick = { channel -> playChannel(channel) },
            onChannelToggleFavorite = { channel, pos -> toggleFavorite(channel, pos, isFavoritesTab = true) }
        )
        rvFavorites.adapter = favoritesAdapter

        // Players adapter
        playerAdapter = PlayerAdapter { selectedPlayer ->
            settingsManager.setPreferredPlayer(selectedPlayer)
            val msg = if (selectedPlayer.isSystemDefault) {
                "Preferred player set to System Default"
            } else {
                "Preferred player set to ${selectedPlayer.label}"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        rvPlayers.adapter = playerAdapter
    }

    private fun setupNavigation() {
        // Tab click listeners
        navTabPlaylists.setOnClickListener { selectTab(0) }
        navTabFavorites.setOnClickListener { selectTab(1) }
        navTabSettings.setOnClickListener { selectTab(2) }

        // D-pad right from navigation rail into content
        val navKeyHandler = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                focusContentFromNav()
                true
            } else {
                false
            }
        }
        navTabPlaylists.setOnKeyListener(navKeyHandler)
        navTabFavorites.setOnKeyListener(navKeyHandler)
        navTabSettings.setOnKeyListener(navKeyHandler)

        // Start with Playlists selected
        selectTab(0)

        // Add Playlist button
        btnAddPlaylist.setOnClickListener {
            AddPlaylistDialog(
                context = this,
                onAddUrl = { name, url -> importPlaylistFromUrl(name, url) },
                onAddFile = { name, file -> importPlaylistFromFile(name, file, autoOpen = true) }
            ).show()
        }

        // Search toggle button
        btnSearchToggle.setOnClickListener {
            if (searchBarLayout.visibility == View.VISIBLE) {
                closeSearch()
            } else {
                openSearch()
            }
        }

        btnClearSearch.setOnClickListener {
            etSearchQuery.setText("")
            currentSearchQuery = ""
            loadChannelsForActiveSelection()
        }

        // Live search text change listener with coroutine debouncing
        etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(150) // Short debounce for snappy TV remote typing
                    currentSearchQuery = s?.toString()?.trim() ?: ""
                    loadChannelsForActiveSelection()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Breadcrumb back button
        btnPlaylistBack.setOnClickListener {
            handleBackInsidePlaylists()
        }
    }

    private fun focusContentFromNav() {
        when (currentTab) {
            0 -> {
                when {
                    searchBarLayout.visibility == View.VISIBLE -> etSearchQuery.requestFocus()
                    rvChannels.visibility == View.VISIBLE && rvChannels.adapter?.itemCount ?: 0 > 0 -> rvChannels.requestFocus()
                    rvCategories.visibility == View.VISIBLE && rvCategories.adapter?.itemCount ?: 0 > 0 -> rvCategories.requestFocus()
                    rvPlaylists.visibility == View.VISIBLE && rvPlaylists.adapter?.itemCount ?: 0 > 0 -> rvPlaylists.requestFocus()
                    btnAddPlaylist.visibility == View.VISIBLE -> btnAddPlaylist.requestFocus()
                    btnSearchToggle.visibility == View.VISIBLE -> btnSearchToggle.requestFocus()
                }
            }
            1 -> {
                if (rvFavorites.visibility == View.VISIBLE && rvFavorites.adapter?.itemCount ?: 0 > 0) {
                    rvFavorites.requestFocus()
                }
            }
            2 -> {
                if (rvPlayers.adapter?.itemCount ?: 0 > 0) {
                    rvPlayers.requestFocus()
                }
            }
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (searchBarLayout.visibility == View.VISIBLE) {
                    closeSearch()
                    return
                }

                if (currentTab == 0 && browseLevel > 0) {
                    handleBackInsidePlaylists()
                    return
                }

                if (currentTab != 0) {
                    selectTab(0)
                    navTabPlaylists.requestFocus()
                    return
                }

                // If on main screen with focus in content, move focus to side rail
                val focused = currentFocus
                if (focused != null && focused != navTabPlaylists && focused != navTabFavorites && focused != navTabSettings) {
                    navTabPlaylists.requestFocus()
                    return
                }

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    private fun selectTab(index: Int) {
        currentTab = index
        contentFlipper.displayedChild = index

        navTabPlaylists.isSelected = (index == 0)
        navTabFavorites.isSelected = (index == 1)
        navTabSettings.isSelected = (index == 2)

        when (index) {
            0 -> {
                updatePlaylistsTopBar()
            }
            1 -> {
                loadFavorites()
            }
            2 -> {
                loadSettings()
            }
        }
    }

    // ==========================================
    // PLAYLISTS BROWSING
    // ==========================================

    private fun loadPlaylists() {
        browseLevel = 0
        activePlaylist = null
        activeCategory = null
        closeSearch()

        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { db.getPlaylists() }
            if (list.isEmpty()) {
                tvPlaylistEmpty.visibility = View.VISIBLE
                rvPlaylists.visibility = View.GONE
            } else {
                tvPlaylistEmpty.visibility = View.GONE
                rvPlaylists.visibility = View.VISIBLE
                playlistAdapter.submitList(list)
            }
            rvCategories.visibility = View.GONE
            rvChannels.visibility = View.GONE
            updatePlaylistsTopBar()
        }
    }

    private fun openPlaylist(playlist: Playlist) {
        activePlaylist = playlist
        lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) { db.getCategories(playlist.id) }
            hasCategories = categories.isNotEmpty()

            if (hasCategories) {
                // Show categories
                browseLevel = 1
                activeCategory = null

                val combinedCategories = mutableListOf<CategorySummary>()
                // First item is All Channels
                combinedCategories.add(
                    CategorySummary(getString(R.string.category_all), playlist.channelCount)
                )
                combinedCategories.addAll(categories)

                rvPlaylists.visibility = View.GONE
                rvChannels.visibility = View.GONE
                rvCategories.visibility = View.VISIBLE
                tvPlaylistEmpty.visibility = View.GONE

                categoryAdapter.submitList(combinedCategories)
                updatePlaylistsTopBar()
                rvCategories.requestFocus()
            } else {
                // No categories exist; show channels directly as required!
                browseLevel = 2
                activeCategory = null
                rvPlaylists.visibility = View.GONE
                rvCategories.visibility = View.GONE
                rvChannels.visibility = View.VISIBLE

                updatePlaylistsTopBar()
                loadChannelsForActiveSelection()
                rvChannels.requestFocus()
            }
        }
    }

    private fun openCategory(categoryName: String) {
        browseLevel = 2
        activeCategory = if (categoryName == getString(R.string.category_all)) null else categoryName
        rvCategories.visibility = View.GONE
        rvChannels.visibility = View.VISIBLE

        updatePlaylistsTopBar()
        loadChannelsForActiveSelection()
        rvChannels.requestFocus()
    }

    private fun loadChannelsForActiveSelection() {
        val playlist = activePlaylist ?: return
        lifecycleScope.launch {
            val channels = withContext(Dispatchers.IO) {
                db.getChannels(
                    playlistId = playlist.id,
                    category = activeCategory,
                    searchQuery = currentSearchQuery.ifBlank { null }
                )
            }
            channelAdapter.submitList(channels)
            if (channels.isEmpty()) {
                tvPlaylistEmpty.text = if (currentSearchQuery.isNotEmpty()) {
                    getString(R.string.empty_search)
                } else {
                    getString(R.string.empty_channels)
                }
                tvPlaylistEmpty.visibility = View.VISIBLE
            } else {
                tvPlaylistEmpty.visibility = View.GONE
            }
        }
    }

    private fun handleBackInsidePlaylists() {
        if (browseLevel == 2) {
            if (hasCategories) {
                // Go back to categories
                browseLevel = 1
                activeCategory = null
                closeSearch()
                rvChannels.visibility = View.GONE
                rvCategories.visibility = View.VISIBLE
                tvPlaylistEmpty.visibility = View.GONE
                updatePlaylistsTopBar()
                rvCategories.requestFocus()
            } else {
                // Go back to playlists list
                loadPlaylists()
                rvPlaylists.requestFocus()
            }
        } else if (browseLevel == 1) {
            // Go back to playlists list
            loadPlaylists()
            rvPlaylists.requestFocus()
        }
    }

    private fun updatePlaylistsTopBar() {
        when (browseLevel) {
            0 -> {
                tvPlaylistTitle.text = getString(R.string.nav_playlists)
                btnPlaylistBack.visibility = View.GONE
                btnSearchToggle.visibility = View.GONE
                btnAddPlaylist.visibility = View.VISIBLE
                closeSearch()
            }
            1 -> {
                val pName = activePlaylist?.name ?: ""
                tvPlaylistTitle.text = pName
                tvBreadcrumb.text = "Playlists"
                btnPlaylistBack.visibility = View.VISIBLE
                btnSearchToggle.visibility = View.GONE
                btnAddPlaylist.visibility = View.GONE
                closeSearch()
            }
            2 -> {
                val pName = activePlaylist?.name ?: ""
                val catName = activeCategory ?: getString(R.string.category_all)
                tvPlaylistTitle.text = "$pName • $catName"
                tvBreadcrumb.text = if (hasCategories) "Categories" else "Playlists"
                btnPlaylistBack.visibility = View.VISIBLE
                btnSearchToggle.visibility = View.VISIBLE
                btnAddPlaylist.visibility = View.GONE
            }
        }
    }

    private fun openSearch() {
        searchBarLayout.visibility = View.VISIBLE
        etSearchQuery.requestFocus()
    }

    private fun closeSearch() {
        searchBarLayout.visibility = View.GONE
        if (currentSearchQuery.isNotEmpty()) {
            currentSearchQuery = ""
            etSearchQuery.setText("")
            if (browseLevel == 2) {
                loadChannelsForActiveSelection()
            }
        }
    }

    private fun deletePlaylist(playlist: Playlist) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.deletePlaylist(playlist.id)
            }
            Toast.makeText(this@MainActivity, "Deleted '${playlist.name}'", Toast.LENGTH_SHORT).show()
            loadPlaylists()
        }
    }

    // ==========================================
    // IMPORT PLAYLIST (URL / FILE)
    // ==========================================

    private fun importPlaylistFromUrl(name: String, url: String) {
        progressLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val channels = withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(url).build()
                    val response = httpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        response.close()
                        throw Exception("HTTP ${response.code}")
                    }
                    val body = response.body ?: throw Exception("Empty response body")
                    val newId = UUID.randomUUID().toString()
                    val parsed = M3uParser.parse(body.byteStream(), newId, name)
                    val playlist = Playlist(
                        id = newId,
                        name = name,
                        sourceType = "URL",
                        sourcePath = url,
                        channelCount = parsed.size
                    )
                    db.insertPlaylist(playlist, parsed)
                    playlist
                }
                progressLoading.visibility = View.GONE
                Toast.makeText(this@MainActivity, "Added '${channels.name}' (${channels.channelCount} CH)", Toast.LENGTH_SHORT).show()
                loadPlaylists()
                openPlaylist(channels)
            } catch (e: Exception) {
                progressLoading.visibility = View.GONE
                Toast.makeText(this@MainActivity, getString(R.string.error_url_download), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun importPlaylistFromFile(name: String, file: File, autoOpen: Boolean) {
        progressLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val playlist = withContext(Dispatchers.IO) {
                    val stream = FileInputStream(file)
                    val newId = UUID.randomUUID().toString()
                    val parsed = M3uParser.parse(stream, newId, name)
                    val p = Playlist(
                        id = newId,
                        name = name,
                        sourceType = "FILE",
                        sourcePath = file.name,
                        channelCount = parsed.size
                    )
                    db.insertPlaylist(p, parsed)
                    p
                }
                progressLoading.visibility = View.GONE
                Toast.makeText(this@MainActivity, "Loaded '${playlist.name}' (${playlist.channelCount} CH)", Toast.LENGTH_SHORT).show()
                loadPlaylists()
                if (autoOpen) {
                    openPlaylist(playlist)
                }
            } catch (e: Exception) {
                progressLoading.visibility = View.GONE
                Toast.makeText(this@MainActivity, getString(R.string.error_file_read), Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==========================================
    // FAVORITES TAB
    // ==========================================

    private fun loadFavorites() {
        lifecycleScope.launch {
            val favs = withContext(Dispatchers.IO) { db.getFavorites() }
            favoritesAdapter.submitList(favs)
            if (favs.isEmpty()) {
                tvFavoritesEmpty.visibility = View.VISIBLE
                rvFavorites.visibility = View.GONE
            } else {
                tvFavoritesEmpty.visibility = View.GONE
                rvFavorites.visibility = View.VISIBLE
                rvFavorites.requestFocus()
            }
        }
    }

    private fun toggleFavorite(channel: Channel, position: Int, isFavoritesTab: Boolean) {
        lifecycleScope.launch {
            val newState = withContext(Dispatchers.IO) {
                db.toggleFavorite(channel.id)
            }
            channel.isFavorite = newState
            if (isFavoritesTab) {
                loadFavorites()
            } else {
                channelAdapter.updateFavorite(position, newState)
            }
            val toastMsg = if (newState) getString(R.string.favorited_toast) else getString(R.string.unfavorited_toast)
            Toast.makeText(this@MainActivity, "${channel.name}: $toastMsg", Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================================
    // SETTINGS TAB
    // ==========================================

    private fun loadSettings() {
        val players = settingsManager.getInstalledVideoPlayers()
        val currentPref = settingsManager.getPreferredPlayerPackage()
        playerAdapter.submitData(players, currentPref)
        rvPlayers.requestFocus()
    }

    // ==========================================
    // STREAM PLAYBACK
    // ==========================================

    private fun playChannel(channel: Channel) {
        val success = settingsManager.launchStream(channel.streamUrl)
        if (!success) {
            Toast.makeText(this, "Could not open stream: ${channel.name}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================================
    // PERMISSIONS (ANDROID TV & MOBILE)
    // ==========================================

    private fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    } catch (_: Exception) {}
                }
            }
        } else {
            // Android 9 (API 28) and Android 10
            val readPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            val writePerm = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (readPerm != PackageManager.PERMISSION_GRANTED || writePerm != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    REQUEST_STORAGE_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage access granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_STORAGE_CODE = 1001
    }
}
