package com.streamvault.app.ui.components.shell

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.streamvault.app.R
import com.streamvault.core.ui.design.BrowseColors
import com.streamvault.core.ui.design.BrowseFocusSpec
import com.streamvault.core.ui.design.BrowseShapes
import com.streamvault.core.ui.interaction.rememberTvInteractionSounds
import com.streamvault.domain.repository.CloudSyncState
import kotlinx.coroutines.delay

/** What the top bar needs to offer "sync now"; null hides the button. */
data class AppSyncShortcut(
    val state: CloudSyncState,
    val onSyncNow: () -> Unit
)

internal val LocalAppSyncShortcut = compositionLocalOf<AppSyncShortcut?> { null }

private enum class SyncOutcome { NONE, DONE, FAILED }

private const val OUTCOME_VISIBLE_MS = 4_000L

/**
 * A small "Sync" button in the top bar of every main page, shown only while signed in to
 * cross-device sync. It spins while syncing, then briefly says whether it worked.
 */
@Composable
internal fun AppSyncTopBarAction(shortcut: AppSyncShortcut) {
    val state = shortcut.state
    if (!state.isConfigured || state.signedInEmail == null) return

    var outcome by remember { mutableStateOf(SyncOutcome.NONE) }
    var wasSyncing by remember { mutableStateOf(state.isSyncing) }
    LaunchedEffect(state.isSyncing) {
        if (wasSyncing && !state.isSyncing) {
            outcome = if (state.lastError == null) SyncOutcome.DONE else SyncOutcome.FAILED
            wasSyncing = false
            delay(OUTCOME_VISIBLE_MS)
            outcome = SyncOutcome.NONE
        } else {
            wasSyncing = state.isSyncing
            if (state.isSyncing) outcome = SyncOutcome.NONE
        }
    }

    val label = when {
        state.isSyncing -> stringResource(R.string.nav_sync_syncing)
        outcome == SyncOutcome.DONE -> stringResource(R.string.nav_sync_done)
        outcome == SyncOutcome.FAILED -> stringResource(R.string.nav_sync_failed)
        else -> stringResource(R.string.nav_sync_now)
    }
    val description = stringResource(R.string.nav_sync_description)
    val accent = when (outcome) {
        SyncOutcome.FAILED -> Color(0xFFF0A898)
        SyncOutcome.DONE -> BrowseColors.Brand
        SyncOutcome.NONE -> BrowseColors.TextSecondary
    }
    val sounds = rememberTvInteractionSounds()

    Surface(
        onClick = {
            if (!state.isSyncing) {
                sounds.playSelect()
                shortcut.onSyncNow()
            }
        },
        modifier = Modifier.semantics { contentDescription = "$label. $description" },
        shape = ClickableSurfaceDefaults.shape(BrowseShapes.Control),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.035f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = accent,
            focusedContainerColor = BrowseColors.Brand.copy(alpha = 0.18f),
            focusedContentColor = BrowseColors.TextPrimary
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, BrowseColors.Outline), shape = BrowseShapes.Control),
            focusedBorder = Border(
                border = BorderStroke(BrowseFocusSpec.BorderWidth, BrowseColors.Focus),
                shape = BrowseShapes.Control
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconModifier = Modifier.size(16.dp)
            when {
                state.isSyncing -> {
                    val spin by rememberInfiniteTransition(label = "syncSpin").animateFloat(
                        initialValue = 0f,
                        targetValue = -360f,
                        animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing), RepeatMode.Restart),
                        label = "syncSpinAngle"
                    )
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = iconModifier.rotate(spin))
                }
                outcome == SyncOutcome.DONE -> Icon(Icons.Default.CloudDone, contentDescription = null, modifier = iconModifier)
                outcome == SyncOutcome.FAILED -> Icon(Icons.Default.SyncProblem, contentDescription = null, modifier = iconModifier)
                else -> Icon(Icons.Default.Sync, contentDescription = null, modifier = iconModifier)
            }
            Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}
