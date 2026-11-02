package com.prayerwheel.app.ui.wheel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prayerwheel.app.data.datastore.ViewMode
import com.prayerwheel.app.data.model.SavedWheel
import com.prayerwheel.app.ui.components.NumberFormatter

/**
 * Bottom bar view mode selector with pill/chip style.
 * More discoverable than a small icon toggle.
 */
@Composable
internal fun ViewModeBottomBar(
    currentMode: ViewMode,
    onModeSelected: (ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModes = listOf(
        ViewMode.SIDE_VIEW to "Side",
        ViewMode.TOP_DOWN to "Top",
        ViewMode.ABSTRACT to "Abstract",
        ViewMode.TABLE_TOP to "Table",
        ViewMode.GLOBE to "Globe"
    )

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        viewModes.forEach { (mode, label) ->
            val isSelected = mode == currentMode
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onModeSelected(mode) },
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    },
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                )
            }
        }
    }
}

/**
 * Quick select cards for saved wheels.
 */
@Composable
internal fun SavedWheelsQuickSelect(
    wheels: List<SavedWheel>,
    currentCapacity: Long,
    onWheelSelected: (SavedWheel) -> Unit,
    onManageWheels: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Saved Wheels",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            TextButton(onClick = onManageWheels) {
                Text(
                    text = "Manage",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            wheels.take(5).forEach { wheel ->
                SavedWheelCard(
                    wheel = wheel,
                    isSelected = wheel.capacity == currentCapacity,
                    onClick = { onWheelSelected(wheel) }
                )
            }
        }
    }
}

@Composable
private fun SavedWheelCard(
    wheel: SavedWheel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(120.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = wheel.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatShortNumber(wheel.capacity),
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

/**
 * Formats a large number in short form (e.g., 100M, 1B).
 */
private fun formatShortNumber(value: Long): String {
    return NumberFormatter.formatLong(value)
}
