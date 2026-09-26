package io.fidelitycard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.fidelitycard.core.CardProgress

/**
 * A row of filled/empty circles standing in for a physical stamp card - no
 * "7/10" fraction to parse, just count how many are lit. `threshold` is
 * capped visually at a reasonable number of dots; a very high threshold
 * would need a different representation, not attempted here.
 */
@Composable
fun StampProgressDots(progress: CardProgress, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val filledCount = progress.stampsSinceLastRedemption.coerceAtMost(progress.threshold)
        repeat(progress.threshold) { index ->
            if (index < filledCount) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray.copy(alpha = 0.4f)),
                )
            }
        }
    }
}
