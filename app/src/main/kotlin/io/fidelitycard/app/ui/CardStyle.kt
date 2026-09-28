package io.fidelitycard.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

/**
 * A translucent circular chip showing a card's [icon] - for use on a
 * surface already tinted with that same card's color (a colored list
 * item or header), so the icon stays legible regardless of exactly which
 * palette color it's sitting on, rather than a same-color-on-same-color
 * box that would blend into the background.
 */
@Composable
fun CardStyleBadge(icon: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * A translucent light "tray" for placing something that itself uses
 * [CardStyle] colors (e.g. [StampProgressDots]) on top of an
 * already-color-tinted surface, without the two blending together.
 */
@Composable
fun ColorSurfaceTray(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(8.dp),
    ) {
        content()
    }
}

/** A colored header band for a business/card detail screen: [color] fill, [icon] badge, [title] in white. */
@Composable
fun CardColorHeader(color: Int, icon: String, title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(color))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardStyleBadge(icon, size = 56.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
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
