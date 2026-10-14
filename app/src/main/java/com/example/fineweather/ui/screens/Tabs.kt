package com.example.fineweather.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.MaterialTheme
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

/** A small two-or-more option pill for toggles that share a line with a title. */
@Composable
internal fun CompactToggle(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "toggle",
) {
    val shape = RoundedCornerShape(50)
    val outline = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    Row(
        modifier =
            modifier
                .height(30.dp)
                .clip(shape)
                .border(1.dp, outline, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .background(if (selected) LightTeal else SoftMist)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                        .padding(horizontal = 12.dp)
                        .testTag("${tagPrefix}_$label"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontFamily = nunitoSansFamily,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) HazyBlue else TealInk,
                )
            }
        }
    }
}

/** Slim text tabs with an underline, for the view switch below the place tabs. */
@Composable
internal fun UnderlineTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "tab",
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(36.dp)) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                            .testTag("${tagPrefix}_$label"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontFamily = nunitoSansFamily,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(if (selected) 3.dp else 0.dp)
                                .background(accent),
                    )
                }
            }
        }
    }
}
