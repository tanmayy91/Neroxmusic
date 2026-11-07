/**
 * Native Apple Music-inspired lyrics renderer for Nerox Music.
 *
 * This renderer is deliberately isolated behind LyricsAnimationStyle.APPLE_V3.
 * It uses Compose only; no WebView or browser runtime.
 */
package com.convx.music.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.withFrameNanos
import com.convx.music.lyrics.LyricsEntry
import kotlinx.coroutines.isActive

/**
 * Apple Music V3's native presentation. Word timestamps are absolute milliseconds
 * after conversion from the LyricsEntry model's seconds-based timestamps.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AppleMusicV3Lyrics(
    lines: List<LyricsEntry>,
    positionMs: () -> Long,
    fontFamily: FontFamily?,
    fontSize: Float,
    lineSpacing: Float,
    showTranslation: Boolean,
    textColor: Color,
    accentColor: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var framePosition by remember { mutableLongStateOf(positionMs()) }

    // Read the player clock at frame cadence so word highlighting does not inherit
    // the slower line-index polling interval used by the standard lyrics screen.
    LaunchedEffect(lines, positionMs) {
        var lastSampleNanos = 0L
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                // 24 Hz keeps karaoke responsive while avoiding a display-refresh-rate
                // recomposition loop on low-power devices.
                if (lastSampleNanos == 0L || frameTimeNanos - lastSampleNanos >= 42_000_000L) {
                    framePosition = positionMs()
                    lastSampleNanos = frameTimeNanos
                }
            }

        }
    }

    // Keep the list-level state stable between lyric changes. The playhead is sampled
    // every frame for karaoke, but the list only needs to react when the active line changes.
    val activeIndex by remember(lines) {
        androidx.compose.runtime.derivedStateOf {
            lines.indexOfLast { it.time <= framePosition }.coerceAtLeast(0)
        }
    }
    val lyricRubber = rememberLyricRubber(listState)

    LaunchedEffect(activeIndex, lines, lyricRubber) {
        if (lines.isNotEmpty()) {
            // The shared rubber scroll carries motion across rapid line changes instead of
            // cancelling one 550ms animation and starting another on every lyric boundary.
            listState.followLyricLine(activeIndex, animate = true, rubber = lyricRubber)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val verticalInset = maxHeight * 0.40f
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = verticalInset, horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(
                items = lines,
                key = { index, entry -> "${entry.time}-$index" },
            ) { index, entry ->
                val distance = kotlin.math.abs(index - activeIndex)
                val isActive = index == activeIndex
                val isBackground = entry.isBackground
                val targetAlpha = when {
                    isActive -> 1f
                    distance == 1 -> 0.58f
                    distance == 2 -> 0.30f
                    else -> 0.16f
                } * if (isBackground) 0.72f else 1f
                val targetScale = when (distance) {
                    0 -> 1f
                    1 -> 0.91f
                    2 -> 0.84f
                    else -> 0.80f
                } * if (isBackground) 0.88f else 1f
                val alpha by animateFloatAsState(targetAlpha, spring(dampingRatio = 0.82f, stiffness = 260f), label = "appleV3Alpha")
                val scale by animateFloatAsState(targetScale, spring(dampingRatio = 0.78f, stiffness = 230f), label = "appleV3Scale")
                val translation by entry.translatedTextFlow.collectAsStateWithLifecycle()
                val romanized by entry.romanizedTextFlow.collectAsStateWithLifecycle()

                // Keep the lyric stage centered so the words remain the visual focus on
                // both phones and tablets. Singer metadata still affects timing, but
                // never pushes the current line away from the beat.
                val lineAlignment = Alignment.CenterHorizontally
                val lineTextAlign = TextAlign.Center
                val beatPulse = if (isActive) {
                    1f + kotlin.math.sin(framePosition / 145f) * 0.012f
                } else {
                    1f
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                if (isActive) {
                                    listOf(
                                        accentColor.copy(alpha = 0.08f),
                                        textColor.copy(alpha = 0.025f),
                                        accentColor.copy(alpha = 0.08f),
                                    )
                                } else {
                                    listOf(
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color.Transparent,
                                    )
                                },
                            ),
                        )
                        .border(
                            width = if (isActive) 1.dp else 0.dp,
                            color = accentColor.copy(alpha = if (isActive) 0.22f else 0f),
                            shape = RoundedCornerShape(28.dp),
                        )
                        .graphicsLayer {
                            this.alpha = alpha
                            scaleX = scale * beatPulse
                            scaleY = scale * beatPulse
                        }
                        .clickable { onSeek(entry.time) }
                        .padding(
                            horizontal = if (isBackground) 28.dp else 0.dp,
                            vertical = if (isBackground) 2.dp else 0.dp,
                        ),
                    horizontalAlignment = lineAlignment,
                ) {
                    if (isActive) {
                        val transition = rememberInfiniteTransition(label = "neroxBeatBubbles")
                        val bubbleAlpha by transition.animateFloat(
                            initialValue = 0.35f,
                            targetValue = 0.85f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 620),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "neroxBeatBubbleAlpha",
                        )
                        Row(
                            modifier = Modifier
                                .padding(top = 8.dp, bottom = 2.dp)
                                .graphicsLayer {
                                    this.alpha = bubbleAlpha
                                    scaleX = 0.96f + bubbleAlpha * 0.08f
                                    scaleY = 0.96f + bubbleAlpha * 0.08f
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "•  •  •",
                                color = accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    val words = remember(entry) {
                        val timed = entry.words.orEmpty()
                        if (timed.isNotEmpty()) {
                            timed.map { word ->
                                Triple(
                                    word.text,
                                    (word.startTime * 1000.0).toLong(),
                                    (word.endTime * 1000.0).toLong().coerceAtLeast((word.startTime * 1000.0).toLong() + 1L),
                                )
                            }
                        } else {
                            val tokens = entry.text.split(Regex("(?<=\\s)|(?=\\s)")).filter { it.isNotEmpty() }
                            val nextTime = lines.getOrNull(index + 1)?.time ?: (entry.time + 4000L)
                            val duration = (nextTime - entry.time).coerceAtLeast(800L)
                            val totalWeight = tokens.sumOf { it.length.coerceAtLeast(1) }.coerceAtLeast(1)
                            var cursor = entry.time
                            tokens.map { token ->
                                val end = cursor + (duration * token.length.coerceAtLeast(1) / totalWeight).coerceAtLeast(1)
                                Triple(token, cursor, end).also { cursor = end }
                            }
                        }
                    }

                    // Only the active line needs live word-progress styling. Keeping the playhead
                    // out of inactive rows' remember keys avoids rebuilding every visible
                    // lyric line on every playback tick.
                    val karaokeText = remember(
                        words,
                        entry.text,
                        if (isActive) framePosition else 0L,
                        isActive,
                        textColor,
                        accentColor,
                        fontSize,
                    ) {
                        buildAnnotatedString {
                            var sourceCursor = 0
                            words.forEach { (word, start, end) ->
                                if (word.isNotBlank()) {
                                    // Keep the exact whitespace and punctuation from the source lyric.
                                    val foundAt = entry.text.indexOf(word, sourceCursor)
                                    if (foundAt >= sourceCursor) {
                                        append(entry.text.substring(sourceCursor, foundAt))
                                        sourceCursor = foundAt
                                    }

                                    val progress = when {
                                        framePosition <= start -> 0f
                                        framePosition >= end -> 1f
                                        else -> ((framePosition - start).toFloat() / (end - start).coerceAtLeast(1L)).coerceIn(0f, 1f)
                                    }
                                    val wordAlpha = if (isActive) 0.40f + progress * 0.60f else 0.86f
                                    val wordColor = if (isActive && progress > 0.02f) {
                                        Color(
                                            red = textColor.red + (accentColor.red - textColor.red) * progress * 0.12f,
                                            green = textColor.green + (accentColor.green - textColor.green) * progress * 0.12f,
                                            blue = textColor.blue + (accentColor.blue - textColor.blue) * progress * 0.12f,
                                            alpha = wordAlpha,
                                        )
                                    } else textColor.copy(alpha = wordAlpha)
                                    withStyle(SpanStyle(color = wordColor, fontWeight = FontWeight.Bold)) {
                                        append(word)
                                    }
                                    sourceCursor = if (foundAt >= 0) {
                                        (foundAt + word.length).coerceAtMost(entry.text.length)
                                    } else {
                                        (sourceCursor + word.length).coerceAtMost(entry.text.length)
                                    }
                                }
                            }
                            if (sourceCursor < entry.text.length) {
                                append(entry.text.substring(sourceCursor))
                            }
                        }
                    }
                    Text(
                        text = karaokeText,
                        modifier = Modifier.fillMaxWidth(),
                        color = textColor,
                        fontFamily = fontFamily,
                        fontSize = (fontSize * if (isBackground) 0.88f else 1f).sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        textAlign = lineTextAlign,
                        lineHeight = (fontSize * lineSpacing.coerceIn(1.12f, 1.45f)).sp,
                        style = TextStyle(
                            shadow = if (isActive) {
                                Shadow(color = accentColor.copy(alpha = 0.16f), blurRadius = 8f)
                            } else Shadow(Color.Transparent, blurRadius = 0f)
                        ),
                    )

                    if (showTranslation && !translation.isNullOrBlank()) {
                        Text(
                            text = translation!!,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            color = textColor.copy(alpha = if (isActive) 0.72f else 0.44f),
                            fontFamily = fontFamily,
                            fontSize = (fontSize * 0.70f).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = lineTextAlign,
                            lineHeight = (fontSize * 0.78f).sp,
                        )
                    }
                    if (!romanized.isNullOrBlank()) {
                        Text(
                            text = romanized!!,
                            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                            color = textColor.copy(alpha = if (isActive) 0.62f else 0.42f),
                            fontFamily = fontFamily,
                            fontSize = (fontSize * 0.62f).sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = lineTextAlign,
                            lineHeight = (fontSize * 0.72f).sp,
                        )
                    }
                }

                // Apple Music-style interlude ellipsis appears only in a real timed gap
                // between the end of this line and the next lyric line.
                val nextEntry = lines.getOrNull(index + 1)
                val lastWordEndMs = entry.words?.lastOrNull()?.endTime?.let { (it * 1000.0).toLong() }
                val nextLineTime = nextEntry?.time
                val interludeStart = lastWordEndMs
                val showInterlude = isActive && interludeStart != null && nextLineTime != null &&
                    nextLineTime - interludeStart >= 700L &&
                    framePosition >= interludeStart && framePosition < nextLineTime
                if (showInterlude) {
                    val transition = rememberInfiniteTransition(label = "appleV3Interlude")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(3) { dotIndex ->
                            val dotAlpha by transition.animateFloat(
                                initialValue = 0.18f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = 520, delayMillis = dotIndex * 170),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                                label = "appleV3InterludeDotAlpha$dotIndex",
                            )
                            val dotLift by transition.animateFloat(
                                initialValue = 0f,
                                targetValue = -5f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = 520, delayMillis = dotIndex * 170),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                                label = "appleV3InterludeDotLift$dotIndex",
                            )
                            Text(
                                text = "•",
                                color = textColor.copy(alpha = dotAlpha),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .graphicsLayer { translationY = dotLift },
                            )
                        }
                    }
                }
            }
        }
    }
}
