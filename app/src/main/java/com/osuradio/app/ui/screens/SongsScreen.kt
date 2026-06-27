package com.osuradio.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.osuradio.app.data.Song
import com.osuradio.app.ui.components.SongSlot
import com.osuradio.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    viewModel: MainViewModel,
    onSongClick: (Song) -> Unit
) {
    val songs = viewModel.songs.collectAsState()
    val currentSong = viewModel.currentSong.collectAsState()
    val isPlaying = viewModel.isPlaying.collectAsState()
    val playlists = viewModel.playlists.collectAsState()
    val searchQuery = viewModel.searchQuery.collectAsState()
    val listState = rememberLazyListState()

    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    var menuSong by remember { mutableStateOf<Song?>(null) }
    var showSongMenu by remember { mutableStateOf(false) }
    var menuAnchorSong by remember { mutableStateOf<Song?>(null) }

    val displayedSongs = viewModel.getFilteredSongs()

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isAtTop,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(250)
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(200)
            ) + fadeOut(animationSpec = tween(150))
        ) {
            OutlinedTextField(
                value = searchQuery.value,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search songs or artists...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                                onImageClick = { viewModel.previewSong(song) },
                                onMoreClick = {
                                    menuAnchorSong = song
                                    menuSong = song
                                    showSongMenu = true
                                }
                            )
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
                                    DropdownMenuItem(
                                        text = { Text("Preview (10s)") },
                                        onClick = {
                                            viewModel.previewSong(song)
                                            showSongMenu = false
                                        }
                                    )
                                    playlists.value.forEach { playlist ->
                                        DropdownMenuItem(
                                            text = { Text("Add to: ${playlist.name}") },
                                            onClick = {
                                                viewModel.addSongToPlaylist(playlist.id, song.id)
                                                showSongMenu = false
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Add, null) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
