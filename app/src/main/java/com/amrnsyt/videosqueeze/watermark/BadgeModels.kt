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

enum class BadgeStyle(val label: String) {
    Pill("Pill"),
    Centered("Centered")
}

enum class LocationMode(val label: String) {
    Place("Place name"),
    Coordinates("Coordinates")
}

/** Colors offered for text, outline and shadow (ARGB). */
object TextPalette {
    val colors: List<Int> = listOf(
        0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFD43B.toInt(), 0xFFFF9500.toInt(), 0xFFFF453A.toInt(),
        0xFFFF6FB5.toInt(), 0xFFBF5AF2.toInt(), 0xFF4F8CFF.toInt(), 0xFF32D4FF.toInt(), 0xFF30D158.toInt()
    )
    val white: Int = colors[0]
    val black: Int = colors[1]
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
    val name: String = "Pixel 9 Pro XL",
    val tags: String = "#teampixel #deetechgadgets",
    val showLogo: Boolean = true,
    val position: BadgePosition = BadgePosition.BottomLeft,
    val sizePct: Float = 100f,
    val opacityPct: Float = 86f,
    val format: ExportFormat = ExportFormat.Jpeg98,
    val style: BadgeStyle = BadgeStyle.Pill,
    val info: String = "",
    val showDate: Boolean = true,
    val showDetails: Boolean = true,
    val showLocation: Boolean = false,
    val locationMode: LocationMode = LocationMode.Place,
    val locationText: String = "",
    val textColor: Int = TextPalette.white,
    val outlineOn: Boolean = false,
    val outlineColor: Int = TextPalette.black,
    val outlineWidth: Float = 40f,
    val shadowOn: Boolean = true,
    val shadowColor: Int = TextPalette.black,
    val shadowStrength: Float = 50f
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
