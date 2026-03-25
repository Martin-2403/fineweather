package com.example.fineweather.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fineweather.nunitoSansFamily
import com.example.fineweather.data.repositories.SettingsDefaults

@Composable
fun SettingsScreen(
    forecastDays: Int,
    onForecastDaysChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFourteenDay = forecastDays == SettingsDefaults.FORECAST_DAYS_LONG

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.Top,
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
                        text = "Forecast length",
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
            //Spacer(modifier = Modifier.height(4.dp))
            Text(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                text = "Switch to 14 days for a longer forecast average. Forecasts longer than 7 days are more uncertain.",
                fontFamily = nunitoSansFamily,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}
