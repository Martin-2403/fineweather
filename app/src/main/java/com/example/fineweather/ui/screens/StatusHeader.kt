package com.example.fineweather.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fineweather.domain.Band
import com.example.fineweather.domain.SummaryBlock
import com.example.fineweather.nunitoSansFamily
import com.example.fineweather.ui.models.ValueWithIcons

internal val WideLayoutMinWidth = 600.dp
private val WideCardMaxWidth = 840.dp
private val CompactCardMaxWidth = 480.dp
private val WideImageMaxWidth = 360.dp
private const val COMPACT_IMAGE_FRACTION = 0.4f
private const val IMAGE_ASPECT_RATIO = 3f / 2f

@Composable
internal fun StatusHeaderCard(
    imageRes: Int,
    summary: List<SummaryBlock>,
    statusCardValue: ValueWithIcons,
    trendCardValue: ValueWithIcons,
    shape: RoundedCornerShape,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val wide = maxWidth >= WideLayoutMinWidth
        Card(
            shape = shape,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            modifier =
                Modifier
                    .widthIn(max = if (wide) WideCardMaxWidth else CompactCardMaxWidth)
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        shape,
                    ),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cardWidth = maxWidth
                val imageWidth =
                    if (wide) minOf(cardWidth * 0.5f, WideImageMaxWidth) else cardWidth * COMPACT_IMAGE_FRACTION
                Column(modifier = Modifier.fillMaxWidth()) {
                    ImageWithText(
                        imageRes = imageRes,
                        imageWidth = imageWidth,
                        blocks = summary,
                    )
                    DividerLine()
                    // The split lines up with the edge of the picture above.
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                    ) {
                        StatusTrendCell(
                            label = "Trend",
                            value = trendCardValue,
                            modifier = Modifier.width(imageWidth),
                        )
                        VerticalDividerLine()
                        StatusTrendCell(
                            label = "Divergence",
                            value = statusCardValue,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageWithText(
    imageRes: Int,
    imageWidth: Dp,
    blocks: List<SummaryBlock>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EarthStatus(
            imageRes = imageRes,
            modifier =
                Modifier
                    .width(imageWidth)
                    .aspectRatio(IMAGE_ASPECT_RATIO),
        )
        SummaryBlocks(
            blocks = blocks,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun EarthStatus(
    imageRes: Int,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(imageRes),
        contentDescription = "Status illustration",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@Composable
internal fun SummaryBlocks(
    blocks: List<SummaryBlock>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is SummaryBlock.Headline -> HeadlineText(block)
                is SummaryBlock.Detail ->
                    Text(
                        text = block.text,
                        fontFamily = nunitoSansFamily,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                is SummaryBlock.Caveat ->
                    Text(
                        text = block.text,
                        fontFamily = nunitoSansFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningIconTint,
                    )
            }
        }
    }
}

@Composable
private fun HeadlineText(block: SummaryBlock.Headline) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(bandColor(block.band), RoundedCornerShape(2.dp)),
        )
        Text(
            text = block.text,
            fontFamily = nunitoSansFamily,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun bandColor(band: Band?): Color =
    when (band) {
        Band.WARM -> WarmIconTint
        Band.COLD -> CoolIconTint
        Band.NEUTRAL -> NeutralIconTint
        null -> Color.Transparent
    }
