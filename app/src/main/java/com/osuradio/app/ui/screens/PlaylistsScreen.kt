package com.osuradio.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.osuradio.app.data.Playlist
import com.osuradio.app.data.Song
import com.osuradio.app.ui.components.ScreenHeader
import com.osuradio.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: MainViewModel,
    onOpenPlaylist: (Playlist) -> Unit
) {
    val playlists = viewModel.playlists.collectAsState()
    val settings = viewModel.settings.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    var showFiltersMenu by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf("A-Z") }
    val filtersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Search state for filtering playlists
    var playlistSearch by remember { mutableStateOf("") }

    val searchedPlaylists = if (playlistSearch.isBlank()) playlists.value
    else playlists.value.filter { it.name.lowercase().contains(playlistSearch.lowercase()) }

    val filteredPlaylists = if (sortOption == "Z-A") {
        searchedPlaylists.sortedByDescending { it.name.lowercase() }
    } else {
        searchedPlaylists.sortedBy { it.name.lowercase() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Playlists",
            subtitle = "${playlists.value.size} ${if (playlists.value.size == 1) "playlist" else "playlists"}"
        )

        // Playlist search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = playlistSearch,
                onValueChange = { playlistSearch = it },
                placeholder = { Text("Search playlists...") },
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
                    if (playlistSearch.isNotEmpty()) {
                        IconButton(onClick = { playlistSearch = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )
            IconButton(onClick = { showFiltersMenu = true }) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Sort and filter playlists",
                    tint = Color.White
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (filteredPlaylists.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = if (playlistSearch.isNotEmpty()) "No playlists found"
                        else "No playlists yet\nTap + to create one",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredPlaylists, key = { it.id }) { playlist ->
                        val songs = viewModel.getSongsForPlaylist(playlist)
                        PlaylistCard(
                            playlist = playlist,
                            songs = songs,
                            onOpen = { onOpenPlaylist(playlist) },
                            onDelete = { viewModel.deletePlaylist(playlist.id) },
                            onRename = { newName -> viewModel.renamePlaylist(playlist.id, newName) }
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, "Create playlist", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; newPlaylistName = "" },
            title = { Text("New Playlist") },
            text = {
                Column {
                    Text("Enter a name for your playlist", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName.trim())
                            newPlaylistName = ""
                            showCreateDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; newPlaylistName = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFiltersMenu) {
        var filtersExpanded by remember { mutableStateOf(false) }
        ModalBottomSheet(
            onDismissRequest = { showFiltersMenu = false },
            sheetState = filtersSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
            )
            ExposedDropdownMenuBox(
                expanded = filtersExpanded,
                onExpandedChange = { filtersExpanded = !filtersExpanded }
            ) {
                OutlinedTextField(
                    value = sortOption,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sort") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filtersExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = filtersExpanded,
                    onDismissRequest = { filtersExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    DropdownMenuItem(
                        text = { Text("A-Z", style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            sortOption = "A-Z"
                            filtersExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Z-A", style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            sortOption = "Z-A"
                            filtersExpanded = false
                        }
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = settings.value.showAsRaster,
                    onCheckedChange = { viewModel.updateSettings(settings.value.copy(showAsRaster = it)) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Show as raster",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    songs: List<Song>,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(playlist.name) }

    // Keep rename text in sync when playlist name changes externally
    renameText = playlist.name

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover art — show first song's image, or nothing if playlist is empty
            if (songs.isNotEmpty()) {
                val coverSong = songs.firstOrNull { it.imagePath != null }
                if (coverSong?.imagePath != null) {
                    AsyncImage(
                        model = coverSong.imagePath,
                        contentDescription = "Playlist cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    // Songs exist but none have a cover — show music note placeholder
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                Modifier.clickable(enabled = false, onClick = {})
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${songs.size} song${if (songs.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.DriveFileRenameOutline,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            renameText = playlist.name
                            showRenameDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist") },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRename(renameText.trim())
                            showRenameDialog = false
                        }
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
