package com.amrnsyt.videosqueeze.watermark

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amrnsyt.videosqueeze.util.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WatermarkViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("watermark", Context.MODE_PRIVATE)

    private val _options = MutableStateFlow(loadOptions())
    val options: StateFlow<BadgeOptions> = _options.asStateFlow()

    private val _state = MutableStateFlow(WmUiState())
    val state: StateFlow<WmUiState> = _state.asStateFlow()

    private val _shareEvents = MutableSharedFlow<Pair<Uri, String>>(extraBufferCapacity = 1)
    val shareEvents: SharedFlow<Pair<Uri, String>> = _shareEvents.asSharedFlow()

    // ---------- options ----------
    fun update(transform: (BadgeOptions) -> BadgeOptions) {
        _options.update(transform)
        saveOptions(_options.value)
    }

    private fun loadOptions(): BadgeOptions {
        val d = BadgeOptions()
        return BadgeOptions(
            name = prefs.getString("name", d.name) ?: d.name,
            tags = prefs.getString("tags", d.tags) ?: d.tags,
            showLogo = prefs.getBoolean("logo", d.showLogo),
            position = BadgePosition.entries.getOrElse(prefs.getInt("pos", 0)) { d.position },
            sizePct = prefs.getFloat("size", d.sizePct),
            opacityPct = prefs.getFloat("opacity", d.opacityPct),
            format = ExportFormat.entries.getOrElse(prefs.getInt("fmt", 0)) { d.format }
        )
    }

    private fun saveOptions(o: BadgeOptions) {
        prefs.edit()
            .putString("name", o.name)
            .putString("tags", o.tags)
            .putBoolean("logo", o.showLogo)
            .putInt("pos", o.position.ordinal)
            .putFloat("size", o.sizePct)
            .putFloat("opacity", o.opacityPct)
            .putInt("fmt", o.format.ordinal)
            .apply()
    }

    // ---------- gallery ----------
    fun loadGallery() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { queryGallery() }
            _state.update { it.copy(photos = list) }
        }
    }

    private fun queryGallery(): List<GalleryPhoto> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val out = ArrayList<GalleryPhoto>()
        val selection = "(${MediaStore.Images.Media.RELATIVE_PATH} IS NULL OR " +
            "${MediaStore.Images.Media.RELATIVE_PATH} NOT LIKE ?)"
        getApplication<Application>().contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
            selection,
            arrayOf("Pictures/WM-CMPRSS%"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            while (c.moveToNext() && out.size < MAX_PHOTOS) {
                val id = c.getLong(0)
                out += GalleryPhoto(ContentUris.withAppendedId(collection, id), c.getString(1) ?: "photo")
            }
        }
        return out
    }

    /** Fallback when gallery access is not granted: work with one picked photo. */
    fun setSingle(uri: Uri?) {
        if (uri == null) return
        var name = "photo"
        getApplication<Application>().contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) name = c.getString(0) ?: name }
        _state.update { it.copy(photos = listOf(GalleryPhoto(uri, name)), message = null) }
    }

    fun clearMessage() {
        _state.update { if (it.message != null && !it.busy) it.copy(message = null) else it }
    }

    // ---------- export ----------
    fun save(photo: GalleryPhoto, share: Boolean) {
        if (_state.value.busy) return
        val o = _options.value
        val app = getApplication<Application>()
        _state.update { it.copy(busy = true, message = "Rendering at full resolution…") }

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, photo.uri)) { dec, _, _ ->
                        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        dec.isMutableRequired = true
                    }
                    val w = bmp.width
                    val h = bmp.height
                    BadgeRenderer.draw(Canvas(bmp), w, h, o)
                    val fmt = o.format
                    val displayName = photo.name.substringBeforeLast('.') + "_wm." + fmt.ext
                    val uri = MediaSaver.saveImage(app, bmp, displayName, fmt.mime, fmt.compress, fmt.quality)
                    bmp.recycle()
                    Triple(uri, "${w}×${h}", MediaSaver.sizeOf(app, uri))
                }
                val mb = "%.1f".format(result.third / 1048576.0)
                _state.update { it.copy(busy = false, message = "Saved ${result.second} • $mb MB in Pictures/WM-CMPRSS") }
                if (share) _shareEvents.emit(result.first to o.format.mime)
            } catch (e: Throwable) {
                _state.update { it.copy(busy = false, message = "Failed: ${e.message ?: "not enough memory for this photo"}") }
            }
        }
    }

    private companion object {
        const val MAX_PHOTOS = 400
    }
}
