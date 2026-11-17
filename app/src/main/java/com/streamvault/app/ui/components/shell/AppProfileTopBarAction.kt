package com.streamvault.app.ui.components.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.streamvault.app.R
import com.streamvault.app.profiles.ProfileAvatar
import com.streamvault.app.profiles.ProfilePlaceholderIcon
import com.streamvault.core.ui.design.BrowseColors
import com.streamvault.core.ui.design.BrowseFocusSpec
import com.streamvault.core.ui.design.BrowseShapes
import com.streamvault.core.ui.interaction.rememberTvInteractionSounds

/** The active profile for the top bar; [name] is null while the provider has no profiles. */
data class AppProfileShortcut(
    val name: String?,
    val colorIndex: Int,
    val onOpen: () -> Unit
)

internal val LocalAppProfileShortcut = compositionLocalOf<AppProfileShortcut?> { null }

/** Opens "Who's watching?": the active profile's avatar and name, or a person icon. */
@Composable
internal fun AppProfileTopBarAction(shortcut: AppProfileShortcut) {
    val sounds = rememberTvInteractionSounds()
    val description = stringResource(R.string.profiles_button)
    Surface(
        onClick = {
            sounds.playSelect()
            shortcut.onOpen()
        },
        modifier = Modifier.semantics { contentDescription = listOfNotNull(shortcut.name, description).joinToString(". ") },
        shape = ClickableSurfaceDefaults.shape(BrowseShapes.Control),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.035f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = BrowseColors.TextSecondary,
            focusedContainerColor = BrowseColors.Brand.copy(alpha = 0.18f),
            focusedContentColor = BrowseColors.TextPrimary
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(BrowseFocusSpec.BorderWidth, BrowseColors.Focus),
                shape = BrowseShapes.Control
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (shortcut.name == null) {
                ProfilePlaceholderIcon(Modifier.size(20.dp))
            } else {
                ProfileAvatar(shortcut.name, shortcut.colorIndex, 24.dp)
                Text(text = shortcut.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}
