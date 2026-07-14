package com.prayerwheel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prayerwheel.app.data.model.WheelSkin
import com.prayerwheel.app.data.model.WheelSkins

/**
 * Bottom sheet for customizing the prayer wheel appearance.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WheelCustomizer(
    selectedSkinId: String,
    unlockedAchievements: Set<String>,
    onSkinSelected: (WheelSkin) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Wheel Appearance",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Choose a skin to customize how your prayer wheel looks",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                WheelSkins.ALL.forEach { skin ->
                    val isLocked = !isSkinUnlocked(skin.id, unlockedAchievements)
                    SkinCard(
                        skin = skin,
                        isSelected = skin.id == selectedSkinId,
                        isLocked = isLocked,
                        onClick = {
                            if (!isLocked) {
                                onSkinSelected(skin)
                                onDismiss()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun isSkinUnlocked(skinId: String, unlockedAchievements: Set<String>): Boolean {
    val requiredAchievement = when (skinId) {
        "sandalwood" -> "mala_complete"
        "lapis_lazuli" -> "ten_thousand"
        "turquoise" -> "hundred_thousand"
        "ruby" -> "ten_million"
        "amethyst" -> "billion"
        "rainbow" -> "trillion"
        else -> null
    }
    return requiredAchievement == null || unlockedAchievements.contains(requiredAchievement)
}

private fun getSkinUnlockRequirement(skinId: String): String {
    return when (skinId) {
        "sandalwood" -> "Mala Complete"
        "lapis_lazuli" -> "10K Mantras"
        "turquoise" -> "100K Mantras"
        "ruby" -> "10M Mantras"
        "amethyst" -> "1B Mantras"
        "rainbow" -> "1T Mantras"
        else -> ""
    }
}

/**
 * A card displaying a skin option with color preview circles.
 */
@Composable
private fun SkinCard(
    skin: WheelSkin,
    isSelected: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(140.dp)
            .clickable(enabled = !isLocked, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isLocked -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .alpha(if (isLocked) 0.5f else 1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skin name
            Text(
                text = skin.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (isLocked) {
                Spacer(modifier = Modifier.height(8.dp))
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = getSkinUnlockRequirement(skin.id),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                // Color preview circles
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    ColorCircle(color = Color(skin.cylinderColor), label = "Cylinder")
                    ColorCircle(color = Color(skin.capColor), label = "Cap")
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    ColorCircle(color = Color(skin.stemColor), label = "Stem")
                    ColorCircle(color = Color(skin.crystalColor), label = "Crystal")
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ColorCircle(color = Color(skin.weightColor), label = "Weight")
                    ColorCircle(color = Color(skin.rayColor), label = "Rays")
                }

                // Selection checkmark
                if (isSelected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * A small circle showing a color preview.
 */
@Composable
private fun ColorCircle(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
        )
    }
}
