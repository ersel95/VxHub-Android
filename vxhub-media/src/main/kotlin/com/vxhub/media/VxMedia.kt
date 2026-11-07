package com.vxhub.media

import android.content.Context
import com.airbnb.lottie.LottieCompositionFactory
import com.vxhub.VxHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Downloads and lookups shared by the media helpers. */
public object VxMedia {
    private val client by lazy { OkHttpClient() }

    /** Where downloaded Lottie files live, next to VxHub's other no-backup files. */
    internal fun animationsDir(context: Context): File = File(context.noBackupFilesDir, "vxhub/animations").apply { mkdirs() }

    /**
     * Downloads a Lottie JSON so [VxLottie] and [VxLottieViews] can play it by file name offline —
     * iOS's `downloadLottieAnimation(from:)`. Already-downloaded files are not fetched again.
     */
    public suspend fun downloadLottieAnimation(context: Context, url: String): File = withContext(Dispatchers.IO) {
        val name = url.substringAfterLast('/').substringBefore('?').ifBlank { "animation.json" }
        val target = File(animationsDir(context), name)
        if (target.exists() && target.length() > 0) return@withContext target
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid URL: $url", e)
        }
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val temp = File(target.parentFile, ".$name.part")
            response.body.byteStream().use { input -> temp.outputStream().use { input.copyTo(it) } }
            if (!temp.renameTo(target)) throw IOException("Could not store $name")
        }
        // Parse once so a broken file fails here rather than at the first play.
        loadComposition(target)
        target
    }

    /** A downloaded animation by file name (with or without `.json`), or null. */
    public fun downloadedAnimation(context: Context, name: String): File? {
        val dir = animationsDir(context)
        return listOf(name, "$name.json").map { File(dir, it) }.firstOrNull { it.exists() && it.length() > 0 }
    }

    private suspend fun loadComposition(file: File) = suspendCancellableCoroutine { continuation ->
        LottieCompositionFactory.fromJsonInputStream(file.inputStream(), file.absolutePath)
            .addListener { if (continuation.isActive) continuation.resume(it) }
            .addFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }

    /** The file VxHub downloaded for [url], else [url] itself for Coil to fetch. */
    internal fun localFileOrUrl(url: String, isLocalized: Boolean): Any =
        runCatching { VxHub.downloadedImageFile(url, isLocalized) }.getOrNull() ?: url
}
