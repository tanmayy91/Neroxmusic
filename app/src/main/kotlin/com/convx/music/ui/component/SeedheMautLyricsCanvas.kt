package com.convx.music.ui.component

import android.graphics.Canvas as NativeCanvas
import android.graphics.LinearGradient
import android.graphics.Paint as NativePaint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.music.lyrics.LyricsEntry
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Palette: dusky, low-contrast pastels. Every theme is a vertical gradient + two glow tints.
// ---------------------------------------------------------------------------------------------

private val SeedheInk = Color(0xFFF7EEF2)
private val SoftRose = Color(0xFFF4A6BC)
private val SoftLavender = Color(0xFF9C8CF0)

private const val SeedheTau = 6.2831855f
private const val SeedhePi = 3.1415927f
private const val SeedheCap = 0.71f

private val SeedheFaceMedium: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
private val SeedheFaceLight: Typeface by lazy { Typeface.create("sans-serif-light", Typeface.NORMAL) }

private class SeedheTheme(
    val top: Color,
    val bottom: Color,
    val fg: Color,
    val accent: Color,
    val accent2: Color,
    val dark: Boolean,
)

private val SoftDusk = SeedheTheme(
    top = Color(0xFF1D1A33), bottom = Color(0xFF3A2A52),
    fg = SeedheInk, accent = SoftRose, accent2 = SoftLavender, dark = true,
)
private val SoftLagoon = SeedheTheme(
    top = Color(0xFF14262E), bottom = Color(0xFF2A4A55),
    fg = Color(0xFFEEF7F5), accent = Color(0xFF8FD8CB), accent2 = Color(0xFF7FB5F0), dark = true,
)
private val SoftBlush = SeedheTheme(
    top = Color(0xFFFBE4E6), bottom = Color(0xFFEFCDE0),
    fg = Color(0xFF4B3556), accent = Color(0xFFD4688F), accent2 = Color(0xFFB59AEB), dark = false,
)
private val SoftPeach = SeedheTheme(
    top = Color(0xFFFCEBDA), bottom = Color(0xFFF5CDBE),
    fg = Color(0xFF5A3B36), accent = Color(0xFFE07A5F), accent2 = Color(0xFFF2B866), dark = false,
)

private val SeedheThemes = arrayOf(SoftDusk, SoftLagoon, SoftBlush, SoftPeach)

/** Theme changes slowly (every few lines) and cross-fades, so the mood drifts instead of flipping. */
private fun seedheThemeFor(index: Int): SeedheTheme = SeedheThemes[(index / 6) % SeedheThemes.size]

private fun seedheBlend(a: SeedheTheme, b: SeedheTheme, t: Float): SeedheTheme = when {
    t >= 1f -> b
    t <= 0f -> a
    else -> SeedheTheme(
        top = lerp(a.top, b.top, t),
        bottom = lerp(a.bottom, b.bottom, t),
        fg = lerp(a.fg, b.fg, t),
        accent = lerp(a.accent, b.accent, t),
        accent2 = lerp(a.accent2, b.accent2, t),
        dark = if (t < 0.5f) a.dark else b.dark,
    )
}

private fun Color.mul(m: Float): Color = copy(alpha = (alpha * m).coerceIn(0f, 1f))

private fun seedheHash(seed: Int): Float {
    var x = seed * 0x9E3779B1.toInt() + 1013904223
    x = x xor (x ushr 15)
    x *= 0x2C1B3C6D
    x = x xor (x ushr 12)
    x *= 0x297A2D39
    x = x xor (x ushr 15)
    return (x and 0xFFFFFF) / 16777215f
}

// ---------------------------------------------------------------------------------------------
// Model + layout
// ---------------------------------------------------------------------------------------------

private class SeedhePaints {
    val text = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
        typeface = SeedheFaceMedium
        textAlign = NativePaint.Align.CENTER
        letterSpacing = 0.005f
    }
    val small = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
        typeface = SeedheFaceLight
        textAlign = NativePaint.Align.CENTER
        letterSpacing = 0.22f
    }
    val note = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
        typeface = SeedheFaceLight
        textAlign = NativePaint.Align.CENTER
        letterSpacing = 0.02f
    }
}

private class SeedheWord(val text: String, val startMs: Long, val endMs: Long) {
    var cx = 0f
    var base = 0f
    var size = 1f
    var width = 0f
}

private fun seedheWords(line: LyricsEntry, next: LyricsEntry?): List<SeedheWord> =
    lineWords(line, next)
        .filter { it.text.isNotBlank() }
        .map { SeedheWord(it.text.trim(), it.startMs, it.endMs) }

/** Holds the current / previous line (for the cross-fade) and the theme we are blending from. */
private class SeedheTrack(initial: SeedheTheme) {
    var from: SeedheTheme = initial
    var to: SeedheTheme = initial
    var current: List<SeedheWord> = emptyList()
    var previous: List<SeedheWord> = emptyList()
    var wasTitle = false
}

private class SeedheArrangeCache {
    var words: List<SeedheWord>? = null
    var w = 0f
    var h = 0f

    fun ensure(words: List<SeedheWord>, w: Float, h: Float, paint: NativePaint) {
        if (this.words === words && this.w == w && this.h == h) return
        seedheArrange(this, words, w, h, paint)
    }
}

/** One calm style for every line: centred, evenly spaced, wrapped into rows. */
private fun seedheArrange(
    cache: SeedheArrangeCache,
    words: List<SeedheWord>,
    w: Float,
    h: Float,
    paint: NativePaint,
) {
    cache.words = words
    cache.w = w
    cache.h = h
    val n = words.size
    if (n == 0) return
    paint.textSkewX = 0f
    paint.style = NativePaint.Style.FILL
    paint.textSize = 100f
    val em = FloatArray(n) { (paint.measureText(words[it].text) / 100f).coerceAtLeast(0.01f) }
    val spaceEm = 0.30f
    val maxW = w * 0.84f
    val maxH = h * 0.50f
    var widest = 0f
    for (e in em) widest = max(widest, e)
    val start = if (n <= 3) h * 0.15f else h * 0.092f
    var s = min(start, maxW / widest)
    val minS = h * 0.035f
    val rowEnd = IntArray(n)
    var rows = 0
    while (true) {
        rows = 0
        var cursor = 0f
        var count = 0
        for (i in 0 until n) {
            val eff = s * em[i]
            val gap = if (count == 0) 0f else spaceEm * s
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
        if (rows * s * 1.3f <= maxH || s <= minS) break
        s *= 0.92f
    }
    val rowH = s * 1.3f
    val total = rows * rowH
    var rowTop = h * 0.46f - total / 2f
    var idx = 0
    for (r in 0 until rows) {
        val end = rowEnd[r]
        var rowW = 0f
        for (i in idx until end) rowW += s * em[i]
        rowW += (end - idx - 1) * spaceEm * s
        var x = (w - rowW) / 2f
        for (i in idx until end) {
            val wd = words[i]
            wd.size = s
            wd.width = s * em[i]
            wd.cx = x + wd.width / 2f
            wd.base = rowTop + rowH / 2f + s * 0.34f
            x += wd.width + spaceEm * s
        }
        rowTop += rowH
        idx = end
    }
}

// ---------------------------------------------------------------------------------------------
// Drawing
// ---------------------------------------------------------------------------------------------

/**
 * Words rest dim, then a feathered wipe fills them with a warm tint as they are sung. When the word
 * ends it settles into the normal text colour and stays very slightly lifted.
 */
private fun seedheDrawWord(
    nc: NativeCanvas,
    paints: SeedhePaints,
    word: SeedheWord,
    theme: SeedheTheme,
    positionMs: Long,
    alphaMul: Float,
    dy: Float,
) {
    val since = positionMs - word.startMs
    val started = since >= 0L
    val span = (word.endMs - word.startMs).coerceAtLeast(1L)
    val progress = (since.toFloat() / span).coerceIn(0f, 1f)
    val active = started && progress < 1f
    val t = (since / 520f).coerceIn(0f, 1f)
    val ease = 1f - (1f - t) * (1f - t) * (1f - t)
    val s = word.size
    val half = word.width / 2f
    val p = paints.text
    p.style = NativePaint.Style.FILL
    p.textSkewX = 0f
    p.textSize = s

    val dim = theme.fg.mul(0.30f * alphaMul)
    val warm = lerp(theme.fg, theme.accent, 0.45f).mul(alphaMul)
    val lit = theme.fg.mul(0.84f * alphaMul)

    nc.save()
    nc.translate(word.cx, word.base + dy - (if (started) s * 0.05f * ease else 0f))
    val sc = if (started) 1f + 0.05f * (1f - ease) else 1f
    nc.scale(sc, sc)

    when {
        !started -> {
            p.color = dim.toArgb()
            nc.drawText(word.text, 0f, 0f, p)
        }
        active -> {
            val feather = 0.22f
            val e1 = (progress * (1f + feather)).coerceIn(0f, 1f)
            val e0 = (progress * (1f + feather) - feather).coerceIn(0f, 1f)
            p.color = 0xFFFFFFFF.toInt()
            p.shader = LinearGradient(
                -half, 0f, half, 0f,
                intArrayOf(warm.toArgb(), warm.toArgb(), dim.toArgb(), dim.toArgb()),
                floatArrayOf(0f, e0, e1, 1f),
                Shader.TileMode.CLAMP,
            )
            nc.drawText(word.text, 0f, 0f, p)
            p.shader = null
        }
        else -> {
            val settle = ((positionMs - word.endMs) / 400f).coerceIn(0f, 1f)
            p.color = lerp(warm, lit, settle).toArgb()
            nc.drawText(word.text, 0f, 0f, p)
        }
    }
    nc.restore()
}

/** Soft spotlight that follows the word being sung. */
private fun DrawScope.seedheHalo(
    word: SeedheWord,
    positionMs: Long,
    theme: SeedheTheme,
    alphaMul: Float,
    dy: Float,
) {
    val since = positionMs - word.startMs
    if (since < 0L) return
    val span = (word.endMs - word.startMs).coerceAtLeast(1L)
    val progress = since.toFloat() / span
    if (progress >= 1f) return
    val a = sin(progress * SeedhePi) * 0.30f * alphaMul
    if (a < 0.01f) return
    val r = word.size * 1.1f + word.width * 0.55f
    val c = Offset(word.cx, word.base + dy - word.size * 0.28f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(theme.accent.copy(alpha = a), Color.Transparent),
            center = c,
            radius = r,
        ),
        radius = r,
        center = c,
    )
}

private fun DrawScope.seedheBackdrop(theme: SeedheTheme, clock: Float, glow: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(theme.top, theme.bottom)))

    val md = size.maxDimension
    val c1 = Offset(
        w * (0.28f + 0.14f * cos(clock * SeedheTau)),
        h * (0.28f + 0.07f * sin(clock * SeedheTau)),
    )
    val r1 = md * 0.60f
    val a1 = (if (theme.dark) 0.22f else 0.28f) + 0.10f * glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(theme.accent.copy(alpha = a1.coerceAtMost(1f)), Color.Transparent),
            center = c1,
            radius = r1,
        ),
        radius = r1,
        center = c1,
    )

    val c2 = Offset(
        w * (0.76f + 0.12f * sin(clock * SeedheTau)),
        h * (0.74f + 0.06f * cos(clock * SeedheTau)),
    )
    val r2 = md * 0.55f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(theme.accent2.copy(alpha = if (theme.dark) 0.18f else 0.22f), Color.Transparent),
            center = c2,
            radius = r2,
        ),
        radius = r2,
        center = c2,
    )
}

/** Slow-rising, softly twinkling circles. Loops seamlessly with the 24s clock. */
private fun DrawScope.seedheBokeh(theme: SeedheTheme, clock: Float, u: Float) {
    val w = size.width
    val h = size.height
    for (i in 0 until 14) {
        val bx = seedheHash(i * 3 + 1)
        val by = seedheHash(i * 3 + 2)
        val cyc = 1 + (i % 2)
        val r = (5f + seedheHash(i * 3 + 3) * 20f) * u
        val f = by - clock * cyc
        val frac = f - floor(f)
        val x = w * bx + sin(clock * SeedheTau * cyc + i) * 14f * u
        val y = frac * (h + 2f * r) - r
        val fade = sin(frac * SeedhePi)
        val base = (0.05f + 0.08f * seedheHash(i * 5 + 9)) * (if (theme.dark) 1f else 1.4f)
        val color = when (i % 3) {
            0 -> theme.accent
            1 -> theme.fg
            else -> theme.accent2
        }
        drawCircle(color.copy(alpha = (base * fade).coerceIn(0f, 1f)), radius = r, center = Offset(x, y))
    }
}

private fun DrawScope.seedheVignette(theme: SeedheTheme) {
    val w = size.width
    val h = size.height
    drawRect(
        brush = Brush.radialGradient(
            0.60f to Color.Transparent,
            1f to Color.Black.copy(alpha = if (theme.dark) 0.30f else 0.07f),
            center = Offset(w / 2f, h / 2f),
            radius = hypot(w, h) / 2f,
        ),
    )
}

private fun DrawScope.seedheLabel(paints: SeedhePaints, theme: SeedheTheme, u: Float) {
    val p = paints.small
    p.style = NativePaint.Style.FILL
    p.textSize = 11f * u
    p.color = theme.fg.mul(0.42f).toArgb()
    drawContext.canvas.nativeCanvas.drawText("SEEDHE MAUT", size.width / 2f, size.height * 0.085f, p)
}

private fun DrawScope.seedheNextLine(
    paints: SeedhePaints,
    text: String,
    theme: SeedheTheme,
    u: Float,
    alphaMul: Float,
) {
    val p = paints.note
    p.style = NativePaint.Style.FILL
    p.textSize = 14f * u
    val maxW = size.width * 0.80f
    val m = p.measureText(text)
    if (m > maxW) p.textSize = p.textSize * maxW / m
    p.color = theme.fg.mul(0.38f * alphaMul).toArgb()
    drawContext.canvas.nativeCanvas.drawText(text, size.width / 2f, size.height * 0.82f, p)
}

/** Instrumental card: soft title plus three breathing dots. */
private fun DrawScope.seedheTitle(
    paints: SeedhePaints,
    theme: SeedheTheme,
    breathe: Float,
    pulse: Float,
    alphaMul: Float,
    u: Float,
) {
    val nc = drawContext.canvas.nativeCanvas
    val p = paints.text
    p.style = NativePaint.Style.FILL
    p.textSkewX = 0f
    p.textSize = 100f
    val label = "Seedhe Maut"
    val w100 = p.measureText(label).coerceAtLeast(1f)
    val s = min(size.width * 0.80f * 100f / w100, size.height * 0.13f)
    p.textSize = s
    p.color = theme.fg.mul((0.62f + 0.30f * breathe) * alphaMul).toArgb()
    val baseline = size.height * 0.46f + s * 0.34f
    nc.drawText(label, size.width / 2f, baseline, p)
    for (i in 0 until 3) {
        val a = 0.25f + 0.55f * (0.5f + 0.5f * sin(pulse * SeedheTau - i * 0.9f))
        drawCircle(
            color = theme.accent.copy(alpha = (a * alphaMul).coerceIn(0f, 1f)),
            radius = 4f * u,
            center = Offset(size.width / 2f + (i - 1) * 18f * u, baseline + 34f * u),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Public composables
// ---------------------------------------------------------------------------------------------

/**
 * Soft lyric screen: a slow pastel gradient with drifting glows and bokeh, lyrics that fade in with a
 * gentle rise, a feathered karaoke wipe per word, and cross-fades between lines and moods.
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
    val words = remember(activeLine, nextLine) {
        if (activeLine != null) seedheWords(activeLine, nextLine) else emptyList()
    }

    // Instrumental = the current line has been fully sung and the next one is still far away.
    val activeEnd = words.lastOrNull()?.endMs ?: activeLine?.time ?: 0L
    val gapAfterCurrent = nextLine?.time?.minus(positionMs) ?: Long.MAX_VALUE
    val gapBeforeFirst = lines.firstOrNull()?.time?.minus(positionMs) ?: Long.MAX_VALUE
    val instrumental = lines.isNotEmpty() && (
        (currentIndex < 0 && gapBeforeFirst > 2_500L) ||
            (currentIndex >= 0 && positionMs >= activeEnd && gapAfterCurrent > 2_500L)
        )
    val showTitle = instrumental || words.isEmpty()
    val index = currentIndex.coerceAtLeast(0)
    val targetTheme = if (showTitle) SoftDusk else seedheThemeFor(index)

    val track = remember { SeedheTrack(targetTheme) }
    if (track.to !== targetTheme) {
        track.from = track.to
        track.to = targetTheme
    }
    if (showTitle) {
        track.previous = emptyList()
    } else if (track.current !== words) {
        track.previous = if (track.wasTitle) emptyList() else track.current
        track.current = words
    }
    track.wasTitle = showTitle

    val transition = rememberInfiniteTransition(label = "seedheSoft")
    val clock = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "seedheSoftClock",
    )
    val breathe = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "seedheSoftBreathe",
    )
    val pulse = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing)),
        label = "seedheSoftPulse",
    )

    val enter = remember { Animatable(1f) }
    val themeMix = remember { Animatable(1f) }
    val titleFade = remember { Animatable(0f) }
    LaunchedEffect(currentIndex) {
        enter.snapTo(0f)
        enter.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(targetTheme) {
        themeMix.snapTo(0f)
        themeMix.animateTo(1f, tween(1_600, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(showTitle) {
        titleFade.animateTo(if (showTitle) 1f else 0f, tween(700, easing = FastOutSlowInEasing))
    }

    val paints = remember { SeedhePaints() }
    val cache = remember { SeedheArrangeCache() }
    val prevCache = remember { SeedheArrangeCache() }

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val u = size.minDimension / 360f
        val e = enter.value
        val fade = titleFade.value
        val theme = seedheBlend(track.from, track.to, themeMix.value)
        val nc = drawContext.canvas.nativeCanvas

        seedheBackdrop(theme, clock.value, 1f - e)
        seedheBokeh(theme, clock.value, u)
        seedheLabel(paints, theme, u)

        if (fade > 0.01f) seedheTitle(paints, theme, breathe.value, pulse.value, fade, u)

        val lineAlpha = 1f - fade
        if (lineAlpha > 0.01f) {
            val prev = track.previous
            if (prev.isNotEmpty()) {
                val pa = (1f - e * 1.5f).coerceIn(0f, 1f) * lineAlpha
                if (pa > 0.01f) {
                    prevCache.ensure(prev, w, h, paints.text)
                    val dy = -e * 22f * u
                    for (word in prev) seedheDrawWord(nc, paints, word, theme, positionMs, pa, dy)
                }
            }

            val cur = track.current
            if (cur.isNotEmpty()) {
                cache.ensure(cur, w, h, paints.text)
                val ca = (e * 1.8f).coerceIn(0f, 1f) * lineAlpha
                val dy = (1f - e) * 20f * u
                for (word in cur) seedheHalo(word, positionMs, theme, ca, dy)
                for (word in cur) seedheDrawWord(nc, paints, word, theme, positionMs, ca, dy)
                val upcoming = nextLine?.text?.trim().orEmpty()
                if (upcoming.isNotEmpty()) seedheNextLine(paints, upcoming, theme, u, e * lineAlpha)
            }
        }

        seedheVignette(theme)
    }
}

@Composable
fun SeedheMautProfileSneakPeek(
    scrollProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "seedheMautProfilePeek")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "seedheMautProfileDrift",
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
            val c1 = Offset(size.width * (0.78f + 0.05f * drift), size.height * (0.26f + 0.06f * drift))
            val r1 = size.minDimension * (0.42f + progress * 0.08f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SoftRose.copy(alpha = 0.30f * (1f - progress * 0.4f)), Color.Transparent),
                    center = c1,
                    radius = r1,
                ),
                radius = r1,
                center = c1,
            )
            val c2 = Offset(size.width * 0.18f, size.height * (0.84f - 0.05f * drift))
            val r2 = size.minDimension * 0.36f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SoftLavender.copy(alpha = 0.22f), Color.Transparent),
                    center = c2,
                    radius = r2,
                ),
                radius = r2,
                center = c2,
            )
            drawCircle(
                color = SeedheInk.copy(alpha = 0.14f),
                radius = size.minDimension * (0.14f + progress * 0.04f),
                center = Offset(size.width * 0.82f, size.height * 0.22f),
                style = Stroke(width = 1.5f),
            )
        }
        val peekFrames = listOf(
            "The duo, unfiltered",
            "Delhi after dark",
            "No skips",
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 22.dp)
                .width(190.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = "Scroll the archive",
                color = SeedheInk.copy(alpha = 0.60f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.2.sp,
            )
            peekFrames.forEachIndexed { index, label ->
                val framePosition = scrollProgress().coerceIn(0f, 0.999f) * peekFrames.size
                val frameDistance = (framePosition - index).coerceIn(-1f, 1f)
                val focused = abs(frameDistance) < 0.5f
                Text(
                    text = label,
                    color = if (focused) SoftRose else SeedheInk.copy(alpha = 0.70f),
                    fontSize = if (focused) 15.sp else 12.sp,
                    fontWeight = if (focused) FontWeight.Medium else FontWeight.Light,
                    letterSpacing = 0.2.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .graphicsLayer {
                            alpha = (1f - abs(frameDistance) * 0.62f).coerceIn(0.18f, 1f)
                            translationX = frameDistance * 34f
                            scaleX = 1f - abs(frameDistance) * 0.10f
                            scaleY = 1f - abs(frameDistance) * 0.10f
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
                text = "Sneak peek · 01",
                color = SoftRose.copy(alpha = 0.88f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                ProfilePeekTag("Delhi · 2017")
                Spacer(Modifier.width(6.dp))
                ProfilePeekTag("Duo mode")
                Spacer(Modifier.width(6.dp))
                ProfilePeekTag("After dark")
            }
        }
    }
}

@Composable
private fun ProfilePeekTag(label: String) {
    Text(
        text = label,
        color = SeedheInk.copy(alpha = 0.90f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
        modifier = Modifier
            .background(SeedheInk.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .border(1.dp, SeedheInk.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
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
