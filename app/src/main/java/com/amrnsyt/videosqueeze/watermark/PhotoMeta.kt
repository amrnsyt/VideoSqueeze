package com.amrnsyt.videosqueeze.watermark

import android.content.Context
import android.location.Geocoder
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/** Camera details read from a photo: lens line, date, GPS position and (optionally) a place name. */
@Immutable
data class PhotoMeta(
    val info: String = "",
    val date: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val place: String = ""
) {
    /** "3.1390° N  101.6869° E", or empty when the photo has no GPS. */
    fun coordinates(): String {
        val la = lat ?: return ""
        val lo = lon ?: return ""
        val ns = if (la >= 0) "N" else "S"
        val ew = if (lo >= 0) "E" else "W"
        return String.format(Locale.US, "%.4f° %s  %.4f° %s", abs(la), ns, abs(lo), ew)
    }
}

object ExifReader {

    private val placeCache = ConcurrentHashMap<String, String>()

    fun read(context: Context, uri: Uri): PhotoMeta =
        readFrom(context, original(uri)) ?: readFrom(context, uri) ?: PhotoMeta()

    // Android removes GPS from media files unless the original is requested (needs ACCESS_MEDIA_LOCATION).
    private fun original(uri: Uri): Uri = try {
        if (uri.authority == "media") MediaStore.setRequireOriginal(uri) else uri
    } catch (e: Exception) {
        uri
    }

    private fun readFrom(context: Context, uri: Uri): PhotoMeta? = try {
        context.contentResolver.openInputStream(uri)?.use { stream -> parse(ExifInterface(stream)) }
    } catch (e: Exception) {
        null
    }

    private fun parse(exif: ExifInterface): PhotoMeta {
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

        // "ISOSpeedRatings" is the EXIF name of the ISO tag in the framework reader
        val iso = exif.getAttributeInt("ISOSpeedRatings", 0)
        if (iso > 0) parts.add("ISO$iso")

        val rawDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

        val gps = FloatArray(2)
        val hasGps = exif.getLatLong(gps)
        return PhotoMeta(
            info = parts.joinToString("   "),
            date = formatDate(rawDate),
            lat = if (hasGps) gps[0].toDouble() else null,
            lon = if (hasGps) gps[1].toDouble() else null
        )
    }

    /** Looks up a readable place ("Petaling Jaya, Selangor"). Needs internet; empty when unavailable. */
    @Suppress("DEPRECATION")
    fun withPlace(context: Context, meta: PhotoMeta): PhotoMeta {
        val lat = meta.lat ?: return meta
        val lon = meta.lon ?: return meta
        val key = String.format(Locale.US, "%.3f,%.3f", lat, lon)
        placeCache[key]?.let { return meta.copy(place = it) }
        val name = try {
            if (!Geocoder.isPresent()) {
                ""
            } else {
                val a = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
                listOfNotNull(a?.locality ?: a?.subAdminArea, a?.adminArea)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(", ")
            }
        } catch (e: Exception) {
            ""
        }
        if (name.isNotEmpty()) placeCache[key] = name
        return meta.copy(place = name)
    }

    private fun trim(d: Double): String = String.format(Locale.US, "%.1f", d).removeSuffix(".0")

    /** "2026:10:21 12:26:09" -> "2026.10.21 12:26" */
    private fun formatDate(raw: String?): String {
        if (raw == null || raw.length < 16) return ""
        return raw.substring(0, 16).replaceFirst(':', '.').replaceFirst(':', '.')
    }
}
