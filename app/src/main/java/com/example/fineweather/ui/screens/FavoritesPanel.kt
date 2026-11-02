package com.example.fineweather.ui.screens

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.nunitoSansFamily

/** Rows shown before the list scrolls, and the height one row takes (a name, a subtitle and padding). */
private const val VISIBLE_ROWS = 8
private val ROW_HEIGHT = 64.dp
private val HEADER_HEIGHT = 40.dp
private val EDGE_MARGIN = 8.dp
private val PANEL_WIDTH = 288.dp

/**
 * The saved favorites in a panel that hangs under the favorites icon of the top bar and grows from it.
 * [anchor] is the icon's bounds in the window; the panel scrolls once it would show more than
 * [VISIBLE_ROWS] rows, and closes on [onDismiss] (outside tap or back press).
 */
@Composable
internal fun FavoritesPanel(
    visible: Boolean,
    anchor: IntRect,
    places: List<GeoPlace>,
    emptyMessage: String,
    onSelect: (GeoPlace) -> Unit,
    onRemove: (GeoPlace) -> Unit,
    onDismiss: () -> Unit,
) {
    // The popup is a window of its own: keep it composed until the exit animation has finished.
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = visible
    if (!visibleState.currentState && !visibleState.targetState) return

    val transition = rememberTransition(visibleState, label = "FavoritesPanel")
    val scale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = if (targetState) 160 else 100) },
        label = "scale",
    ) { shown -> if (shown) 1f else 0.7f }
    val alpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = if (targetState) 120 else 100) },
        label = "alpha",
    ) { shown -> if (shown) 1f else 0f }

    val density = LocalDensity.current
    val edgeMarginPx = with(density) { EDGE_MARGIN.roundToPx() }
    var transformOrigin by remember { mutableStateOf(TransformOrigin(1f, 0f)) }
    val positionProvider =
        remember(anchor, edgeMarginPx) {
            AnchoredPanelPositionProvider(anchor, edgeMarginPx) { transformOrigin = it }
        }
    // Never taller than the room under the icon, e.g. in landscape.
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val roomBelow = with(density) { (windowHeightPx - anchor.bottom).toDp() } - EDGE_MARGIN * 2
    val maxHeight = minOf(HEADER_HEIGHT + ROW_HEIGHT * VISIBLE_ROWS, roomBelow)

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier =
                Modifier
                    .testTag("favoritesPanel")
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        this.transformOrigin = transformOrigin
                    },
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .width(PANEL_WIDTH)
                        .heightIn(max = maxHeight)
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            ) {
                Text(
                    text = "Favorites",
                    modifier = Modifier.semantics { heading() },
                    fontFamily = nunitoSansFamily,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                DividerLine()
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (places.isEmpty()) {
                        Text(
                            text = emptyMessage,
                            modifier = Modifier.padding(vertical = 12.dp),
                            fontFamily = nunitoSansFamily,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    places.forEachIndexed { index, place ->
                        PlaceRow(
                            place = place,
                            onClick = { onSelect(place) },
                            onRemove = { onRemove(place) },
                        )
                        if (index != places.lastIndex) {
                            DividerLine()
                        }
                    }
                }
            }
        }
    }
}

/**
 * The left edge of a panel [panelWidth] wide that sits at the end of the window, [edgeMargin] in from it
 * (and at the start in right-to-left layouts), where the top bar's icons are. Never closer than
 * [edgeMargin] to the left edge, even when the panel is wider than the window.
 */
internal fun panelLeft(
    windowWidth: Int,
    panelWidth: Int,
    edgeMargin: Int,
    rtl: Boolean,
): Int = if (rtl) edgeMargin else (windowWidth - panelWidth - edgeMargin).coerceAtLeast(edgeMargin)

/**
 * Puts a popup right under [anchor] (window coordinates), at the end of the window like the top bar's
 * icons (the favorites icon is not the last one, so lining up with it would leave the panel off to the
 * side), and reports where the anchor's center lies on the popup's top edge for scaling from it.
 */
private class AnchoredPanelPositionProvider(
    private val anchor: IntRect,
    private val edgeMarginPx: Int,
    private val onOriginCalculated: (TransformOrigin) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = panelLeft(windowSize.width, popupContentSize.width, edgeMarginPx, layoutDirection == LayoutDirection.Rtl)
        val y = anchor.bottom
        val originX =
            if (popupContentSize.width > 0) (anchor.center.x - x).toFloat() / popupContentSize.width else 1f
        onOriginCalculated(TransformOrigin(originX.coerceIn(0f, 1f), 0f))
        return IntOffset(x, y)
    }
}
