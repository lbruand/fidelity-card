package io.fidelitycard.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A card's visual personality (`color`/`icon` on `ProgramManifest` -
 * TODO.md "Product / UX"): a small fixed palette/icon set, deliberately
 * not a free-form color/image picker - keeps the choice quick for an
 * issuer creating a card, and keeps every rendering simple (a filled
 * circle + one glyph) regardless of what gets picked.
 */
object CardStyle {
    val colors: List<Int> = listOf(
        0xFF00897B, 0xFFD84315, 0xFF5E35B1, 0xFF1E88E5,
        0xFFF9A825, 0xFF43A047, 0xFFD81B60, 0xFF6D4C41,
    ).map { it.toInt() }

    val icons: List<String> = listOf("☕", "🍕", "🍔", "🍦", "🎂", "🎁", "⭐", "❤️", "🍩", "🐶", "🎮", "✂️")

    val defaultColor: Int = colors.first()
    val defaultIcon: String = icons.first()
}

/** A small filled badge showing a card's [color]/[icon] - used anywhere a card/program is listed. */
@Composable
fun CardStyleBadge(color: Int, icon: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(Color(color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, style = MaterialTheme.typography.titleMedium)
    }
}

/** A row of tappable color swatches, marking [selected] with a check mark. */
@Composable
fun ColorPickerRow(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CardStyle.colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .clickable { onSelect(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (color == selected) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White)
                }
            }
        }
    }
}

/** A row of tappable emoji, marking [selected] with a border. */
@Composable
fun IconPickerRow(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CardStyle.icons.forEach { icon ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(
                        if (icon == selected) {
                            Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(icon) },
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
