package com.amrnsyt.videosqueeze.watermark

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.Immutable
import java.util.Locale
import kotlin.math.roundToInt

/** Camera details shown by the Centered badge, e.g. "23mm   f/1.7   1/4476s   ISO50". */
@Immutable
data class PhotoMeta(
    val info: String = "",
    val date: String = ""
)

object ExifReader {

    fun read(context: Context, uri: Uri): PhotoMeta = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val parts = ArrayList<String>()

            val f35 = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
            val focal = if (f35 > 0) f35.toDouble() else exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
            if (focal > 0) parts.add("${focal.roundToInt()}mm")

            val fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
            if (fNumber > 0) parts.add("f/" + trim(fNumber))

            val exposure = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
            if (exposure > 0) {
                parts.add(if (exposure < 1.0) "1/${(1.0 / exposure).roundToInt()}s" else trim(exposure) + "s")
            }

            val iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
            if (iso > 0) parts.add("ISO$iso")

            val rawDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            PhotoMeta(parts.joinToString("   "), formatDate(rawDate))
        } ?: PhotoMeta()
    } catch (e: Exception) {
        PhotoMeta()
    }

    private fun trim(d: Double): String = String.format(Locale.US, "%.1f", d).removeSuffix(".0")

    /** "2026:10:21 12:26:09" -> "2026.10.21 12:26" */
    private fun formatDate(raw: String?): String {
        if (raw == null || raw.length < 16) return ""
        return raw.substring(0, 16).replaceFirst(':', '.').replaceFirst(':', '.')
    }
}
