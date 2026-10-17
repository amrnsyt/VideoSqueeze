@file:OptIn(UnstableApi::class)

package com.amrnsyt.videosqueeze.video

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaCodecInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.amrnsyt.videosqueeze.util.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VideoViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<VideoUiState>(VideoUiState.Idle)
    val state: StateFlow<VideoUiState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(VideoSettings())
    val settings: StateFlow<VideoSettings> = _settings.asStateFlow()

    private var job: Job? = null

    // ---------- settings ----------
    fun selectPlatform(index: Int) = _settings.update { s ->
        s.copy(platformIndex = index, maxMb = Platforms[index].mb ?: s.maxMb)
    }

    fun setMaxMb(mb: Int) = _settings.update {
        it.copy(maxMb = mb.coerceIn(1, 4000), platformIndex = Platforms.lastIndex)
    }

    fun setUltra(on: Boolean) = _settings.update { it.copy(ultra = on) }
    fun setRes(i: Int) = _settings.update { it.copy(resIndex = i) }
    fun setFps(i: Int) = _settings.update { it.copy(fpsIndex = i) }

    // ---------- picking ----------
    fun onPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) { readMeta(uri) }
            _state.value = if (meta != null) VideoUiState.Ready(meta)
            else VideoUiState.Error(null, "Cannot read this video.")
        }
    }

    private fun readMeta(uri: Uri): VideoMeta? {
        val context = getApplication<Application>()
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) {
                val t = w; w = h; h = t
            }
            val frames = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull()
            val fps = if (frames != null && frames > 0 && dur > 0) frames * 1000f / dur
            else r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f

            var name = "video"
            var size = 0L
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0) ?: name
                    size = c.getLong(1)
                }
            }
            val thumb = try {
                r.getFrameAtTime(min(1_000_000L, dur * 500L), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { f ->
                    val k = 1080f / max(f.width, f.height)
                    if (k < 1f) {
                        Bitmap.createScaledBitmap(f, (f.width * k).roundToInt(), (f.height * k).roundToInt(), true)
                            .also { if (it !== f) f.recycle() }
                    } else f
                }
            } catch (e: Exception) {
                null
            }
            VideoMeta(uri, name, size, dur, w, h, fps, thumb)
        } catch (e: Exception) {
            null
        } finally {
            r.release()
        }
    }

    // ---------- compress ----------
    fun compress() {
        val meta = _state.value.metaOrNull() ?: return
        if (_state.value is VideoUiState.Working) return
        val app = getApplication<Application>()
        val settings = _settings.value
        val target = settings.maxMb * 1048576L

        job = viewModelScope.launch {
            var factor = 0.92
            var best: File? = null
            try {
                for (pass in 1..3) {
                    _state.value = VideoUiState.Working(meta, pass, 0f)
                    val plan = Planner.plan(meta, settings, factor)
                    val out = File(app.cacheDir, "vs_pass_$pass.mp4")
                    out.delete()
                    runTransformer(meta, plan, out) { p ->
                        _state.value = VideoUiState.Working(meta, pass, p)
                    }
                    if (best == null || out.length() < best.length()) best = out
                    if (out.length() <= target) {
                        best = out
                        break
                    }
                    factor *= target.toDouble() / out.length() * 0.95
                }
                val finalFile = best ?: error("No output produced")
                val displayName = meta.name.substringBeforeLast('.') + "_compressed.mp4"
                val bytes = finalFile.length()
                val uri = withContext(Dispatchers.IO) { MediaSaver.saveVideo(app, finalFile, displayName) }
                _state.value = VideoUiState.Done(meta, uri, bytes, bytes <= target)
            } catch (e: CancellationException) {
                _state.value = VideoUiState.Ready(meta)
                throw e
            } catch (e: Exception) {
                _state.value = VideoUiState.Error(meta, e.message ?: "Export failed")
            } finally {
                app.cacheDir.listFiles { f -> f.name.startsWith("vs_pass_") }?.forEach { it.delete() }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private suspend fun runTransformer(
        meta: VideoMeta,
        plan: Plan,
        out: File,
        onProgress: (Float) -> Unit
    ): Unit = suspendCancellableCoroutine<Unit> { cont ->
        val app = getApplication<Application>()

        val encoderSettings = VideoEncoderSettings.Builder()
            .setBitrate(plan.videoBps)
            .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            .build()

        val transformer = Transformer.Builder(app)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(app)
                    .setRequestedVideoEncoderSettings(encoderSettings)
                    .build()
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (cont.isActive) cont.resumeWithException(exportException)
                }
            })
            .build()

        val effects = buildList<Effect> {
            add(Presentation.createForWidthAndHeight(plan.width, plan.height, Presentation.LAYOUT_SCALE_TO_FIT))
            if (plan.dropFrames) add(FrameDropEffect.createDefaultFrameDropEffect(plan.fps.toFloat()))
        }
        val item = EditedMediaItem.Builder(MediaItem.fromUri(meta.uri))
            .setEffects(Effects(emptyList(), effects))
            .build()

        transformer.start(item, out.absolutePath)

        val poller = viewModelScope.launch {
            val holder = ProgressHolder()
            while (cont.isActive) {
                if (transformer.getProgress(holder) != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    onProgress((holder.progress / 100f).coerceIn(0f, 1f))
                }
                delay(300)
            }
        }
        cont.invokeOnCancellation {
            poller.cancel()
            transformer.cancel()
        }
    }
}
