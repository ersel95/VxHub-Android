package com.vxhub.firebase

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.analytics.FirebaseAnalytics
import com.vxhub.VxHub
import com.vxhub.VxLogLevel
import com.vxhub.providers.VxFirebaseProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Firebase Analytics, configured from the panel's google-services.json — so the app ships no
 * Firebase config of its own. When the app *does* configure Firebase itself, that configuration
 * is used as-is.
 */
public class VxFirebaseAnalyticsProvider(context: Context) : VxFirebaseProvider {
    private val appContext: Context = context.applicationContext

    override val isConfigured: Boolean
        get() = runCatching { FirebaseApp.getApps(appContext).isNotEmpty() }.getOrDefault(false)

    override fun configure(context: Context, googleServicesJson: String): Boolean {
        if (isConfigured) return true
        val config = GoogleServicesJson.parse(googleServicesJson, appContext.packageName) ?: run {
            VxHub.Internal.log(
                VxLogLevel.ERROR,
                "google-services.json from the panel has no client for ${appContext.packageName}; Firebase stays off",
            )
            return false
        }
        val options = FirebaseOptions.Builder()
            .setApplicationId(config.applicationId)
            .setApiKey(config.apiKey)
            .setProjectId(config.projectId)
            .apply {
                config.gcmSenderId?.let(::setGcmSenderId)
                config.storageBucket?.let(::setStorageBucket)
                config.databaseUrl?.let(::setDatabaseUrl)
            }
            .build()
        FirebaseApp.initializeApp(appContext, options)
        FirebaseAnalytics.getInstance(appContext)
        VxHub.Internal.log(VxLogLevel.INFO, "Firebase configured for project ${config.projectId}")
        return true
    }

    override suspend fun appInstanceId(): String? {
        if (!isConfigured) return null
        return FirebaseAnalytics.getInstance(appContext).appInstanceId.awaitOrNull()
    }
}

private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) continuation.resume(if (task.isSuccessful) task.result else null)
    }
}
