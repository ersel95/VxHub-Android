package com.vxhub.amplitude

import android.app.Application
import android.content.Context
import com.amplitude.android.Amplitude
import com.amplitude.android.AutocaptureOption
import com.amplitude.android.Configuration
import com.amplitude.core.events.Identify
import com.amplitude.experiment.Experiment
import com.amplitude.experiment.ExperimentClient
import com.amplitude.experiment.ExperimentConfig
import com.amplitude.experiment.ExperimentUser
import com.vxhub.VxHub
import com.vxhub.VxLogLevel
import com.vxhub.providers.VxAnalyticsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Amplitude analytics and Amplitude Experiment — iOS's `VxAmplitudeManager`: sessions and app
 * lifecycle tracked automatically, events uploaded one by one, and experiments started for the
 * device with `isSubscriber` and `user-platform` as user properties.
 */
public class VxAmplitudeProvider : VxAnalyticsProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var amplitude: Amplitude? = null

    @Volatile
    private var experiment: ExperimentClient? = null

    override fun initialize(
        context: Context,
        userId: String,
        apiKey: String,
        deploymentKey: String?,
        deviceId: String,
        isSubscriber: Boolean,
    ) {
        if (amplitude != null) return
        val application = context.applicationContext as Application
        amplitude = Amplitude(
            Configuration(
                apiKey = apiKey,
                context = application,
                flushQueueSize = 1,
                autocapture = setOf(AutocaptureOption.SESSIONS, AutocaptureOption.APP_LIFECYCLES),
            ),
        ).apply {
            setUserId(userId)
            setDeviceId(deviceId)
        }

        if (deploymentKey.isNullOrBlank()) return
        val client = Experiment.initializeWithAmplitudeAnalytics(application, deploymentKey, ExperimentConfig.builder().build())
        experiment = client
        val user = ExperimentUser.builder()
            .userId(deviceId)
            .deviceId(deviceId)
            .userProperty("isSubscriber", isSubscriber)
            .userProperty("user-platform", "android")
            .build()
        scope.launch {
            runCatching { client.start(user).get() }
                .onFailure { VxHub.Internal.log(VxLogLevel.ERROR, "Experiment start error: ${it.message}", it) }
        }
    }

    override fun logEvent(name: String, properties: Map<String, Any?>?) {
        val client = amplitude ?: return
        val values = properties?.filterValues { it != null }?.mapValues { it.value as Any }
        if (values == null) client.track(name) else client.track(name, values)
    }

    override fun variant(flagKey: String): String? = experiment?.variant(flagKey)?.value

    override fun variantPayload(flagKey: String): Map<String, Any?>? = experiment?.variant(flagKey)?.payload?.toPlainMap()

    override fun changeUserId(userId: String?) {
        amplitude?.setUserId(userId)
    }

    override fun setLoginData(fullName: String?, email: String?) {
        val client = amplitude ?: return
        val identify = Identify().set("user-platform", "android")
        if (fullName != null) identify.set("name", fullName)
        if (email != null) identify.set("email", email)
        client.identify(identify)
    }
}

/** Experiment payloads arrive as JSON objects, maps or JSON text; callers get plain Kotlin maps. */
internal fun Any.toPlainMap(): Map<String, Any?>? = when (this) {
    is JSONObject -> keys().asSequence().associateWith { opt(it).toPlain() }
    is Map<*, *> -> entries.associate { (k, v) -> k.toString() to v.toPlain() }
    is String -> runCatching { JSONObject(this) }.getOrNull()?.toPlainMap()
    else -> null
}

private fun Any?.toPlain(): Any? = when (this) {
    null, JSONObject.NULL -> null
    is JSONObject -> toPlainMap()
    is JSONArray -> (0 until length()).map { opt(it).toPlain() }
    is Map<*, *> -> toPlainMap()
    is List<*> -> map { it.toPlain() }
    else -> this
}
