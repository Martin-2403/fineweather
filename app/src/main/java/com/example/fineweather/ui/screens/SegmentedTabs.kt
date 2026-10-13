package com.example.fineweather.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fineweather.nunitoSansFamily
import com.example.fineweather.ui.theme.HazyBlue
import com.example.fineweather.ui.theme.LightTeal
import com.example.fineweather.ui.theme.SoftMist
import com.example.fineweather.ui.theme.TealInk

@Composable
internal fun SegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "segment",
) {
    val isDarkTheme = isSystemInDarkTheme()
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                icon = {},
                contentPadding = SegmentedButtonDefaults.ContentPadding,
                colors =
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = LightTeal,
                        activeContentColor = HazyBlue,
                        inactiveContainerColor = SoftMist,
                        inactiveContentColor = TealInk,
                    ),
                border =
                    BorderStroke(
                        width = 1.dp,
                        color =
                            if (isDarkTheme) {
                                HazyBlue
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            },
                    ),
                modifier = Modifier.testTag("${tagPrefix}_$label"),
            ) {
                Text(
                    text = label,
                    fontFamily = nunitoSansFamily,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
