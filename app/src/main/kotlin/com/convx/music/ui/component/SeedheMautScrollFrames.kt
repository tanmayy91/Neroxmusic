package com.convx.music.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile
import kotlin.math.roundToInt

private const val ZipUrl =
    "https://pdirzojzcohajcvcqbtk.supabase.co/storage/v1/object/public/cv1/app.zip"

/** Cached under `cacheDir`. Bump the version in the file name if the remote zip is ever replaced. */
private const val CacheFolder = "seedhe_jacket"
private const val CacheFileName = "app_v1.zip"

private const val MaxDownloadAttempts = 3

/** Matches the studio backdrop of the frames so letterboxing in wide heroes is invisible. */
private val FrameBackdrop = Color(0xFF111514)

/** Full-size decoded frames are evicted past this size (RGB_565, ~1.8 MB each at 720x1280). */
private const val HiResCacheBytes = 32 * 1024 * 1024

/** Quarter-size copies of every frame (~115 KB each): the instant fallback while full-size frames decode. */
private const val LowResSample = 4

private val FrameExtensions = listOf(".jpg", ".jpeg", ".png", ".webp")

/**
 * Downloads the frame zip once, keeps it in the app cache and reads frames straight out of it
 * (no extraction). Mutable fields are touched on the main thread only; decoding runs on
 * [Dispatchers.IO] and results are published back on the caller's (main) context.
 */
private class JacketFrames(context: Context) {
    private val appContext = context.applicationContext
    private var zip: ZipFile? = null
    private var names: List<String> = emptyList()
    private var low: Array<ImageBitmap?> = emptyArray()
    private val hi = object : LruCache<Int, ImageBitmap>(HiResCacheBytes) {
        override fun sizeOf(key: Int, value: ImageBitmap): Int = value.width * value.height * 2
    }

    /** Bumped whenever a decode lands; read in the draw phase so arrivals redraw without recomposing. */
    val version = mutableIntStateOf(0)

    /** True once the first frame is on screen-ready; until then nothing is drawn and the normal hero shows. */
    var ready by mutableStateOf(false)
        private set

    val count: Int get() = names.size

    /** Returns false if the zip could not be downloaded or read; the hero then stays as it was. */
    suspend fun open(): Boolean {
        repeat(MaxDownloadAttempts) { attempt ->
            val opened = withContext(Dispatchers.IO) {
                val file = ensureZip() ?: return@withContext null
                readZip(file)
            }
            if (opened != null) {
                zip = opened.first
                names = opened.second
                low = arrayOfNulls(names.size)
                return true
            }
            if (attempt < MaxDownloadAttempts - 1) delay(2_000L * (attempt + 1))
        }
        return false
    }

    fun close() {
        val z = zip
        zip = null
        try {
            z?.close()
        } catch (_: IOException) {
        }
    }

    fun best(index: Int): ImageBitmap? =
        if (index !in 0 until count) null else hi.get(index) ?: low[index]

    fun indexFor(progress: Float): Int =
        if (count <= 1) 0 else (progress.coerceIn(0f, 1f) * (count - 1)).roundToInt()

    suspend fun loadLowRes() {
        for (i in 0 until count) {
            if (low[i] != null) continue
            val bmp = withContext(Dispatchers.IO) { decode(i, LowResSample) } ?: continue
            low[i] = bmp
            if (!ready) ready = true
            version.intValue++
        }
    }

    /** Decodes the frames nearest [center] first, so the one on screen and its neighbours are sharp. */
    suspend fun prefetchAround(center: Int) {
        for (distance in 0..4) {
            for (index in intArrayOf(center + distance, center - distance).distinct()) {
                if (index !in 0 until count || hi.get(index) != null) continue
                val bmp = withContext(Dispatchers.IO) { decode(index, 1) } ?: continue
                hi.put(index, bmp)
                version.intValue++
            }
        }
    }

    // ---- IO-thread helpers -------------------------------------------------------------

    private fun decode(index: Int, sample: Int): ImageBitmap? {
        val z = zip ?: return null
        val name = names.getOrNull(index) ?: return null
        return try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565 // frames are opaque; halves the memory
            }
            val entry = z.getEntry(name) ?: return null
            z.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, options) }
                ?.asImageBitmap()
        } catch (_: Exception) {
            // IOException, or IllegalStateException if the zip was closed while leaving the screen.
            null
        }
    }

    /** Cached zip if it is intact, otherwise download it (atomically, via a .part file). */
    private fun ensureZip(): File? {
        val dir = File(appContext.cacheDir, CacheFolder).apply { mkdirs() }
        val cached = File(dir, CacheFileName)
        if (cached.length() > 0 && isValidZip(cached)) return cached
        cached.delete()

        val part = File(dir, "$CacheFileName.part")
        return try {
            val connection = (URL(ZipUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                connection.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
            } finally {
                connection.disconnect()
            }
            if (isValidZip(part) && part.renameTo(cached)) cached else null
        } catch (_: IOException) {
            null
        } finally {
            part.delete() // no-op after a successful rename
        }
    }

    private fun isValidZip(file: File): Boolean = try {
        ZipFile(file).use { it.size() > 0 }
    } catch (_: IOException) {
        false
    }

    /** Opens the zip and lists its frames in name order (the files are numbered 000000_…, 000001_…). */
    private fun readZip(file: File): Pair<ZipFile, List<String>>? = try {
        val z = ZipFile(file)
        val frames = z.entries().asSequence()
            .filter { !it.isDirectory }
            .map { it.name }
            .filter { name ->
                val leaf = name.substringAfterLast('/')
                !name.startsWith("__MACOSX") && !leaf.startsWith(".") &&
                    FrameExtensions.any { leaf.endsWith(it, ignoreCase = true) }
            }
            .sorted()
            .toList()
        if (frames.isEmpty()) {
            z.close()
            file.delete() // unusable cache: re-download on the next attempt
            null
        } else {
            z to frames
        }
    } catch (_: IOException) {
        file.delete()
        null
    }
}

/**
 * Scroll-scrubbed frame sequence: [scrollProgress] (0..1) picks the frame, interpolating between
 * neighbours, so scrolling down plays the jacket coming off and scrolling up plays it in reverse.
 *
 * The frames come from a zip that is downloaded on first use and cached in the app cache, so the
 * APK does not grow. Until the first frame is ready (or if the download fails) nothing is drawn
 * and the artist's normal hero shows through. Set [enabled] to false (e.g. data saver) to skip
 * the download entirely.
 *
 * Progress is only read in the draw phase, so scrolling redraws this layer but never recomposes it.
 * Portrait/square boxes crop to the top of the frame (face and torso); wide boxes fit the full
 * frame and letterbox with the backdrop colour. The foot fades into [fadeColor] (the page tint).
 */
@Composable
fun SeedheMautScrollFrames(
    scrollProgress: () -> Float,
    fadeColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (enabled) ScrollFramesContent(scrollProgress, fadeColor, modifier)
}

@Composable
private fun ScrollFramesContent(
    scrollProgress: () -> Float,
    fadeColor: Color,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val frames = remember(context) { JacketFrames(context) }

    DisposableEffect(frames) { onDispose { frames.close() } }

    LaunchedEffect(frames) {
        if (!frames.open()) return@LaunchedEffect
        launch { frames.loadLowRes() }
        snapshotFlow { frames.indexFor(scrollProgress()) }
            .distinctUntilChanged()
            .collectLatest { frames.prefetchAround(it) }
    }

    val fade by animateFloatAsState(
        targetValue = if (frames.ready) 1f else 0f,
        animationSpec = tween(350),
        label = "seedheJacketFade",
    )
    if (!frames.ready) return

    Canvas(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer { alpha = fade },
    ) {
        @Suppress("UNUSED_VARIABLE")
        val tick = frames.version.intValue // subscribe: redraw when a frame finishes decoding

        drawRect(FrameBackdrop)

        val last = (frames.count - 1).coerceAtLeast(0)
        val position = scrollProgress().coerceIn(0f, 1f) * last
        val from = position.toInt().coerceIn(0, last)
        val blend = position - from
        val a = frames.best(from)
        val b = frames.best(from + 1)
        if (a == null) {
            b?.let { drawFrame(it, 1f) }
        } else {
            drawFrame(a, 1f)
            if (blend > 0.01f && b != null) drawFrame(b, blend)
        }

        drawRect(
            brush = Brush.verticalGradient(
                0.70f to Color.Transparent,
                1f to fadeColor,
            ),
        )
    }
}

private fun DrawScope.drawFrame(image: ImageBitmap, alpha: Float) {
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    val wide = size.width > size.height
    val scale = if (wide) size.height / ih else maxOf(size.width / iw, size.height / ih)
    val dw = iw * scale
    val dh = ih * scale
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(((size.width - dw) / 2f).roundToInt(), 0),
        dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Medium,
    )
}
