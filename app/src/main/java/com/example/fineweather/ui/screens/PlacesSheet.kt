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
 * A bottom sheet listing places to pick from, e.g. the other matches of a search. The saved favorites
 * have their own [FavoritesPanel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlacesSheet(
    title: String,
    places: List<GeoPlace>,
    emptyMessage: String,
    onSelect: (GeoPlace) -> Unit,
    onDismiss: () -> Unit,
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
                    onRemove = null,
                )
                if (index != places.lastIndex) {
                    DividerLine()
                }
            }
        }
    }
}

/**
 * A place to pick, with a remove button when [onRemove] is given and, before it, a house button when
 * [onToggleHome] is given that shows whether the place [isHome].
 */
@Composable
internal fun PlaceRow(
    place: GeoPlace,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    isHome: Boolean = false,
    onToggleHome: (() -> Unit)? = null,
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
        if (onToggleHome != null || onRemove != null) Spacer(modifier = Modifier.width(4.dp))
        if (onToggleHome != null) {
            IconButton(
                onClick = onToggleHome,
                modifier = Modifier.testTag("homeFavoriteButton"),
            ) {
                Icon(
                    painter = painterResource(id = if (isHome) R.drawable.home_filled else R.drawable.home_outline),
                    contentDescription =
                        if (isHome) "Remove ${place.displayName()} as home" else "Set ${place.displayName()} as home",
                    tint =
                        if (isHome) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.testTag("removeFavoriteButton"),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.delete_24),
                    contentDescription = "Remove ${place.displayName()} from favorites",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
