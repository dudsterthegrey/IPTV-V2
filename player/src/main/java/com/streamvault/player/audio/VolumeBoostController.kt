package com.streamvault.player.audio

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player

/**
 * Raises loudness above the player's 100% volume with Android's [LoudnessEnhancer], for quiet
 * streams on TV speakers. The effect follows the player's audio session; 0 dB removes it.
 * Passthrough (bitstreamed Dolby/DTS) audio is decoded by the receiver, so it is not boosted.
 */
internal class VolumeBoostController {
    private var audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var gainDb: Int = 0
    private var enhancer: LoudnessEnhancer? = null

    val listener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            attach(audioSessionId)
        }
    }

    fun attach(sessionId: Int) {
        if (sessionId == audioSessionId && enhancer != null) return
        releaseEnhancer()
        audioSessionId = sessionId
        apply()
    }

    fun setGainDb(db: Int) {
        val clamped = db.coerceIn(0, MAX_BOOST_DB)
        if (clamped == gainDb) return
        gainDb = clamped
        apply()
    }

    fun release() {
        releaseEnhancer()
        audioSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private fun apply() {
        if (gainDb == 0 || audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
            releaseEnhancer()
            return
        }
        runCatching {
            val effect = enhancer ?: LoudnessEnhancer(audioSessionId).also { enhancer = it }
            effect.setTargetGain(gainDb * 100) // millibels
            effect.enabled = true
        }.onFailure { error ->
            Log.w(TAG, "volume-boost unavailable gainDb=$gainDb: ${error.javaClass.simpleName}")
            releaseEnhancer()
        }
    }

    private fun releaseEnhancer() {
        enhancer?.let { runCatching { it.release() } }
        enhancer = null
    }

    companion object {
        const val MAX_BOOST_DB = 15
        private const val TAG = "VolumeBoost"
    }
}
