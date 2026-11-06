package com.vxhub

import android.app.Application
import android.content.Context
import androidx.annotation.RestrictTo
import androidx.startup.Initializer
import com.vxhub.internal.system.ActivityTracker

/**
 * Runs at process start through AndroidX Startup. It only starts tracking the foreground activity,
 * so banners and the review prompt work even when the app initializes VxHub late. No network, no
 * disk: VxHub itself starts in [VxHub.initialize].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class VxHubInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        (context.applicationContext as? Application)?.let(ActivityTracker::install)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
