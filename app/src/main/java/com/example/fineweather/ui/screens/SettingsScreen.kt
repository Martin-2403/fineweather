package com.example.fineweather.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.data.repositories.SettingsDefaults
import com.example.fineweather.nunitoSansFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    forecastDays: Int,
    historicReference: HistoricReference,
    searchLanguage: String,
    temperatureComparisonMode: TemperatureComparisonMode,
    onForecastDaysChange: (Int) -> Unit,
    onHistoricReferenceChange: (HistoricReference) -> Unit,
    onSearchLanguageChange: (String) -> Unit,
    onTemperatureComparisonModeChange: (TemperatureComparisonMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFourteenDay = forecastDays == SettingsDefaults.FORECAST_DAYS_LONG
    var referenceMenuExpanded by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var comparisonMenuExpanded by remember { mutableStateOf(false) }
    val referenceOptions =
        listOf(
            HistoricReference.CLASSIC to "Historical baseline",
            HistoricReference.CURRENT to "Current reference",
        )
    val languageOptions = SettingsDefaults.SEARCH_LANGUAGE_OPTIONS

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
    ) {
        Column(
        ) {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Use longer forecast",
                            fontFamily = nunitoSansFamily,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (isFourteenDay) "14 days" else "7 days",
                            fontFamily = nunitoSansFamily,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                    Switch(
                        checked = isFourteenDay,
                        onCheckedChange = { checked ->
                            onForecastDaysChange(
                                if (checked) {
                                    SettingsDefaults.FORECAST_DAYS_LONG
                                } else {
                                    SettingsDefaults.FORECAST_DAYS_SHORT
                                },
                            )
                        },
                        modifier = Modifier.testTag("forecastDaysToggle"),
                    )
                }
                Text(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    text = "Switch to 14 days for a longer forecast average. " +
                            "Forecasts longer than 7 days are more uncertain.",
                    fontFamily = nunitoSansFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "Search language",
                        fontFamily = nunitoSansFamily,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = languageMenuExpanded,
                        onExpandedChange = { languageMenuExpanded = !languageMenuExpanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val languageLabel =
                            languageOptions.firstOrNull { it.code == searchLanguage }
                                ?.displayLabel()
                                ?: searchLanguage
                        OutlinedTextField(
                            value = languageLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = {
                                Text("Language", fontFamily = nunitoSansFamily)
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuExpanded)
                            },
                            modifier =
                                Modifier
                                    .menuAnchor(
                                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                        enabled = true
                                    )
                                    .fillMaxWidth()
                                    .testTag("searchLanguageDropdown"),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = nunitoSansFamily,
                            ),
                        )
                        ExposedDropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { languageMenuExpanded = false },
                        ) {
                            languageOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.displayLabel(),
                                            fontFamily = nunitoSansFamily,
                                        )
                                    },
                                    onClick = {
                                        onSearchLanguageChange(option.code)
                                        languageMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Search results are localized when available. If a translation is not " +
                                "available, the API falls back to English or the native location name.",
                        fontFamily = nunitoSansFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "Historic reference",
                        fontFamily = nunitoSansFamily,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = referenceMenuExpanded,
                        onExpandedChange = { referenceMenuExpanded = !referenceMenuExpanded },
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        val referenceLabel =
                            referenceOptions.firstOrNull { it.first == historicReference }
                                ?.let { "${it.second}: ${historicReference.displayLabel()}" }
                                ?: historicReference.displayLabel()
                        OutlinedTextField(
                            value = referenceLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = {
                                Text("Timeframe", fontFamily = nunitoSansFamily)
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = referenceMenuExpanded)
                            },
                            modifier =
                                Modifier
                                    .menuAnchor(
                                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                        enabled = true
                                    )
                                    .fillMaxWidth()
                                    .testTag("historicReferenceDropdown"),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = nunitoSansFamily,
                            ),
                        )
                        ExposedDropdownMenu(
                            expanded = referenceMenuExpanded,
                            onDismissRequest = { referenceMenuExpanded = false },
                        ) {
                            referenceOptions.forEach { (option, description) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "$description: ${option.displayLabel()} ",
                                            fontFamily = nunitoSansFamily,
                                        )
                                    },
                                    onClick = {
                                        onHistoricReferenceChange(option)
                                        referenceMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Use the current World Meteorological Organization reference 30‑year normal for comparing conditions to the present‑day climate, and the historical baseline (1961–1990) when assessing long‑term climate change trends relative to a stable past reference period.",
                        fontFamily = nunitoSansFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "Divergence method",
                        fontFamily = nunitoSansFamily,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = comparisonMenuExpanded,
                        onExpandedChange = { comparisonMenuExpanded = !comparisonMenuExpanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = temperatureComparisonMode.label,
                            onValueChange = {},
                            readOnly = true,
                            label = {
                                Text("Method", fontFamily = nunitoSansFamily)
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = comparisonMenuExpanded)
                            },
                            modifier =
                                Modifier
                                    .menuAnchor(
                                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                        enabled = true,
                                    )
                                    .fillMaxWidth()
                                    .testTag("temperatureComparisonDropdown"),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = nunitoSansFamily,
                            ),
                        )
                        ExposedDropdownMenu(
                            expanded = comparisonMenuExpanded,
                            onDismissRequest = { comparisonMenuExpanded = false },
                        ) {
                            TemperatureComparisonMode.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontFamily = nunitoSansFamily,
                                        )
                                    },
                                    onClick = {
                                        onTemperatureComparisonModeChange(option)
                                        comparisonMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = temperatureComparisonMode.description,
                        fontFamily = nunitoSansFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}
