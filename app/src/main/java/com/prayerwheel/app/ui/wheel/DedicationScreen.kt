package com.prayerwheel.app.ui.wheel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prayerwheel.app.R
import com.prayerwheel.app.data.datastore.UserPreferences
import com.prayerwheel.app.ui.components.NumberFormatter
import com.prayerwheel.app.ui.theme.StarGold
import kotlinx.coroutines.launch
import java.math.BigInteger

private const val JE_NYER_TIBETAN = "འདི་ལྟར་བསགས་པའི་དགེ་བ་འདིས།"
private const val JE_NYER_ENGLISH =
    "By this virtue, may all beings attain the state of perfect awakening."

/**
 * Dedication prompt screen shown after a session ends.
 * Displays session stats and allows user to enter a dedication.
 *
 * When [jeNyerEnabled] is true (default), a Tibetan rejoicing (*je-nyer*)
 * section is shown first; tapping "Rejoice in this merit" plays a brief
 * glow then transitions to the dedication input.
 */
@Composable
fun DedicationScreen(
    sessionMantras: BigInteger,
    mantraName: String,
    customDedication: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sessionRotations: Long = 0L,
    jeNyerEnabled: Boolean = true
) {
    val defaultDedication = UserPreferences.DEFAULT_DEDICATION_TEXT
    var dedicationText by remember(customDedication) {
        mutableStateOf(customDedication ?: defaultDedication)
    }

    var rejoiced by remember { mutableStateOf(false) }
    var rejoicing by remember { mutableStateOf(false) }
    var glowAlpha by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    val showRejoiceSection = jeNyerEnabled && !rejoiced

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .imePadding()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .align(Alignment.Center),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Title
                Text(
                    text = "Session Complete",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (showRejoiceSection) {
                    Text(
                        text = JE_NYER_TIBETAN,
                        fontFamily = FontFamily(Font(R.font.noto_sans_tibetan)),
                        style = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = JE_NYER_ENGLISH,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "This session: ${NumberFormatter.formatWithFull(sessionMantras)} mantras accumulated." +
                            if (sessionRotations > 0L) {
                                "\n${NumberFormatter.formatWithFull(sessionRotations.toBigInteger())} rotations of $mantraName"
                            } else "",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (rejoicing) {
                        Text(
                            text = "Rejoicing in this merit.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    val containerColor = lerp(
                        MaterialTheme.colorScheme.primaryContainer,
                        StarGold,
                        glowAlpha
                    )
                    TextButton(
                        onClick = {
                            if (!rejoicing) {
                                rejoicing = true
                                scope.launch {
                                    glowAlpha = 1f
                                    val animatable = Animatable(1f)
                                    animatable.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(durationMillis = 1200)
                                    ) {
                                        glowAlpha = value
                                    }
                                    rejoiced = true
                                }
                            }
                        },
                        enabled = !rejoicing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(containerColor)
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "Rejoice in this merit",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    Text(
                        text = "You accumulated ${NumberFormatter.formatWithFull(sessionMantras)} mantras of ${mantraName}",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = defaultDedication,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = dedicationText,
                        onValueChange = { dedicationText = it },
                        label = { Text("Your dedication") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    TextButton(
                        onClick = { onConfirm(dedicationText.ifBlank { null }) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "Confirm",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}


