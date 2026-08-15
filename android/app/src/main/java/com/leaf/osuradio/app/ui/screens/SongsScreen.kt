package com.leaf.osuradio.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.leaf.osuradio.data.Playlist
import com.leaf.osuradio.data.Song
import com.leaf.osuradio.ui.components.PlayingIndicator
import com.leaf.osuradio.ui.components.ScreenHeader
import com.leaf.osuradio.ui.components.SongSlot
import com.leaf.osuradio.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    viewModel: MainViewModel,
    onSongClick: (Song, List<Song>) -> Unit
) {
    val currentSong = viewModel.currentSong.collectAsState()
    val isPlaying = viewModel.isPlaying.collectAsState()
    val playlists = viewModel.playlists.collectAsState()
    val searchQuery = viewModel.searchQuery.collectAsState()
    val songs = viewModel.songs.collectAsState()
    val settings = viewModel.settings.collectAsState()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    // Song context menu state
    var menuAnchorSong by remember { mutableStateOf<Song?>(null) }
    var showSongMenu by remember { mutableStateOf(false) }
    // Whether the Playlist accordion is expanded inside the dropdown
    var playlistSectionExpanded by remember { mutableStateOf(false) }

    var showFiltersMenu by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf("A-Z") }
    val filtersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Delete confirmation
    var deleteConfirmSong by remember { mutableStateOf<Song?>(null) }

    // Reset accordion whenever the menu closes
    LaunchedEffect(showSongMenu) {
        if (!showSongMenu) playlistSectionExpanded = false
    }

    val displayedSongs = viewModel.getFilteredSongs()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Library",
            subtitle = buildString {
                append(songs.value.size)
                append(if (songs.value.size == 1) " song" else " songs")
                if (playlists.value.isNotEmpty()) {
                    append(" • ")
                    append(playlists.value.size)
                    append(if (playlists.value.size == 1) " playlist" else " playlists")
                }
            }
        )

        // Search + Sort/filter row
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
            IconButton(onClick = { showFiltersMenu = true }) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Sort and filter songs",
                    tint = Color.White
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
                        text = "No songs found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                if (settings.value.showAsRaster) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayedSongs, key = { it.id }) { song ->
                            Box {
                                SongTile(
                                    song = song,
                                    isPlaying = currentSong.value?.id == song.id && isPlaying.value,
                                    onClick = { onSongClick(song, displayedSongs) },
                                    onMoreClick = {
                                        menuAnchorSong = song
                                        playlistSectionExpanded = false
                                        showSongMenu = true
                                    }
                                )
                                SongContextMenu(
                                    visible = showSongMenu && menuAnchorSong?.id == song.id,
                                    song = song,
                                    playlists = playlists.value,
                                    playlistSectionExpanded = playlistSectionExpanded,
                                    onPlaylistSectionToggle = { playlistSectionExpanded = !playlistSectionExpanded },
                                    onToggleInPlaylist = { viewModel.toggleSongInPlaylist(it, song.id) },
                                    onPlay = {
                                        onSongClick(song, displayedSongs)
                                        showSongMenu = false
                                    },
                                    onDelete = {
                                        deleteConfirmSong = song
                                        showSongMenu = false
                                    },
                                    onDismiss = { showSongMenu = false }
                                )
                            }
                        }
                    }
                } else {
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
                                    onSlotClick = { onSongClick(song, displayedSongs) },
                                    onImageClick = { /* no-op */ },
                                    onMoreClick = {
                                        menuAnchorSong = song
                                        playlistSectionExpanded = false
                                        showSongMenu = true
                                    }
                                )
                                SongContextMenu(
                                    visible = showSongMenu && menuAnchorSong?.id == song.id,
                                    song = song,
                                    playlists = playlists.value,
                                    playlistSectionExpanded = playlistSectionExpanded,
                                    onPlaylistSectionToggle = { playlistSectionExpanded = !playlistSectionExpanded },
                                    onToggleInPlaylist = { viewModel.toggleSongInPlaylist(it, song.id) },
                                    onPlay = {
                                        onSongClick(song, displayedSongs)
                                        showSongMenu = false
                                    },
                                    onDelete = {
                                        deleteConfirmSong = song
                                        showSongMenu = false
                                    },
                                    onDismiss = { showSongMenu = false }
                                )
                            }
                        }
                    }
                }
            }
        }
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
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                    colors = ButtonDefaults.buttonColors(
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

@Composable
private fun SongTile(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }

    val containerColor by animateColorAsState(
        targetValue = if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(250),
        label = "tile_container_color"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tile_scale"
    )

    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    }
                )
            }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            if (song.imagePath != null) {
                AsyncImage(
                    model = song.imagePath,
                    contentDescription = "Album art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center)
                )
            }
            if (isPlaying) {
                PlayingIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                )
            }
        }
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SongContextMenu(
    visible: Boolean,
    song: Song,
    playlists: List<Playlist>,
    playlistSectionExpanded: Boolean,
    onPlaylistSectionToggle: () -> Unit,
    onToggleInPlaylist: (String) -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        DropdownMenuItem(
            text = { Text("Play") },
            leadingIcon = {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            onClick = onPlay
        )

        HorizontalDivider()

        DropdownMenuItem(
            text = { Text("Playlist") },
            leadingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null
                )
            },
            trailingIcon = {
                Icon(
                    if (playlistSectionExpanded)
                        Icons.Filled.KeyboardArrowDown
                    else
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null
                )
            },
            onClick = onPlaylistSectionToggle
        )

        if (playlistSectionExpanded) {
            if (playlists.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "No playlists yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.padding(start = 16.dp)
                )
            } else {
                playlists.forEach { playlist ->
                    val inPlaylist = playlist.songIds.contains(song.id)
                    DropdownMenuItem(
                        text = {
                            Text(
                                playlist.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (inPlaylist)
                                    Icons.Filled.CheckCircle
                                else
                                    Icons.Filled.RadioButtonUnchecked,
                                contentDescription = if (inPlaylist)
                                    "In playlist" else "Not in playlist",
                                tint = if (inPlaylist)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { onToggleInPlaylist(playlist.id) },
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
            HorizontalDivider()
        }

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
            onClick = onDelete
        )
    }
}
