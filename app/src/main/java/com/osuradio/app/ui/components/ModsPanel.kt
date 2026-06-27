package com.osuradio.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.osuradio.app.data.ModSettings
import com.osuradio.app.data.SongMod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModsPanel(
    modSettings: ModSettings,
    onModChanged: (SongMod, Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var customSpeed by remember { mutableStateOf(modSettings.customSpeed) }

    val modLabels = mapOf(
        SongMod.NONE to "None",
        SongMod.DAYCORE to "Daycore",
        SongMod.NIGHTCORE to "Nightcore",
        SongMod.DOUBLE_TIME to "Double Time",
        SongMod.HALF_TIME to "Half Time",
        SongMod.WIND_UP to "Wind Up",
        SongMod.WIND_DOWN to "Wind Down",
        SongMod.BASS_BOOST to "Bass Boost",
        SongMod.VAPORWAVE to "Vaporwave",
        SongMod.CUSTOM_SPEED to "Custom Speed"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = modLabels[modSettings.activeMod] ?: "None",
                onValueChange = {},
                readOnly = true,
                label = { Text("Mods") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                SongMod.entries.forEach { mod ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                modLabels[mod] ?: mod.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = {
                            expanded = false
                            onModChanged(mod, customSpeed)
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = modSettings.activeMod == SongMod.CUSTOM_SPEED,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = "Speed: ${String.format("%.2f", customSpeed)}x",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "0.25x",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = customSpeed,
                        onValueChange = { customSpeed = it },
                        onValueChangeFinished = { onModChanged(SongMod.CUSTOM_SPEED, customSpeed) },
                        valueRange = 0.25f..3.0f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Text(
                        text = "3.0x",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
