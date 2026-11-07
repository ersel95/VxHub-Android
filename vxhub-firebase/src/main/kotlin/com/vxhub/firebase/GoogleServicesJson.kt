package com.vxhub.firebase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The values FirebaseOptions needs, read from a google-services.json. */
internal data class GoogleServicesConfig(
    val applicationId: String,
    val apiKey: String,
    val projectId: String,
    val gcmSenderId: String?,
    val storageBucket: String?,
    val databaseUrl: String?,
)

/**
 * Reads google-services.json the way the google-services Gradle plugin does, which an app using
 * VxHub never runs: the file comes from the panel at runtime (iOS downloads GoogleService-Info.plist
 * the same way).
 */
internal object GoogleServicesJson {
    /**
     * The client entry for [packageName]. A file without one returns null rather than another app's
     * entry — Firebase would otherwise report this app's events as that app's.
     */
    fun parse(json: String, packageName: String): GoogleServicesConfig? = runCatching {
        val root = Json.parseToJsonElement(json).jsonObject
        val project = root["project_info"]?.jsonObject ?: return null
        val client = root["client"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it.packageName() == packageName } ?: return null
        GoogleServicesConfig(
            applicationId = client["client_info"]?.jsonObject?.string("mobilesdk_app_id") ?: return null,
            apiKey = client["api_key"]?.jsonArray?.firstOrNull()?.jsonObject?.string("current_key") ?: return null,
            projectId = project.string("project_id") ?: return null,
            gcmSenderId = project.string("project_number"),
            storageBucket = project.string("storage_bucket"),
            databaseUrl = project.string("firebase_url"),
        )
    }.getOrNull()

    private fun JsonObject.packageName(): String? =
        this["client_info"]?.jsonObject?.get("android_client_info")?.jsonObject?.string("package_name")

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}
