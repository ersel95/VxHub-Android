package com.vxhub.internal.content

import android.content.Context
import com.vxhub.internal.VxLog
import com.vxhub.internal.storage.VxFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The panel's localization file: a flat JSON object of key → text for the device's language.
 *
 * Kept on disk and in memory. Unlike iOS, which downloads a URL once and never again, the file is
 * fetched on every initialization with HTTP caching, so an edit in the panel reaches apps without
 * a URL change — and costs a 304 when nothing changed.
 */
internal class VxLocalizer(private val context: Context, private val downloader: VxDownloader) {

    @Volatile
    private var strings: Map<String, String> = emptyMap()

    val isLoaded: Boolean get() = strings.isNotEmpty()

    /** Loads the last downloaded file, so text is localized before the network answers. */
    suspend fun loadCached() = withContext(Dispatchers.IO) {
        val file = VxFiles.localizationFile(context)
        if (file.exists()) {
            runCatching { strings = parse(file.readText()) }
                .onFailure { VxLog.w("Cached localization unreadable: ${it.message}") }
        }
    }

    /** Downloads [url] and replaces the strings. A blank URL is normal: the app uses bundled text. */
    suspend fun refresh(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            VxLog.i("No remote localization file configured; using bundled strings")
            return true
        }
        val body = downloader.fetchText(url) ?: return false
        return withContext(Dispatchers.IO) {
            try {
                val parsed = parse(body)
                VxFiles.writeAtomically(VxFiles.localizationFile(context), body.toByteArray(Charsets.UTF_8))
                strings = parsed
                VxLog.i("Localization loaded: ${parsed.size} strings")
                true
            } catch (e: Exception) {
                VxLog.e("Localization file is not a JSON object; keeping the previous one", e)
                false
            }
        }
    }

    fun clear() {
        strings = emptyMap()
        VxFiles.localizationFile(context).delete()
    }

    /** The text for [key], with the `\n` escapes the panel stores turned into line breaks. */
    fun localize(key: String): String? = strings[key]

    private fun parse(body: String): Map<String, String> {
        val root: JsonObject = Json.parseToJsonElement(body).jsonObject
        return buildMap {
            for ((key, value) in root) {
                val text = (value as? JsonPrimitive)?.contentOrNull ?: continue
                put(key, text.replace("\\n", "\n"))
            }
        }
    }
}
