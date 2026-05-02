package com.prayerwheel.app.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale

/**
 * Dialog for sharing prayer intentions and sending light.
 */
@Composable
fun ShareIntentionDialog(
    currentMantraName: String,
    sessionMantras: BigInteger,
    currentIntention: String,
    onSendLight: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Share Your Practice",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Intention display
                if (currentIntention.isNotBlank()) {
                    Column {
                        Text(
                            text = "Intention",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = currentIntention,
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }

                // Session summary
                Column {
                    Text(
                        text = "This Session",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${formatNumber(sessionMantras)} mantras accumulated",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Mantra: $currentMantraName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Devotional message
                Text(
                    text = "Share your dedication or send light to all beings",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            Row {
                Button(
                    onClick = {
                        val shareText = buildShareText(
                            mantraName = currentMantraName,
                            mantras = sessionMantras,
                            intention = currentIntention
                        )
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share Practice")
                        context.startActivity(shareIntent)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share")
                }

                Spacer(modifier = Modifier.width(12.dp))

                OutlinedButton(
                    onClick = {
                        onSendLight()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send Light")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Builds the text content for sharing.
 */
private fun buildShareText(
    mantraName: String,
    mantras: BigInteger,
    intention: String
): String {
    val formattedMantras = NumberFormat.getNumberInstance(Locale.getDefault()).format(mantras)
    return buildString {
        appendLine("🙏 Prayer Wheel Practice")
        appendLine()
        appendLine("Mantra: $mantraName")
        appendLine("Mantras accumulated: $formattedMantras")
        if (intention.isNotBlank()) {
            appendLine()
            appendLine("Intention: $intention")
        }
        appendLine()
        appendLine("May the merit of this practice benefit all sentient beings.")
    }
}

/**
 * Formats a BigInteger for display.
 */
private fun formatNumber(number: BigInteger): String {
    return NumberFormat.getNumberInstance(Locale.getDefault()).format(number)
}
