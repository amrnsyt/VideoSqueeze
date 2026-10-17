package com.amrnsyt.videosqueeze.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import android.util.Size
import kotlin.math.max

/** Small in-memory cache so swiping between photos stays smooth. */
object ImageCache {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(context: Context, uri: Uri, maxPx: Int): Bitmap? {
        val key = "$uri@$maxPx"
        cache.get(key)?.let { return it }
        val bmp = try {
            context.contentResolver.loadThumbnail(uri, Size(maxPx, maxPx), null)
        } catch (e: Exception) {
            decode(context, uri, maxPx)
        } ?: return null
        cache.put(key, bmp)
        return bmp
    }

    private fun decode(context: Context, uri: Uri, maxPx: Int): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            dec.setTargetSampleSize(max(1, max(info.size.width, info.size.height) / maxPx))
        }
    } catch (e: Exception) {
        null
    }
}
