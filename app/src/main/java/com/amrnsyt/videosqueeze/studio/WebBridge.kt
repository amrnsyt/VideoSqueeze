package com.amrnsyt.videosqueeze.studio

import android.app.Activity
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.view.WindowManager
import android.webkit.JavascriptInterface
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Functions the bundled web studio can call (window.AndroidBridge).
 * Saving streams the file piece by piece straight into Movies/WM-CMPRSS.
 */
class WebBridge(private val activity: Activity) {

    private class Sink(val uri: Uri, val out: OutputStream, val name: String)

    private val sinks = ConcurrentHashMap<Int, Sink>()
    private val counter = AtomicInteger(1)

    @JavascriptInterface
    fun beginSave(name: String): Int = try {
        val resolver = activity.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/WM-CMPRSS")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        val out = uri?.let { resolver.openOutputStream(it) }
        if (uri == null || out == null) {
            -1
        } else {
            val id = counter.getAndIncrement()
            sinks[id] = Sink(uri, out, name)
            id
        }
    } catch (e: Exception) {
        -1
    }

    @JavascriptInterface
    fun writeChunk(id: Int, base64: String): Boolean {
        val sink = sinks[id] ?: return false
        return try {
            sink.out.write(Base64.decode(base64, Base64.DEFAULT))
            true
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun endSave(id: Int): String {
        val sink = sinks.remove(id) ?: return ""
        return try {
            sink.out.close()
            val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            activity.contentResolver.update(sink.uri, values, null, null)
            "Movies/WM-CMPRSS"
        } catch (e: Exception) {
            ""
        }
    }

    @JavascriptInterface
    fun cancelSave(id: Int) {
        val sink = sinks.remove(id) ?: return
        try {
            sink.out.close()
            activity.contentResolver.delete(sink.uri, null, null)
        } catch (e: Exception) {
            // nothing to clean up
        }
    }

    @JavascriptInterface
    fun keepAwake(on: Boolean) {
        activity.runOnUiThread {
            val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            if (on) activity.window.addFlags(flag) else activity.window.clearFlags(flag)
        }
    }
}
