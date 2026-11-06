/**
 * Nerox Music cold-start intro.
 * Uses the existing Nerox Music vector mark; no replacement artwork is generated.
 */
package com.convx.music.ui.component

import android.graphics.Paint
import android.graphics.Typeface
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.music.R
import kotlin.math.pow

private val Mint = Color(0xFFC9E4C8)
private val LogoInk = Color(0xFF222222)
private val NexaInk = Color(0xFF12382A)
private val MusicInk = Color(0xFF1A1A1A)
private val TaglineInk = Color(0xFF3D5A48)

@Composable
fun ConvxIntro(onFinished: () -> Unit) {
    val context = LocalContext.current
    val reducedMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    val clock = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            // Reduced motion: show the finished composition, hold for one second, then fade.
            var elapsed = 0f
            var last = withFrameNanos { it }
            while (elapsed < REDUCED_HOLD_MS) {
                val now = withFrameNanos { it }
                elapsed += ((now - last) / 1_000_000f).coerceAtMost(MAX_FRAME_STEP_MS)
                last = now
            }
            clock.floatValue = MIN_HOLD_MS
        }
        var last = withFrameNanos { it }
        while (clock.floatValue < TOTAL_MS) {
            val now = withFrameNanos { it }
            val delta = ((now - last) / 1_000_000f).coerceAtMost(MAX_FRAME_STEP_MS)
            last = now
            clock.floatValue = (clock.floatValue + delta).coerceAtMost(TOTAL_MS)
        }
        onFinished()
    }

    val logoVector = ImageVector.vectorResource(R.drawable.nexamusic_logo_mark)
    val logoPath = remember(logoVector) {
        Path().apply {
            logoVector.root.filterIsInstance<VectorPath>().forEach {
                addPath(it.pathData.toPath())
            }
        }
    }
    val logoViewportWidth = logoVector.viewportWidth
    val logoViewportHeight = logoVector.viewportHeight

    val wordPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        }
    }
    val taglinePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }

    Canvas(
        Modifier.fillMaxSize().graphicsLayer {
            alpha = 1f - easeIn(phase(clock.floatValue, MIN_HOLD_MS, TOTAL_MS))
        },
    ) {
        drawRect(Mint)

        val t = if (reducedMotion) TOTAL_MS - EXIT_MS else clock.floatValue
        val logoWidth = minOf(size.width * 0.48f, 200.dp.toPx())
        val logoHeight = logoWidth * (logoViewportHeight / logoViewportWidth)
        val wordSize = 46.sp.toPx()
        val taglineSize = 16.sp.toPx()
        val logoGap = 40.dp.toPx()
        val taglineGap = 6.dp.toPx()
        val totalHeight = logoHeight + logoGap + wordSize + taglineGap + taglineSize
        val centerX = size.width / 2f
        val logoTop = (size.height - totalHeight) / 2f
        val logoLeft = centerX - logoWidth / 2f
        val logoBottom = logoTop + logoHeight
        val wordTop = logoBottom + logoGap
        val wordBaseline = wordTop + wordSize * 0.88f
        val taglineBaseline = wordTop + wordSize + taglineGap + taglineSize * 0.82f

        val exit = phase(t, MIN_HOLD_MS, TOTAL_MS)
        val exitScale = 1f + 0.18f * easeIn(exit)
        val exitAlpha = 1f - easeIn(exit)

        withTransform({
            scale(exitScale, exitScale, Offset(centerX, size.height / 2f))
        }) {
            // Logo reveal: a clean bottom-to-top wipe with a gentle scale settle.
            val wipe = phase(t, LOGO_START_MS, LOGO_START_MS + LOGO_WIPE_MS)
            val logoScale = 0.94f + 0.06f * easeOut(phase(t, LOGO_START_MS, LOGO_SCALE_END_MS))
            val visibleTop = logoBottom - logoHeight * wipe
            if (wipe > 0f) {
                clipRect(
                    left = logoLeft,
                    top = visibleTop,
                    right = logoLeft + logoWidth,
                    bottom = logoBottom,
                ) {
                    withTransform({
                        scale(logoScale, logoScale, Offset(centerX, logoTop + logoHeight / 2f))
                    }) {
                        withTransform({
                            // Place the viewport first, then scale its contents.
                            // Reversing these operations scales the translation too,
                            // shifting the visible mark toward the left edge.
                            translate(logoLeft, logoTop)
                            scale(
                                logoWidth / logoViewportWidth,
                                logoHeight / logoViewportHeight,
                                Offset.Zero,
                            )
                        }) {
                            drawPath(logoPath, SolidColor(LogoInk))
                        }
                    }
                }
            }

            // Two fine outline rings radiate from the logo's centre.
            listOf(RING_ONE_START_MS, RING_TWO_START_MS).forEach { start ->
                val p = phase(t, start, start + RING_DURATION_MS)
                if (p in 0f..1f && t >= start) {
                    drawCircle(
                        color = LogoInk.copy(alpha = 0.55f * (1f - p) * exitAlpha),
                        radius = logoWidth * (0.7f + 1.6f * easeOut(p)),
                        center = Offset(centerX, logoTop + logoHeight / 2f),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }

            // Wordmark letters rise into their own clipped reveal windows.
            val letters = "Nerox Music"
            val letterGap = (-2).dp.toPx()
            wordPaint.textSize = wordSize
            val widths = letters.map { wordPaint.measureText(it.toString()) }
            val fullWidth = widths.sum() + letterGap * (letters.length - 1)
            var x = centerX - fullWidth / 2f
            letters.forEachIndexed { index, char ->
                val start = WORD_START_MS + index * LETTER_STAGGER_MS
                val p = phase(t, start, start + LETTER_DURATION_MS)
                val charWidth = widths[index]
                if (p > 0f) {
                    val rise = (1f - easeOut(p)) * wordSize * 1.15f
                    wordPaint.color = (if (index < 4) NexaInk else MusicInk).toArgb()
                    val canvas = drawContext.canvas.nativeCanvas
                    val save = canvas.save()
                    canvas.clipRect(
                        x - 1f,
                        wordBaseline - wordSize * 1.05f,
                        x + charWidth + 1f,
                        wordBaseline + wordSize * 0.22f,
                    )
                    canvas.drawText(char.toString(), x, wordBaseline + rise, wordPaint)
                    canvas.restoreToCount(save)
                }
                x += charWidth + letterGap
            }

            // Tagline fades in and settles upward after the wordmark.
            val taglineP = phase(t, TAGLINE_START_MS, TAGLINE_START_MS + TAGLINE_DURATION_MS)
            if (taglineP > 0f) {
                taglinePaint.textSize = taglineSize
                taglinePaint.color = TaglineInk.toArgb()
                taglinePaint.alpha = (255f * easeOut(taglineP) * exitAlpha).toInt().coerceIn(0, 255)
                drawContext.canvas.nativeCanvas.drawText(
                    "Music that moves with you.",
                    centerX,
                    taglineBaseline + 8.dp.toPx() * (1f - easeOut(taglineP)),
                    taglinePaint,
                )
            }

        }

    }
}

private fun phase(time: Float, start: Float, end: Float): Float =
    ((time - start) / (end - start)).coerceIn(0f, 1f)

private fun easeOut(x: Float): Float = 1f - (1f - x).pow(3)
private fun easeIn(x: Float): Float = x * x * x

private const val LOGO_START_MS = 300f
private const val LOGO_WIPE_MS = 1_150f
private const val LOGO_SCALE_END_MS = 1_900f
private const val RING_ONE_START_MS = 1_250f
private const val RING_TWO_START_MS = 1_550f
private const val RING_DURATION_MS = 1_800f
private const val WORD_START_MS = 1_350f
private const val LETTER_DURATION_MS = 800f
private const val LETTER_STAGGER_MS = 55f
private const val TAGLINE_START_MS = 2_200f
private const val TAGLINE_DURATION_MS = 800f
private const val MIN_HOLD_MS = 3_200f
private const val EXIT_MS = 500f
private const val TOTAL_MS = MIN_HOLD_MS + EXIT_MS
private const val REDUCED_HOLD_MS = 1_000f
private const val MAX_FRAME_STEP_MS = 34f
