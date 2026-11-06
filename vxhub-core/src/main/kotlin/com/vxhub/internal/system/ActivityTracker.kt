package com.vxhub.internal.system

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Remembers the foreground activity, so SDK features that need one (banners, the review prompt)
 * work without the app passing it around. Installed at process start by [com.vxhub.VxHubInitializer].
 */
internal object ActivityTracker : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var resumed: WeakReference<Activity>? = null

    @Volatile
    private var installed = false

    val current: Activity?
        get() = resumed?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    fun install(application: Application) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            application.registerActivityLifecycleCallbacks(this)
            installed = true
        }
    }

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
