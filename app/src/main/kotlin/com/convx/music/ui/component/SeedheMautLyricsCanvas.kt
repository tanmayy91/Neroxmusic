package com.convx.music.ui.component

import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint as NativePaint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.music.lyrics.LyricsEntry
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.launch

private val SeedheInk = Color(0xFFF2EEE9)
private val SeedheCrimson = Color(0xFF8F1727)
private val SeedheCyan = Color(0xFF1E9BA7)
private val SeedheBlack = Color(0xFF0A0A0C)
private val SeedheRed = Color(0xFFE3121D)
private val SeedhePaper = Color(0xFFECE5D3)
private val SeedheTape = Color(0xFFFFD60A)
private const val SeedheTau = 6.2831855f

private val SeedheFace: Typeface by lazy { Typeface.create("sans-serif-condensed", Typeface.BOLD) }

private enum class SeedheStyle { SLAM, BLOCKS, STACK, SCRIBBLE, OUTLINE }

private class SeedheTheme(
    val bg: Color,
    val fg: Color,
    val accent: Color,
    val onAccent: Color,
    val glow: Color,
    val dots: Color,
    val vignette: Color,
    val dark: Boolean,
)

private val SeedheThemeDark = SeedheTheme(
    SeedheBlack, SeedheInk, SeedheRed, SeedheInk,
    SeedheRed.copy(alpha = 0.40f), SeedheRed.copy(alpha = 0.28f), Color.Black.copy(alpha = 0.60f), true,
)
private val SeedheThemeRed = SeedheTheme(
    SeedheRed, SeedheInk, SeedheBlack, SeedheInk,
    Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.50f), false,
)
private val SeedheThemePaper = SeedheTheme(
    SeedhePaper, SeedheBlack, SeedheRed, SeedheInk,
    SeedheRed.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.14f), Color(0xFF3A2E1C).copy(alpha = 0.30f), false,
)

private fun seedheThemeFor(index: Int): SeedheTheme = when (index % 10) {
    2, 7 -> SeedheThemeRed
    5 -> SeedheThemePaper
    else -> SeedheThemeDark
}

private val SeedheStyleOrder = arrayOf(
    SeedheStyle.BLOCKS, SeedheStyle.STACK, SeedheStyle.SCRIBBLE, SeedheStyle.OUTLINE, SeedheStyle.SLAM,
)

private fun seedheStyleFor(index: Int, wordCount: Int): SeedheStyle {
    if (wordCount <= 1) return SeedheStyle.SLAM
    if (wordCount <= 3) return if (index % 2 == 0) SeedheStyle.SLAM else SeedheStyle.STACK
    var s = SeedheStyleOrder[(index * 2 + index / 5) % SeedheStyleOrder.size]
    if (s == SeedheStyle.STACK && wordCount > 6) s = SeedheStyle.BLOCKS
    if (s == SeedheStyle.SLAM && wordCount > 5) s = SeedheStyle.SCRIBBLE
    return s
}

private fun seedheHash(seed: Int): Float {
    var x = seed * 0x9E3779B1.toInt() + 1013904223
    x = x xor (x ushr 15)
    x *= 0x2C1B3C6D
    x = x xor (x ushr 12)
    x *= 0x297A2D39
    x = x xor (x ushr 15)
    return (x and 0xFFFFFF) / 16777215f
}

private class SeedhePaints {
    val text = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
        typeface = SeedheFace
        textAlign = NativePaint.Align.CENTER
        letterSpacing = -0.01f
    }
    val small = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
        typeface = SeedheFace
        textAlign = NativePaint.Align.CENTER
        letterSpacing = 0.06f
    }
    val band = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
        typeface = SeedheFace
        textAlign = NativePaint.Align.LEFT
        letterSpacing = 0.02f
    }
    val shape = NativePaint(NativePaint.ANTI_ALIAS_FLAG)
    val rect = RectF()
}

private class SeedheWord(val text: String, val startMs: Long, val endMs: Long) {
    var cx = 0f
    var base = 0f
    var size = 1f
    var width = 0f
    var rot = 0f
}

private fun seedheWords(line: LyricsEntry, next: LyricsEntry?): List<SeedheWord> =
    lineWords(line, next)
        .filter { it.text.isNotBlank() }
        .map { SeedheWord(it.text.trim().uppercase(), it.startMs, it.endMs) }

private class SeedheSpec(
    val padX: Float,
    val padY: Float,
    val gapX: Float,
    val gapY: Float,
    val startSize: Float,
)

private class SeedheArrangeCache {
    var words: List<SeedheWord>? = null
    var style: SeedheStyle? = null
    var w = 0f
    var h = 0f
    var left = 0f
    var top = 0f
    var right = 0f
    var bottom = 0f
    var padXf = 0f

    fun ensure(
        words: List<SeedheWord>,
        style: SeedheStyle,
        w: Float,
        h: Float,
        paint: NativePaint,
        seed: Int,
    ) {
        if (this.words === words && this.style == style && this.w == w && this.h == h) return
        seedheArrange(this, words, style, w, h, paint, seed)
    }
}

private const val SeedheCap = 0.71f

private fun seedheArrange(
    cache: SeedheArrangeCache,
    words: List<SeedheWord>,
    style: SeedheStyle,
    w: Float,
    h: Float,
    paint: NativePaint,
    seed: Int,
) {
    cache.words = words
    cache.style = style
    cache.w = w
    cache.h = h
    val n = words.size
    if (n == 0) return
    paint.textSkewX = 0f
    paint.style = NativePaint.Style.FILL
    paint.textSize = 100f
    val w100 = FloatArray(n) { paint.measureText(words[it].text).coerceAtLeast(1f) }
    val cy = h * 0.47f

    if (style == SeedheStyle.STACK) {
        val maxW = w * 0.88f
        val sizeCap = h * 0.17f
        val sizes = FloatArray(n) { min(maxW * 100f / w100[it], sizeCap) }
        var capSum = 0f
        for (i in 0 until n) capSum += sizes[i] * SeedheCap
        val gap1 = 0.16f * (capSum / n)
        val total1 = capSum + (n - 1) * gap1
        val f = min(1f, (h * 0.5f) / total1)
        var y = cy - total1 * f / 2f
        var widest = 0f
        for (i in 0 until n) {
            val s = sizes[i] * f
            val wd = words[i]
            wd.size = s
            wd.width = w100[i] * s / 100f
            wd.cx = w / 2f
            wd.base = y + s * SeedheCap
            wd.rot = 0f
            widest = max(widest, wd.width)
            y = wd.base + gap1 * f
        }
        cache.left = w / 2f - widest / 2f
        cache.right = w / 2f + widest / 2f
        cache.top = cy - total1 * f / 2f
        cache.bottom = cy + total1 * f / 2f
        cache.padXf = 0f
        return
    }

    val spec = when (style) {
        SeedheStyle.SLAM -> SeedheSpec(0f, 0f, 0.20f, 0.18f, h * 0.24f)
        SeedheStyle.OUTLINE -> SeedheSpec(0f, 0f, 0.22f, 0.26f, h * 0.15f)
        SeedheStyle.SCRIBBLE -> SeedheSpec(0.02f, 0f, 0.24f, 0.34f, h * 0.12f)
        else -> SeedheSpec(0.24f, 0.20f, 0.16f, 0.18f, h * 0.11f)
    }
    val maxW = w * 0.90f
    val maxH = h * 0.46f
    var widestEm = 0f
    for (i in 0 until n) widestEm = max(widestEm, w100[i] / 100f + 2f * spec.padX)
    var s = min(spec.startSize, maxW / widestEm)
    val minS = h * 0.04f
    val rowEnd = IntArray(n)
    var rows = 0
    while (true) {
        rows = 0
        var cursor = 0f
        var count = 0
        for (i in 0 until n) {
            val eff = s * (w100[i] / 100f + 2f * spec.padX)
            val gap = if (count == 0) 0f else spec.gapX * s
            if (count > 0 && cursor + gap + eff > maxW) {
                rowEnd[rows++] = i
                cursor = eff
                count = 1
            } else {
                cursor += gap + eff
                count++
            }
        }
        rowEnd[rows++] = n
        val rowH = s * (SeedheCap + 2f * spec.padY)
        val total = rows * rowH + (rows - 1) * spec.gapY * s
        if (total <= maxH || s <= minS) break
        s *= 0.92f
    }
    val rowH = s * (SeedheCap + 2f * spec.padY)
    val total = rows * rowH + (rows - 1) * spec.gapY * s
    var rowTop = cy - total / 2f
    var idx = 0
    var minL = w
    var maxR = 0f
    for (r in 0 until rows) {
        val end = rowEnd[r]
        var rowW = 0f
        for (i in idx until end) rowW += s * (w100[i] / 100f + 2f * spec.padX)
        rowW += (end - idx - 1) * spec.gapX * s
        var x = (w - rowW) / 2f
        for (i in idx until end) {
            val eff = s * (w100[i] / 100f + 2f * spec.padX)
            val wd = words[i]
            wd.cx = x + eff / 2f
            wd.base = rowTop + s * (spec.padY + SeedheCap)
            wd.size = s
            wd.width = w100[i] * s / 100f
            wd.rot = if (style == SeedheStyle.BLOCKS) seedheHash(seed * 31 + i) * 6f - 3f else 0f
            x += eff + spec.gapX * s
        }
        minL = min(minL, (w - rowW) / 2f)
        maxR = max(maxR, (w + rowW) / 2f)
        rowTop += rowH + spec.gapY * s
        idx = end
    }
    cache.left = minL
    cache.right = maxR
    cache.top = cy - total / 2f
    cache.bottom = cy + total / 2f
    cache.padXf = spec.padX
}

private fun seedheDrawWord(
    nc: NativeCanvas,
    paints: SeedhePaints,
    word: SeedheWord,
    index: Int,
    style: SeedheStyle,
    theme: SeedheTheme,
    positionMs: Long,
    padXf: Float,
    ghost: Boolean,
    ghostColor: Int,
    ghostDx: Float,
) {
    val since = positionMs - word.startMs
    val started = since >= 0L
    if (ghost && !started) return
    val span = (word.endMs - word.startMs).coerceAtLeast(1L)
    val progress = (since.toFloat() / span).coerceIn(0f, 1f)
    val active = started && progress < 1f
    val popT = (since / 260f).coerceIn(0f, 1f)
    val pop = if (started) 1f + 0.34f * (1f - popT) * (1f - popT) else 1f
    val jolt = if (started) sin(positionMs / 38f) * 2.4f * (1f - popT) else 0f
    val s = word.size
    val capH = s * SeedheCap
    val half = word.width / 2f
    val p = paints.text
    p.textSize = s
    p.textSkewX = when (style) {
        SeedheStyle.SLAM -> -0.18f
        SeedheStyle.OUTLINE -> -0.22f
        SeedheStyle.SCRIBBLE -> -0.08f
        else -> 0f
    }
    nc.save()
    nc.translate(word.cx + ghostDx, word.base - capH / 2f)
    nc.rotate(word.rot + jolt)
    nc.scale(pop, pop)
    val ty = capH / 2f
    val wipeRight = -half + word.width * progress + (if (progress >= 1f) s * 0.3f else 0f)

    if (ghost) {
        p.style = NativePaint.Style.FILL
        p.color = ghostColor
        nc.drawText(word.text, 0f, ty, p)
    } else if (style == SeedheStyle.BLOCKS) {
        val padX = padXf * s
        val padY = 0.2f * s
        val boxW = word.width + 2f * padX
        val boxH = capH + 2f * padY
        val radius = s * 0.09f
        val sp = paints.shape
        paints.rect.set(-boxW / 2f, -boxH / 2f, boxW / 2f, boxH / 2f)
        if (started) {
            sp.style = NativePaint.Style.FILL
            sp.color = (if (active) theme.accent else theme.fg).toArgb()
            nc.drawRoundRect(paints.rect, radius, radius, sp)
            p.style = NativePaint.Style.FILL
            p.color = (if (active) theme.onAccent else theme.bg).toArgb()
        } else {
            sp.style = NativePaint.Style.STROKE
            sp.strokeWidth = max(1f, s * 0.03f)
            sp.color = theme.fg.copy(alpha = 0.30f).toArgb()
            nc.drawRoundRect(paints.rect, radius, radius, sp)
            p.style = NativePaint.Style.FILL
            p.color = theme.fg.copy(alpha = 0.38f).toArgb()
        }
        nc.drawText(word.text, 0f, ty, p)
    } else if (style == SeedheStyle.OUTLINE) {
        p.style = NativePaint.Style.STROKE
        p.strokeWidth = s * 0.035f
        p.strokeJoin = NativePaint.Join.ROUND
        p.color = theme.fg.copy(alpha = 0.60f).toArgb()
        nc.drawText(word.text, 0f, ty, p)
        if (started) {
            p.style = NativePaint.Style.FILL
            p.color = (if (active) theme.accent else theme.fg).toArgb()
            nc.save()
            nc.clipRect(-half - s * 0.2f, -capH / 2f - s * 0.3f, wipeRight, ty + s * 0.4f)
            nc.drawText(word.text, 0f, ty, p)
            nc.restore()
        }
    } else {
        p.style = NativePaint.Style.FILL
        if (started && style == SeedheStyle.SLAM) {
            val off = s * (0.03f + 0.05f * (1f - popT))
            p.color = theme.accent.copy(alpha = 0.95f).toArgb()
            nc.drawText(word.text, off, ty + off, p)
        }
        p.color = theme.fg.copy(alpha = if (theme.dark) 0.20f else 0.18f).toArgb()
        nc.drawText(word.text, 0f, ty, p)
        if (started) {
            val color = when {
                style == SeedheStyle.STACK -> if (index % 2 == 0) theme.fg else theme.accent
                style == SeedheStyle.SCRIBBLE && active -> theme.accent
                else -> theme.fg
            }
            p.color = color.toArgb()
            nc.save()
            nc.clipRect(-half - s * 0.2f, -capH / 2f - s * 0.3f, wipeRight, ty + s * 0.4f)
            nc.drawText(word.text, 0f, ty, p)
            nc.restore()
        }
    }
    nc.restore()
}

/** Hand-drawn marker loop that scribbles itself around the key word. */
private fun DrawScope.seedheScribble(
    word: SeedheWord,
    positionMs: Long,
    theme: SeedheTheme,
    path: Path,
    seed: Int,
) {
    val since = positionMs - word.startMs
    if (since < 0L) return
    val t = (since / 420f).coerceIn(0f, 1f)
    val steps = 70
    val visible = (t * steps).toInt()
    if (visible < 2) return
    val s = word.size
    val capH = s * SeedheCap
    val cx = word.cx
    val cy = word.base - capH / 2f
    val rx = word.width / 2f + s * 0.16f
    val ry = capH / 2f + s * 0.20f
    val phase = seedheHash(seed) * 6f
    path.reset()
    for (i in 0..visible) {
        val f = i / steps.toFloat()
        val a = -2.4f + f * SeedheTau * 1.45f
        val k = 1f + 0.07f * sin(i * 0.55f + phase) + 0.09f * f
        val x = cx + rx * cos(a) * k
        val y = cy + ry * sin(a) * k
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(
        path = path,
        color = theme.accent,
        style = Stroke(width = max(3f, s * 0.07f), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.seedheBackdrop(theme: SeedheTheme, clock: Float, impact: Float, u: Float) {
    val w = size.width
    val h = size.height
    drawRect(theme.bg)

    // slow-moving colour glow that flares on every new line
    val glowCenter = Offset(
        w * (0.5f + 0.38f * cos(clock * SeedheTau)),
        h * (0.30f + 0.12f * sin(clock * SeedheTau * 2f)),
    )
    val glowRadius = size.maxDimension * 0.62f
    val glow = theme.glow.copy(alpha = (theme.glow.alpha + 0.30f * impact).coerceAtMost(1f))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glow, Color.Transparent),
            center = glowCenter,
            radius = glowRadius,
        ),
        radius = glowRadius,
        center = glowCenter,
    )

    // comic halftone dots fading out from the bottom-left corner
    val spacing = 17f * u
    val cols = (w / spacing).toInt() + 2
    val rows = (h / spacing).toInt() + 2
    val ox = w * (0.05f + 0.04f * sin(clock * SeedheTau))
    val oy = h * 0.98f
    val reach = h * 0.85f
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val x = c * spacing + (if (r % 2 == 0) 0f else spacing / 2f)
            val y = r * spacing
            val k = 1f - hypot(x - ox, y - oy) / reach
            if (k <= 0.08f) continue
            drawCircle(theme.dots, radius = spacing * 0.46f * k, center = Offset(x, y))
        }
    }
}

/** Manga-style speed lines that flash out from the centre when a new line lands. */
private fun DrawScope.seedheBurst(theme: SeedheTheme, impact: Float, u: Float, seed: Int) {
    if (impact < 0.05f) return
    val cx = size.width / 2f
    val cy = size.height * 0.47f
    val maxR = hypot(size.width, size.height) / 2f
    val color = (if (theme.dark) theme.fg else theme.accent).copy(alpha = 0.22f * impact)
    val n = 26
    for (i in 0 until n) {
        val a = (i + seedheHash(seed * 53 + i) * 0.8f) / n * SeedheTau
        val r0 = maxR * (0.34f + 0.16f * (1f - impact) + seedheHash(seed + i * 7) * 0.10f)
        val r1 = r0 + maxR * (0.25f + seedheHash(seed + i * 11) * 0.30f)
        drawLine(
            color = color,
            start = Offset(cx + cos(a) * r0, cy + sin(a) * r0),
            end = Offset(cx + cos(a) * r1, cy + sin(a) * r1),
            strokeWidth = (1f + seedheHash(seed + i * 3) * 2.5f) * u,
        )
    }
}

/** Giant outlined key word running up the left edge, barely there. */
private fun DrawScope.seedheGhostWord(paints: SeedhePaints, word: String, theme: SeedheTheme, u: Float) {
    val p = paints.text
    p.textSkewX = 0f
    p.style = NativePaint.Style.FILL
    p.textSize = 100f
    val w100 = p.measureText(word).coerceAtLeast(1f)
    val s = min(size.height * 0.80f * 100f / w100, size.width * 0.55f / SeedheCap)
    p.textSize = s
    p.style = NativePaint.Style.STROKE
    p.strokeWidth = 1.6f * u
    p.strokeJoin = NativePaint.Join.ROUND
    p.color = theme.fg.copy(alpha = if (theme.dark) 0.10f else 0.12f).toArgb()
    val nc = drawContext.canvas.nativeCanvas
    nc.save()
    nc.translate(size.width * 0.03f + s * SeedheCap, size.height * 0.5f)
    nc.rotate(-90f)
    nc.drawText(word, 0f, 0f, p)
    nc.restore()
    p.style = NativePaint.Style.FILL
}

/** Caution-tape style ticker band with scrolling text. */
private fun DrawScope.seedheBand(
    paints: SeedhePaints,
    phrase: String,
    centerY: Float,
    heightPx: Float,
    angle: Float,
    bg: Color,
    fg: Color,
    clock: Float,
    direction: Float,
) {
    val w = size.width
    rotate(angle, Offset(w / 2f, centerY)) {
        drawRect(
            color = bg,
            topLeft = Offset(-w * 0.2f, centerY - heightPx / 2f),
            size = Size(w * 1.4f, heightPx),
        )
        val p = paints.band
        p.style = NativePaint.Style.FILL
        p.textSize = heightPx * 0.62f
        p.color = fg.toArgb()
        val period = p.measureText(phrase)
        if (period > 1f) {
            val raw = clock * 3f * period * direction
            val offset = ((raw % period) + period) % period
            val baseline = centerY + p.textSize * 0.355f
            val nc = drawContext.canvas.nativeCanvas
            var x = offset - period
            while (x < w * 1.2f) {
                nc.drawText(phrase, x, baseline, p)
                x += period
            }
        }
    }
}

private fun DrawScope.seedheSparkle(path: Path, cx: Float, cy: Float, r: Float, color: Color) {
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

private val SeedheSparkAnchors = floatArrayOf(
    0.12f, 0.20f, 0.86f, 0.27f, 0.92f, 0.70f, 0.09f, 0.74f, 0.78f, 0.15f, 0.24f, 0.80f,
)

private fun DrawScope.seedheSparkles(
    path: Path,
    theme: SeedheTheme,
    clock: Float,
    u: Float,
    seed: Int,
) {
    for (i in 0 until SeedheSparkAnchors.size / 2) {
        val jx = (seedheHash(seed * 13 + i) - 0.5f) * 0.08f
        val jy = (seedheHash(seed * 17 + i) - 0.5f) * 0.06f
        val twinkle = 0.65f + 0.35f * sin(clock * SeedheTau * 6f + i * 1.7f)
        val r = (7f + seedheHash(seed * 7 + i) * 10f) * u * twinkle
        val color = when (i % 3) {
            0 -> theme.accent
            1 -> SeedheTape
            else -> theme.fg
        }
        seedheSparkle(
            path,
            size.width * (SeedheSparkAnchors[i * 2] + jx),
            size.height * (SeedheSparkAnchors[i * 2 + 1] + jy),
            r,
            color.copy(alpha = 0.85f),
        )
    }
}

private fun DrawScope.seedheTape(center: Offset, length: Float, thick: Float, angle: Float) {
    rotate(angle, center) {
        val topLeft = Offset(center.x - length / 2f, center.y - thick / 2f)
        drawRect(SeedheTape.copy(alpha = 0.92f), topLeft = topLeft, size = Size(length, thick))
        drawRect(Color.Black.copy(alpha = 0.10f), topLeft = topLeft, size = Size(length, thick * 0.18f))
    }
}

/** Film grain, dust scratches and a dark vignette on top of everything. */
private fun DrawScope.seedheOverlay(theme: SeedheTheme, frame: Int, impact: Float, u: Float) {
    val w = size.width
    val h = size.height
    val grain = if (theme.dark) SeedheInk else Color.Black
    for (i in 0 until 70) {
        val a = seedheHash(frame * 97 + i * 13)
        val b = seedheHash(frame * 57 + i * 29 + 7)
        val c = seedheHash(frame * 31 + i * 5 + 3)
        drawCircle(
            color = grain.copy(alpha = 0.05f + c * 0.12f),
            radius = (0.5f + c * 1.1f) * u,
            center = Offset(a * w, b * h),
        )
    }
    if (frame % 4 == 0) {
        val x = seedheHash(frame) * w
        drawLine(grain.copy(alpha = 0.10f), Offset(x, 0f), Offset(x, h), strokeWidth = 0.6f * u)
    }
    drawRect(
        brush = Brush.radialGradient(
            0.55f to Color.Transparent,
            1f to theme.vignette,
            center = Offset(w / 2f, h / 2f),
            radius = hypot(w, h) / 2f,
        ),
    )
    val flash = impact * impact * impact
    if (flash > 0.01f) {
        drawRect((if (theme.dark) theme.fg else theme.accent).copy(alpha = 0.30f * flash))
    }
}

private fun DrawScope.seedheNextLine(paints: SeedhePaints, text: String, theme: SeedheTheme, u: Float) {
    val p = paints.small
    p.style = NativePaint.Style.FILL
    p.textSkewX = 0f
    p.textSize = 13f * u
    val maxW = size.width * 0.84f
    val m = p.measureText(text)
    if (m > maxW) p.textSize = p.textSize * maxW / m
    p.color = theme.fg.copy(alpha = 0.50f).toArgb()
    drawContext.canvas.nativeCanvas.drawText(text, size.width / 2f, size.height * 0.835f, p)
}

/**
 * Seedhe Maut lyric screen: a street-poster "lyric edit". Every line gets its own look
 * (slam, stamped labels, justified stack, marker scribble, hollow outline), words land
 * in time with the vocals, and the whole frame punches on each new line.
 */
@Composable
fun SeedheMautLyricsCanvas(
    lines: List<LyricsEntry>,
    currentIndex: Int,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val activeLine = lines.getOrNull(currentIndex)
    val nextLine = lines.getOrNull(currentIndex + 1)
    val gapAfterCurrent = nextLine?.time?.minus(positionMs) ?: Long.MAX_VALUE
    val gapBeforeFirst = lines.firstOrNull()?.time?.minus(positionMs) ?: Long.MAX_VALUE
    val instrumental = lines.isNotEmpty() &&
        (currentIndex < 0 && gapBeforeFirst > 2_500L || currentIndex >= 0 && gapAfterCurrent > 2_500L)

    val words = remember(activeLine, nextLine) {
        if (activeLine != null) seedheWords(activeLine, nextLine) else emptyList()
    }
    val showTitle = instrumental || words.isEmpty()
    val index = currentIndex.coerceAtLeast(0)
    val style = remember(index, words.size) { seedheStyleFor(index, words.size) }
    val lineTheme = remember(index) { seedheThemeFor(index) }
    val theme = if (showTitle) SeedheThemeDark else lineTheme
    val keyWord = remember(words) {
        var best = 0
        for (i in words.indices) if (words[i].text.length >= words[best].text.length) best = i
        best
    }
    val shortLine = words.size <= 3

    val transition = rememberInfiniteTransition(label = "seedheMautBackdrop")
    val clock = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "seedheMautClock",
    )
    val breathe = transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "seedheMautBreathe",
    )
    val impact = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(currentIndex) {
        impact.snapTo(1f)
        launch { impact.animateTo(0f, tween(520, easing = LinearOutSlowInEasing)) }
        if (shortLine) {
            shake.snapTo(1f)
            shake.animateTo(0f, tween(260))
        }
    }

    val paints = remember { SeedhePaints() }
    val cache = remember { SeedheArrangeCache() }
    val titleCache = remember { SeedheArrangeCache() }
    val sparklePath = remember { Path() }
    val scribblePath = remember { Path() }
    val titleWords = remember { listOf(SeedheWord("SEEDHE", 0L, 1L), SeedheWord("MAUT", 0L, 1L)) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer {
                val sc = 1.025f + 0.05f * impact.value
                scaleX = sc
                scaleY = sc
                translationX = sin(positionMs / 17f) * 7f * shake.value
                translationY = cos(positionMs / 23f) * 4f * shake.value
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

            seedheBackdrop(theme, clk, imp, u)
            if (!showTitle) {
                cache.ensure(words, style, w, h, paints.text, index)
                seedheBurst(theme, imp, u, index)
                seedheGhostWord(paints, words[keyWord].text, theme, u)
            }

            seedheBand(
                paints, "SEEDHE MAUT  ×  DELHI  ×  ", h * 0.085f, 44f * u, 2.5f,
                theme.accent, theme.onAccent, clk, -1f,
            )
            seedheBand(
                paints, "ENCORE ABJ  ×  CALM  ×  ", h * 0.915f, 44f * u, -3f,
                SeedheTape, SeedheBlack, clk, 1f,
            )
            seedheSparkles(sparklePath, theme, clk, u, index)

            if (showTitle) {
                titleCache.ensure(titleWords, SeedheStyle.STACK, w, h, paints.text, 0)
                val ctr = Offset(w / 2f, h * 0.47f)
                rotate(-4f, ctr) {
                    scale(breathe.value, ctr) {
                        titleWords.forEachIndexed { i, word ->
                            seedheDrawWord(
                                nc, paints, word, i, SeedheStyle.STACK, theme,
                                10_000L, 0f, false, 0, 0f,
                            )
                        }
                    }
                }
            } else {
                if (style == SeedheStyle.BLOCKS || style == SeedheStyle.SCRIBBLE) {
                    val slide = imp * 40f * u
                    seedheTape(Offset(cache.left + 0.04f * w - slide, cache.top - 0.01f * h), 70f * u, 15f * u, -35f)
                    seedheTape(Offset(cache.right - 0.04f * w + slide, cache.bottom + 0.01f * h), 70f * u, 15f * u, -35f)
                }
                if (imp > 0.04f && style != SeedheStyle.BLOCKS) {
                    val off = imp * 9f * u
                    val ghostA = (if (theme.accent == SeedheBlack) SeedheBlack else SeedheRed).copy(alpha = 0.60f).toArgb()
                    val ghostB = SeedheCyan.copy(alpha = 0.55f).toArgb()
                    words.forEachIndexed { i, word ->
                        seedheDrawWord(nc, paints, word, i, style, theme, positionMs, cache.padXf, true, ghostA, -off)
                    }
                    words.forEachIndexed { i, word ->
                        seedheDrawWord(nc, paints, word, i, style, theme, positionMs, cache.padXf, true, ghostB, off)
                    }
                }
                words.forEachIndexed { i, word ->
                    seedheDrawWord(nc, paints, word, i, style, theme, positionMs, cache.padXf, false, 0, 0f)
                }
                if (style == SeedheStyle.SCRIBBLE) {
                    seedheScribble(words[keyWord], positionMs, theme, scribblePath, index)
                }
                val upcoming = nextLine?.text?.trim().orEmpty()
                if (upcoming.isNotEmpty()) seedheNextLine(paints, upcoming.uppercase(), theme, u)
            }

            seedheOverlay(theme, frame, imp, u)
        }
    }
}

@Composable
fun SeedheMautProfileSneakPeek(
    scrollProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "seedheMautProfilePeek")
    val scanPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_600)),
        label = "seedheMautProfileScan",
    )
    Box(
        modifier = modifier.graphicsLayer {
            val progress = scrollProgress().coerceIn(0f, 1f)
            alpha = (1f - progress * 0.72f).coerceIn(0.18f, 1f)
            translationY = -progress * size.height * 0.08f
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val progress = scrollProgress().coerceIn(0f, 1f)
            val scanY = size.height * (0.25f + scanPhase * 0.55f)
            drawLine(
                color = SeedheCyan.copy(alpha = 0.28f * (1f - progress * 0.4f)),
                start = androidx.compose.ui.geometry.Offset(size.width * 0.08f, scanY),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.92f, scanY),
                strokeWidth = 1.5f,
            )
            drawCircle(
                color = SeedheCrimson.copy(alpha = 0.36f),
                radius = size.minDimension * (0.12f + progress * 0.05f),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.22f),
                style = Stroke(width = 1.5f),
            )
            drawLine(
                color = SeedheInk.copy(alpha = 0.26f),
                start = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.86f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.86f),
                strokeWidth = 1f,
            )
        }
        val peekFrames = listOf(
            "THE DUO / UNFILTERED",
            "DELHI AFTER DARK",
            "NO SKIPS / ALL CAPS",
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 22.dp)
                .width(190.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = "SCROLL THE ARCHIVE",
                color = SeedheInk.copy(alpha = 0.66f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            peekFrames.forEachIndexed { index, label ->
                val framePosition = scrollProgress().coerceIn(0f, 0.999f) * peekFrames.size
                val frameDistance = (framePosition - index).coerceIn(-1f, 1f)
                Text(
                    text = label,
                    color = if (abs(frameDistance) < 0.5f) SeedheCyan else SeedheInk.copy(alpha = 0.74f),
                    fontSize = if (abs(frameDistance) < 0.5f) 15.sp else 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = if (abs(frameDistance) < 0.5f) (-0.4).sp else 0.7.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .graphicsLayer {
                            alpha = (1f - abs(frameDistance) * 0.62f).coerceIn(0.18f, 1f)
                            translationX = frameDistance * 34f
                            rotationX = frameDistance * 18f
                            scaleX = 1f - abs(frameDistance) * 0.12f
                            scaleY = 1f - abs(frameDistance) * 0.12f
                        },
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 22.dp, bottom = 22.dp)
                .graphicsLayer {
                    val progress = scrollProgress().coerceIn(0f, 1f)
                    alpha = (1f - progress * 1.5f).coerceIn(0f, 1f)
                    translationX = -progress * 52f
                },
        ) {
            Text(
                text = "SNEAK PEEK / 01",
                color = SeedheCyan.copy(alpha = 0.88f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp,
            )
            Spacer(Modifier.padding(vertical = 3.dp))
            Row {
                ProfilePeekTag("DELHI / 2017")
                Spacer(Modifier.width(6.dp))
                ProfilePeekTag("DUO MODE")
                Spacer(Modifier.width(6.dp))
                ProfilePeekTag("AFTER DARK")
            }
        }
    }
}

@Composable
private fun ProfilePeekTag(label: String) {
    Text(
        text = label,
        color = SeedheInk.copy(alpha = 0.9f),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier
            .background(SeedheBlack.copy(alpha = 0.64f), RoundedCornerShape(4.dp))
            .border(1.dp, SeedheInk.copy(alpha = 0.24f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp),
    )
}

private data class LineWord(val text: String, val startMs: Long, val endMs: Long)

private fun lineWords(line: LyricsEntry, nextLine: LyricsEntry?): List<LineWord> {
    val timedWords = line.words.orEmpty()
    if (timedWords.isNotEmpty()) {
        return timedWords.map {
            LineWord(
                text = it.text,
                startMs = (it.startTime * 1_000.0).toLong(),
                endMs = (it.endTime * 1_000.0).toLong().coerceAtLeast((it.startTime * 1_000.0).toLong() + 1L),
            )
        }
    }
    val tokens = line.text.split(Regex("(?<=\\s)|(?=\\s)")).filter(String::isNotEmpty)
    val duration = ((nextLine?.time ?: line.time + 4_000L) - line.time).coerceAtLeast(1L)
    val totalWeight = tokens.sumOf { it.length.coerceAtLeast(1) }.coerceAtLeast(1)
    var cursor = line.time
    return tokens.map { token ->
        val end = cursor + (duration * token.length.coerceAtLeast(1) / totalWeight).coerceAtLeast(1L)
        LineWord(token, cursor, end).also { cursor = end }
    }
}
