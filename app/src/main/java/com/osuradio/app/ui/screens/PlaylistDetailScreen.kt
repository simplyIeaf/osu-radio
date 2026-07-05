package com.osuradio.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.osuradio.app.data.Playlist
import com.osuradio.app.data.RepeatMode
import com.osuradio.app.data.Song
import com.osuradio.app.viewmodel.MainViewModel
import java.util.concurrent.TimeUnit

@Composable
fun PlaylistDetailScreen(
    viewModel: MainViewModel,
    playlist: Playlist,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    val settings = viewModel.settings.collectAsState()
    val playlists = viewModel.playlists.collectAsState()
    val allSongs = viewModel.songs.collectAsState()
    val currentPlaylist = playlists.value.find { it.id == playlist.id } ?: playlist
    val songs = allSongs.value.filter { currentPlaylist.songIds.contains(it.id) }
    val totalDurationMs = songs.sumOf { it.duration }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        PlaylistCoverGrid(songs = songs)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = currentPlaylist.name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${songs.size} songs • ${formatPlaylistDuration(totalDurationMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.playPlaylist(currentPlaylist, shuffle = true) }) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (settings.value.shuffle) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(24.dp))
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = { viewModel.playPlaylist(currentPlaylist, shuffle = false) }) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.width(24.dp))
            IconButton(onClick = { viewModel.toggleRepeat() }) {
                Icon(
                    imageVector = when (settings.value.repeat) {
                        RepeatMode.ONE -> Icons.Filled.RepeatOne
                        else -> Icons.Filled.Repeat
                    },
                    contentDescription = "Loop",
                    tint = if (settings.value.repeat != RepeatMode.NONE) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No songs in this playlist",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    PlaylistSongRow(
                        song = song,
                        onClick = { viewModel.playPlaylistFrom(currentPlaylist, song) },
                        onRemove = { viewModel.removeSongFromPlaylist(currentPlaylist.id, song.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCoverGrid(songs: List<Song>) {
    val covers = songs.take(4)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        when (covers.size) {
            0 -> CoverCell(song = null, modifier = Modifier.fillMaxSize())
            1 -> CoverCell(song = covers[0], modifier = Modifier.fillMaxSize())
            2 -> Row(modifier = Modifier.fillMaxSize()) {
                CoverCell(song = covers[0], modifier = Modifier.weight(1f).fillMaxSize())
                CoverCell(song = covers[1], modifier = Modifier.weight(1f).fillMaxSize())
            }
            3 -> Row(modifier = Modifier.fillMaxSize()) {
                CoverCell(song = covers[0], modifier = Modifier.weight(1f).fillMaxSize())
                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    CoverCell(song = covers[1], modifier = Modifier.weight(1f).fillMaxSize())
                    CoverCell(song = covers[2], modifier = Modifier.weight(1f).fillMaxSize())
                }
            }
            else -> Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxSize()) {
                    CoverCell(song = covers[0], modifier = Modifier.weight(1f).fillMaxSize())
                    CoverCell(song = covers[1], modifier = Modifier.weight(1f).fillMaxSize())
                }
                Row(modifier = Modifier.weight(1f).fillMaxSize()) {
                    CoverCell(song = covers[2], modifier = Modifier.weight(1f).fillMaxSize())
                    CoverCell(song = covers[3], modifier = Modifier.weight(1f).fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun CoverCell(song: Song?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        if (song?.imagePath != null) {
            AsyncImage(
                model = song.imagePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun PlaylistSongRow(
    song: Song,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (song.imagePath != null) {
                AsyncImage(
                    model = song.imagePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.Center)
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Remove from playlist",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatPlaylistDuration(totalMs: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalMs)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "$hours hour${if (hours != 1L) "s" else ""} $minutes minute${if (minutes != 1L) "s" else ""}"
    } else {
        "$minutes minute${if (minutes != 1L) "s" else ""}"
    }
}
