package com.vxhub.internal.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vxhub.internal.VxLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * The SDK's key-value state, in one DataStore file under `noBackupFilesDir`.
 *
 * No-backup on purpose: Auto Backup would restore a device id, an auth session or a pending
 * purchase onto a *different* phone, where the backend would merge two devices into one.
 *
 * Reads are synchronous from an in-memory snapshot that [load] fills before VxHub makes its first
 * request and every write refreshes, so header building and property getters never block on disk.
 */
internal class VxPreferences(private val dataStore: DataStore<Preferences>, private val scope: CoroutineScope) {

    private val snapshot = MutableStateFlow(emptyPreferences())

    /** Loads the file into memory. Safe to call more than once. */
    suspend fun load() {
        snapshot.value = runCatching { dataStore.data.first() }
            .onFailure { VxLog.e("Preferences could not be read; starting empty", it) }
            .getOrDefault(emptyPreferences())
    }

    operator fun <T> get(key: Preferences.Key<T>): T? = snapshot.value[key]

    suspend fun edit(block: (MutablePreferences) -> Unit) {
        try {
            snapshot.value = dataStore.edit(block)
        } catch (e: Exception) {
            VxLog.e("Preferences write failed", e)
        }
    }

    /** For callers on a synchronous path; the write still completes off the main thread. */
    fun editAsync(block: (MutablePreferences) -> Unit) {
        scope.launch { edit(block) }
    }

    object Keys {
        val DEVICE_ID_FALLBACK = stringPreferencesKey("device_id_fallback")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val DOWNLOADED_URLS = stringSetPreferencesKey("downloaded_urls")
        val LAST_REVIEW_REQUEST_AT = longPreferencesKey("last_review_request_at")
        val LAST_RESTORED_VID = stringPreferencesKey("last_restored_vid")
        val PENDING_PURCHASES = stringPreferencesKey("pending_purchases")
        val ACTIVE_NON_CONSUMABLES = stringSetPreferencesKey("active_non_consumables")
        val RETENTION_COIN_GIVEN = booleanPreferencesKey("retention_coin_given")
        val AUTH_SESSION = stringPreferencesKey("auth_session")
        val LAST_REGISTRATION = stringPreferencesKey("last_registration")
    }

    companion object {
        fun create(context: Context, scope: CoroutineScope): VxPreferences {
            val dataStore = PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = { File(VxFiles.baseDir(context), "vxhub.preferences_pb") },
            )
            return VxPreferences(dataStore, scope)
        }
    }
}

/** Where the SDK keeps files. Everything sits under `noBackupFilesDir/vxhub`. */
internal object VxFiles {
    fun baseDir(context: Context): File = File(context.noBackupFilesDir, "vxhub").apply { mkdirs() }

    fun imagesDir(context: Context): File = File(baseDir(context), "images").apply { mkdirs() }

    fun videosDir(context: Context): File = File(baseDir(context), "videos").apply { mkdirs() }

    fun thirdPartyDir(context: Context): File = File(baseDir(context), "third_party").apply { mkdirs() }

    fun localizationFile(context: Context): File = File(baseDir(context), "localization.json")

    /** Writes through a temp file so a crash mid-write never leaves a half file behind. */
    fun writeAtomically(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                error("Could not move ${temp.name} into place")
            }
        }
    }
}
