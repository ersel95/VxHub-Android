package com.vxhub.onesignal

import android.content.Context
import com.onesignal.OneSignal
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationWillDisplayEvent
import org.json.JSONArray
import org.json.JSONObject
import com.vxhub.providers.VxPushProvider

/**
 * OneSignal push — iOS's `VxOneSignalManager`: initialized with the panel's app id and logged in
 * with the device's vid, so a push can target a device from the panel.
 */
public class VxOneSignalProvider : VxPushProvider {
    @Volatile
    private var initialized = false

    @Volatile
    private var notificationListener: ((Map<String, Any?>) -> Unit)? = null

    override fun initialize(context: Context, appId: String, externalId: String) {
        if (initialized) return
        OneSignal.initWithContext(context.applicationContext, appId)
        initialized = true
        OneSignal.login(externalId)
        OneSignal.Notifications.addForegroundLifecycleListener(
            object : INotificationLifecycleListener {
                override fun onWillDisplay(event: INotificationWillDisplayEvent) {
                    notificationListener?.invoke(event.notification.additionalData?.toMap().orEmpty())
                }
            },
        )
    }

    override fun changeExternalId(externalId: String) {
        if (!initialized) return
        OneSignal.logout()
        OneSignal.login(externalId)
    }

    override fun addEmail(email: String) {
        if (initialized) OneSignal.User.addEmail(email)
    }

    override fun logout() {
        if (initialized) OneSignal.logout()
    }

    override val subscriptionId: String?
        get() = if (initialized) OneSignal.User.pushSubscription.id.takeIf { it.isNotBlank() } else null

    override val pushToken: String?
        get() = if (initialized) OneSignal.User.pushSubscription.token.takeIf { it.isNotBlank() } else null

    override fun setNotificationListener(listener: (Map<String, Any?>) -> Unit) {
        notificationListener = listener
    }
}

private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { opt(it).toPlain() }

private fun Any?.toPlain(): Any? = when (this) {
    null, JSONObject.NULL -> null
    is JSONObject -> toMap()
    is JSONArray -> (0 until length()).map { opt(it).toPlain() }
    else -> this
}
