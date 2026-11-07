package com.convx.music.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.convx.music.LocalPlayerConnection
import com.convx.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.convx.music.lyrics.LyricsEntry
import com.convx.music.lyrics.LyricsUtils.parseLyrics
import com.convx.music.lyrics.primaryLyricLineIndex
import com.convx.music.models.MediaMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val CinematicCrimson = Color(0xFFE5002B)
private val CinematicDeepRed = Color(0xFF28000A)
private val CinematicCard = Color(0xFF6D071C).copy(alpha = 0.78f)

/**
 * A music-video style lyrics treatment. It deliberately avoids a karaoke-list layout:
 * the playhead drives a small story frame with oversized background type and a focused
 * glass card in the foreground.
 */
@Composable
fun CinematicLyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val metadata by playerConnection.mediaMetadata.collectAsState(initial = null)
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val lyrics = remember(lyricsEntity?.lyrics) {
        lyricsEntity?.lyrics?.trim()?.takeUnless { it.isNullOrEmpty() || it == LYRICS_NOT_FOUND }
    }
    val lines = remember(lyrics) {
        lyrics?.let { raw ->
            if (raw.startsWith("[")) parseLyrics(raw)
            else raw.lines().mapIndexed { index, text -> LyricsEntry(index * 100L, text) }
        }.orEmpty()
    }
    var position by remember { mutableLongStateOf(0L) }

    LaunchedEffect(lyrics) {
        while (isActive) {
            position = sliderPositionProvider() ?: playerConnection.player.currentPosition
            delay(100L)
        }
    }

    val currentIndex = remember(lines, position, lyrics) {
        if (lines.isEmpty()) -1
        else if (lyrics?.startsWith("[") == true) {
            primaryLyricLineIndex(lines, position + (currentSong?.song?.lyricsOffset ?: 0))
                .coerceIn(0, lines.lastIndex)
        } else {
            lines.indexOfLast { it.time <= position }.coerceIn(0, lines.lastIndex)
        }
    }
    val activeLine = lines.getOrNull(currentIndex)
    val artwork = metadata?.thumbnailUrl
    val animatedZoom by animateFloatAsState(
        targetValue = if (currentIndex >= 0) 1.08f else 1f,
        animationSpec = tween(1_200, easing = FastOutSlowInEasing),
        label = "cinematicArtworkZoom",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinematicDeepRed),
    ) {
        AsyncImage(
            model = artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animatedZoom
                    scaleY = animatedZoom
                    alpha = 0.30f
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF9D0A27).copy(alpha = 0.42f), Color.Black.copy(alpha = 0.94f)),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.35f), Color(0xFF41000F).copy(alpha = 0.25f), Color.Black.copy(alpha = 0.86f)),
                    ),
                ),
        )

        if (lines.isNotEmpty()) {
            BackgroundLyricTypography(lines, currentIndex)
        }

        if (metadata != null && activeLine != null) {
            CinematicLyricCard(
                metadata = metadata,
                lines = lines,
                currentIndex = currentIndex,
                position = position,
                artwork = artwork,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            Text(
                text = "NO LYRICS",
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun BackgroundLyricTypography(lines: List<LyricsEntry>, currentIndex: Int) {
    val visible = (-2..2).mapNotNull { offset ->
        lines.getOrNull(currentIndex + offset)?.let { offset to it }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        visible.forEach { (offset, line) ->
            val distance = kotlin.math.abs(offset)
            Text(
                text = line.text.ifBlank { "·" }.uppercase(),
                maxLines = 1,
                softWrap = false,
                color = CinematicCrimson.copy(alpha = when (distance) {
                    0 -> 0.92f
                    1 -> 0.52f
                    else -> 0.28f
                }),
                fontSize = if (distance == 0) 56.sp else 42.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp,
                style = MaterialTheme.typography.displayLarge.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.72f), blurRadius = 18f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (distance == 0) 1f else 0.8f)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun CinematicLyricCard(
    metadata: MediaMetadata?,
    lines: List<LyricsEntry>,
    currentIndex: Int,
    position: Long,
    artwork: String?,
    modifier: Modifier = Modifier,
) {
    val title = metadata?.title.orEmpty()
    val artist = metadata?.artists?.joinToString(", ") { it.name }.orEmpty()
    val cardShape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth(0.9f)
            .padding(horizontal = 16.dp, vertical = 22.dp)
            .clip(cardShape)
            .background(CinematicCard)
            .border(1.dp, Color(0xFFFF7188).copy(alpha = 0.55f), cardShape)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = artist,
                    color = Color(0xFFFFB8C2),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            Text("✦", color = Color(0xFFFFD6DC), fontSize = 19.sp)
        }
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            targetState = currentIndex,
            transitionSpec = {
                (fadeIn(tween(240)) togetherWith fadeOut(tween(180)))
                    .using(SizeTransform(clip = false))
            },
            label = "cinematicLyricCard",
        ) { index ->
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                (-1..2).forEach { offset ->
                    val line = lines.getOrNull(index + offset) ?: return@forEach
                    val active = offset == 0
                    Text(
                        text = if (active) highlightedLyric(line, position) else AnnotatedString(line.text),
                        color = if (active) Color.White else Color(0xFFFF9EAD).copy(alpha = if (offset > 0) 0.7f else 0.48f),
                        fontSize = if (active) 20.sp else 17.sp,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                        lineHeight = if (active) 25.sp else 21.sp,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun highlightedLyric(line: LyricsEntry, position: Long): AnnotatedString {
    val words = line.words.orEmpty()
    if (words.isEmpty()) return AnnotatedString(line.text)
    return buildAnnotatedString {
        var cursor = 0
        words.forEach { word ->
            val start = line.text.indexOf(word.text, cursor).takeIf { it >= 0 } ?: cursor
            if (start > cursor) append(line.text.substring(cursor, start))
            withStyle(
                SpanStyle(
                    color = if (position >= word.startTime * 1000) Color.White else Color(0xFFFFA8B5),
                ),
            ) {
                append(word.text)
            }
            cursor = (start + word.text.length).coerceAtMost(line.text.length)
        }
        if (cursor < line.text.length) append(line.text.substring(cursor))
    }
}
