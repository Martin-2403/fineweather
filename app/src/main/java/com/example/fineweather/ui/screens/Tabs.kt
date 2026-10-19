package com.example.fineweather.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.ceil
import com.example.fineweather.nunitoSansFamily
import com.example.fineweather.ui.theme.HazyBlue
import com.example.fineweather.ui.theme.LightTeal
import com.example.fineweather.ui.theme.SoftMist
import com.example.fineweather.ui.theme.TealInk

/**
 * A small two-or-more option pill for toggles that share a line with a title. [contentDescriptions]
 * spell out short labels for screen readers, one per label. With [fillWidth] the pill stretches
 * across its parent and splits the width evenly between the options.
 */
@Composable
internal fun CompactToggle(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "toggle",
    contentDescriptions: List<String>? = null,
    fillWidth: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    val outline = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    Row(
        modifier =
            modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .height(30.dp)
                .clip(shape)
                .selectableGroup()
                .border(1.dp, outline, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier =
                    Modifier
                        .then(if (fillWidth) Modifier.weight(1f) else Modifier)
                        .fillMaxHeight()
                        .background(if (selected) LightTeal else SoftMist)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                        .padding(horizontal = 12.dp)
                        .testTag("${tagPrefix}_$label")
                        .semantics { contentDescriptions?.getOrNull(index)?.let { contentDescription = it } },
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

/**
 * Folder-style tabs for the card below them: each tab has a rounded top with borders on the top and
 * sides. The selected tab is filled like the card and opens into it; the others leave the card's
 * top border as their baseline.
 */
@Composable
internal fun CardTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    shape: RoundedCornerShape,
    containerColor: Color,
    modifier: Modifier = Modifier,
    tagPrefix: String = "tab",
) {
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val tabShape = shape.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
    Row(
        // Drawn above the card so the selected tab can cover the card's top border.
        modifier = modifier.fillMaxWidth().height(36.dp).zIndex(1f),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawBehind { drawTab(tabShape, selected, containerColor, outline) }
                        .clip(tabShape)
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
            }
        }
    }
}

/** Draws a tab's top and side borders inside its bounds, like `Modifier.border` does for the cards. */
private fun DrawScope.drawTab(
    shape: RoundedCornerShape,
    selected: Boolean,
    fill: Color,
    outline: Color,
) {
    // Modifier.border rounds its width up to whole pixels, so the tab has to as well to line up with the card.
    val stroke = ceil(1.dp.toPx())
    // The selected tab reaches one stroke past its bounds to cover the card's top border.
    val bottom = if (selected) size.height + stroke else size.height
    if (selected) {
        drawOutline(shape.createOutline(Size(size.width, bottom), layoutDirection, this), fill)
    }
    val inset = stroke / 2
    val radius = shape.topStart.toPx(size, this)
    val arc = 2 * (radius - inset)
    val path =
        Path().apply {
            moveTo(inset, bottom)
            lineTo(inset, radius)
            arcTo(Rect(inset, inset, inset + arc, inset + arc), 180f, 90f, false)
            lineTo(size.width - radius, inset)
            arcTo(Rect(size.width - inset - arc, inset, size.width - inset, inset + arc), 270f, 90f, false)
            lineTo(size.width - inset, bottom)
        }
    drawPath(path, outline, style = Stroke(stroke))
}
