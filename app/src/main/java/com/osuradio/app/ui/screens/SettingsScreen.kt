package com.osuradio.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.osuradio.app.BuildConfig
import com.osuradio.app.data.AnimationStyle
import com.osuradio.app.data.AudioTransition
import com.osuradio.app.data.EqualizerSettings
import com.osuradio.app.data.EqPreset
import com.osuradio.app.data.ThemeColors
import com.osuradio.app.ui.components.ScreenHeader
import com.osuradio.app.viewmodel.MainViewModel

private val EQ_BAND_LABELS = listOf("Sub\n60Hz", "Bass\n230Hz", "Mid\n910Hz", "Hi\n3.6kHz", "Air\n14kHz")

private data class SettingsTabSpec(val label: String, val icon: ImageVector)

private val ACCENT_COLORS = listOf(
    "Pink" to 0xFFFF66AA,
    "Purple" to 0xFF9B59B6,
    "Blue" to 0xFF3498DB,
    "Cyan" to 0xFF00BCD4,
    "Teal" to 0xFF26A69A,
    "Green" to 0xFF66BB6A,
    "Amber" to 0xFFFFC107,
    "Orange" to 0xFFFF9800,
    "Red" to 0xFFEF5350
)

private val BACKGROUND_COLORS = listOf(
    "Dark" to 0xFF121212,
    "Charcoal" to 0xFF101418,
    "Purple" to 0xFF1A0A2E,
    "Blue" to 0xFF0A1628,
    "Green" to 0xFF0A1E12,
    "Brown" to 0xFF1E1410
)

private val SURFACE_COLORS = listOf(
    "Default" to 0xFF1E1E1E,
    "Card" to 0xFF2A2A2A,
    "Indigo" to 0xFF1A1A2E,
    "Slate" to 0xFF232B36
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val settings = viewModel.settings.collectAsState()
    val animationStyle = settings.value.animationStyle

    val tabs = listOf(
        SettingsTabSpec("General", Icons.Filled.Settings),
        SettingsTabSpec("Audio", Icons.Filled.Equalizer),
        SettingsTabSpec("Synchronization", Icons.Filled.Sync),
        SettingsTabSpec("About", Icons.Filled.Info)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Settings",
            subtitle = "Customize your experience"
        )
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = Color.White,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    text = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    when (animationStyle) {
                        AnimationStyle.SLIDE -> {
                            if (targetState > initialState) {
                                slideInHorizontally(
                                    initialOffsetX = { it },
                                    animationSpec = tween(300)
                                ) togetherWith slideOutHorizontally(
                                    targetOffsetX = { -it },
                                    animationSpec = tween(300)
                                )
                            } else {
                                slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(300)
                                ) togetherWith slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = tween(300)
                                )
                            }
                        }
                        AnimationStyle.FADE -> fadeIn(tween(250)) togetherWith fadeOut(tween(250))
                        AnimationStyle.SCALE -> fadeIn(tween(250)) togetherWith fadeOut(tween(250))
                        AnimationStyle.NONE -> fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    }
                },
                label = "settings_tabs"
            ) { tab ->
                when (tab) {
                    0 -> GeneralSettingsTab(viewModel)
                    1 -> AudioSettingsTab(viewModel)
                    2 -> SynchronizationSettingsTab(viewModel)
                    3 -> AboutTab()
                }
            }
        }
    }
}

@Composable
private fun GeneralSettingsTab(viewModel: MainViewModel) {
    val settings = viewModel.settings.collectAsState()

    val animationLabels = mapOf(
        AnimationStyle.SLIDE to "Slide",
        AnimationStyle.FADE  to "Fade",
        AnimationStyle.SCALE to "Scale",
        AnimationStyle.NONE  to "None"
    )

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsCard(title = "General") {
                SettingsDropdown(
                    label    = "App Animations",
                    selected = animationLabels[settings.value.animationStyle] ?: "Slide",
                    options  = AnimationStyle.entries.map { animationLabels[it] ?: it.name },
                    onSelect = { label ->
                        viewModel.updateSettings(
                            settings.value.copy(
                                animationStyle = animationLabels.entries.first { it.value == label }.key
                            )
                        )
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                SettingsToggle(
                    title    = "Automatically check for updates",
                    subtitle = "Checks on app start (takes effect after restart)",
                    checked  = settings.value.autoCheckUpdates,
                    onChange = { viewModel.updateSettings(settings.value.copy(autoCheckUpdates = it)) }
                )
            }
        }
        item {
            SettingsCard(title = "Themes") {
                val colors = settings.value.themeColors
                val update = { newColors: ThemeColors ->
                    viewModel.updateSettings(settings.value.copy(themeColors = newColors))
                }
                ColorPickerRow(
                    label    = "Accent",
                    current  = colors.primary,
                    colors   = ACCENT_COLORS,
                    onSelect = { color -> update(colors.copy(primary = color)) },
                    onReset  = { update(colors.copy(primary = null)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerRow(
                    label    = "Accent Light",
                    current  = colors.secondary,
                    colors   = ACCENT_COLORS,
                    onSelect = { color -> update(colors.copy(secondary = color)) },
                    onReset  = { update(colors.copy(secondary = null)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerRow(
                    label    = "Background",
                    current  = colors.background,
                    colors   = BACKGROUND_COLORS,
                    onSelect = { color -> update(colors.copy(background = color)) },
                    onReset  = { update(colors.copy(background = null)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerRow(
                    label    = "Surface",
                    current  = colors.surface,
                    colors   = SURFACE_COLORS,
                    onSelect = { color -> update(colors.copy(surface = color)) },
                    onReset  = { update(colors.copy(surface = null)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerRow(
                    label    = "Cards",
                    current  = colors.surfaceVariant,
                    colors   = SURFACE_COLORS,
                    onSelect = { color -> update(colors.copy(surfaceVariant = color)) },
                    onReset  = { update(colors.copy(surfaceVariant = null)) }
                )
            }
        }
    }
}

@Composable
private fun AudioSettingsTab(viewModel: MainViewModel) {
    val settings = viewModel.settings.collectAsState()
    val eq = settings.value.equalizerSettings ?: EqualizerSettings()

    val transitionLabels = mapOf(
        AudioTransition.NONE        to "None",
        AudioTransition.FADE_IN_OUT to "Fade In/Out",
        AudioTransition.CROSSFADE   to "Crossfade",
        AudioTransition.SWOOSH      to "Swoosh"
    )

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsCard(title = "Audio") {
                SettingsDropdown(
                    label    = "Audio Transitions",
                    selected = transitionLabels[settings.value.audioTransition] ?: "Fade In/Out",
                    options  = AudioTransition.entries.map { transitionLabels[it] ?: it.name },
                    onSelect = { label ->
                        viewModel.updateSettings(
                            settings.value.copy(
                                audioTransition = transitionLabels.entries.first { it.value == label }.key
                            )
                        )
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                SettingsToggle(
                    title    = "Equalizer",
                    subtitle = "Apply per-band gain to audio output",
                    checked  = eq.enabled,
                    onChange = { viewModel.updateSettings(settings.value.copy(equalizerSettings = eq.copy(enabled = it))) }
                )

                if (eq.enabled) {
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsDropdown(
                        label    = "Preset",
                        selected = eq.preset.label,
                        options  = EqPreset.entries.map { it.label },
                        onSelect = { label ->
                            val preset = EqPreset.entries.first { it.label == label }
                            viewModel.updateSettings(
                                settings.value.copy(
                                    equalizerSettings = eq.copy(preset = preset, bandLevels = preset.bandLevels)
                                )
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val bandLevels = if (eq.bandLevels.size == 5) eq.bandLevels else List(5) { 0 }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EQ_BAND_LABELS.forEachIndexed { i, label ->
                            EqBandSlider(
                                label = label,
                                levelMb = bandLevels[i],
                                onLevelChange = { newLevel ->
                                    val newLevels = bandLevels.toMutableList().also { it[i] = newLevel }
                                    viewModel.updateSettings(
                                        settings.value.copy(
                                            equalizerSettings = eq.copy(
                                                preset = EqPreset.FLAT, // custom — reset preset label
                                                bandLevels = newLevels
                                            )
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                SettingsToggle(
                    title    = "Loudness Normalization",
                    subtitle = "Boosts quiet tracks to a consistent perceived volume",
                    checked  = settings.value.loudnessNormalization,
                    onChange = { viewModel.updateSettings(settings.value.copy(loudnessNormalization = it)) }
                )

                if (settings.value.loudnessNormalization) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text  = "Target Gain",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text  = "+${settings.value.loudnessGainDb} dB",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Slider(
                        value         = settings.value.loudnessGainDb.toFloat(),
                        onValueChange = { viewModel.updateSettings(settings.value.copy(loudnessGainDb = it.toInt())) },
                        valueRange    = 0f..10f,
                        steps         = 9,
                        modifier      = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SynchronizationSettingsTab(viewModel: MainViewModel) {
    val settings = viewModel.settings.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsCard(title = "Synchronization") {
                SettingsToggle(
                    title    = "osu!droid",
                    subtitle = "Sync songs from osu!droid on app start",
                    checked  = settings.value.syncWithOsuDroid,
                    onChange = { viewModel.updateSettings(settings.value.copy(syncWithOsuDroid = it)) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = "When enabled, osu!radio copies songs that are missing from " +
                            "osu!droid's Songs folder into your library every time the app starts. " +
                            "Turn it off to only keep the songs you have downloaded or imported.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutTab() {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsCard(title = "About") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Version", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(BuildConfig.APP_VERSION, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Developer", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text("@simplyIeaf", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text   = title,
                style  = MaterialTheme.typography.titleMedium,
                color  = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked  = checked,
            onCheckedChange = onChange,
            colors   = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

/** Vertical EQ band slider showing level in dB. */
@Composable
private fun EqBandSlider(
    label: String,
    levelMb: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val db = levelMb / 100
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text  = "${if (db >= 0) "+" else ""}$db",
            style = MaterialTheme.typography.labelSmall,
            color = if (levelMb != 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value         = levelMb.toFloat(),
            onValueChange = { onLevelChange(it.toInt()) },
            valueRange    = -1500f..1500f,
            modifier      = Modifier
                .height(120.dp)
                .padding(vertical = 4.dp)
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value         = selected,
            onValueChange = {},
            readOnly      = true,
            label         = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text    = { Text(option, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { expanded = false; onSelect(option) }
                )
            }
        }
    }
}

/** Row of color swatches used to customize the theme. A highlighted border marks the active color. */
@Composable
private fun ColorPickerRow(
    label: String,
    current: Long?,
    colors: List<Pair<String, Long>>,
    onSelect: (Long) -> Unit,
    onReset: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onReset) {
                Text(
                    text = "Reset",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            colors.forEach { (_, color) ->
                val selected = current == color
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected)
                                MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .clickable { onSelect(color) }
                )
            }
        }
    }
}
