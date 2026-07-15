package com.osuradio.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.osuradio.app.data.Playlist
import com.osuradio.app.data.Song
import com.osuradio.app.ui.components.SongSlot
import com.osuradio.app.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    viewModel: MainViewModel,
    onSongClick: (Song) -> Unit
) {
    val context = LocalContext.current
    val songs = viewModel.songs.collectAsState()
    val currentSong = viewModel.currentSong.collectAsState()
    val isPlaying = viewModel.isPlaying.collectAsState()
    val playlists = viewModel.playlists.collectAsState()
    val searchQuery = viewModel.searchQuery.collectAsState()
    val isSyncing = viewModel.isSyncing.collectAsState()
    val listState = rememberLazyListState()

    // Song context menu state
    var menuAnchorSong by remember { mutableStateOf<Song?>(null) }
    var showSongMenu by remember { mutableStateOf(false) }

    // Playlist selection dialog
    var playlistDialogSong by remember { mutableStateOf<Song?>(null) }

    // Delete confirmation
    var deleteConfirmSong by remember { mutableStateOf<Song?>(null) }

    // Sync button debounce: grey out for 5 seconds after press
    var syncCooldown by remember { mutableStateOf(false) }
    LaunchedEffect(syncCooldown) {
        if (syncCooldown) {
            delay(5_000L)
            syncCooldown = false
        }
    }

    val displayedSongs = viewModel.getFilteredSongs()

    Column(modifier = Modifier.fillMaxSize()) {
        // Search + Sync row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery.value,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search songs or artists...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.value.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )
            // Sync button with 5-second debounce
            IconButton(
                onClick = {
                    if (!syncCooldown && !isSyncing.value) {
                        syncCooldown = true
                        viewModel.syncSongs(context)
                    }
                },
                enabled = !syncCooldown && !isSyncing.value
            ) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = "Sync songs",
                    tint = if (syncCooldown || isSyncing.value)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else MaterialTheme.colorScheme.primary
                )
            }
        }

        if (displayedSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = if (searchQuery.value.isNotEmpty()) "No songs found"
                        else "No songs found\nMake sure osu!droid is installed\nwith songs in the Songs folder",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayedSongs, key = { it.id }) { song ->
                        Box {
                            SongSlot(
                                song = song,
                                isPlaying = currentSong.value?.id == song.id && isPlaying.value,
                                onSlotClick = { onSongClick(song) },
                                onImageClick = { /* image click — no preview */ },
                                onMoreClick = {
                                    menuAnchorSong = song
                                    showSongMenu = true
                                }
                            )
                            // Context dropdown menu
                            if (showSongMenu && menuAnchorSong?.id == song.id) {
                                DropdownMenu(
                                    expanded = true,
                                    onDismissRequest = { showSongMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Play") },
                                        onClick = {
                                            onSongClick(song)
                                            showSongMenu = false
                                        }
                                    )
                                    // Add to Playlist — opens multi-select dialog
                                    DropdownMenuItem(
                                        text = { Text("Add to Playlist") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.PlaylistAdd, contentDescription = null)
                                        },
                                        onClick = {
                                            playlistDialogSong = song
                                            showSongMenu = false
                                        }
                                    )
                                    // Delete song from osu!radio/Songs
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Delete",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            deleteConfirmSong = song
                                            showSongMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Playlist multi-select dialog
    val pSong = playlistDialogSong
    if (pSong != null) {
        PlaylistSelectionDialog(
            song = pSong,
            playlists = playlists.value,
            onToggle = { playlistId -> viewModel.toggleSongInPlaylist(playlistId, pSong.id) },
            onDismiss = { playlistDialogSong = null }
        )
    }

    // Delete confirmation dialog
    val dSong = deleteConfirmSong
    if (dSong != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmSong = null },
            title = { Text("Delete Song") },
            text = {
                Text(
                    "Delete \"${dSong.title}\" from the Songs folder? This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSong(dSong)
                        deleteConfirmSong = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmSong = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Dialog showing all playlists with checkboxes.
 * Songs already in a playlist show a check mark.
 */
@Composable
private fun PlaylistSelectionDialog(
    song: Song,
    playlists: List<Playlist>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            if (playlists.isEmpty()) {
                Text(
                    "No playlists yet. Create one in the Playlists tab.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column {
                    playlists.forEach { playlist ->
                        val isInPlaylist = playlist.songIds.contains(song.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isInPlaylist,
                                onCheckedChange = { onToggle(playlist.id) }
                            )
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp)
                            )
                            if (isInPlaylist) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "In playlist",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
