package com.convx.music.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.music.lyrics.LyricsEntry
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val SeedheInk = Color(0xFFF2EEE9)
private val SeedheCrimson = Color(0xFF8F1727)
private val SeedheCyan = Color(0xFF1E9BA7)
private val SeedheBlack = Color(0xFF0A0A0C)

private data class InkParticle(val x: Float, val y: Float, val radius: Float, val alpha: Float)
private enum class SeedheVisualMode {
    MONOGRAPH,
    SIGNAL,
    GRID,
    ORBIT,
}

/**
 * The artist-specific canvas is selected by [CinematicLyrics]. Keeping this composable
 * artist-agnostic makes it usable with the same parsed lyrics on every playback surface.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SeedheMautLyricsCanvas(
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
    val lineProgress = activeLine?.let {
        val duration = (nextLine?.time ?: (it.time + 4_000L)) - it.time
        ((positionMs - it.time).toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f)
    } ?: 0f

    val transition = rememberInfiniteTransition(label = "seedheMautBackdrop")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18_000)),
        label = "seedheMautDrift",
    )
    val ambientPulse by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2_400)),
        label = "seedheMautAmbientPulse",
    )
    val flash = remember { Animatable(0f) }
    val slamScale = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(currentIndex) {
        flash.snapTo(1f)
        flash.animateTo(0f, tween(80))
        if (activeLine != null) {
            slamScale.snapTo(1.4f)
            slamScale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            if (activeLine.text.trim().split(Regex("\\s+")).size <= 3) {
                shake.snapTo(1f)
                shake.animateTo(0f, tween(180))
            }
        }
    }

    val grain = remember {
        Array(96) { index ->
            InkParticle(
                x = ((index * 47) % 101) / 100f,
                y = ((index * 73) % 103) / 100f,
                radius = 0.45f + (index % 4) * 0.35f,
                alpha = 0.025f + (index % 5) * 0.009f,
            )
        }
    }
    val wireframePath = remember { Path() }
    val inkPath = remember { Path() }
    val flashAlpha by animateFloatAsState(flash.value, tween(80), label = "seedheMautFlash")
    val lineAngle = (((currentIndex.coerceAtLeast(0) * 17) % 28 - 14) * (PI / 180.0)).toFloat()
    val lineScale = 0.84f + (currentIndex.coerceAtLeast(0) % 4) * 0.06f
    val shortLine = (activeLine?.text?.trim()?.split(Regex("\\s+"))?.size ?: 0) <= 3
    val textOffset = (sin(drift * (PI * 2.0)) * 10f).toFloat()
    val visualModes = SeedheVisualMode.values()
    val visualMode = visualModes[currentIndex.coerceAtLeast(0) % visualModes.size]

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(SeedheBlack)

            val crimsonX = size.width * (0.25f + drift * 0.16f)
            val crimsonY = size.height * (0.20f + sin(drift * (PI * 2.0)).toFloat() * 0.04f)
            drawCircle(
                color = SeedheCrimson.copy(alpha = 0.15f + flashAlpha * 0.30f),
                radius = size.minDimension * 0.64f,
                center = androidx.compose.ui.geometry.Offset(crimsonX, crimsonY),
            )
            drawCircle(
                color = Color(0xFF24242A).copy(alpha = 0.38f),
                radius = size.minDimension * 0.56f,
                center = androidx.compose.ui.geometry.Offset(
                    size.width * (0.78f - drift * 0.12f),
                    size.height * 0.78f,
                ),
            )

            grain.forEach { particle ->
                drawCircle(
                    color = SeedheInk.copy(alpha = particle.alpha),
                    radius = particle.radius,
                    center = androidx.compose.ui.geometry.Offset(
                        particle.x * size.width,
                        particle.y * size.height,
                    ),
                )
            }

            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val wireScale = if (instrumental) ambientPulse * 1.34f else lineScale
            rotate(lineAngle + sin(drift * (PI * 2.0)).toFloat() * 0.05f, center) {
                scale(wireScale, center) {
                    val wireColor = SeedheCyan.copy(alpha = if (instrumental) 0.34f else 0.18f)
                    wireframePath.reset()
                    wireframePath.moveTo(size.width * 0.18f, size.height * 0.30f)
                    wireframePath.lineTo(size.width * 0.82f, size.height * 0.30f)
                    wireframePath.lineTo(size.width * 0.68f, size.height * 0.70f)
                    wireframePath.lineTo(size.width * 0.32f, size.height * 0.70f)
                    wireframePath.close()
                    drawPath(
                        path = wireframePath,
                        color = wireColor,
                        style = Stroke(width = 1.2f, cap = StrokeCap.Square),
                    )
                    drawLine(
                        color = wireColor,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.30f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.70f),
                        strokeWidth = 1.2f,
                        cap = StrokeCap.Square,
                    )
                    drawLine(
                        color = wireColor,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.30f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.70f),
                        strokeWidth = 1.2f,
                        cap = StrokeCap.Square,
                    )
                    if (instrumental) {
                        drawCircle(
                            color = SeedheCrimson.copy(alpha = 0.24f),
                            radius = size.minDimension * 0.16f * ambientPulse,
                            center = center,
                            style = Stroke(width = 2.2f),
                        )
                    }
                }
                when (visualMode) {
                    SeedheVisualMode.MONOGRAPH -> Unit
                    SeedheVisualMode.SIGNAL -> {
                        repeat(18) { index ->
                            val x = size.width * (index + 1) / 19f
                            val height = size.height *
    (0.03f + 0.04f * abs(sin(index * 1.7f + drift * PI * 2.0)).toFloat())
                            drawLine(
                                color = SeedheCyan.copy(alpha = 0.16f + index % 3 * 0.04f),
                                start = androidx.compose.ui.geometry.Offset(x, center.y - height),
                                end = androidx.compose.ui.geometry.Offset(x, center.y + height),
                                strokeWidth = if (index % 4 == 0) 3f else 1f,
                            )
                        }
                        drawCircle(
                            color = SeedheCrimson.copy(alpha = 0.18f),
                            radius = size.minDimension * (0.12f + ambientPulse * 0.035f),
                            center = center,
                            style = Stroke(width = 1.5f),
                        )
                    }
                    SeedheVisualMode.GRID -> {
                        val spacing = size.minDimension / 10f
                        for (index in 1..9) {
                            drawLine(
                                color = SeedheCyan.copy(alpha = 0.10f),
                                start = androidx.compose.ui.geometry.Offset(index * spacing, 0f),
                                end = androidx.compose.ui.geometry.Offset(index * spacing, size.height),
                                strokeWidth = if (index == 5) 1.8f else 0.7f,
                            )
                            drawLine(
                                color = SeedheCyan.copy(alpha = 0.10f),
                                start = androidx.compose.ui.geometry.Offset(0f, index * spacing),
                                end = androidx.compose.ui.geometry.Offset(size.width, index * spacing),
                                strokeWidth = if (index == 5) 1.8f else 0.7f,
                            )
                        }
                        drawRect(
                            color = SeedheCrimson.copy(alpha = 0.30f),
                            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.18f),
                            size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.64f),
                            style = Stroke(width = 1.5f),
                        )
                    }
                    SeedheVisualMode.ORBIT -> {
                        rotate(drift * 360f, center) {
                            repeat(3) { ring ->
                                val diameter = size.minDimension * (0.36f + ring * 0.20f)
                                drawArc(
                                    color = if (ring % 2 == 0) SeedheCyan else SeedheCrimson,
                                    startAngle = 18f + ring * 75f,
                                    sweepAngle = 118f,
                                    useCenter = false,
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        center.x - diameter / 2f,
                                        center.y - diameter / 2f,
                                    ),
                                    size = androidx.compose.ui.geometry.Size(diameter, diameter),
                                    style = Stroke(width = 1.5f),
                                )
                            }
                        }
                        drawCircle(
                            color = SeedheInk.copy(alpha = 0.14f),
                            radius = size.minDimension * 0.08f * ambientPulse,
                            center = center,
                        )
                    }
                }
            }

            val inkColor = SeedheCrimson.copy(alpha = 0.16f)
            repeat(8) { index ->
                val y = size.height * (0.12f + index * 0.105f)
                inkPath.reset()
                inkPath.moveTo(size.width * 0.08f, y)
                inkPath.cubicTo(
                    size.width * 0.30f,
                    y - 10f,
                    size.width * 0.68f,
                    y + 9f,
                    size.width * 0.92f,
                    y - 4f,
                )
                drawPath(
                    path = inkPath,
                    color = inkColor,
                    style = Stroke(width = 1f, cap = StrokeCap.Round),
                )
            }
        }

        if (!instrumental && activeLine != null) {
            HeroLine(
                line = activeLine,
                nextLine = nextLine,
                positionMs = positionMs,
                scale = slamScale.value,
                shake = shake.value,
                shortLine = shortLine,
                textOffset = textOffset,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (!instrumental) {
            ModeStamp(
                mode = visualMode,
                progress = lineProgress,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 26.dp),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                previousLine?.let {
                    Text(
                        text = it.text,
                        color = SeedheInk.copy(alpha = 0.25f),
                        fontSize = 15.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
                if (activeLine != null) {
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(vertical = 44.dp))
                }
                nextLine?.let {
                    Text(
                        text = it.text,
                        color = SeedheInk.copy(alpha = 0.25f),
                        fontSize = 15.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeStamp(
    mode: SeedheVisualMode,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "SEEDHE / MAUT",
            color = SeedheInk.copy(alpha = 0.62f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Text(
            text = "${mode.name}  //  ${(progress * 100).toInt().toString().padStart(2, '0')}",
            color = SeedheCyan.copy(alpha = 0.72f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
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

@Composable
private fun HeroLine(
    line: LyricsEntry,
    nextLine: LyricsEntry?,
    positionMs: Long,
    scale: Float,
    shake: Float,
    shortLine: Boolean,
    textOffset: Float,
    modifier: Modifier = Modifier,
) {
    val duration = ((nextLine?.time ?: line.time + 4_000L) - line.time).coerceAtLeast(1L)
    val lineProgress = ((positionMs - line.time).toFloat() / duration).coerceIn(0f, 1f)
    val wordData = remember(line, nextLine) { lineWords(line, nextLine) }
    val hasWordTimings = line.words.orEmpty().isNotEmpty()
    val text = remember(line, lineProgress, positionMs, wordData, hasWordTimings) {
        if (hasWordTimings) {
            buildAnnotatedString {
                var cursor = 0
                wordData.forEach { word ->
                    val start = line.text.indexOf(word.text, cursor).takeIf { it >= cursor } ?: cursor
                    if (start > cursor) append(line.text.substring(cursor, start))
                    val progress = ((positionMs - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                    withStyle(SpanStyle(color = SeedheInk.copy(alpha = 0.38f + progress * 0.62f))) {
                        append(word.text)
                    }
                    cursor = (start + word.text.length).coerceAtMost(line.text.length)
                }
                if (cursor < line.text.length) append(line.text.substring(cursor))
            }
        } else {
            AnnotatedString(line.text)
        }
    }

    val fill = androidx.compose.ui.graphics.Brush.horizontalGradient(
        0f to SeedheInk,
        (lineProgress - 0.05f).coerceIn(0f, 1f) to SeedheInk,
        (lineProgress + 0.05f).coerceIn(0f, 1f) to SeedheCyan.copy(alpha = 0.45f),
        1f to SeedheInk.copy(alpha = 0.30f),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = if (shortLine) sin(positionMs / 18f) * 5f * shake else textOffset
                translationY = if (shortLine) cos(positionMs / 21f) * 3f * shake else 0f
            },
        contentAlignment = Alignment.Center,
    ) {
        if (shortLine) {
            Text(
                text = text,
                color = SeedheInk,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 52.sp,
                letterSpacing = (-1.4).sp,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    brush = if (hasWordTimings) null else fill,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.75f),
                        blurRadius = 16f,
                    ),
                ),
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center,
            ) {
                wordData.forEachIndexed { index, word ->
                    val progress = ((positionMs - word.startMs).toFloat() /
                        (word.endMs - word.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                    Text(
                        text = word.text.trim(),
                        color = SeedheInk.copy(alpha = if (hasWordTimings) 0.38f + progress * 0.62f else 0.7f + lineProgress * 0.3f),
                        fontSize = 39.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 44.sp,
                        letterSpacing = (-1.1).sp,
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .graphicsLayer {
                                translationY = sin(index * 0.9f + positionMs / 180f) * 4f
                            },
                    )
                }
            }
        }
        if (lineProgress < 0.15f) {
            Text(
                text = text,
                color = SeedheCrimson.copy(alpha = 0.42f),
                fontSize = if (shortLine) 48.sp else 39.sp,
                fontWeight = FontWeight.Black,
                lineHeight = if (shortLine) 52.sp else 44.sp,
                letterSpacing = (-1.4).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { translationX = -3f },
            )
            Text(
                text = text,
                color = SeedheCyan.copy(alpha = 0.38f),
                fontSize = if (shortLine) 48.sp else 39.sp,
                fontWeight = FontWeight.Black,
                lineHeight = if (shortLine) 52.sp else 44.sp,
                letterSpacing = (-1.4).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { translationX = 3f },
            )
        }
    }
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
