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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val SeedheInk = Color(0xFFF2EEE9)
private val SeedheCrimson = Color(0xFF8F1727)
private val SeedheCyan = Color(0xFF1E9BA7)
private val SeedheBlack = Color(0xFF0A0A0C)
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
            strokeWidt
