package com.osuradio.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.osuradio.app.data.RepeatMode
import com.osuradio.app.ui.components.ModsPanel
import com.osuradio.app.ui.components.OsuImage
import com.osuradio.app.viewmodel.MainViewModel
import java.util.Calendar
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val currentSong = viewModel.currentSong.collectAsState()
    val isPlaying = viewModel.isPlaying.collectAsState()
    val currentPositionMs = viewModel.currentPositionMs.collectAsState()
    val settings = viewModel.settings.collectAsState()
    val modSettings = viewModel.modSettings.collectAsState()
    val queue = viewModel.queue.collectAsState()
    val sleepTimerEndAtMs = viewModel.sleepTimerEndAtMs.collectAsState()

    var showMods by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    val playInteraction = remember { MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (playPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "play_scale"
    )
    val modsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val song = currentSong.value ?: return

    val bgColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.background,
        animationSpec = tween(600),
        label = "bg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        bgColor,
                        bgColor
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val showQueuePanel = maxWidth >= 1000.dp
            val queuePanelWidth = 320.dp
            val contentWidth = if (showQueuePanel) (maxWidth - queuePanelWidth) else maxWidth
            val artSide = minOf(contentWidth * 0.42f, maxHeight * 0.42f, 440.dp).coerceAtLeast(120.dp)

            Column(modifier = Modifier.fillMaxSize()) {
                // ── Top bar ──────────────────────────────────────────────────
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Now Playing",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Filled.ArrowBackIosNew,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Row {
                            if (!showQueuePanel) {
                                IconButton(onClick = { showQueueSheet = true }) {
                                    Icon(
                                        Icons.Filled.QueueMusic,
                                        contentDescription = "Queue",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                            IconButton(onClick = {
                                if (sleepTimerEndAtMs.value != null) {
                                    viewModel.cancelSleepTimer()
                                } else {
                                    showSleepTimer = true
                                }
                            }) {
                                Icon(
                                    Icons.Filled.Bedtime,
                                    contentDescription = "Sleep timer",
                                    tint = if (sleepTimerEndAtMs.value != null)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                                )
                            }
                            IconButton(onClick = { showMods = true }) {
                                Icon(
                                    Icons.Filled.Tune,
                                    contentDescription = "Mods",
                                    tint = if (modSettings.value.activeMod.name != "NONE")
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }

                // ── Main content (no scrolling) ─────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Left: album art + track info + controls
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 32.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(artSide)
                                .shadow(10.dp, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Crossfade(
                                targetState = song.imagePath,
                                animationSpec = tween(400),
                                label = "album_art"
                            ) { imagePath ->
                                if (imagePath != null) {
                                    OsuImage(
                                        model = imagePath,
                                        contentDescription = "Album art",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(80.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatMs(currentPositionMs.value),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = if (song.duration > 0) currentPositionMs.value.toFloat() / song.duration.toFloat() else 0f,
                                onValueChange = { fraction ->
                                    viewModel.seekTo((fraction * song.duration).toLong())
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                            Text(
                                text = formatMs(song.duration),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.toggleShuffle() }) {
                                Icon(
                                    Icons.Filled.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (settings.value.shuffle) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.skipToPrev() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Filled.SkipPrevious,
                                    contentDescription = "Previous",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .graphicsLayer {
                                        scaleX = playScale
                                        scaleY = playScale
                                    }
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = { viewModel.togglePlayback() },
                                    interactionSource = playInteraction,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying.value) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying.value) "Pause" else "Play",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.skipToNext() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Filled.SkipNext,
                                    contentDescription = "Next",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.toggleRepeat() }) {
                                Icon(
                                    imageVector = when (settings.value.repeat) {
                                        RepeatMode.ONE -> Icons.Filled.RepeatOne
                                        else -> Icons.Filled.Repeat
                                    },
                                    contentDescription = "Repeat",
                                    tint = if (settings.value.repeat != RepeatMode.NONE) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // Right: persistent "Up Next" queue panel
                    if (showQueuePanel) {
                        VerticalDivider(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                        Column(
                            modifier = Modifier
                                .width(queuePanelWidth)
                                .fillMaxHeight()
                                .padding(vertical = 16.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                text = "Up Next",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                            )
                            if (queue.value.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Queue is empty",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    itemsIndexed(queue.value, key = { _, item -> item.id }) { index, item ->
                                        val isCurrent = item.id == song.id
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                    else Color.Transparent
                                                )
                                                .clickable {
                                                    viewModel.playQueueIndex(index)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isCurrent) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = "Now playing",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = (index + 1).toString(),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                            Column(
                                                modifier = Modifier.weight(1f).padding(start = 12.dp)
                                            ) {
                                                Text(
                                                    text = item.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (isCurrent)
                                                        MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = item.artist,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
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
    }

    if (showMods) {
        ModalBottomSheet(
            onDismissRequest = { showMods = false },
            sheetState = modsSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ModsPanel(
                modSettings = modSettings.value,
                onModChanged = { mod, speed -> viewModel.applyMod(mod, speed) }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = queueSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Text(
                text = "Up Next",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
            )
            if (queue.value.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Queue is empty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    itemsIndexed(queue.value, key = { _, item -> item.id }) { index, item ->
                        val isCurrent = item.id == song.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    viewModel.playQueueIndex(index)
                                    showQueueSheet = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Now playing",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = (index + 1).toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f).padding(start = 12.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isCurrent)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSleepTimer) {
        val now = Calendar.getInstance()
        var sleepHour by remember { mutableStateOf(now.get(Calendar.HOUR_OF_DAY)) }
        var sleepMinute by remember { mutableStateOf(now.get(Calendar.MINUTE)) }
        var hourMenuOpen by remember { mutableStateOf(false) }
        var minuteMenuOpen by remember { mutableStateOf(false) }
        Dialog(onDismissRequest = { showSleepTimer = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sleep at",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box {
                            TextButton(onClick = { hourMenuOpen = true }) {
                                Text(
                                    text = "%02d".format(sleepHour),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            DropdownMenu(
                                expanded = hourMenuOpen,
                                onDismissRequest = { hourMenuOpen = false }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .heightIn(max = 280.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    (0..23).forEach { hour ->
                                        DropdownMenuItem(
                                            text = { Text("%02d".format(hour)) },
                                            onClick = {
                                                sleepHour = hour
                                                hourMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = ":",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box {
                            TextButton(onClick = { minuteMenuOpen = true }) {
                                Text(
                                    text = "%02d".format(sleepMinute),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            DropdownMenu(
                                expanded = minuteMenuOpen,
                                onDismissRequest = { minuteMenuOpen = false }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .heightIn(max = 280.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    (0..59).forEach { minute ->
                                        DropdownMenuItem(
                                            text = { Text("%02d".format(minute)) },
                                            onClick = {
                                                sleepMinute = minute
                                                minuteMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showSleepTimer = false }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = {
                            val target = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, sleepHour)
                                set(Calendar.MINUTE, sleepMinute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            if (target.timeInMillis <= System.currentTimeMillis()) {
                                target.add(Calendar.DAY_OF_YEAR, 1)
                            }
                            val minutes = ((target.timeInMillis - System.currentTimeMillis()) / 60_000L)
                                .toInt()
                                .coerceAtLeast(1)
                            viewModel.startSleepTimer(minutes)
                            showSleepTimer = false
                        }) {
                            Text("Start")
                        }
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val min = TimeUnit.MILLISECONDS.toMinutes(ms)
    val sec = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(min, sec)
}
