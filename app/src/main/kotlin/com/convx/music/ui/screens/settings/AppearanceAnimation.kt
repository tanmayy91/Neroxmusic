package com.convx.music.ui.screens.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

private const val FrameCount = 65
private const val AnimationFolder = "appearance_character_v1"
private const val AnimationUrl =
    "https://pdirzojzcohajcvcqbtk.supabase.co/storage/v1/object/public/cv1/app.zip"

private object AppearanceAnimationRepository {
    suspend fun ensureFrames(context: Context): File = withContext(Dispatchers.IO) {
        val target = File(context.filesDir, AnimationFolder)
        if (hasAllFrames(target)) return@withContext target

        val temporary = File(context.filesDir, "$AnimationFolder.tmp")
        temporary.deleteRecursively()
        check(temporary.mkdirs()) { "Unable to create animation cache directory" }
        val zipFile = File(context.cacheDir, "$AnimationFolder.zip")
        try {
            downloadZip(zipFile)
            extractFrames(zipFile, temporary)
            check((0 until FrameCount).all { File(temporary, frameName(it)).isFile }) {
                "Animation archive did not contain all $FrameCount JPEG frames"
            }
            File(temporary, ".complete").writeText("1")
            target.deleteRecursively()
            check(temporary.renameTo(target)) { "Unable to publish animation cache" }
            target
        } finally {
            zipFile.delete()
            temporary.deleteRecursively()
        }
    }

    private fun hasAllFrames(directory: File): Boolean =
        File(directory, ".complete").isFile &&
            (0 until FrameCount).all { File(directory, frameName(it)).isFile }

    private fun downloadZip(destination: File) {
        val connection = (URL(AnimationUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = "GET"
        }
        try {
            check(connection.responseCode in 200..299) {
                "Animation download failed with HTTP ${connection.responseCode}"
            }
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractFrames(zipFile: File, destination: File) {
        val seen = BooleanArray(FrameCount)
        var extractedCount = 0
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val index = entry.name.substringBefore('_').toIntOrNull()
                if (
                    !entry.isDirectory &&
                    entry.name.endsWith(".jpg", ignoreCase = true) &&
                    index != null &&
                    index in 0 until FrameCount &&
                    !seen[index]
                ) {
                    FileOutputStream(File(destination, frameName(index))).use { output ->
                        zip.copyTo(output)
                    }
                    seen[index] = true
                    extractedCount++
                }
            }
        }
        check(extractedCount == FrameCount) {
            "Animation archive contained $extractedCount usable frames"
        }
    }
}

private fun frameName(index: Int): String = "frame_%03d.jpg".format(index)

private class FrameBitmapCache(private val maxEntries: Int = 5) {
    private val entries = LinkedHashMap<Int, Bitmap>(maxEntries, 0.75f, true)

    @Synchronized
    fun get(index: Int): Bitmap? = entries[index]

    @Synchronized
    fun put(index: Int, bitmap: Bitmap) {
        entries[index] = bitmap
        while (entries.size > maxEntries) entries.remove(entries.keys.first())
    }

    fun decode(directory: File, index: Int, targetWidth: Int): Bitmap? {
        get(index)?.let { return it }
        val file = File(directory, frameName(index))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = (bounds.outWidth / targetWidth.coerceAtLeast(1)).coerceAtLeast(1)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(file.path, options)?.also { put(index, it) }
    }
}

@Composable
fun AppearanceAnimationBackground(listState: LazyListState, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val cache = remember { FrameBitmapCache() }
    var directory by remember { mutableStateOf<File?>(null) }
    var titleHeight by remember { mutableIntStateOf(1) }
    var bodyHeight by remember { mutableIntStateOf(1) }
    var bottomHeight by remember { mutableIntStateOf(with(density) { 80.dp.roundToPx() }) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        try {
            directory = AppearanceAnimationRepository.ensureFrames(context)
        } catch (error: Exception) {
            Timber.tag("AppearanceAnimation").e(error, "Unable to prepare appearance animation")
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.toList() }.collect { items ->
            items.firstOrNull { it.index == 0 }?.size?.let { titleHeight = it }
            items.firstOrNull { it.index == 1 }?.size?.let { bodyHeight = it }
            items.firstOrNull { it.index == 2 }?.size?.let { bottomHeight = it }
        }
    }

    val frameIndex by remember(listState, titleHeight, bodyHeight, bottomHeight) {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            val scroll = if (first?.index == 0) {
                listState.firstVisibleItemScrollOffset
            } else {
                titleHeight + listState.firstVisibleItemScrollOffset
            }
            val viewport = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
            val maxScroll = (titleHeight + bodyHeight + bottomHeight - viewport).coerceAtLeast(1)
            ((scroll.toFloat() / maxScroll) * (FrameCount - 1)).roundToInt().coerceIn(0, FrameCount - 1)
        }
    }

    LaunchedEffect(directory, frameIndex, configuration.screenWidthDp) {
        val frames = directory ?: return@LaunchedEffect
        val targetWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }.coerceIn(320, 720)
        withContext(Dispatchers.IO) {
            for (offset in -2..2) {
                val index = (frameIndex + offset).coerceIn(0, FrameCount - 1)
                cache.decode(frames, index, targetWidth)
            }
            cache.get(frameIndex)
        }?.let { bitmap = it }
    }

    Box(modifier = modifier) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.28f,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
