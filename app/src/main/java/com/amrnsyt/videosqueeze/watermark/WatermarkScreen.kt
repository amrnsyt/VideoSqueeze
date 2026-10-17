@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.amrnsyt.videosqueeze.watermark

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.amrnsyt.videosqueeze.ui.SwitchRow
import com.amrnsyt.videosqueeze.ui.ValueSlider
import com.amrnsyt.videosqueeze.ui.VideoSqueezeTheme
import com.amrnsyt.videosqueeze.util.ImageCache
import com.amrnsyt.videosqueeze.util.Intents
import com.amrnsyt.videosqueeze.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun WatermarkScreen(vm: WatermarkViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var access by remember { mutableStateOf(Permissions.hasGalleryAccess(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = Permissions.hasGalleryAccess(context)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { vm.setSingle(it) }

    LaunchedEffect(access) { if (access) vm.loadGallery() }
    LaunchedEffect(vm) {
        vm.shareEvents.collect { (uri, mime) -> Intents.share(context, uri, mime) }
    }

    WatermarkContent(
        state = state,
        options = options,
        hasAccess = access,
        onRequestAccess = { permissionLauncher.launch(Permissions.galleryPermissions()) },
        onPickOne = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onChange = vm::update,
        onSave = { vm.save(it, share = false) },
        onShare = { vm.save(it, share = true) },
        onPageChanged = vm::clearMessage
    )
}

@Composable
fun WatermarkContent(
    state: WmUiState,
    options: BadgeOptions,
    hasAccess: Boolean,
    onRequestAccess: () -> Unit,
    onPickOne: () -> Unit,
    onChange: ((BadgeOptions) -> BadgeOptions) -> Unit,
    onSave: (GalleryPhoto) -> Unit,
    onShare: (GalleryPhoto) -> Unit,
    onPageChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photos = state.photos
    val pagerState = rememberPagerState { photos.size }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val current = photos.getOrNull(pagerState.currentPage)

    LaunchedEffect(pagerState.currentPage) { onPageChanged() }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            EditorTopBar {
                if (current != null) {
                    GhostPill("Share", { onShare(current) }, enabled = !state.busy)
                    PrimaryPill(if (state.busy) "Saving…" else "Save", { onSave(current) }, enabled = !state.busy)
                }
            }
        }
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            if (photos.isEmpty()) {
                Box(modifier = Modifier.weight(1f)) {
                    if (hasAccess) {
                        PreviewEmptyState(
                            title = "No photos found",
                            subtitle = "Take a photo or choose one manually.",
                            primaryLabel = "Choose a photo",
                            onPrimary = onPickOne
                        )
                    } else {
                        PreviewEmptyState(
                            title = "Swipe through your gallery",
                            subtitle = "Allow access to your photos to preview the watermark on each one, live.",
                            primaryLabel = "Allow gallery access",
                            onPrimary = onRequestAccess,
                            secondaryLabel = "Choose one photo instead",
                            onSecondary = onPickOne
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        pageSpacing = Spacing.m,
                        key = { photos[it].uri.toString() }
                    ) { page ->
                        PhotoPage(photo = photos[page], options = options)
                    }
                    MediaTag(
                        text = "${pagerState.currentPage + 1} / ${photos.size}",
                        modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.m)
                    )
                }

                Filmstrip(
                    photos = photos,
                    selected = pagerState.currentPage,
                    onSelect = { i -> scope.launch { pagerState.animateScrollToPage(i) } }
                )

                AnimatedVisibility(visible = state.message != null, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = state.message.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs)
                    )
                }

                WatermarkPanel(options = options, tab = tab, onTab = { tab = it }, onChange = onChange)
            }
        }
    }
}

/** One swipeable page: the photo with the badge drawn live on top of it. */
@Composable
private fun PhotoPage(photo: GalleryPhoto, options: BadgeOptions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(initialValue = null, photo.uri) {
        value = withContext(Dispatchers.IO) { ImageCache.load(context, photo.uri, PREVIEW_PX) }
    }
    Box(modifier = modifier.fillMaxSize().padding(horizontal = Spacing.s), contentAlignment = Alignment.Center) {
        Crossfade(targetState = bmp, animationSpec = tween(250), label = "photo") { b ->
            if (b == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                val img = remember(b) { b.asImageBitmap() }
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(b.width.toFloat() / b.height)
                            .clip(MaterialTheme.shapes.medium)
                    ) {
                        Image(img, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawIntoCanvas { c ->
                                BadgeRenderer.draw(c.nativeCanvas, size.width.toInt(), size.height.toInt(), options)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Filmstrip(photos: List<GalleryPhoto>, selected: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.animateScrollToItem(max(0, selected - 2)) }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
        contentPadding = PaddingValues(horizontal = Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        itemsIndexed(photos, key = { _, p -> p.uri.toString() }) { i, p ->
            Thumb(photo = p, selected = i == selected, onClick = { onSelect(i) })
        }
    }
}

@Composable
private fun Thumb(photo: GalleryPhoto, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(initialValue = null, photo.uri) {
        value = withContext(Dispatchers.IO) { ImageCache.load(context, photo.uri, THUMB_PX) }
    }
    val scale by animateFloatAsState(if (selected) 1f else 0.88f, tween(200), label = "thumbScale")
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = Modifier
            .size(Spacing.thumb)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .then(if (selected) Modifier.border(Spacing.stroke, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
    ) {
        val b = bmp
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            Image(img, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun WatermarkPanel(
    options: BadgeOptions,
    tab: Int,
    onTab: (Int) -> Unit,
    onChange: ((BadgeOptions) -> BadgeOptions) -> Unit
) {
    EditorPanel(modifier = Modifier.heightIn(max = Spacing.panelMax)) {
        PanelTabs(listOf("Badge", "Style", "Export"), tab, onTab)
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.l)
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "wmTab"
            ) { t ->
                Column(modifier = Modifier.padding(top = Spacing.m)) {
                    when (t) {
                        0 -> BadgeTab(options, onChange)
                        1 -> StyleTab(options, onChange)
                        else -> ExportTab(options, onChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeTab(options: BadgeOptions, onChange: ((BadgeOptions) -> BadgeOptions) -> Unit) {
    OutlinedTextField(
        value = options.name,
        onValueChange = { v -> onChange { it.copy(name = v) } },
        label = { Text("Device name") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = options.tags,
        onValueChange = { v -> onChange { it.copy(tags = v) } },
        label = { Text("Hashtags (small line)") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.m)
    )
    SwitchRow(
        title = "Show G logo",
        subtitle = "Round logo on the left of the badge.",
        checked = options.showLogo,
        onCheckedChange = { v -> onChange { it.copy(showLogo = v) } },
        modifier = Modifier.padding(top = Spacing.s)
    )
}

@Composable
private fun StyleTab(options: BadgeOptions, onChange: ((BadgeOptions) -> BadgeOptions) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        BadgePosition.entries.forEach { p ->
            SelectChip(p.label, options.position == p, { onChange { it.copy(position = p) } })
        }
    }
    ValueSlider(
        label = "Size",
        valueText = "${options.sizePct.roundToInt()}%",
        value = options.sizePct,
        range = 50f..200f,
        onValueChange = { v -> onChange { it.copy(sizePct = v) } }
    )
    ValueSlider(
        label = "Badge opacity",
        valueText = "${options.opacityPct.roundToInt()}%",
        value = options.opacityPct,
        range = 30f..100f,
        onValueChange = { v -> onChange { it.copy(opacityPct = v) } }
    )
}

@Composable
private fun ExportTab(options: BadgeOptions, onChange: ((BadgeOptions) -> BadgeOptions) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        ExportFormat.entries.forEach { f ->
            SelectChip(f.label, options.format == f, { onChange { it.copy(format = f) } })
        }
    }
    Text(
        "Saved at the photo's original pixel size to Pictures/WM-CMPRSS. GPS and camera metadata are not carried over.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.m)
    )
}

private const val PREVIEW_PX = 1600
private const val THUMB_PX = 200

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun WatermarkContentPreview() {
    VideoSqueezeTheme {
        WatermarkContent(
            state = WmUiState(photos = emptyList()),
            options = BadgeOptions(),
            hasAccess = false,
            onRequestAccess = {}, onPickOne = {}, onChange = {}, onSave = {}, onShare = {}, onPageChanged = {}
        )
    }
}
