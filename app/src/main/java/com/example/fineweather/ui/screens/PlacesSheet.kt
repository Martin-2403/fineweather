package com.example.fineweather.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fineweather.R
import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.nunitoSansFamily

/**
 * A bottom sheet listing places to pick from: the saved favorites (with a remove button per row)
 * or the other matches of a search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlacesSheet(
    title: String,
    places: List<GeoPlace>,
    emptyMessage: String,
    onSelect: (GeoPlace) -> Unit,
    onDismiss: () -> Unit,
    onRemove: ((GeoPlace) -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("placesSheet"),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                fontFamily = nunitoSansFamily,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            DividerLine()
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
                    onRemove = onRemove?.let { remove -> { remove(place) } },
                )
                if (index != places.lastIndex) {
                    DividerLine()
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(
    place: GeoPlace,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val subtitle = place.displaySubtitle()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(vertical = 12.dp)
                    .testTag("placeRow"),
        ) {
            Text(
                text = place.displayName(),
                fontFamily = nunitoSansFamily,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontFamily = nunitoSansFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onRemove != null) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier.testTag("removeFavoriteButton"),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.delete_24),
                    contentDescription = "Remove favorite",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
