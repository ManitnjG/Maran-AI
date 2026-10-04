package ai.maran.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * MARAN's native, reusable Compose component layer.
 *
 * This captures the existing MARAN palette as central tokens. The external Figma
 * file currently denies design inspection; replace these tokens with approved
 * Figma variables once that reference can be read. No copied or assumed Figma assets.
 */
internal object MaranTokens {
    val background = Color(0xFF10172B)
    val surface = Color(0xFF1A243A)
    val elevated = Color(0xFF202D48)
    val accent = Color(0xFF818CF8)
    val accentMuted = Color(0xFF312E81)
    val secondary = Color(0xFF38BDF8)
    val text = Color(0xFFF8FAFC)
    val muted = Color(0xFFA5B4CD)
    val outline = Color(0xFF546482)
    val success = Color(0xFF6EE7B7)
    val warning = Color(0xFFFCA5A5)
    val cornerSmall = 12.dp
    val cornerMedium = 16.dp
    val cornerLarge = 24.dp
    val screenPadding = 18.dp
}

@Composable
internal fun MaranTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MaranTokens.accent,
            onPrimary = Color(0xFF10172B),
            primaryContainer = MaranTokens.accentMuted,
            onPrimaryContainer = MaranTokens.text,
            secondary = MaranTokens.secondary,
            onSecondary = MaranTokens.background,
            tertiary = Color(0xFFC084FC),
            background = MaranTokens.background,
            onBackground = MaranTokens.text,
            surface = MaranTokens.surface,
            onSurface = MaranTokens.text,
            surfaceVariant = MaranTokens.elevated,
            onSurfaceVariant = MaranTokens.muted,
            outline = MaranTokens.outline,
            error = MaranTokens.warning
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(MaranTokens.cornerSmall),
            medium = RoundedCornerShape(MaranTokens.cornerMedium),
            large = RoundedCornerShape(MaranTokens.cornerLarge),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}

@Composable
internal fun MaranPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(MaranTokens.cornerSmall),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
}

@Composable
internal fun MaranSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(MaranTokens.cornerSmall),
        border = BorderStroke(1.dp, MaranTokens.outline),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
}

@Composable
internal fun MaranPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(MaranTokens.cornerMedium),
        border = BorderStroke(1.dp, MaranTokens.outline.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = MaranTokens.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
internal fun MaranSectionHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun MaranInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 4
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        modifier = modifier,
        minLines = minLines,
        maxLines = maxLines,
        shape = RoundedCornerShape(MaranTokens.cornerMedium),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaranTokens.accent,
            unfocusedBorderColor = MaranTokens.outline,
            focusedContainerColor = MaranTokens.surface,
            unfocusedContainerColor = MaranTokens.surface
        )
    )
}
