@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.amrnsyt.videosqueeze.video

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amrnsyt.videosqueeze.ui.EditorPanel
import com.amrnsyt.videosqueeze.ui.EditorTopBar
import com.amrnsyt.videosqueeze.ui.GhostPill
import com.amrnsyt.videosqueeze.ui.MediaTag
import com.amrnsyt.videosqueeze.ui.PanelTabs
import com.amrnsyt.videosqueeze.ui.PreviewEmptyState
import com.amrnsyt.videosqueeze.ui.PrimaryPill
import com.amrnsyt.videosqueeze.ui.SelectChip
import com.amrnsyt.videosqueeze.ui.Spacing
import com.amrnsyt.videosqueeze.ui.StepSlider
import com.amrnsyt.videosqueeze.ui.SwitchRow
import com.amrnsyt.videosqueeze.ui.VideoSqueezeTheme
import com.amrnsyt.videosqueeze.util.Intents
import kotlin.math.roundToInt

@Composable
fun VideoScreen(vm: VideoViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { vm.onPicked(it) }

    val working = state is VideoUiState.Working
    DisposableEffect(working) {
        view.keepScreenOn = working
        onDispose { view.keepScreenOn = false }
    }

    VideoContent(
        state = state,
        settings = settings,
        onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
        onPlatform = vm::selectPlatform,
        onMaxMb = vm::setMaxMb,
        onUltra = vm::setUltra,
        onRes = vm::setRes,
        onFps = vm::setFps,
        onCompress = vm::compress,
        onCancel = vm::cancel,
        onShare = { Intents.share(context, it, "video/mp4") },
        onOpen = { Intents.view(context, it, "video/mp4") }
    )
}

@Composable
fun VideoContent(
    state: VideoUiState,
    settings: VideoSettings,
    onPick: () -> Unit,
    onPlatform: (Int) -> Unit,
    onMaxMb: (Int) -> Unit,
    onUltra: (Boolean) -> Unit,
    onRes: (Int) -> Unit,
    onFps: (Int) -> Unit,
    onCompress: () -> Unit,
    onCancel: () -> Unit,
    onShare: (Uri) -> Unit,
    onOpen: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val meta = state.metaOrNull()
    val working = state as? VideoUiState.Working
    val done = state as? VideoUiState.Done
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(done != null) { if (done != null) tab = 2 }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            EditorTopBar {
                if (working != null) GhostPill("Cancel", onCancel)
                else if (meta != null) PrimaryPill("Compress", onCompress)
            }
        }
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                VideoPreview(
                    state = state,
                    progress = working?.progress ?: 0f,
                    pass = working?.pass ?: 1,
                    onPick = onPick
                )
            }
            if (meta != null) {
                AnimatedVisibility(
                    visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
                    enter = slideInVertically(tween(380)) { it } + fadeIn(tween(380))
                ) {
                    VideoPanel(
                        meta = meta,
                        settings = settings,
                        done = done,
                        errorText = (state as? VideoUiState.Error)?.message,
                        tab = tab,
                        onTab = { tab = it },
                        busy = working != null,
                        onPlatform = onPlatform, onMaxMb = onMaxMb, onUltra = onUltra, onRes = onRes, onFps = onFps,
                        onShare = onShare, onOpen = onOpen
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPreview(state: VideoUiState, progress: Float, pass: Int, onPick: () -> Unit) {
    val meta = state.metaOrNull()
    if (meta == null) {
        PreviewEmptyState(
            title = "Compress a video",
            subtitle = "Pick a video and fit it under the upload limit of WhatsApp, Instagram and more.",
            primaryLabel = "Choose video",
            onPrimary = onPick
        )
        return
    }
    val working = state is VideoUiState.Working
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.l)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = !working, onClick = onPick),
        contentAlignment = Alignment.Center
    ) {
        val thumb = meta.thumb
        if (thumb != null) {
            val img = remember(thumb) { thumb.asImageBitmap() }
            Image(img, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        MediaTag(
            text = "${formatDuration(meta.durationMs)} • ${meta.width}×${meta.height} • ${formatMb(meta.sizeBytes)}",
            modifier = Modifier.align(Alignment.TopStart).padding(Spacing.m)
        )
        if (!working) {
            MediaTag(
                text = "Tap to change",
                modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.m)
            )
        }
        AnimatedVisibility(visible = working, enter = fadeIn(tween(250)), exit = fadeOut(tween(250))) {
            val animated by animateFloatAsState(progress, tween(300), label = "progress")
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    CircularProgressIndicator(progress = { animated }, color = MaterialTheme.colorScheme.primary)
                    Text("${(animated * 100).roundToInt()}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Pass $pass of 3", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun VideoPanel(
    meta: VideoMeta,
    settings: VideoSettings,
    done: VideoUiState.Done?,
    errorText: String?,
    tab: Int,
    onTab: (Int) -> Unit,
    busy: Boolean,
    onPlatform: (Int) -> Unit,
    onMaxMb: (Int) -> Unit,
    onUltra: (Boolean) -> Unit,
    onRes: (Int) -> Unit,
    onFps: (Int) -> Unit,
    onShare: (Uri) -> Unit,
    onOpen: (Uri) -> Unit
) {
    val labels = if (done != null) listOf("Target", "Quality", "Result") else listOf("Target", "Quality")
    val cur = tab.coerceAtMost(labels.lastIndex)
    val plan = remember(meta, settings) { Planner.plan(meta, settings, 0.92) }

    EditorPanel(modifier = Modifier.heightIn(max = Spacing.panelMax)) {
        PanelTabs(labels, cur, onTab)
        Text(
            text = "${plan.width}×${plan.height} • ${plan.fps} fps • ~${plan.videoBps / 1000} kbps",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.m)
        )
        if (plan.reduced) {
            Text(
                "Resolution lowered automatically to keep the picture clean at this size.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (errorText != null) {
            Text(errorText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.l)
        ) {
            AnimatedContent(
                targetState = cur,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "videoTab"
            ) { t ->
                Column {
                    when (t) {
                        0 -> TargetTab(meta, settings, !busy, onPlatform, onMaxMb)
                        1 -> QualityTab(settings, !busy, onUltra, onRes, onFps)
                        else -> done?.let { d -> ResultTab(d, { onShare(d.outUri) }, { onOpen(d.outUri) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetTab(
    meta: VideoMeta,
    settings: VideoSettings,
    enabled: Boolean,
    onPlatform: (Int) -> Unit,
    onMaxMb: (Int) -> Unit
) {
    FlowRow(
        modifier = Modifier.padding(top = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        Platforms.forEachIndexed { i, p ->
            SelectChip(
                text = if (p.mb != null) "${p.name} • ${p.mb}MB" else p.name,
                selected = settings.platformIndex == i,
                onClick = { onPlatform(i) },
                enabled = enabled
            )
        }
    }
    MaxMbField(settings.maxMb, enabled, onMaxMb)
    if (meta.sizeBytes in 1..(settings.maxMb * 1048576L)) {
        Text(
            "Already under the limit (${formatMb(meta.sizeBytes)}). Compressing is optional.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = Spacing.s)
        )
    }
}

@Composable
private fun MaxMbField(maxMb: Int, enabled: Boolean, onMaxMb: (Int) -> Unit) {
    var text by remember(maxMb) { mutableStateOf(maxMb.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            val digits = v.filter { it.isDigit() }.take(4)
            text = digits
            digits.toIntOrNull()?.takeIf { it >= 1 }?.let(onMaxMb)
        },
        enabled = enabled,
        label = { Text("Max size (MB)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.m)
    )
}

@Composable
private fun QualityTab(
    settings: VideoSettings,
    enabled: Boolean,
    onUltra: (Boolean) -> Unit,
    onRes: (Int) -> Unit,
    onFps: (Int) -> Unit
) {
    SwitchRow(
        title = "Ultra HD",
        subtitle = "Resolution cap and frame rate limit. Off keeps original size at 30 fps.",
        checked = settings.ultra,
        onCheckedChange = onUltra,
        modifier = Modifier.padding(top = Spacing.s)
    )
    StepSlider(
        title = "Resolution",
        hint = "Maximum. Never upscaled.",
        labels = ResolutionOptions.map { it.first },
        index = settings.resIndex,
        onIndexChange = onRes,
        enabled = settings.ultra && enabled
    )
    StepSlider(
        title = "Frame rate",
        hint = "Maximum. Not increased.",
        labels = FpsOptions.map { it.toString() },
        index = settings.fpsIndex,
        onIndexChange = onFps,
        enabled = settings.ultra && enabled
    )
}

@Composable
private fun ResultTab(done: VideoUiState.Done, onShare: () -> Unit, onOpen: () -> Unit) {
    val saved = if (done.meta.sizeBytes > 0)
        (100 - done.outBytes * 100.0 / done.meta.sizeBytes).coerceAtLeast(0.0).roundToInt() else 0
    Column(modifier = Modifier.padding(top = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            "${formatMb(done.meta.sizeBytes)} → ${formatMb(done.outBytes)} ($saved% smaller)",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            if (done.fits) "Fits the size limit. Saved to Movies/WM-CMPRSS."
            else "Still above the limit — lower the max size slightly and retry.",
            style = MaterialTheme.typography.bodySmall,
            color = if (done.fits) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
        )
        Row(
            modifier = Modifier.padding(top = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrimaryPill("Share", onShare)
            OutlinedButton(onClick = onOpen) { Text("Open") }
        }
    }
}

private fun formatMb(bytes: Long): String = "%.1f MB".format(bytes / 1048576.0)

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun VideoContentPreview() {
    VideoSqueezeTheme {
        val meta = VideoMeta(Uri.EMPTY, "clip.mp4", 180L * 1048576, 60_000, 1080, 1920, 30f)
        VideoContent(
            state = VideoUiState.Ready(meta),
            settings = VideoSettings(),
            onPick = {}, onPlatform = {}, onMaxMb = {}, onUltra = {}, onRes = {}, onFps = {},
            onCompress = {}, onCancel = {}, onShare = {}, onOpen = {}
        )
    }
}
