package com.amrnsyt.videosqueeze.video

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Immutable
data class Platform(val name: String, val mb: Int?)

val Platforms = listOf(
    Platform("WhatsApp", 32),
    Platform("Instagram DM", 25),
    Platform("Instagram Post/Reel", 100),
    Platform("Facebook", 100),
    Platform("Discord", 10),
    Platform("Email", 25),
    Platform("Custom", null)
)

val ResolutionOptions = listOf("480p" to 480, "720p" to 720, "1080p" to 1080, "2k" to 1440, "4k" to 2160)
val FpsOptions = listOf(24, 25, 30, 50, 60)

@Immutable
data class VideoMeta(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Float,
    val thumb: Bitmap? = null
)

@Immutable
data class VideoSettings(
    val platformIndex: Int = 0,
    val maxMb: Int = 32,
    val ultra: Boolean = true,
    val resIndex: Int = 2,
    val fpsIndex: Int = 4
)

@Immutable
data class Plan(
    val videoBps: Int,
    val audioBps: Int,
    val width: Int,
    val height: Int,
    val fps: Int,
    val dropFrames: Boolean,
    val reduced: Boolean,
    val tooSmall: Boolean
)

@Immutable
sealed interface VideoUiState {
    data object Idle : VideoUiState
    data class Ready(val meta: VideoMeta) : VideoUiState
    data class Working(val meta: VideoMeta, val pass: Int, val progress: Float) : VideoUiState
    data class Done(val meta: VideoMeta, val outUri: Uri, val outBytes: Long, val fits: Boolean) : VideoUiState
    data class Error(val meta: VideoMeta?, val message: String) : VideoUiState
}

fun VideoUiState.metaOrNull(): VideoMeta? = when (this) {
    is VideoUiState.Ready -> meta
    is VideoUiState.Working -> meta
    is VideoUiState.Done -> meta
    is VideoUiState.Error -> meta
    VideoUiState.Idle -> null
}

object Planner {
    /** Works out bitrate / size / fps so the file lands just under the size limit. */
    fun plan(meta: VideoMeta, s: VideoSettings, factor: Double): Plan {
        val durSec = (meta.durationMs / 1000.0).coerceAtLeast(0.1)
        val total = s.maxMb * 1048576.0 * 8.0 * factor / durSec

        var audio = 128_000
        if (total < 2.5e6) audio = 96_000
        if (total < 1.0e6) audio = 64_000
        val vbps = max(80_000.0, total - audio)

        val targetFps = if (s.ultra) FpsOptions[s.fpsIndex] else 30
        val srcFps = if (meta.fps > 1f) meta.fps else targetFps.toFloat()
        val drop = targetFps < srcFps - 0.5f
        val fpsOut = if (drop) targetFps else srcFps.roundToInt()

        var scale = 1.0
        if (s.ultra) {
            val cap = ResolutionOptions[s.resIndex].second.toDouble()
            scale = min(1.0, cap / min(meta.width, meta.height))
        }
        val fit = sqrt(vbps / (0.03 * meta.width * meta.height * max(1, fpsOut)))
        var reduced = false
        if (fit < scale) {
            scale = max(0.2, fit)
            reduced = true
        }
        fun even(n: Double) = max(2, (n / 2).roundToInt() * 2)

        return Plan(
            videoBps = vbps.toInt(),
            audioBps = audio,
            width = even(meta.width * scale),
            height = even(meta.height * scale),
            fps = fpsOut,
            dropFrames = drop,
            reduced = reduced,
            tooSmall = vbps <= 80_000.0
        )
    }
}
