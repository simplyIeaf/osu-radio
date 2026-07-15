package com.osuradio.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.osuradio.app.BuildConfig
import com.osuradio.app.data.AnimationStyle
import com.osuradio.app.data.AudioTransition
import com.osuradio.app.data.EqualizerSettings
import com.osuradio.app.data.EqPreset
import com.osuradio.app.viewmodel.MainViewModel

private val EQ_BAND_LABELS = listOf("Sub\n60Hz", "Bass\n230Hz", "Mid\n910Hz", "Hi\n3.6kHz", "Air\n14kHz")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings = viewModel.settings.collectAsState()
    val eq = settings.value.equalizerSettings ?: EqualizerSettings()

    val animationLabels = mapOf(
        AnimationStyle.SLIDE to "Slide",
        AnimationStyle.FADE  to "Fade",
        AnimationStyle.SCALE to "Scale",
        AnimationStyle.NONE  to "None"
    )
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
        // ── General ──────────────────────────────────────────────────────────
        item {
            SettingsCard(title = "Settings") {
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
                Spacer(modifier = Modifier.height(12.dp))
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
                    title    = "Automatically check for updates",
                    subtitle = "Checks on app start (takes effect after restart)",
                    checked  = settings.value.autoCheckUpdates,
                    onChange = { viewModel.updateSettings(settings.value.copy(autoCheckUpdates = it)) }
                )
            }
        }

        // ── Audio ────────────────────────────────────────────────────────────
        item {
            SettingsCard(title = "Audio") {
                // Equalizer
                SettingsToggle(
                    title    = "Equalizer",
                    subtitle = "Apply per-band gain to audio output",
                    checked  = eq.enabled,
                    onChange = { viewModel.updateSettings(settings.value.copy(equalizerSettings = eq.copy(enabled = it))) }
                )

                if (eq.enabled) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset picker
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

                    // Band sliders — each from -1500 to +1500 mB displayed as dB
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

                // Loudness Normalization
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

        // ── About ─────────────────────────────────────────────────────────────
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
