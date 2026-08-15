package com.leaf.osuradio

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.leaf.osuradio.data.AnimationStyle
import com.leaf.osuradio.data.Playlist
import com.leaf.osuradio.ui.components.MiniPlayer
import com.leaf.osuradio.ui.screens.DownloadScreen
import com.leaf.osuradio.ui.screens.PlayerScreen
import com.leaf.osuradio.ui.screens.PlaylistDetailScreen
import com.leaf.osuradio.ui.screens.PlaylistsScreen
import com.leaf.osuradio.ui.screens.SettingsScreen
import com.leaf.osuradio.ui.screens.SongsScreen
import com.leaf.osuradio.ui.screens.LoadingScreen
import com.leaf.osuradio.ui.theme.OsuRadioTheme
import com.leaf.osuradio.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        requestManageStoragePermission()
        viewModel.initialize(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingIntent(intent)

        setContent {
            val settings = viewModel.settings.collectAsState()
            OsuRadioTheme(
                theme = settings.value.theme,
                colors = settings.value.themeColors
            ) {
                val uiScale = settings.value.uiScale.coerceIn(0.8f, 1.6f)
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density * uiScale, density.fontScale * uiScale)
                ) {
                    MainApp(
                        viewModel = viewModel,
                        animationStyle = settings.value.animationStyle,
                        activity = this
                    )
                }
            }
        }

        viewModel.bindMusicService(this)
        permissionLauncher.launch(permissions)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()
        ) {
            viewModel.initialize(this)
        }
    }

    private fun requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val extra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                extra ?: intent.data
            }
            else -> intent?.data
        }
        uri?.let {
            viewModel.importOszFile(this, it)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        viewModel.unbindMusicService(this)
        super.onDestroy()
    }
}

data class NavTab(val label: String, val icon: ImageVector)

@Composable
fun MainApp(
    viewModel: MainViewModel,
    animationStyle: AnimationStyle,
    activity: ComponentActivity
) {
    val isLoading = viewModel.isLoading.collectAsState()
    val loadingMessage = viewModel.loadingMessage.collectAsState()
    val currentSong = viewModel.currentSong.collectAsState()
    val isPlaying = viewModel.isPlaying.collectAsState()
    val currentPositionMs = viewModel.currentPositionMs.collectAsState()
    val updatePrompt = viewModel.updatePrompt.collectAsState()
    val updateDownloading = viewModel.updateDownloading.collectAsState()
    val updateProgress = viewModel.updateDownloadProgress.collectAsState()
    val releaseNotes = viewModel.releaseNotes.collectAsState()
    val updateFailed = viewModel.updateFailed.collectAsState()

    var showPlayer by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(viewModel.settings.value.lastTab) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val tabs = listOf(
        NavTab("Songs", Icons.Filled.LibraryMusic),
        NavTab("Playlists", Icons.AutoMirrored.Filled.PlaylistPlay),
        NavTab("Download", Icons.Filled.Download),
        NavTab("Settings", Icons.Filled.Settings)
    )

    LaunchedEffect(updatePrompt.value) {
        val prompt = updatePrompt.value ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Would you like to update to ${prompt.latestVersion}?",
            actionLabel = "Yes",
            withDismissAction = true
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.startDownloadAndInstall(activity)
            SnackbarResult.Dismissed -> viewModel.dismissUpdate()
        }
    }

    LaunchedEffect(updateDownloading.value) {
        if (updateDownloading.value) {
            snackbarHostState.showSnackbar("Downloading update... ${updateProgress.value}%")
        }
    }

    LaunchedEffect(updateFailed.value) {
        if (updateFailed.value) {
            val result = snackbarHostState.showSnackbar(
                message = "Update failed to download",
                actionLabel = "Open",
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/simplyIeaf/osu-radio/releases")
                )
                activity.startActivity(intent)
            }
            viewModel.dismissUpdateFailed()
        }
    }

    LaunchedEffect(isLoading.value) {
        if (!isLoading.value) selectedTab = viewModel.settings.value.lastTab
    }

    androidx.activity.compose.BackHandler(enabled = showPlayer) {
        showPlayer = false
    }

    androidx.activity.compose.BackHandler(enabled = !showPlayer && selectedPlaylist != null) {
        selectedPlaylist = null
    }

    if (isLoading.value) {
        LoadingScreen(message = loadingMessage.value)
        return
    }

    releaseNotes.value?.let { notes ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissReleaseNotes() },
            title = { Text("What's new in v${notes.version}") },
            text = {
                Text(
                    text = notes.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissReleaseNotes() }) { Text("Got it") }
            }
        )
    }

    // Use AnimatedContent for the player/main-screen transition so both screens participate
    // in the animation and there's no z-fighting or instant Scaffold appearance.
    val showPlayerFull = showPlayer && currentSong.value != null
    AnimatedContent(
        targetState = showPlayerFull,
        transitionSpec = {
            when (animationStyle) {
                AnimationStyle.SLIDE -> {
                    if (targetState) {
                        // Opening player: slide up
                        slideInVertically(initialOffsetY = { it }, animationSpec = tween(350)) togetherWith
                                fadeOut(animationSpec = tween(200))
                    } else {
                        // Closing player: slide down
                        fadeIn(animationSpec = tween(200)) togetherWith
                                slideOutVertically(targetOffsetY = { it }, animationSpec = tween(350))
                    }
                }
                AnimationStyle.FADE -> fadeIn(tween(350)) togetherWith fadeOut(tween(350))
                AnimationStyle.SCALE -> fadeIn(tween(350)) togetherWith fadeOut(tween(350))
                AnimationStyle.NONE -> fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            }
        },
        label = "player_main_content"
    ) { isPlayerVisible ->
        if (isPlayerVisible) {
            PlayerScreen(
                viewModel = viewModel,
                onBack = { showPlayer = false }
            )
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    Column {
                        AnimatedVisibility(
                            visible = currentSong.value != null,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(300)
                            ) + fadeIn(animationSpec = tween(250)),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(300)
                            ) + fadeOut(animationSpec = tween(250))
                        ) {
                            MiniPlayer(
                                song = currentSong.value!!,
                                isPlaying = isPlaying.value,
                                currentPositionMs = currentPositionMs.value,
                                onPlayPause = { viewModel.pauseResume() },
                                onNext = { viewModel.skipToNext() },
                                onClick = { showPlayer = true }
                            )
                        }
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = {
                                        selectedTab = index
                                        viewModel.setLastTab(index)
                                    },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            when (animationStyle) {
                                AnimationStyle.SLIDE -> {
                                    if (targetState > initialState) {
                                        slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) togetherWith
                                                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300))
                                    } else {
                                        slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) togetherWith
                                                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                                    }
                                }
                                AnimationStyle.FADE -> fadeIn(tween(250)) togetherWith fadeOut(tween(250))
                                AnimationStyle.SCALE -> fadeIn(tween(250)) togetherWith fadeOut(tween(250))
                                AnimationStyle.NONE -> fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            }
                        },
                        label = "tab_content"
                    ) { tab ->
                        when (tab) {
                            0 -> SongsScreen(
                                viewModel = viewModel,
                                onSongClick = { song, source ->
                                    viewModel.playSong(song, source)
                                    showPlayer = true
                                }
                            )
                            1 -> {
                                AnimatedContent(
                                    targetState = selectedPlaylist,
                                    transitionSpec = {
                                        if (targetState != null) {
                                            (slideInVertically(
                                                initialOffsetY = { it },
                                                animationSpec = tween(300)
                                            ) + fadeIn(animationSpec = tween(200))) togetherWith
                                                fadeOut(animationSpec = tween(150))
                                        } else {
                                            fadeIn(animationSpec = tween(150)) togetherWith
                                                (slideOutVertically(
                                                    targetOffsetY = { it },
                                                    animationSpec = tween(300)
                                                ) + fadeOut(animationSpec = tween(200)))
                                        }
                                    },
                                    label = "playlist_detail"
                                ) { playlist ->
                                    if (playlist != null) {
                                        PlaylistDetailScreen(
                                            viewModel = viewModel,
                                            playlist = playlist,
                                            onBack = { selectedPlaylist = null },
                                            onSongClick = { showPlayer = true }
                                        )
                                    } else {
                                        PlaylistsScreen(
                                            viewModel = viewModel,
                                            onOpenPlaylist = { selectedPlaylist = it }
                                        )
                                    }
                                }
                            }
                            2 -> DownloadScreen(viewModel = viewModel)
                            3 -> SettingsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
