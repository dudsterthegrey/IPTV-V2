package com.streamvault.core.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import com.streamvault.core.ui.R
import com.streamvault.core.ui.design.AppColors
import com.streamvault.core.ui.interaction.TvIconButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Starts the system speech recognizer and hands back the best transcription. */
@Stable
class VoiceSearchLauncher internal constructor(private val start: () -> Unit) {
    fun launch() = start()
}

@Composable
fun rememberVoiceSearchLauncher(onResult: (String) -> Unit): VoiceSearchLauncher {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val unavailableMessage = stringResource(R.string.voice_search_unavailable)
    val prompt = stringResource(R.string.voice_search_prompt)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(currentOnResult)
    }
    return remember(launcher, context) {
        VoiceSearchLauncher {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            // Fire TV and some Android TV builds ship without a speech recognizer.
            val launched = intent.resolveActivity(context.packageManager) != null &&
                runCatching { launcher.launch(intent) }
                    .onFailure { if (it !is ActivityNotFoundException) throw it }
                    .isSuccess
            if (!launched) {
                Toast.makeText(context, unavailableMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** Round mic button placed next to a search field. */
@Composable
fun VoiceSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvIconButton(
        onClick = onClick,
        modifier = modifier,
        scale = ButtonDefaults.scale(focusedScale = 1f),
        shape = ButtonDefaults.shape(shape = CircleShape),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, AppColors.Outline), shape = CircleShape),
            focusedBorder = Border(BorderStroke(2.dp, AppColors.Focus), shape = CircleShape)
        ),
        colors = ButtonDefaults.colors(
            containerColor = AppColors.SurfaceEmphasis,
            focusedContainerColor = AppColors.Brand,
            contentColor = AppColors.TextSecondary,
            focusedContentColor = AppColors.OnPrimary
        )
    ) {
        Icon(imageVector = Icons.Filled.Mic, contentDescription = stringResource(R.string.voice_search_button))
    }
}

/**
 * One-shot hand-off for the remote's search/mic key: whoever receives the key outside the Search
 * screen calls [request], and the Search screen starts listening once it is showing.
 */
object VoiceSearchRequests {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() {
        _pending.value = true
    }

    /** Returns true once per [request]. */
    fun consume(): Boolean {
        if (!_pending.value) return false
        _pending.value = false
        return true
    }
}
