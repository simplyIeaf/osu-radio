package com.osuradio.app

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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.osuradio.app.data.AnimationStyle
import com.osuradio.app.ui.components.MiniPlayer
import com.osuradio.app.ui.screens.PlayerScreen
import com.osuradio.app.ui.screens.PlaylistsScreen
import com.osuradio.app.ui.screens.SettingsScreen
import com.osuradio.app.ui.screens.SongsScreen
import com.osuradio.app.ui.screens.LoadingScreen
import com.osuradio.app.ui.theme.OsuRadioTheme
import com.osuradio.app.viewmodel.MainViewModel
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

        handleIncomingIntent(intent)

        setContent {
            val settings = viewModel.settings.collectAsState()
            OsuRadioTheme(theme = settings.value.theme) {
                MainApp(
                    viewModel = viewModel,
                    animationStyle = settings.value.animationStyle,
                    activity = this
                )
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
            Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                ?: intent.data
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
    val currentSong = viewModel.currentSong.collectAsState()
    val isPlaying = viewModel.isPlaying.collectAsState()
    val currentPositionMs = viewModel.currentPositionMs.collectAsState()
    val updatePrompt = viewModel.updatePrompt.collectAsState()
    val updateDownloading = viewModel.updateDownloading.collectAsState()
    val updateProgress = viewModel.updateDownloadProgress.collectAsState()
    val successUpdateVersion = viewModel.successUpdateVersion.collectAsState()

    var showPlayer by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val tabs = listOf(
        NavTab("Songs", Icons.Filled.LibraryMusic),
        NavTab("Playlists", Icons.Filled.PlaylistPlay),
        NavTab("Settings", Icons.Filled.Settings)
    )

    LaunchedEffect(successUpdateVersion.value) {
        val ver = successUpdateVersion.value
        if (!ver.isNullOrEmpty()) {
            snackbarHostState.showSnackbar(
                message = "osu!radio has been updated to $ver",
                actionLabel = "OK"
            )
            viewModel.dismissSuccessUpdate()
        }
    }

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

    androidx.activity.compose.BackHandler(enabled = showPlayer) {
        showPlayer = false
    }

    if (isLoading.value) {
        LoadingScreen(message = viewModel.loadingMessage.collectAsState().value)
        return
    }

    AnimatedVisibility(
        visible = showPlayer && currentSong.value != null,
        enter = when (animationStyle) {
            AnimationStyle.SLIDE -> slideInVertically(initialOffsetY = { it }, animationSpec = tween(350))
            AnimationStyle.FADE -> fadeIn(animationSpec = tween(350))
            AnimationStyle.SCALE -> fadeIn(animationSpec = tween(350))
            AnimationStyle.NONE -> fadeIn(animationSpec = tween(0))
        },
        exit = when (animationStyle) {
            AnimationStyle.SLIDE -> slideOutVertically(targetOffsetY = { it }, animationSpec = tween(350))
            AnimationStyle.FADE -> fadeOut(animationSpec = tween(350))
            AnimationStyle.SCALE -> fadeOut(animationSpec = tween(350))
            AnimationStyle.NONE -> fadeOut(animationSpec = tween(0))
        }
    ) {
        PlayerScreen(
            viewModel = viewModel,
            onBack = { showPlayer = false }
        )
    }

    if (!showPlayer) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column {
                    AnimatedVisibility(visible = currentSong.value != null) {
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
                                onClick = { selectedTab = index },
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
                            onSongClick = { song ->
                                viewModel.playSong(song)
                                showPlayer = true
                            }
                        )
                        1 -> PlaylistsScreen(viewModel = viewModel)
                        2 -> SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
