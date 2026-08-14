package com.osuradio.app

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.exitApplication
import androidx.compose.ui.window.rememberWindowState
import com.osuradio.app.data.AnimationStyle
import com.osuradio.app.data.Playlist
import com.osuradio.app.ui.components.MiniPlayer
import com.osuradio.app.ui.screens.DownloadScreen
import com.osuradio.app.ui.screens.ImportScreen
import com.osuradio.app.ui.screens.LoadingScreen
import com.osuradio.app.ui.screens.PlayerScreen
import com.osuradio.app.ui.screens.PlaylistDetailScreen
import com.osuradio.app.ui.screens.PlaylistsScreen
import com.osuradio.app.ui.screens.SettingsScreen
import com.osuradio.app.ui.screens.SongsScreen
import com.osuradio.app.ui.theme.OsuRadioTheme
import com.osuradio.app.viewmodel.MainViewModel
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

fun main() = application {
    val viewModel = remember { MainViewModel() }

    DisposableEffect(Unit) {
        viewModel.initialize()
        onDispose { viewModel.release() }
    }

    val settings by viewModel.settings.collectAsState()

    Window(
        onCloseRequest = {
            viewModel.release()
            exitApplication()
        },
        title = "osu!radio",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        OsuRadioTheme(
            theme = settings.theme,
            colors = settings.themeColors
        ) {
            MainApp(viewModel = viewModel)
        }
    }
}

data class NavTab(val label: String, val icon: ImageVector)

@Composable
fun MainApp(viewModel: MainViewModel) {
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingMessage by viewModel.loadingMessage.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val updatePrompt by viewModel.updatePrompt.collectAsState()
    val updateDownloading by viewModel.updateDownloading.collectAsState()
    val updateProgress by viewModel.updateDownloadProgress.collectAsState()
    val updateDownloaded by viewModel.updateDownloaded.collectAsState()
    val releaseNotes by viewModel.releaseNotes.collectAsState()
    val updateFailed by viewModel.updateFailed.collectAsState()
    val uriHandler = LocalUriHandler.current

    var showPlayer by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(viewModel.settings.value.lastTab) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val tabs = listOf(
        NavTab("Songs", Icons.Filled.LibraryMusic),
        NavTab("Playlists", Icons.Filled.PlaylistPlay),
        NavTab("Download", Icons.Filled.Download),
        NavTab("Import", Icons.Filled.Archive),
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
            SnackbarResult.ActionPerformed -> viewModel.startDownloadAndUpdate()
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
                uriHandler.openUri("https://github.com/simplyIeaf/osu-radio/releases")
            }
            viewModel.dismissUpdateFailed()
        }
    }

    LaunchedEffect(isLoading.value) {
        if (!isLoading.value) selectedTab = viewModel.settings.value.lastTab
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

    val downloadedFile = updateDownloaded.value
    if (downloadedFile != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDownloaded() },
            title = { Text("Update downloaded") },
            text = {
                Text(
                    text = "The update was saved to:\n$downloadedFile\n\nYou can install it manually by running the file.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        val parent = File(downloadedFile).parentFile ?: File(downloadedFile)
                        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(parent)
                    }
                    viewModel.dismissUpdateDownloaded()
                }) { Text("Open folder") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdateDownloaded() }) { Text("Close") }
            }
        )
    }

    // Use AnimatedContent for the player/main-screen transition so both screens participate
    // in the animation and there's no z-fighting or instant Scaffold appearance.
    val showPlayerFull = showPlayer && currentSong != null
    AnimatedContent(
        targetState = showPlayerFull,
        transitionSpec = {
            when (settings.animationStyle) {
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
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0.dp)
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            Image(
                                painter = painterResource("ic_app_logo.png"),
                                contentDescription = "osu!radio",
                                modifier = Modifier
                                    .padding(top = 12.dp, bottom = 12.dp)
                                    .size(40.dp)
                            )
                        }
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            NavigationRailItem(
                                selected = selectedTab == index,
                                onClick = {
                                    selectedTab = index
                                    viewModel.setLastTab(index)
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            AnimatedContent(
                                targetState = selectedTab,
                                transitionSpec = {
                                    when (settings.animationStyle) {
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
                                    3 -> ImportScreen(
                                        viewModel = viewModel,
                                        onImportOsz = {
                                            chooseFile("osz", "osu! beatmap (.osz)")?.let { file ->
                                                viewModel.importOszFile(file)
                                            }
                                        },
                                        onImportZip = {
                                            chooseFile("zip", "Zip archive (.zip)")?.let { file ->
                                                viewModel.importZipFile(file)
                                            }
                                        }
                                    )
                                    4 -> SettingsScreen(viewModel = viewModel)
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = currentSong != null,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(300)
                            ) + fadeIn(animationSpec = tween(250)),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(300)
                            ) + fadeOut(animationSpec = tween(250))
                        ) {
                            if (currentSong != null) {
                                MiniPlayer(
                                    song = currentSong!!,
                                    isPlaying = isPlaying,
                                    currentPositionMs = currentPositionMs,
                                    onPlayPause = { viewModel.togglePlayback() },
                                    onNext = { viewModel.skipToNext() },
                                    onClick = { showPlayer = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun chooseFile(extension: String, description: String): File? {
    val chooser = JFileChooser()
    chooser.fileFilter = FileNameExtensionFilter(description, extension)
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}
