package com.vxhub.internal.content

import android.content.Context
import android.net.Uri
import com.vxhub.internal.VxLog
import com.vxhub.internal.storage.VxFiles
import com.vxhub.internal.storage.VxPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Downloads files the panel points at (localization, images, videos, google-services.json).
 *
 * Uses a client without VxHub's headers: these URLs are on CDNs, and the hub id has no business
 * travelling to them.
 */
internal class VxDownloader(
    private val context: Context,
    private val client: OkHttpClient,
    private val prefs: VxPreferences,
) {

    /** A text resource, revalidated through the HTTP cache. Null on any failure. */
    suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    VxLog.w("Download of $url failed with ${response.code}")
                    null
                } else {
                    response.body.string()
                }
            }
        } catch (e: IOException) {
            VxLog.w("Download of $url failed: ${e.message}")
            null
        } catch (e: IllegalArgumentException) {
            VxLog.w("Not a downloadable URL: $url")
            null
        }
    }

    /**
     * Saves [url] into [directory] as [fileName]. Already-downloaded URLs are not fetched again, as
     * on iOS. Throws on failure.
     */
    suspend fun download(url: String, directory: File, fileName: String): File = withContext(Dispatchers.IO) {
        val target = File(directory, fileName)
        if (target.exists() && isDownloaded(url)) return@withContext target
        VxLog.i("Downloading $url")
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid URL: $url", e)
        }
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val temp = File(directory, ".$fileName.part")
            response.body.byteStream().use { input -> temp.outputStream().use { input.copyTo(it) } }
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) throw IOException("Could not store $fileName")
            }
        }
        markDownloaded(url)
        target
    }

    fun isDownloaded(url: String): Boolean = url in prefs[VxPreferences.Keys.DOWNLOADED_URLS].orEmpty()

    private suspend fun markDownloaded(url: String) {
        prefs.edit { it[VxPreferences.Keys.DOWNLOADED_URLS] = it[VxPreferences.Keys.DOWNLOADED_URLS].orEmpty() + url }
    }

    suspend fun forget(url: String) {
        prefs.edit { it[VxPreferences.Keys.DOWNLOADED_URLS] = it[VxPreferences.Keys.DOWNLOADED_URLS].orEmpty() - url }
    }

    // region Images and videos

    fun imageFile(url: String, isLocalized: Boolean): File? =
        imageFileName(url, isLocalized)?.let { File(VxFiles.imagesDir(context), it) }?.takeIf { it.exists() }

    suspend fun downloadImage(url: String, isLocalized: Boolean): File {
        val name = imageFileName(url, isLocalized) ?: throw IOException("Invalid URL: $url")
        return download(url, VxFiles.imagesDir(context), name)
    }

    fun videoFile(url: String): File? = videoFileName(url)?.let { File(VxFiles.videosDir(context), it) }?.takeIf { it.exists() }

    suspend fun downloadVideo(url: String): File {
        val name = videoFileName(url) ?: throw IOException("Invalid URL: $url")
        return download(url, VxFiles.videosDir(context), name)
    }

    // endregion

    internal companion object {
        /**
         * iOS names a localized image after the last three path segments joined with `-`
         * (`…/en/onboarding/hero.png` → `en-onboarding-hero.png`), so each language's copy of the
         * same file name gets its own file; otherwise after the last segment.
         */
        fun imageFileName(url: String, isLocalized: Boolean): String? {
            val segments = runCatching { Uri.parse(url).pathSegments }.getOrNull()?.filter { it.isNotBlank() }
            if (segments.isNullOrEmpty()) return null
            return if (isLocalized) segments.takeLast(3).joinToString("-") else segments.last()
        }

        fun videoFileName(url: String): String? {
            val last = runCatching { Uri.parse(url).lastPathSegment }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
            return if (last.endsWith(".mp4", ignoreCase = true)) last else "$last.mp4"
        }
    }
}
