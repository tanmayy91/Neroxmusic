package com.convx.music.ui.component

import android.graphics.Canvas as HkNativeCanvas
import android.graphics.Paint as HkPaint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as hkLerpColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.convx.music.lyrics.LyricsEntry
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.launch

private const val HkTau = 6.2831855f
private const val HkPi = 3.1415927f
private val HkCream = Color(0xFFFFF7F0)
private val HkBulb = Color(0xFFFFC46B)
private val HkBulbCore = Color(0xFFFFF3D6)

private class HkMood(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val orb: Color,
    val far: Color,
    val near: Color,
    val lake: Color,
    val accent: Color,
    val stars: Float,
)

/** Golden hour -> twilight -> blue night. The sky drifts through them as the song moves on. */
private val HkMoods = arrayOf(
    HkMood(
        Color(0xFF2B2160), Color(0xFFD9577A), Color(0xFFFFB27A), Color(0xFFFFE9C4),
        Color(0xFF7A3A78), Color(0xFF26133C), Color(0xFFB4487A), Color(0xFFFFC09A), 0.15f,
    ),
    HkMood(
        Color(0xFF16164A), Color(0xFF5B3F9E), Color(0xFFF08FB0), Color(0xFFFFF0FA),
        Color(0xFF47318A), Color(0xFF170F3C), Color(0xFF6A4AAE), Color(0xFFFFB8E0), 0.55f,
    ),
    HkMood(
        Color(0xFF070B24), Color(0xFF1E2A66), Color(0xFF4F78D6), Color(0xFFF4F8FF),
        Color(0xFF1F2A66), Color(0xFF080C26), Color(0xFF1B3A86), Color(0xFFA8D8FF), 1f,
    ),
)

private class HkPalette {
    var top = Color.Black
    var mid = Color.Black
    var bottom = Color.Black
    var orb = Color.White
    var far = Color.Black
    var near = Color.Black
    var lake = Color.Black
    var accent = Color.White
    var stars = 0f

    fun update(pos: Float) {
        val base = floor(pos).toInt()
        val t = pos - base
        val a = HkMoods[base.mod(HkMoods.size)]
        val b = HkMoods[(base + 1).mod(HkMoods.size)]
        top = hkLerpColor(a.top, b.top, t)
        mid = hkLerpColor(a.mid, b.mid, t)
        bottom = hkLerpColor(a.bottom, b.bottom, t)
        orb = hkLerpColor(a.orb, b.orb, t)
        far = hkLerpColor(a.far, b.far, t)
        near = hkLerpColor(a.near, b.near, t)
        lake = hkLerpColor(a.lake, b.lake, t)
        accent = hkLerpColor(a.accent, b.accent, t)
        stars = a.stars + (b.stars - a.stars) * t
    }
}

private fun hkHash(seed: Int): Float {
    var x = seed * 0x9E3779B1.toInt() + 1013904223
    x = x xor (x ushr 15)
    x *= 0x2C1B3C6D
    x = x xor (x ushr 12)
    x *= 0x297A2D39
    x = x xor (x ushr 15)
    return (x and 0xFFFFFF) / 16777215f
}

private fun HkPaint.hkColor(c: Color, a: Float) {
    color = c.copy(alpha = (c.alpha * a).coerceIn(0f, 1f)).toArgb()
}

private class HkPaints {
    val text = HkPaint(HkPaint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = HkPaint.Align.CENTER
        letterSpacing = 0.01f
    }
    val title = HkPaint(HkPaint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("serif", Typeface.ITALIC)
        textAlign = HkPaint.Align.CENTER
        letterSpacing = 0.02f
    }
    val small = HkPaint(HkPaint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = HkPaint.Align.CENTER
        letterSpacing = 0.18f
    }
    val line = HkPaint(HkPaint.ANTI_ALIAS_FLAG).apply {
        style = HkPaint.Style.STROKE
        strokeCap = HkPaint.Cap.ROUND
    }
}

private class HkWord(val text: String, val startMs: Long, val endMs: Long) {
    var cx = 0f
    var base = 0f
    var width = 0f
}

private fun hkWords(line: LyricsEntry, next: LyricsEntry?): List<HkWord> {
    val timed = line.words.orEmpty()
    if (timed.isNotEmpty()) {
        return timed.map {
            val s = (it.startTime * 1_000.0).toLong()
            HkWord(it.text.trim(), s, (it.endTime * 1_000.0).toLong().coerceAtLeast(s + 1L))
        }.filter { it.text.isNotEmpty() }
    }
    val tokens = line.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val duration = ((next?.time ?: (line.time + 4_000L)) - line.time).coerceAtLeast(1L)
    val total = tokens.sumOf { it.length.coerceAtLeast(1) }.coerceAtLeast(1)
    var cursor = line.time
    return tokens.map { t ->
        val end = cursor + (duration * t.length.coerceAtLeast(1) / total).coerceAtLeast(1L)
        HkWord(t, cursor, end).also { cursor = end }
    }
}

private class HkLayout {
    var words: List<HkWord>? = null
    var w = 0f
    var h = 0f
    var short = false
    var size = 1f
    var left = 0f
    var right = 0f
    var top = 0f
    var bottom = 0f

    fun ensure(words: List<HkWord>, w: Float, h: Float, short: Boolean, paint: HkPaint) {
        if (this.words === words && this.w == w && this.h == h && this.short == short) return
        this.words = words
        this.w = w
        this.h = h
        this.short = short
        arrange(paint)
    }

    private fun arrange(paint: HkPaint) {
        val list = words ?: return
        val n = list.size
        if (n == 0) return
        paint.textSize = 100f
        val w100 = FloatArray(n) { paint.measureText(list[it].text).coerceAtLeast(1f) }
        val maxW = w * 0.84f
        val maxH = h * 0.30f
        var widest = 0f
        for (i in 0 until n) widest = max(widest, w100[i] / 100f)
        var s = min(if (short) h * 0.10f else h * 0.064f, maxW / widest)
        val minS = h * 0.032f
        val rowEnd = IntArray(n)
        var rows = 0
        while (true) {
            rows = 0
            var cursor = 0f
            var count = 0
            for (i in 0 until n) {
                val ww = w100[i] * s / 100f
                val gap = if (count == 0) 0f else 0.28f * s
                if (count > 0 && cursor + gap + ww > maxW) {
                    rowEnd[rows++] = i
                    cursor = ww
                    count = 1
                } else {
                    cursor += gap + ww
                    count++
                }
            }
            rowEnd[rows++] = n
            val total = (rows - 1) * 1.22f * s + 0.9f * s
            if (total <= maxH || s <= minS) break
            s *= 0.93f
        }
        val total = (rows - 1) * 1.22f * s + 0.9f * s
        val top0 = h * 0.40f - total / 2f
        var idx = 0
        var minL = w
        var maxR = 0f
        for (r in 0 until rows) {
            val end = rowEnd[r]
            var rowW = 0f
            for (i in idx until end) rowW += w100[i] * s / 100f
            rowW += (end - idx - 1) * 0.28f * s
            var x = (w - rowW) / 2f
            for (i in idx until end) {
                val wd = list[i]
                wd.width = w100[i] * s / 100f
                wd.cx = x + wd.width / 2f
                wd.base = top0 + 0.72f * s + r * 1.22f * s
                x += wd.width + 0.28f * s
            }
            minL = min(minL, (w - rowW) / 2f)
            maxR = max(maxR, (w + rowW) / 2f)
            idx = end
        }
        size = s
        left = minL
        right = maxR
        top = top0
        bottom = top0 + total
    }
}

private fun DrawScope.hkSparkle(path: Path, cx: Float, cy: Float, r: Float, color: Color) {
    path.reset()
    val inner = r * 0.22f
    for (i in 0 until 8) {
        val a = (i * 45f - 90f) * 0.017453292f
        val rad = if (i % 2 == 0) r else inner
        val x = cx + cos(a) * rad
        val y = cy + sin(a) * rad
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.hkSky(p: HkPalette, horizon: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(p.top, p.mid, p.bottom),
            startY = 0f,
            endY = horizon,
        ),
    )
}

private fun DrawScope.hkStars(p: HkPalette, clock: Float, u: Float, path: Path) {
    if (p.stars < 0.04f) return
    val w = size.width
    val h = size.height
    for (i in 0 until 70) {
        val x = hkHash(i * 3 + 1) * w
        val y = hkHash(i * 3 + 2) * h * 0.52f
        val tw = 0.45f + 0.55f * sin(clock * HkTau * (8 + i % 5) + i * 1.3f)
        val fade = 1f - y / (h * 0.6f)
        val a = (p.stars * tw * fade).coerceIn(0f, 1f)
        if (i % 10 == 0) {
            hkSparkle(path, x, y, (4f + hkHash(i) * 4f) * u, Color.White.copy(alpha = a))
        } else {
            drawCircle(Color.White.copy(alpha = a), radius = (0.5f + hkHash(i + 99) * 1.1f) * u, center = Offset(x, y))
        }
    }
}

/** The big anime sun / moon with a soft halo and slow breathing rings. */
private fun DrawScope.hkOrb(p: HkPalette, clock: Float, u: Float, cx: Float, cy: Float, r: Float) {
    val c = Offset(cx, cy)
    val breathe = 1f + 0.02f * sin(clock * HkTau * 8f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(p.orb.copy(alpha = 0.55f), p.orb.copy(alpha = 0.12f), Color.Transparent),
            center = c,
            radius = r * 3.2f,
        ),
        radius = r * 3.2f,
        center = c,
    )
    drawCircle(p.orb.copy(alpha = 0.16f), radius = r * 1.28f * breathe, center = c, style = Stroke(1.5f * u))
    drawCircle(p.orb.copy(alpha = 0.08f), radius = r * 1.62f * breathe, center = c, style = Stroke(1.2f * u))
    drawCircle(p.orb, radius = r, center = c)
    drawCircle(p.mid.copy(alpha = 0.16f), radius = r * 0.74f, center = Offset(cx + r * 0.20f, cy + r * 0.16f))
    drawCircle(p.mid.copy(alpha = 0.10f), radius = r * 0.12f, center = Offset(cx - r * 0.35f, cy - r * 0.25f))
    drawCircle(p.mid.copy(alpha = 0.08f), radius = r * 0.08f, center = Offset(cx - r * 0.1f, cy + r * 0.4f))
}

private fun DrawScope.hkCloudShape(color: Color, cx: Float, cy: Float, s: Float) {
    drawRoundRect(
        color = color,
        topLeft = Offset(cx - s * 1.1f, cy - s * 0.18f),
        size = Size(s * 2.2f, s * 0.36f),
        cornerRadius = CornerRadius(s * 0.18f, s * 0.18f),
    )
    drawCircle(color, s * 0.45f, Offset(cx - s * 0.5f, cy - s * 0.22f))
    drawCircle(color, s * 0.62f, Offset(cx, cy - s * 0.34f))
    drawCircle(color, s * 0.42f, Offset(cx + s * 0.55f, cy - s * 0.2f))
}

/** yFraction, sizeFraction, speed(loops per cycle), phase, alpha */
private val HkClouds = floatArrayOf(
    0.16f, 0.20f, 1f, 0.10f, 0.55f,
    0.30f, 0.28f, 2f, 0.55f, 0.50f,
    0.42f, 0.22f, 1f, 0.85f, 0.45f,
    0.55f, 0.30f, 2f, 0.30f, 0.50f,
    0.62f, 0.18f, 3f, 0.70f, 0.40f,
)

private fun DrawScope.hkClouds(p: HkPalette, clock: Float) {
    val w = size.width
    val h = size.height
    val body = hkLerpColor(p.mid, Color.White, 0.35f)
    val rim = hkLerpColor(p.bottom, Color.White, 0.70f)
    for (i in 0 until HkClouds.size / 5) {
        val o = i * 5
        val a = HkClouds[o + 4]
        val x = ((HkClouds[o + 3] + clock * HkClouds[o + 2]) % 1f * 1.5f - 0.25f) * w
        val y = h * HkClouds[o]
        val s = w * HkClouds[o + 1]
        hkCloudShape(rim.copy(alpha = a * 0.9f), x, y - s * 0.07f, s)
        hkCloudShape(body.copy(alpha = a), x, y, s)
    }
}

private fun DrawScope.hkBirds(p: HkPalette, clock: Float, u: Float, path: Path) {
    val w = size.width
    val h = size.height
    for (i in 0 until 3) {
        val x = (((i * 0.31f) + clock * 3f) % 1f * 1.3f - 0.15f) * w
        val y = h * (0.22f + 0.04f * i) + sin(clock * HkTau * 5f + i * 2f) * 0.012f * h
        val s = (7f + i * 2f) * u
        val flap = sin(clock * HkTau * 60f + i * 1.9f) * 0.45f * s
        path.reset()
        path.moveTo(x - s, y + flap)
        path.quadraticBezierTo(x - s * 0.4f, y - s * 0.3f, x, y)
        path.quadraticBezierTo(x + s * 0.4f, y - s * 0.3f, x + s, y + flap)
        drawPath(
            path = path,
            color = p.near.copy(alpha = 0.75f),
            style = Stroke(width = 1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private fun DrawScope.hkShootingStar(t: Float, u: Float) {
    if (t <= 0.001f || t >= 0.999f) return
    val w = size.width
    val h = size.height
    val sx = w * 0.95f
    val sy = h * 0.06f
    val ex = w * 0.30f
    val ey = h * 0.32f
    val head = Offset(sx + (ex - sx) * t, sy + (ey - sy) * t)
    val tailT = (t - 0.22f).coerceAtLeast(0f)
    val tail = Offset(sx + (ex - sx) * tailT, sy + (ey - sy) * tailT)
    val a = sin(t * HkPi)
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = a)),
            start = tail,
            end = head,
        ),
        start = tail,
        end = head,
        strokeWidth = 2.4f * u,
        cap = StrokeCap.Round,
    )
    drawCircle(Color.White.copy(alpha = a * 0.35f), radius = 7f * u, center = head)
    drawCircle(Color.White.copy(alpha = a), radius = 2.4f * u, center = head)
}

/** Stylised Hauz Khas tomb: crenellated walls, two chhatris and a big onion dome. */
private fun hkBuildSkyline(path: Path, w: Float, h: Float, y0: Float) {
    path.reset()
    val ground = y0 + h * 0.02f

    fun dome(cx: Float, yb: Float, r: Float, hd: Float) {
        path.lineTo(cx - r, yb)
        path.cubicTo(cx - r * 1.25f, yb - hd * 0.35f, cx - r * 0.35f, yb - hd * 0.55f, cx, yb - hd)
        path.cubicTo(cx + r * 0.35f, yb - hd * 0.55f, cx + r * 1.25f, yb - hd * 0.35f, cx + r, yb)
    }

    fun wall(fromX: Float, toX: Float, wallY: Float) {
        val m = 0.012f * h
        val st = 0.024f * w
        var x = fromX
        while (x < toX) {
            path.lineTo(x, wallY - m)
            path.lineTo(x + st * 0.5f, wallY - m)
            path.lineTo(x + st * 0.5f, wallY)
            path.lineTo(x + st, wallY)
            x += st
        }
    }

    path.moveTo(-0.05f * w, ground)
    path.lineTo(-0.05f * w, y0 - 0.05f * h)
    wall(-0.05f * w, 0.24f * w, y0 - 0.05f * h)
    path.lineTo(0.25f * w, y0 - 0.07f * h)
    path.lineTo(0.27f * w, y0 - 0.07f * h)
    path.lineTo(0.27f * w, y0 - 0.11f * h)
    dome(0.295f * w, y0 - 0.11f * h, 0.026f * w, 0.04f * h)
    path.lineTo(0.33f * w, y0 - 0.11f * h)
    path.lineTo(0.33f * w, y0 - 0.095f * h)
    path.lineTo(0.34f * w, y0 - 0.095f * h)
    path.lineTo(0.34f * w, y0 - 0.135f * h)
    path.lineTo(0.36f * w, y0 - 0.135f * h)
    dome(0.455f * w, y0 - 0.135f * h, 0.095f * w, 0.085f * h)
    path.lineTo(0.57f * w, y0 - 0.135f * h)
    path.lineTo(0.57f * w, y0 - 0.095f * h)
    path.lineTo(0.58f * w, y0 - 0.095f * h)
    path.lineTo(0.58f * w, y0 - 0.11f * h)
    dome(0.605f * w, y0 - 0.11f * h, 0.026f * w, 0.04f * h)
    path.lineTo(0.64f * w, y0 - 0.11f * h)
    path.lineTo(0.64f * w, y0 - 0.07f * h)
    path.lineTo(0.70f * w, y0 - 0.07f * h)
    path.lineTo(0.70f * w, y0 - 0.05f * h)
    wall(0.70f * w, 1.05f * w, y0 - 0.05f * h)
    path.lineTo(1.05f * w, ground)
    path.close()
}

private fun DrawScope.hkSkyline(p: HkPalette, clock: Float, u: Float, y0: Float, path: Path, arch: Path) {
    val w = size.width
    val h = size.height
    // soft tree line behind the monument
    for (i in 0..16) {
        val x = i / 16f * 1.1f * w - 0.05f * w
        val r = (0.035f + 0.03f * hkHash(i + 40)) * w
        drawCircle(p.far.copy(alpha = 0.9f), r, Offset(x, y0 - 0.01f * h - hkHash(i + 70) * 0.03f * h))
    }
    drawPath(path, p.near)
    // finials
    val tipColor = p.near
    drawLine(tipColor, Offset(0.455f * w, y0 - 0.22f * h), Offset(0.455f * w, y0 - 0.245f * h), strokeWidth = 2f * u, cap = StrokeCap.Round)
    drawCircle(tipColor, 2.6f * u, Offset(0.455f * w, y0 - 0.247f * h))
    drawLine(tipColor, Offset(0.295f * w, y0 - 0.15f * h), Offset(0.295f * w, y0 - 0.165f * h), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
    drawLine(tipColor, Offset(0.605f * w, y0 - 0.15f * h), Offset(0.605f * w, y0 - 0.165f * h), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
    // warm lit arches
    for (i in 0 until 5) {
        val cx = (0.375f + i * 0.04f) * w
        val half = 0.008f * w
        val yb = y0 - 0.012f * h
        val hh = 0.045f * h
        val flicker = 0.70f + 0.25f * sin(clock * HkTau * (6 + i) + i * 1.9f)
        arch.reset()
        arch.moveTo(cx - half, yb)
        arch.lineTo(cx - half, yb - hh)
        arch.quadraticBezierTo(cx, yb - hh - half * 2.2f, cx + half, yb - hh)
        arch.lineTo(cx + half, yb)
        arch.close()
        drawPath(arch, HkBulb.copy(alpha = flicker))
    }
}

private fun DrawScope.hkLake(p: HkPalette, clock: Float, u: Float, y0: Float, orbX: Float, orbR: Float, skyline: Path) {
    val w = size.width
    val h = size.height
    val lakeH = h - y0
    drawRect(
        brush = Brush.verticalGradient(listOf(p.lake, p.near), startY = y0, endY = h),
        topLeft = Offset(0f, y0),
        size = Size(w, lakeH),
    )
    drawRect(
        brush = Brush.verticalGradient(
            listOf(p.bottom.copy(alpha = 0.55f), Color.Transparent),
            startY = y0,
            endY = y0 + lakeH * 0.45f,
        ),
        topLeft = Offset(0f, y0),
        size = Size(w, lakeH),
    )
    scale(scaleX = 1f, scaleY = -0.7f, pivot = Offset(w / 2f, y0)) {
        drawPath(skyline, p.near.copy(alpha = 0.45f))
    }
    // glittering moon / sun path on the water
    for (i in 0 until 12) {
        val y = y0 + (i + 0.5f) * lakeH / 14f
        val width = orbR * (1.5f - i * 0.08f) * (0.7f + 0.3f * sin(clock * HkTau * 6f + i * 0.9f))
        val x = orbX + sin(clock * HkTau * 4f + i) * orbR * 0.08f
        drawLine(
            color = p.orb.copy(alpha = 0.45f * (1f - i / 12f)),
            start = Offset(x - width / 2f, y),
            end = Offset(x + width / 2f, y),
            strokeWidth = (2.4f + i * 0.15f) * u,
            cap = StrokeCap.Round,
        )
    }
    // slow ripples
    for (k in 0 until 3) {
        val ph = (clock * 4f + k / 3f) % 1f
        val cx = w * (0.2f + 0.3f * k + 0.1f * hkHash(k + 5))
        val cy = y0 + lakeH * (0.35f + 0.18f * k)
        val rx = (10f + ph * 46f) * u
        drawOval(
            color = Color.White.copy(alpha = (1f - ph) * 0.25f),
            topLeft = Offset(cx - rx, cy - rx * 0.28f),
            size = Size(rx * 2f, rx * 0.56f),
            style = Stroke(1.2f * u),
        )
    }
    // mist along the shoreline
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, p.bottom.copy(alpha = 0.35f), Color.Transparent),
            startY = y0 - 0.04f * h,
            endY = y0 + 0.05f * h,
        ),
        topLeft = Offset(0f, y0 - 0.04f * h),
        size = Size(w, 0.09f * h),
    )
}

/** Gulmohar branch hanging into the frame from a top corner. */
private fun DrawScope.hkBranch(p: HkPalette, clock: Float, u: Float, path: Path, mirror: Boolean, k: Float, seed: Int) {
    val w = size.width
    val h = size.height
    val sway = sin(clock * HkTau * 2f) * 0.8f
    fun px(x: Float) = if (mirror) w - x else x
    val x0 = -0.05f * w
    val y0 = 0.04f * h
    val x1 = 0.22f * w * k
    val y1 = 0.01f * h
    val x2 = 0.42f * w * k
    val y2 = 0.17f * h * k
    rotate(if (mirror) -sway else sway, Offset(if (mirror) w else 0f, 0f)) {
        path.reset()
        path.moveTo(px(x0), y0)
        path.quadraticBezierTo(px(x1), y1, px(x2), y2)
        drawPath(path, p.near, style = Stroke(width = 5f * u * k, cap = StrokeCap.Round))
        for (i in 0 until 26) {
            val t = i / 25f
            val a = (1f - t) * (1f - t)
            val b = 2f * (1f - t) * t
            val c = t * t
            val bx = a * x0 + b * x1 + c * x2
            val by = a * y0 + b * y1 + c * y2
            val jx = (hkHash(seed + i) - 0.5f) * 0.05f * w
            val jy = hkHash(seed + i + 50) * 0.06f * h * k
            val r = (0.016f + hkHash(seed + i + 90) * 0.018f) * w * k
            drawCircle(p.near, r, Offset(px(bx + jx), by + jy))
        }
        for (i in 0 until 9) {
            val t = (i + 0.5f) / 9f
            val a = (1f - t) * (1f - t)
            val b = 2f * (1f - t) * t
            val c = t * t
            val bx = a * x0 + b * x1 + c * x2
            val by = a * y0 + b * y1 + c * y2
            val jx = (hkHash(seed + i + 130) - 0.5f) * 0.04f * w
            val jy = (0.01f + hkHash(seed + i + 170) * 0.05f) * h * k
            val r = (0.007f + hkHash(seed + i + 210) * 0.007f) * w * k
            val c0 = Offset(px(bx + jx), by + jy)
            drawCircle(Color(0xFFFF6B3D).copy(alpha = 0.18f), r * 2.6f, c0)
            drawCircle(Color(0xFFFF6B3D).copy(alpha = 0.95f), r, c0)
            drawCircle(Color(0xFFFFD36B).copy(alpha = 0.9f), r * 0.4f, c0)
        }
    }
}

private fun DrawScope.hkFairyLights(p: HkPalette, clock: Float, u: Float, path: Path) {
    val w = size.width
    val h = size.height
    fun yAt(t: Float) = h * (0.085f + 0.04f * 4f * t * (1f - t))
    path.reset()
    for (i in 0..30) {
        val t = i / 30f
        val x = t * w * 1.04f - 0.02f * w
        if (i == 0) path.moveTo(x, yAt(t)) else path.lineTo(x, yAt(t))
    }
    drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(1.2f * u))
    for (i in 0 until 16) {
        val t = (i + 0.5f) / 16f
        val c = Offset(t * w * 1.04f - 0.02f * w, yAt(t) + 4f * u)
        val tw = 0.65f + 0.35f * sin(clock * HkTau * (5 + i % 4) + i * 2.1f)
        drawCircle(HkBulb.copy(alpha = 0.18f * tw), 9f * u, c)
        drawCircle(HkBulb.copy(alpha = 0.35f * tw), 5f * u, c)
        drawCircle(HkBulbCore.copy(alpha = 0.95f), 2.2f * u, c)
    }
}

private fun DrawScope.hkPetals(clock: Float, u: Float) {
    val w = size.width
    val h = size.height
    for (i in 0 until 26) {
        val k = 6 + i % 4
        val ph = hkHash(i * 5 + 1)
        val prog = (ph + clock * k) % 1f
        val y = prog * h * 1.15f - 0.08f * h
        val x = hkHash(i * 5 + 2) * w * 1.1f - 0.05f * w - prog * 0.14f * w +
            sin(clock * HkTau * (2 + i % 3) + ph * 6f) * 0.04f * w
        val s = (3.5f + hkHash(i * 5 + 3) * 4.5f) * u
        val rot = clock * 360f * k * (if (i % 2 == 0) 1f else -1f) + ph * 360f
        val col = when (i % 3) {
            0 -> Color(0xFFFFB3C7)
            1 -> Color(0xFFFF7A4D)
            else -> Color(0xFFFFD36B)
        }
        val a = min(1f, min(prog, 1f - prog) * 6f)
        rotate(rot, Offset(x, y)) {
            drawOval(col.copy(alpha = 0.85f * a), Offset(x - s, y - s * 0.55f), Size(s * 2f, s * 1.1f))
        }
    }
}

private fun DrawScope.hkFireflies(clock: Float, u: Float) {
    val w = size.width
    val h = size.height
    for (i in 0 until 16) {
        val k = 2 + i % 3
        val prog = (hkHash(i * 7 + 1) + clock * k) % 1f
        val y = h * (0.92f - prog * 0.40f)
        val x = hkHash(i * 7 + 2) * w + sin(clock * HkTau * (3 + i % 4) + i) * 0.03f * w
        val a = (sin(prog * HkPi) * (0.45f + 0.25f * sin(clock * HkTau * 10f + i))).coerceIn(0f, 1f)
        val r = (3f + hkHash(i * 7 + 3) * 5f) * u
        val c = Offset(x, y)
        drawCircle(HkBulb.copy(alpha = a * 0.25f), r * 2.6f, c)
        drawCircle(HkBulb.copy(alpha = a * 0.6f), r, c)
        drawCircle(HkBulbCore.copy(alpha = a), r * 0.4f, c)
    }
}

private fun DrawScope.hkOverlay(p: HkPalette, frame: Int, impact: Float, u: Float) {
    val w = size.width
    val h = size.height
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(p.accent.copy(alpha = 0.16f), Color.Transparent),
            start = Offset.Zero,
            end = Offset(w * 0.7f, h * 0.5f),
        ),
    )
    drawRect(
        brush = Brush.radialGradient(
            0.6f to Color.Transparent,
            1f to p.near.copy(alpha = 0.55f),
            center = Offset(w / 2f, h / 2f),
            radius = hypot(w, h) / 2f,
        ),
    )
    for (i in 0 until 28) {
        drawCircle(
            color = Color.White.copy(alpha = 0.04f + hkHash(frame * 31 + i * 5) * 0.05f),
            radius = (0.5f + hkHash(frame * 17 + i) * 0.9f) * u,
            center = Offset(hkHash(frame * 97 + i * 13) * w, hkHash(frame * 57 + i * 29) * h),
        )
    }
    if (impact > 0.01f) drawRect(Color.White.copy(alpha = 0.10f * impact * impact))
}

private fun hkGlint(nc: HkNativeCanvas, paint: HkPaint, x: Float, y: Float, r: Float) {
    nc.drawLine(x - r, y, x + r, y, paint)
    nc.drawLine(x, y - r, x, y + r, paint)
    nc.drawLine(x - r * 0.55f, y - r * 0.55f, x + r * 0.55f, y + r * 0.55f, paint)
    nc.drawLine(x - r * 0.55f, y + r * 0.55f, x + r * 0.55f, y - r * 0.55f, paint)
}

/** Draws one lyric line: soft glow, bloom + sparkle as each word is sung, gentle karaoke fill. */
private fun hkDrawLine(
    nc: HkNativeCanvas,
    paints: HkPaints,
    words: List<HkWord>,
    lay: HkLayout,
    p: HkPalette,
    positionMs: Long,
    alpha: Float,
    yShift: Float,
    live: Boolean,
) {
    val tp = paints.text
    val s = lay.size
    tp.textSize = s
    tp.textSkewX = 0f
    tp.strokeJoin = HkPaint.Join.ROUND
    for (word in words) {
        val since = positionMs - word.startMs
        val started = !live || since >= 0L
        val span = (word.endMs - word.startMs).coerceAtLeast(1L)
        val progress = if (live) (since.toFloat() / span).coerceIn(0f, 1f) else 1f
        val active = live && started && progress < 1f
        val bloomT = if (live) (since / 380f).coerceIn(0f, 1f) else 1f
        val sc = if (live && started) 1f + 0.10f * sin(bloomT * HkPi) else 1f
        val half = word.width / 2f
        val ty = s * 0.3f

        nc.save()
        nc.translate(word.cx, word.base + yShift - s * 0.3f)
        nc.scale(sc, sc)

        tp.style = HkPaint.Style.FILL
        tp.hkColor(p.near, 0.45f * alpha)
        nc.drawText(word.text, 0f, ty + s * 0.05f, tp)

        if (live && started) {
            val g = if (active) 1f else 0.55f
            tp.style = HkPaint.Style.STROKE
            tp.strokeWidth = s * 0.22f
            tp.hkColor(p.accent, 0.10f * g * alpha)
            nc.drawText(word.text, 0f, ty, tp)
            tp.strokeWidth = s * 0.10f
            tp.hkColor(p.accent, 0.18f * g * alpha)
            nc.drawText(word.text, 0f, ty, tp)
        }

        tp.style = HkPaint.Style.FILL
        tp.hkColor(HkCream, 0.42f * alpha)
        nc.drawText(word.text, 0f, ty, tp)
        if (started) {
            tp.hkColor(if (active) hkLerpColor(HkCream, p.accent, 0.30f) else HkCream, alpha)
            nc.save()
            nc.clipRect(
                -half - s * 0.2f,
                -s,
                -half + word.width * progress + (if (progress >= 1f) s * 0.3f else 0f),
                ty + s * 0.5f,
            )
            nc.drawText(word.text, 0f, ty, tp)
            nc.restore()
        }

        if (live && started && since < 520L) {
            val gT = 1f - since / 520f
            val ln = paints.line
            ln.strokeWidth = max(1f, s * 0.04f)
            ln.hkColor(HkCream, gT * alpha)
            hkGlint(nc, ln, half * 0.85f, -s * 0.62f, s * (0.10f + 0.12f * gT))
        }
        nc.restore()
    }
}

private fun DrawScope.hkUnderline(p: HkPalette, lay: HkLayout, progress: Float, alpha: Float, u: Float, path: Path) {
    if (progress <= 0.01f) return
    val x0 = lay.left
    val x1 = lay.left + (lay.right - lay.left) * progress
    val y = lay.bottom + lay.size * 0.18f
    val amp = lay.size * 0.05f
    val wl = lay.size * 1.1f
    path.reset()
    var x = x0
    var first = true
    while (x <= x1) {
        val yy = y + sin((x - x0) / wl * HkTau) * amp
        if (first) {
            path.moveTo(x, yy)
            first = false
        } else {
            path.lineTo(x, yy)
        }
        x += 6f * u
    }
    drawPath(
        path = path,
        color = p.accent.copy(alpha = 0.55f * alpha),
        style = Stroke(width = max(2f * u, lay.size * 0.06f), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.hkTitle(paints: HkPaints, p: HkPalette, clock: Float, u: Float) {
    val w = size.width
    val h = size.height
    val nc = drawContext.canvas.nativeCanvas
    val tp = paints.title
    tp.textSkewX = 0f
    tp.textSize = 100f
    val m = tp.measureText("Seedhe Maut").coerceAtLeast(1f)
    val s = min(h * 0.085f, w * 0.86f * 100f / m)
    val breathe = 1f + 0.02f * sin(clock * HkTau * 30f)
    tp.textSize = s
    tp.strokeJoin = HkPaint.Join.ROUND
    nc.save()
    nc.translate(w / 2f, h * 0.40f)
    nc.scale(breathe, breathe)
    tp.style = HkPaint.Style.FILL
    tp.hkColor(p.near, 0.45f)
    nc.drawText("Seedhe Maut", 0f, s * 0.05f, tp)
    tp.style = HkPaint.Style.STROKE
    tp.strokeWidth = s * 0.20f
    tp.hkColor(p.accent, 0.12f)
    nc.drawText("Seedhe Maut", 0f, 0f, tp)
    tp.strokeWidth = s * 0.09f
    tp.hkColor(p.accent, 0.20f)
    nc.drawText("Seedhe Maut", 0f, 0f, tp)
    tp.style = HkPaint.Style.FILL
    tp.hkColor(HkCream, 1f)
    nc.drawText("Seedhe Maut", 0f, 0f, tp)
    nc.restore()

    val sp = paints.small
    sp.style = HkPaint.Style.FILL
    sp.textSize = 11f * u
    sp.hkColor(HkCream, 0.75f)
    nc.drawText("encore abj  ·  calm", w / 2f, h * 0.40f + s * 0.85f, sp)
    for (i in 0 until 3) {
        val r = (4f + 2.5f * max(0f, sin(clock * HkTau * 10f - i * 0.9f))) * u
        drawCircle(HkCream.copy(alpha = 0.85f), r, Offset(w / 2f + (i - 1) * 18f * u, h * 0.40f + s * 0.85f + 34f * u))
    }
}

/**
 * Seedhe Maut lyrics, "Hauz Khas at dusk" edition: an anime sky over the Hauz Khas lake and
 * tomb, drifting clouds, petals and fireflies, with soft glowing lyrics that bloom word by word.
 * Same parameters as [SeedheMautLyricsCanvas], so either can be used.
 */
@Composable
fun SeedheMautHauzKhasLyricsCanvas(
    lines: List<LyricsEntry>,
    currentIndex: Int,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val activeLine = lines.getOrNull(currentIndex)
    val nextLine = lines.getOrNull(currentIndex + 1)
    val previousLine = lines.getOrNull(currentIndex - 1)
    val gapAfterCurrent = nextLine?.time?.minus(positionMs) ?: Long.MAX_VALUE
    val gapBeforeFirst = lines.firstOrNull()?.time?.minus(positionMs) ?: Long.MAX_VALUE
    val instrumental = lines.isNotEmpty() &&
        (currentIndex < 0 && gapBeforeFirst > 2_500L || currentIndex >= 0 && gapAfterCurrent > 2_500L)

    val words = remember(activeLine, nextLine) {
        if (activeLine != null) hkWords(activeLine, nextLine) else emptyList()
    }
    val prevWords = remember(previousLine, activeLine) {
        if (previousLine != null && activeLine != null) hkWords(previousLine, activeLine) else emptyList()
    }
    val showTitle = instrumental || words.isEmpty()
    val index = currentIndex.coerceAtLeast(0)
    val short = words.size <= 3

    val moodPos = remember { Animatable((index / 4).toFloat()) }
    LaunchedEffect(index / 4) {
        moodPos.animateTo((index / 4).toFloat(), tween(2_400, easing = FastOutSlowInEasing))
    }
    val impact = remember { Animatable(0f) }
    val shoot = remember { Animatable(0f) }
    LaunchedEffect(currentIndex) {
        impact.snapTo(1f)
        launch { impact.animateTo(0f, tween(700, easing = LinearOutSlowInEasing)) }
        if (index % 4 == 3 && activeLine != null) {
            shoot.snapTo(0f)
            shoot.animateTo(1f, tween(1_100, easing = LinearEasing))
        }
    }
    val transition = rememberInfiniteTransition(label = "hauzKhasSky")
    val clock = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)),
        label = "hauzKhasClock",
    )

    val palette = remember { HkPalette() }
    val paints = remember { HkPaints() }
    val cur = remember { HkLayout() }
    val prev = remember { HkLayout() }
    val skylinePath = remember { Path() }
    val archPath = remember { Path() }
    val miscPath = remember { Path() }
    val sparklePath = remember { Path() }
    val underlinePath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer {
                val c = clock.value
                val sc = 1.04f + 0.03f * impact.value
                scaleX = sc
                scaleY = sc
                translationX = sin(c * HkTau) * 6f
                translationY = cos(c * HkTau) * 4f
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val u = size.minDimension / 360f
            val clk = clock.value
            val imp = impact.value
            val frame = (positionMs / 55L).toInt()
            val nc = drawContext.canvas.nativeCanvas
            palette.update(moodPos.value)

            val y0 = h * 0.70f
            val orbR = size.minDimension * 0.13f
            val orbX = w * (0.76f + 0.02f * sin(clk * HkTau))
            val orbY = h * 0.18f

            hkSky(palette, y0)
            hkStars(palette, clk, u, sparklePath)
            hkOrb(palette, clk, u, orbX, orbY, orbR)
            hkClouds(palette, clk)
            hkBirds(palette, clk, u, miscPath)
            hkShootingStar(shoot.value, u)
            hkBuildSkyline(skylinePath, w, h, y0)
            hkLake(palette, clk, u, y0, orbX, orbR, skylinePath)
            hkSkyline(palette, clk, u, y0, skylinePath, archPath)
            hkFireflies(clk, u)
            hkBranch(palette, clk, u, miscPath, false, 1f, 11)
            hkBranch(palette, clk, u, miscPath, true, 0.7f, 77)
            hkFairyLights(palette, clk, u, miscPath)

            // soft dusk scrim so the lyrics stay readable over the bright sky
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, palette.near.copy(alpha = 0.28f), Color.Transparent),
                    startY = h * 0.18f,
                    endY = h * 0.62f,
                ),
                topLeft = Offset(0f, h * 0.18f),
                size = Size(w, h * 0.44f),
            )

            if (showTitle) {
                hkTitle(paints, palette, clk, u)
            } else {
                val lineStart = activeLine?.time ?: 0L
                val lineEnd = nextLine?.time ?: (lineStart + 4_000L)
                val sinceStart = (positionMs - lineStart).toFloat()
                val enter = (sinceStart / 380f).coerceIn(0f, 1f)
                val ease = 1f - (1f - enter) * (1f - enter) * (1f - enter)
                val lineProgress = (sinceStart / (lineEnd - lineStart).coerceAtLeast(1L)).coerceIn(0f, 1f)

                cur.ensure(words, w, h, short, paints.text)
                if (prevWords.isNotEmpty()) {
                    val t2 = (sinceStart / 500f).coerceIn(0f, 1f)
                    if (t2 < 1f) {
                        prev.ensure(prevWords, w, h, prevWords.size <= 3, paints.text)
                        hkDrawLine(nc, paints, prevWords, prev, palette, positionMs, (1f - t2) * 0.45f, -t2 * 0.08f * h, false)
                    }
                }
                hkDrawLine(nc, paints, words, cur, palette, positionMs, ease, (1f - ease) * 0.03f * h, true)
                hkUnderline(palette, cur, lineProgress, ease, u, underlinePath)

                if (short && imp > 0.02f) {
                    for (i in 0 until 6) {
                        val ang = i * HkTau / 6f + 0.4f
                        val dist = w * (0.25f + 0.25f * (1f - imp))
                        hkSparkleBurst(
                            sparklePath,
                            w / 2f + cos(ang) * dist,
                            h * 0.40f + sin(ang) * dist * 0.6f,
                            (8f + hkHash(i + index) * 8f) * u * imp,
                            HkCream.copy(alpha = imp),
                        )
                    }
                }

                val upcoming = nextLine?.text?.trim().orEmpty()
                if (upcoming.isNotEmpty()) {
                    val np = paints.text
                    np.style = HkPaint.Style.FILL
                    np.textSkewX = 0f
                    np.textSize = 13f * u
                    val m = np.measureText(upcoming)
                    val maxW = w * 0.84f
                    if (m > maxW) np.textSize = np.textSize * maxW / m
                    np.hkColor(HkCream, 0.50f)
                    nc.drawText(upcoming, w / 2f, h * 0.625f, np)
                }
            }

            hkPetals(clk, u)
            hkOverlay(palette, frame, imp, u)
        }
    }
}

private fun DrawScope.hkSparkleBurst(path: Path, cx: Float, cy: Float, r: Float, color: Color) =
    hkSparkle(path, cx, cy, r, color)
