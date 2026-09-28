package io.fidelitycard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.fidelitycard.core.CardProgress

/**
 * A row of filled/empty circles standing in for a physical stamp card - no
 * "7/10" fraction to parse, just count how many are lit. A filled dot
 * shows the program's own [icon] (card personalization, TODO.md
 * "Product / UX") rather than a generic checkmark, on the program's own
 * [color] - the "stamp" is literally the business's stamp. `threshold` is
 * capped visually at a reasonable number of dots; a very high threshold
 * would need a different representation, not attempted here.
 */
@Composable
fun StampProgressDots(progress: CardProgress, color: Int, icon: String, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val filledCount = progress.stampCount.coerceAtMost(progress.threshold)
        repeat(progress.threshold) { index ->
            if (index < filledCount) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(color)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(icon, style = MaterialTheme.typography.bodySmall)
                }
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
