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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amrnsyt.videosqueeze.ui.BigButton
import com.amrnsyt.videosqueeze.ui.ChipScroller
import com.amrnsyt.videosqueeze.ui.EditorTopBar
import com.amrnsyt.videosqueeze.ui.MediaTag
import com.amrnsyt.videosqueeze.ui.PanelTabs
import com.amrnsyt.videosqueeze.ui.PreviewEmptyState
import com.amrnsyt.videosqueeze.ui.SelectChip
import com.amrnsyt.videosqueeze.ui.Sheet
import com.amrnsyt.videosqueeze.ui.Spacing
import com.amrnsyt.videosqueeze.ui.StepSlider
import com.amrnsyt.videosqueeze.ui.SwitchRow
import com.amrnsyt.videosqueeze.ui.ValueSlider
import com.amrnsyt.videosqueeze.ui.VideoSqueezeTheme
import com.amrnsyt.videosqueeze.ui.tabTransition
import com.amrnsyt.videosqueeze.util.Intents
import kotlin.math.roundToInt

/** Everything the Compress screen can do, bundled so the screen code stays readable. */
class VideoActions(
    val onPick: () -> Unit,
    val onPlatform: (Int) -> Unit,
    val onMaxMb: (Int) -> Unit,
    val onUltra: (Boolean) -> Unit,
    val onRes: (Int) -> Unit,
    val onFps: (Int) -> Unit,
    val onSmooth: (Boolean) -> Unit,
    val onEnhance: (Boolean) -> Unit,
    val onStrength: (Float) -> Unit,
    val onVivid: (Boolean) -> Unit,
    val onVSat: (Float) -> Unit,
    val onVVib: (Float) -> Unit,
    val onVCon: (Float) -> Unit,
    val onVBri: (Float) -> Unit,
    val onVReset: () -> Unit,
    val onCompress: () -> Unit,
    val onCancel: () -> Unit,
    val onShare: (Uri) -> Unit,
    val onOpen: (Uri) -> Unit
)

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

    val actions = remember(vm, context, picker) {
        VideoActions(
            onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
            onPlatform = vm::selectPlatform,
            onMaxMb = vm::setMaxMb,
            onUltra = vm::setUltra,
            onRes = vm::setRes,
            onFps = vm::setFps,
            onSmooth = vm::setSmooth,
            onEnhance = vm::setEnhance,
            onStrength = vm::setEnhanceStrength,
            onVivid = vm::setVivid,
            onVSat = vm::setVSat,
            onVVib = vm::setVVib,
            onVCon = vm::setVCon,
            onVBri = vm::setVBri,
            onVReset = vm::resetVivid,
            onCompress = vm::compress,
            onCancel = vm::cancel,
            onShare = { Intents.share(context, it, "video/mp4") },
            onOpen = { Intents.view(context, it, "video/mp4") }
        )
    }

    VideoContent(state = state, settings = settings, actions = actions)
}

@Composable
fun VideoContent(
    state: VideoUiState,
    settings: VideoSettings,
    actions: VideoActions,
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
        topBar = { EditorTopBar {} }
    ) { inner ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(inner)) {
            val sheetMax = maxHeight * 0.64f
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    VideoPreview(
                        state = state,
                        progress = working?.progress ?: 0f,
                        pass = working?.pass ?: 1,
                        onPick = actions.onPick
                    )
                }
                if (meta != null) {
                    AnimatedVisibility(
                        visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
                        enter = slideInVertically(tween(380)) { it } + fadeIn(tween(380))
                    ) {
                        VideoSheet(
                            meta = meta,
                            settings = settings,
                            state = state,
                            tab = tab,
                            onTab = { tab = it },
                            actions = actions,
                            sheetMax = sheetMax
                        )
                    }
                }
            }
        }
    }
}

// ---------- preview ----------

@Composable
private fun VideoPreview(state: VideoUiState, progress: Float, pass: Int, onPick: () -> Unit) {
    val meta = state.metaOrNull()
    if (meta == null) {
        PreviewEmptyState(
            title = "Compress a video",
            subtitle = "Fit it under the upload limit of WhatsApp, Instagram and more, keeping it sharp and smooth.",
            primaryLabel = "Choose video",
            onPrimary = onPick
        )
        return
    }
    val working = state is VideoUiState.Working
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.l, vertical = Spacing.s)
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
            text = "${formatDuration(meta.durationMs)} · ${meta.width}×${meta.height} · ${formatMb(meta.sizeBytes)}",
            modifier = Modifier.align(Alignment.TopStart).padding(Spacing.m)
        )
        if (!working) {
            MediaTag(
                text = "Tap to change video",
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.m)
                ) {
                    CircularProgressIndicator(progress = { animated }, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "${(animated * 100).roundToInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Pass $pass of 3",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ---------- sheet ----------

@Composable
private fun VideoSheet(
    meta: VideoMeta,
    settings: VideoSettings,
    state: VideoUiState,
    tab: Int,
    onTab: (Int) -> Unit,
    actions: VideoActions,
    sheetMax: Dp
) {
    val working = state as? VideoUiState.Working
    val done = state as? VideoUiState.Done
    val busy = working != null
    val labels = if (done != null) listOf("Quality", "Look", "Result") else listOf("Quality", "Look")
    val cur = tab.coerceAtMost(labels.lastIndex)
    val plan = remember(meta, settings) { Planner.plan(meta, settings, 0.92) }

    Sheet(maxHeight = sheetMax) {
        TargetRow(settings, !busy, actions.onPlatform, actions.onMaxMb)
        SummaryCard(meta, settings, plan, (state as? VideoUiState.Error)?.message)
        PanelTabs(labels, cur, onTab, Modifier.padding(top = Spacing.m))
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(top = Spacing.s)
        ) {
            AnimatedContent(targetState = cur, transitionSpec = tabTransition(), label = "videoTab") { t ->
                Column {
                    when (t) {
                        0 -> QualityTab(settings, !busy, actions)
                        1 -> LookTab(settings, !busy, actions)
                        else -> done?.let { ResultTab(it, { actions.onShare(it.outUri) }, { actions.onOpen(it.outUri) }) }
                    }
                }
            }
        }
        ActionArea(working, settings.maxMb, actions)
    }
}

@Composable
private fun TargetRow(settings: VideoSettings, enabled: Boolean, onPlatform: (Int) -> Unit, onMaxMb: (Int) -> Unit) {
    Column {
        ChipScroller {
            Platforms.forEachIndexed { i, p ->
                SelectChip(
                    text = if (p.mb != null) "${p.name} · ${p.mb} MB" else p.name,
                    selected = settings.platformIndex == i,
                    onClick = { onPlatform(i) },
                    enabled = enabled
                )
            }
        }
        AnimatedVisibility(visible = settings.platformIndex == Platforms.lastIndex) {
            MaxMbField(settings.maxMb, enabled, onMaxMb)
        }
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
            .padding(top = Spacing.s)
    )
}

@Composable
private fun SummaryCard(meta: VideoMeta, settings: VideoSettings, plan: Plan, error: String?) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.m)
    ) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${plan.width}×${plan.height} · ${plan.fps} fps · ${"%.1f".format(plan.videoBps / 1e6)} Mbps",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "≤ ${settings.maxMb} MB",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            val tags = buildList {
                if (plan.enhance > 0f) add("Enhance")
                if (plan.blend) add("Smooth ×2")
                if (plan.vivid != null) add("Vivid")
            }
            if (tags.isNotEmpty()) {
                Text(
                    tags.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (meta.sizeBytes in 1..(settings.maxMb * 1048576L)) {
                Text(
                    "Already under the limit (${formatMb(meta.sizeBytes)}). Compressing is optional.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (plan.reduced) {
                Text(
                    "Resolution lowered automatically to keep the picture clean at this size.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (error != null) {
                Text(error, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ---------- tabs ----------

@Composable
private fun QualityTab(settings: VideoSettings, enabled: Boolean, a: VideoActions) {
    SwitchRow(
        title = "Ultra HD",
        subtitle = "Resolution cap and frame rate target. Off keeps original size at 30 fps.",
        checked = settings.ultra,
        onCheckedChange = a.onUltra,
        modifier = Modifier.padding(top = Spacing.xs)
    )
    StepSlider(
        title = "Resolution",
        hint = "Maximum, never upscaled",
        labels = ResolutionOptions.map { it.first },
        index = settings.resIndex,
        onIndexChange = a.onRes,
        enabled = settings.ultra && enabled
    )
    StepSlider(
        title = "Frame rate",
        hint = "Target",
        labels = FpsOptions.map { it.toString() },
        index = settings.fpsIndex,
        onIndexChange = a.onFps,
        enabled = settings.ultra && enabled
    )
    SwitchRow(
        title = "Smooth",
        subtitle = "Blended in-between frames when the target is higher than the video's frame rate.",
        checked = settings.smooth,
        onCheckedChange = a.onSmooth,
        modifier = Modifier.padding(top = Spacing.m)
    )
}

@Composable
private fun LookTab(settings: VideoSettings, enabled: Boolean, a: VideoActions) {
    SwitchRow(
        title = "Enhance",
        subtitle = "Sharper picture with an HDR-style look: richer shadows, softer highlights.",
        checked = settings.enhance,
        onCheckedChange = a.onEnhance,
        modifier = Modifier.padding(top = Spacing.xs)
    )
    AnimatedVisibility(visible = settings.enhance) {
        ValueSlider(
            label = "Strength",
            valueText = "${settings.enhanceStrength.roundToInt()}%",
            value = settings.enhanceStrength,
            range = 0f..100f,
            onValueChange = a.onStrength,
            enabled = enabled
        )
    }
    SwitchRow(
        title = "Vivid color",
        subtitle = "Richer, punchier colors. Adjust by hand.",
        checked = settings.vivid,
        onCheckedChange = a.onVivid,
        modifier = Modifier.padding(top = Spacing.m)
    )
    AnimatedVisibility(visible = settings.vivid) {
        Column {
            ValueSlider("Saturation", "${settings.vSat.roundToInt()}%", settings.vSat, 50f..200f, a.onVSat, enabled = enabled)
            ValueSlider("Vibrance", signed(settings.vVib), settings.vVib, -100f..100f, a.onVVib, enabled = enabled)
            ValueSlider("Contrast", "${settings.vCon.roundToInt()}%", settings.vCon, 80f..130f, a.onVCon, enabled = enabled)
            ValueSlider("Brightness", signed(settings.vBri), settings.vBri, -20f..20f, a.onVBri, enabled = enabled)
            TextButton(onClick = a.onVReset, enabled = enabled) { Text("Reset colors") }
        }
    }
}

private fun signed(v: Float): String {
    val n = v.roundToInt()
    return if (n > 0) "+$n" else n.toString()
}

@Composable
private fun ResultTab(done: VideoUiState.Done, onShare: () -> Unit, onOpen: () -> Unit) {
    val saved = if (done.meta.sizeBytes > 0)
        (100 - done.outBytes * 100.0 / done.meta.sizeBytes).coerceAtLeast(0.0).roundToInt() else 0
    Column(modifier = Modifier.padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            "${formatMb(done.meta.sizeBytes)} → ${formatMb(done.outBytes)}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "$saved% smaller",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(
            if (done.fits) "Fits your size limit. Saved to Movies/WM-CMPRSS."
            else "Still above the limit. Lower the max size a little and try again.",
            style = MaterialTheme.typography.bodySmall,
            color = if (done.fits) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
        )
        done.note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(
            modifier = Modifier.padding(top = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            BigButton("Share", onShare, modifier = Modifier.weight(1f))
            BigButton("Open", onOpen, modifier = Modifier.weight(1f), primary = false)
        }
    }
}

// ---------- action area ----------

@Composable
private fun ActionArea(working: VideoUiState.Working?, maxMb: Int, a: VideoActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.s, bottom = Spacing.m)
    ) {
        AnimatedContent(
            targetState = working != null,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "cta"
        ) { isWorking ->
            if (isWorking) {
                WorkingBar(working?.progress ?: 0f, working?.pass ?: 1, a.onCancel)
            } else {
                BigButton("Compress to $maxMb MB", a.onCompress, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun WorkingBar(progress: Float, pass: Int, onCancel: () -> Unit) {
    val animated by animateFloatAsState(progress, tween(300), label = "bar")
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Compressing · pass $pass of 3", style = MaterialTheme.typography.labelLarge)
            Text("${(animated * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline
        )
        BigButton("Cancel", onCancel, modifier = Modifier.fillMaxWidth(), primary = false)
    }
}

// ---------- helpers ----------

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
            actions = VideoActions(
                {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}
            )
        )
    }
}
