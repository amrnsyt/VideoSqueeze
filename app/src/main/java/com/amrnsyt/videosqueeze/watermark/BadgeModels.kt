package com.amrnsyt.videosqueeze.watermark

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable

enum class BadgePosition(val label: String) {
    BottomLeft("Bottom left"),
    BottomCenter("Bottom center"),
    BottomRight("Bottom right"),
    TopLeft("Top left"),
    TopRight("Top right")
}

enum class ExportFormat(
    val label: String,
    val ext: String,
    val mime: String,
    val compress: Bitmap.CompressFormat,
    val quality: Int
) {
    Jpeg98("JPEG 98%", "jpg", "image/jpeg", Bitmap.CompressFormat.JPEG, 98),
    Jpeg100("JPEG 100%", "jpg", "image/jpeg", Bitmap.CompressFormat.JPEG, 100),
    Png("PNG lossless", "png", "image/png", Bitmap.CompressFormat.PNG, 100)
}

@Immutable
data class BadgeOptions(
    val name: String = "Pixel 10 Pro XL",
    val tags: String = "#teampixel #deetechgadgets",
    val showLogo: Boolean = true,
    val position: BadgePosition = BadgePosition.BottomLeft,
    val sizePct: Float = 100f,
    val opacityPct: Float = 86f,
    val format: ExportFormat = ExportFormat.Jpeg98
)

@Immutable
data class GalleryPhoto(
    val uri: Uri,
    val name: String
)

@Immutable
data class WmUiState(
    val photos: List<GalleryPhoto> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null
)
