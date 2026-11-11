package com.amrnsyt.videosqueeze.watermark

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the badge. Everything scales from the shorter image side,
 * so a small preview looks identical to the full-resolution export.
 */
object BadgeRenderer {

    fun draw(canvas: Canvas, w: Int, h: Int, o: BadgeOptions, meta: PhotoMeta? = null) {
        if (o.style == BadgeStyle.Centered) drawCentered(canvas, w, h, o, meta) else drawPill(canvas, w, h, o, meta)
    }

    // ---------- shared helpers ----------

    private fun withAlpha(color: Int, a: Float): Int =
        Color.argb((Color.alpha(color) * a).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun basePaint(size: Float, medium: Boolean, spacing: Float, align: Paint.Align) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            textSize = size
            letterSpacing = spacing
            textAlign = align
        }

    /** Text with the user's color, optional outline and optional shadow. */
    private fun styledText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        base: Paint,
        o: BadgeOptions,
        u: Float,
        alpha: Float
    ) {
        val a = alpha.coerceIn(0f, 1f)
        val fill = Paint(base).apply {
            style = Paint.Style.FILL
            color = withAlpha(o.textColor, a)
        }
        val outline = if (o.outlineOn) {
            Paint(base).apply {
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeWidth = u * (0.05f + 0.20f * o.outlineWidth / 100f)
                color = withAlpha(o.outlineColor, a)
            }
        } else null
        if (o.shadowOn) {
            val k = (o.shadowStrength / 100f).coerceIn(0f, 1f)
            val shadowPaint = outline ?: fill
            shadowPaint.setShadowLayer(
                u * (0.04f + 0.30f * k),
                0f,
                u * (0.03f + 0.06f * k),
                withAlpha(o.shadowColor, (0.35f + 0.6f * k) * a)
            )
        }
        if (outline != null) canvas.drawText(text, x, y, outline)
        canvas.drawText(text, x, y, fill)
    }

    /** Text of the location row, or empty when location is off or unknown. */
    private fun locationLabel(o: BadgeOptions, meta: PhotoMeta?): String {
        if (!o.showLocation) return ""
        val custom = o.locationText.trim()
        if (custom.isNotEmpty()) return custom
        if (meta == null) return ""
        val coords = meta.coordinates()
        return if (o.locationMode == LocationMode.Place) meta.place.ifBlank { coords } else coords
    }

    // ---------- Centered style ----------

    /**
     * Camera-brand look: logo, device name, lens details, date and location,
     * stacked and centered, with no background pill.
     */
    private fun drawCentered(canvas: Canvas, w: Int, h: Int, o: BadgeOptions, meta: PhotoMeta?) {
        val s = min(w, h).toFloat()
        val u = s * 0.0327f * (o.sizePct / 100f)
        val m = s * 0.0275f

        val name = o.name.ifBlank { "Pixel 9 Pro XL" }.uppercase()
        val info = if (o.showDetails) o.info.trim().ifEmpty { meta?.info.orEmpty() } else ""
        val date = if (o.showDate) meta?.date.orEmpty() else ""
        val place = locationLabel(o, meta)

        val opacity = (o.opacityPct / 100f).coerceIn(0.35f, 1f)
        val namePaint = basePaint(u * 0.66f, true, 0.06f, Paint.Align.CENTER)
        val infoPaint = basePaint(u * 0.51f, false, 0.05f, Paint.Align.CENTER)
        val datePaint = basePaint(u * 0.50f, false, 0.05f, Paint.Align.CENTER)
        val placePaint = basePaint(u * 0.50f, false, 0.04f, Paint.Align.CENTER)

        // rows from top to bottom: L = logo, N = name, I = details, D = date, P = place
        val rows = ArrayList<Char>()
        if (o.showLogo) rows.add('L')
        rows.add('N')
        if (info.isNotEmpty()) rows.add('I')
        if (date.isNotEmpty()) rows.add('D')
        if (place.isNotEmpty()) rows.add('P')

        fun pitch(upper: Char): Float = when (upper) {
            'L' -> 1.82f * u
            'N' -> 1.14f * u
            else -> 0.89f * u
        }
        val offsets = FloatArray(rows.size)
        for (i in 1 until rows.size) offsets[i] = offsets[i - 1] + pitch(rows[i - 1])
        val total = offsets.last()

        val logoR = u * 0.84f
        val maxW = listOf(
            namePaint.measureText(name),
            if (info.isNotEmpty()) infoPaint.measureText(info) else 0f,
            if (date.isNotEmpty()) datePaint.measureText(date) else 0f,
            if (place.isNotEmpty()) placePaint.measureText(place) else 0f,
            if (o.showLogo) logoR * 2f else 0f
        ).max()

        val cx = when (o.position) {
            BadgePosition.BottomLeft, BadgePosition.TopLeft -> m + maxW / 2f
            BadgePosition.BottomRight, BadgePosition.TopRight -> w - m - maxW / 2f
            BadgePosition.BottomCenter -> w / 2f
        }
        val top = o.position == BadgePosition.TopLeft || o.position == BadgePosition.TopRight
        val firstY = if (top) u * 1.0f + (if (rows[0] == 'L') logoR else u * 0.35f)
        else h - u * 1.5f - total

        val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            if (o.shadowOn) {
                val k = (o.shadowStrength / 100f).coerceIn(0f, 1f)
                setShadowLayer(u * (0.06f + 0.24f * k), 0f, u * 0.05f, withAlpha(o.shadowColor, (0.35f + 0.6f * k) * opacity))
            }
        }
        rows.forEachIndexed { i, kind ->
            val y = firstY + offsets[i]
            when (kind) {
                'L' -> {
                    canvas.drawCircle(cx, y, logoR, logoPaint)
                    drawG(canvas, cx, y, logoR * 0.62f)
                }
                'N' -> styledText(canvas, name, cx, y + namePaint.textSize * 0.35f, namePaint, o, u, opacity)
                'I' -> styledText(canvas, info, cx, y + infoPaint.textSize * 0.35f, infoPaint, o, u, opacity * 0.92f)
                'D' -> styledText(canvas, date, cx, y + datePaint.textSize * 0.35f, datePaint, o, u, opacity * 0.85f)
                else -> styledText(canvas, place, cx, y + placePaint.textSize * 0.35f, placePaint, o, u, opacity * 0.85f)
            }
        }
    }

    // ---------- Pill style ----------

    private fun drawPill(canvas: Canvas, w: Int, h: Int, o: BadgeOptions, meta: PhotoMeta?) {
        val s = min(w, h).toFloat()
        val hh = s * 0.0327f * (o.sizePct / 100f)   // badge height
        val m = s * 0.0275f                           // edge margin
        val f1 = hh * 0.42f
        val f2 = hh * 0.175f
        val padL = hh * 0.07f
        val circ = hh * 0.86f
        val gap = hh * 0.2f
        val padR = hh * 0.42f

        val name = o.name.ifBlank { "Pixel 9 Pro XL" }
        val second = listOfNotNull(
            if (o.showDetails) o.tags.trim().ifEmpty { null } else null,
            locationLabel(o, meta).ifEmpty { null }
        ).joinToString("  ·  ")

        val namePaint = basePaint(f1, true, 0f, Paint.Align.LEFT)
        val tagPaint = basePaint(f2, false, 0f, Paint.Align.LEFT)

        val w1 = namePaint.measureText(name)
        val w2 = if (second.isNotEmpty()) tagPaint.measureText(second) else 0f
        val left = if (o.showLogo) padL + circ + gap else hh * 0.45f
        val pw = left + max(w1, w2) + padR

        var x = m
        var y = h - hh - m
        when (o.position) {
            BadgePosition.BottomLeft -> {}
            BadgePosition.BottomCenter -> x = (w - pw) / 2f
            BadgePosition.BottomRight -> x = w - pw - m
            BadgePosition.TopLeft -> y = m
            BadgePosition.TopRight -> { x = w - pw - m; y = m }
        }

        val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((o.opacityPct / 100f * 255).toInt().coerceIn(0, 255), 28, 28, 30)
        }
        canvas.drawRoundRect(RectF(x, y, x + pw, y + hh), hh / 2f, hh / 2f, pill)

        if (o.showLogo) {
            val cx = x + padL + circ / 2f
            val cy = y + hh / 2f
            val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawCircle(cx, cy, circ / 2f, white)
            drawG(canvas, cx, cy, circ * 0.31f)
        }

        val tx = x + left
        if (second.isNotEmpty()) {
            styledText(canvas, name, tx, y + hh * 0.5f, namePaint, o, hh, 1f)
            styledText(canvas, second, tx, y + hh * 0.5f + f2 * 1.45f, tagPaint, o, hh, 0.6f)
        } else {
            styledText(canvas, name, tx, y + hh / 2f + f1 * 0.35f, namePaint, o, hh, 1f)
        }
    }

    /** Stylised multicolour "G" built from arcs and a bar. */
    private fun drawG(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val t = radius * 0.38f
        val r = radius - t / 2f
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = t
            strokeCap = Paint.Cap.BUTT
        }
        fun arc(color: Int, start: Float, sweep: Float) {
            p.color = color
            canvas.drawArc(oval, start, sweep, false, p)
        }
        arc(Color.rgb(0xEA, 0x43, 0x35), 200f, 115f)
        arc(Color.rgb(0xFB, 0xBC, 0x05), 135f, 65f)
        arc(Color.rgb(0x34, 0xA8, 0x53), 45f, 90f)
        arc(Color.rgb(0x42, 0x85, 0xF4), 0f, 45f)
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x42, 0x85, 0xF4) }
        canvas.drawRect(cx, cy - t / 2f, cx + radius, cy + t / 2f, bar)
    }
}
