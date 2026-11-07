package com.vxhub.facebook

import android.app.Application
import android.content.Context
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.vxhub.providers.VxFacebookProvider

/**
 * Facebook App Events with the panel's app id and client token — iOS's `VxFacebookManager`. The
 * manifest turns the SDK's own start-up initialization off, because the id only exists once the
 * backend answered.
 */
public class VxFacebookAppEventsProvider : VxFacebookProvider {
    @Volatile
    private var appContext: Context? = null

    override fun initialize(context: Context, appId: String, clientToken: String, appName: String?) {
        val application = context.applicationContext as Application
        appContext = application
        FacebookSdk.setApplicationId(appId)
        FacebookSdk.setClientToken(clientToken)
        appName?.let(FacebookSdk::setApplicationName)
        @Suppress("DEPRECATION") // The documented way to start the SDK with an id set at runtime.
        FacebookSdk.sdkInitialize(application)
        FacebookSdk.setAutoInitEnabled(true)
        FacebookSdk.fullyInitialize()
        FacebookSdk.setAutoLogAppEventsEnabled(true)
        AppEventsLogger.activateApp(application)
    }

    override val anonymousId: String
        get() = appContext?.let { AppEventsLogger.getAnonymousAppDeviceGUID(it) }.orEmpty()

    override fun setAdvertiserTrackingEnabled(enabled: Boolean) {
        FacebookSdk.setAdvertiserIDCollectionEnabled(enabled)
    }
}
