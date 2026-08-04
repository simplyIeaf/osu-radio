package com.osuradio.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.osuradio.app.data.NerinyanBeatmapSet
import com.osuradio.app.ui.components.DownloadSortPanel
import com.osuradio.app.utils.DownloadStatus
import com.osuradio.app.utils.DownloadTask
import com.osuradio.app.viewmodel.MainViewModel
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(viewModel: MainViewModel) {
    val searchQuery = viewModel.downloadSearchQuery.collectAsState()
    val sortOption = viewModel.downloadSortOption.collectAsState()
    val statusOption = viewModel.downloadStatusOption.collectAsState()
    val noVideo = viewModel.downloadNoVideo.collectAsState()
    val results = viewModel.downloadResults.collectAsState()
    val loading = viewModel.downloadLoading.collectAsState()
    val downloadTasks = viewModel.downloadTasks.collectAsState()

    var showSortSheet by remember { mutableStateOf(false) }
    val sortSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= results.value.size - 5 && results.value.isNotEmpty()
        }
    }

    androidx.compose.runtime.LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMoreDownloadResults()
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (results.value.isEmpty()) {
            viewModel.refreshDownloadSearch()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery.value,
                onValueChange = { viewModel.setDownloadSearchQuery(it) },
                placeholder = { Text("Search beatmaps...") },
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
                        IconButton(onClick = { viewModel.setDownloadSearchQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )
            IconButton(onClick = { showSortSheet = true }) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = "Sort and filter",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (results.value.isEmpty() && !loading.value) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No beatmaps found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(results.value, key = { _, set -> set.id }) { _, set ->
                    BeatmapSetCard(
                        set = set,
                        task = downloadTasks.value.find { it.beatmapsetId == set.id },
                        onDownload = { viewModel.downloadBeatmapset(set) },
                        onPause = { viewModel.pauseDownload(set.id) },
                        onResume = { viewModel.resumeDownload(set.id) },
                        onCancel = { viewModel.cancelDownload(set.id) }
                    )
                }
                if (loading.value) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            sheetState = sortSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DownloadSortPanel(
                sortOption = sortOption.value,
                statusOption = statusOption.value,
                noVideo = noVideo.value,
                onSortChanged = { viewModel.setDownloadSortOption(it) },
                onStatusChanged = { viewModel.setDownloadStatusOption(it) },
                onNoVideoChanged = { viewModel.setDownloadNoVideo(it) }
            )
        }
    }
}

@Composable
private fun BeatmapSetCard(
    set: NerinyanBeatmapSet,
    task: DownloadTask?,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val status = task?.status
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.Center)
            )
            AsyncImage(
                model = set.covers?.list ?: "https://assets.ppy.sh/beatmaps/${set.id}/covers/list@2x.jpg",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = set.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = set.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when (status) {
                    null -> "${set.status.capitalized()} • ${set.beatmaps.size} difficulties"
                    DownloadStatus.QUEUED -> "Queued..."
                    DownloadStatus.DOWNLOADING -> "Downloading ${
                        task?.progress?.takeIf { it > 0 }?.let { "$it%" } ?: ""
                    }".trim()
                    DownloadStatus.PAUSED -> "Paused"
                    DownloadStatus.FAILED -> "Download failed"
                    DownloadStatus.COMPLETED -> "Imported to library"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (status == DownloadStatus.FAILED)
                    MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (status == DownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { (task?.progress ?: 0).coerceIn(0, 100) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )
            }
        }
        DownloadCardActions(
            status = status,
            onDownload = onDownload,
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancel
        )
    }
}

@Composable
private fun DownloadCardActions(
    status: DownloadStatus?,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    when (status) {
        null -> IconButton(onClick = onDownload) {
            Icon(
                Icons.Filled.Download,
                contentDescription = "Download",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        DownloadStatus.QUEUED -> IconButton(onClick = onCancel) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Cancel",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DownloadStatus.DOWNLOADING -> Row {
            IconButton(onClick = onPause) {
                Icon(
                    Icons.Filled.Pause,
                    contentDescription = "Pause",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Cancel",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DownloadStatus.PAUSED -> Row {
            IconButton(onClick = onResume) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Resume",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DownloadStatus.FAILED -> Row {
            IconButton(onClick = onResume) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Retry",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DownloadStatus.COMPLETED -> Icon(
            Icons.Filled.CheckCircle,
            contentDescription = "Imported",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

private fun String.capitalized(): String = replaceFirstChar { it.uppercase() }
