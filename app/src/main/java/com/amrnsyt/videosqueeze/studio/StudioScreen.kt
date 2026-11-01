@file:OptIn(ExperimentalMaterial3Api::class)

package com.amrnsyt.videosqueeze.studio

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.amrnsyt.videosqueeze.ui.EditorTopBar
import java.io.IOException

private const val HOST = "appassets.androidplatform.net"
private const val START_URL = "https://$HOST/assets/web/index.html"

/** Remembers the pending file-picker request coming from the web page. */
private class FileChooserState {
    var callback: ValueCallback<Array<Uri>>? = null
}

/** Serves the bundled web files from the APK, with the right content types (.wasm needs one). */
private class StudioAssetHandler(private val context: Context) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? = try {
        WebResourceResponse(mimeOf(path), null, context.assets.open(path))
    } catch (e: IOException) {
        null
    }

    private fun mimeOf(path: String): String = when {
        path.endsWith(".html") -> "text/html"
        path.endsWith(".js") -> "text/javascript"
        path.endsWith(".css") -> "text/css"
        path.endsWith(".wasm") -> "application/wasm"
        path.endsWith(".json") -> "application/json"
        path.endsWith(".svg") -> "image/svg+xml"
        else -> "application/octet-stream"
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * The web studio (FFmpeg.wasm compressor + 30-second splitter) running fully inside the app.
 * It stays composed once opened, so an encode keeps running while you use the other tabs.
 */
@Composable
fun StudioScreen(modifier: Modifier = Modifier) {
    val chooser = remember { FileChooserState() }
    val background = MaterialTheme.colorScheme.background.toArgb()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        chooser.callback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        chooser.callback = null
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { EditorTopBar {} }
    ) { inner ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            factory = { ctx -> createStudioWebView(ctx, background, chooser) { mime -> launcher.launch(mime) } }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createStudioWebView(
    context: Context,
    background: Int,
    chooser: FileChooserState,
    pick: (String) -> Unit
): WebView {
    val loader = WebViewAssetLoader.Builder()
        .setDomain(HOST)
        .addPathHandler("/assets/", StudioAssetHandler(context))
        .build()

    return WebView(context).apply {
        setBackgroundColor(background)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            mediaPlaybackRequiresUserGesture = false
        }
        context.findActivity()?.let { addJavascriptInterface(WebBridge(it), "AndroidBridge") }

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                chooser.callback?.onReceiveValue(null)
                chooser.callback = filePathCallback
                val types = fileChooserParams.acceptTypes.joinToString(",")
                val mime = when {
                    types.contains("image") -> "image/*"
                    types.contains("video") || types.contains(".mp4") || types.contains(".mov") -> "video/*"
                    else -> "*/*"
                }
                pick(mime)
                return true
            }
        }
        loadUrl(START_URL)
    }
}
