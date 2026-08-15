package com.osuradio.app.ui.screens

import com.osuradio.app.audio.AudioDevices
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.osuradio.app.BuildConfig
import com.osuradio.app.data.AnimationStyle
import com.osuradio.app.data.AudioTransition
import com.osuradio.app.data.EqPreset
import com.osuradio.app.data.ThemeColors
import com.osuradio.app.ui.components.ScreenHeader
import com.osuradio.app.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val EQ_BAND_LABELS = listOf("Sub\n60Hz", "Bass\n230Hz", "Mid\n910Hz", "Hi\n3.6kHz", "Air\n14kHz")

private data class SettingsTabSpec(val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val settings = viewModel.settings.collectAsState()
    val animationStyle = settings.value.animationStyle

    val tabs = listOf(
        SettingsTabSpec("General", Icons.Filled.Settings),
        SettingsTabSpec("Audio", Icons.Filled.Equalizer),
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
            contentColor = MaterialTheme.colorScheme.onBackground,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedTab),
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
                    2 -> AboutTab()
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
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                var pendingScale by remember { mutableFloatStateOf(settings.value.uiScale) }
                val scope = rememberCoroutineScope()
                var applyJob by remember { mutableStateOf<Job?>(null) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("UI Scale", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        String.format("%.2fx", pendingScale),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = pendingScale,
                    onValueChange = { newValue ->
                        pendingScale = newValue
                        applyJob?.cancel()
                        applyJob = scope.launch {
                            delay(1000)
                            viewModel.updateUiScale(newValue)
                        }
                    },
                    valueRange = 0.8f..1.6f,
                    steps = 7
                )
            }
        }
        item {
            SettingsCard(title = "Themes") {
                val colors = settings.value.themeColors
                val update = { newColors: ThemeColors ->
                    viewModel.updateSettings(settings.value.copy(themeColors = newColors))
                }
                ColorPickerField(
                    label    = "Accent",
                    current  = colors.primary,
                    defaultColor = MaterialTheme.colorScheme.primary,
                    onColorChange = { color -> update(colors.copy(primary = color)) },
                    onReset  = { update(colors.copy(primary = null)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerField(
                    label    = "Accent Light",
                    current  = colors.secondary,
                    defaultColor = MaterialTheme.colorScheme.secondary,
                    onColorChange = { color -> update(colors.copy(secondary = color)) },
                    onReset  = { update(colors.copy(secondary = null)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerField(
                    label    = "Background",
                    current  = colors.background,
                    defaultColor = MaterialTheme.colorScheme.background,
                    onColorChange = { color -> update(colors.copy(background = color)) },
                    onReset  = { update(colors.copy(background = null)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerField(
                    label    = "Surface",
                    current  = colors.surface,
                    defaultColor = MaterialTheme.colorScheme.surface,
                    onColorChange = { color -> update(colors.copy(surface = color)) },
                    onReset  = { update(colors.copy(surface = null)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerField(
                    label    = "Cards",
                    current  = colors.surfaceVariant,
                    defaultColor = MaterialTheme.colorScheme.surfaceVariant,
                    onColorChange = { color -> update(colors.copy(surfaceVariant = color)) },
                    onReset  = { update(colors.copy(surfaceVariant = null)) }
                )
            }
        }
    }
}

@Composable
private fun AudioSettingsTab(viewModel: MainViewModel) {
    val settings = viewModel.settings.collectAsState()
    val eq = settings.value.equalizerSettings
    val devices = remember { AudioDevices.list() }

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

                val deviceId = settings.value.audioOutputDevice
                val deviceOptions = listOf("System Default") + devices.map { it.label }
                val selectedDevice = if (deviceId.isEmpty()) "System Default"
                    else devices.firstOrNull { it.id == deviceId }?.label ?: "System Default"
                SettingsDropdown(
                    label    = "Audio Output",
                    selected = selectedDevice,
                    options  = deviceOptions,
                    onSelect = { label ->
                        val id = if (label == "System Default") ""
                            else devices.firstOrNull { it.label == label }?.id ?: ""
                        viewModel.updateSettings(settings.value.copy(audioOutputDevice = id))
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                SettingsToggle(
                    title    = "Audio Compatibility",
                    subtitle = "Use the legacy Java Sound engine that works on most systems",
                    checked  = settings.value.audioCompatibility,
                    onChange = { viewModel.updateSettings(settings.value.copy(audioCompatibility = it)) }
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
                            color = MaterialTheme.colorScheme.onSurface,
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
private fun AboutTab() {
    val uriHandler = LocalUriHandler.current
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
                    Text(
                        text = "@simplyIeaf",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/simplyIeaf")
                        }
                    )
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
            modifier      = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text    = { Text(option, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { expanded = false; onSelect(option) }
                )
            }
        }
    }
}

@Composable
private fun ColorPickerField(
    label: String,
    current: Long?,
    defaultColor: Color,
    onColorChange: (Long) -> Unit,
    onReset: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val baseColor = current?.let { Color(it.toInt()) } ?: defaultColor
    val baseHsv = remember(baseColor) { argbToHsv(baseColor.toArgb().toLong()) }
    var hue by remember(baseColor) { mutableFloatStateOf(baseHsv[0]) }
    var saturation by remember(baseColor) { mutableFloatStateOf(baseHsv[1]) }
    var value by remember(baseColor) { mutableFloatStateOf(baseHsv[2]) }

    val displayColor = if (expanded) Color(hsvToArgb(hue, saturation, value).toInt()) else baseColor

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(displayColor)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = argbHex(displayColor.toArgb().toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onReset) {
                Text(
                    text = "Reset",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                SvBox(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onLiveChange = { s, v ->
                        saturation = s
                        value = v
                    },
                    onCommit = { onColorChange(hsvToArgb(hue, saturation, value)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                HueBar(
                    hue = hue,
                    onLiveChange = { hue = it },
                    onCommit = { onColorChange(hsvToArgb(hue, saturation, value)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                )
            }
        }
    }
}

@Composable
private fun SvBox(
    hue: Float,
    saturation: Float,
    value: Float,
    onLiveChange: (Float, Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnLiveChange by rememberUpdatedState(onLiveChange)
    val currentOnCommit by rememberUpdatedState(onCommit)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color.White, Color(hsvToArgb(hue, 1f, 1f).toInt()))
                )
            )
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { position ->
                        val x = (position.x / size.width).coerceIn(0f, 1f)
                        val y = (position.y / size.height).coerceIn(0f, 1f)
                        currentOnLiveChange(x, 1f - y)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val x = (change.position.x / size.width).coerceIn(0f, 1f)
                        val y = (change.position.y / size.height).coerceIn(0f, 1f)
                        currentOnLiveChange(x, 1f - y)
                    },
                    onDragEnd = { currentOnCommit() }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cx = saturation * size.width
            val cy = (1f - value) * size.height
            drawCircle(Color.White, 6.dp.toPx(), Offset(cx, cy))
            drawCircle(Color.Black, 6.dp.toPx(), Offset(cx, cy), style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun HueBar(
    hue: Float,
    onLiveChange: (Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnLiveChange by rememberUpdatedState(onLiveChange)
    val currentOnCommit by rememberUpdatedState(onCommit)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFFFF0000),
                        Color(0xFFFFFF00),
                        Color(0xFF00FF00),
                        Color(0xFF00FFFF),
                        Color(0xFF0000FF),
                        Color(0xFFFF00FF),
                        Color(0xFFFF0000)
                    )
                )
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { position ->
                        currentOnLiveChange((position.x / size.width).coerceIn(0f, 1f) * 360f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentOnLiveChange((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                    },
                    onDragEnd = { currentOnCommit() }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cx = (hue / 360f) * size.width
            val cy = size.height / 2f
            drawCircle(Color.White, 6.dp.toPx(), Offset(cx, cy), style = Stroke(2.dp.toPx()))
            drawCircle(Color(0xFF444444), 3.dp.toPx(), Offset(cx, cy))
        }
    }
}

private fun argbHex(argb: Long): String {
    val value = argb.toInt()
    return String.format(
        "#%02X%02X%02X",
        (value shr 16) and 0xFF,
        (value shr 8) and 0xFF,
        value and 0xFF
    )
}

private fun argbToHsv(argb: Long): FloatArray {
    val color = Color(argb.toInt())
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val hue = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    val saturation = if (max == 0f) 0f else d / max
    return floatArrayOf((hue + 360f) % 360f, saturation, max)
}

private fun hsvToArgb(hue: Float, saturation: Float, value: Float): Long {
    val hh = (((hue % 360f) + 360f) % 360f) / 60f
    val i = hh.toInt()
    val f = hh - i
    val p = value * (1f - saturation)
    val q = value * (1f - f * saturation)
    val t = value * (1f - (1f - f) * saturation)
    val (r, g, b) = when (i) {
        0 -> Triple(value, t, p)
        1 -> Triple(q, value, p)
        2 -> Triple(p, value, t)
        3 -> Triple(p, q, value)
        4 -> Triple(t, p, value)
        else -> Triple(value, p, q)
    }
    return Color(r, g, b).toArgb().toLong()
}
