package com.amrnsyt.videosqueeze

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.amrnsyt.videosqueeze.studio.StudioScreen
import com.amrnsyt.videosqueeze.ui.AppNavBar
import com.amrnsyt.videosqueeze.ui.VideoSqueezeTheme
import com.amrnsyt.videosqueeze.video.VideoScreen
import com.amrnsyt.videosqueeze.watermark.WatermarkScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoSqueezeTheme {
                AppRoot()
            }
        }
    }
}

private const val STUDIO_TAB = 2

@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var studioOpened by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AppNavBar(
                selected = tab,
                onSelect = {
                    tab = it
                    if (it == STUDIO_TAB) studioOpened = true
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            // Lives underneath the other tabs once opened, so an encode is never interrupted.
            if (studioOpened) StudioScreen(modifier = Modifier.fillMaxSize())

            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(320)) { it * dir / 5 } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(320)) { -it * dir / 5 } + fadeOut(tween(160)))
                },
                label = "mode"
            ) { t ->
                when (t) {
                    0 -> VideoScreen()
                    1 -> WatermarkScreen()
                    else -> Spacer(Modifier.fillMaxSize())
                }
            }
        }
    }
}
