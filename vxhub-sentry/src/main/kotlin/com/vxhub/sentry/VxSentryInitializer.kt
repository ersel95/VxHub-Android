package com.vxhub.sentry

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.startup.Initializer
import com.vxhub.providers.VxProviderRegistry

/** Registers [VxSentryProvider] at process start. Linking `vxhub-sentry` is all an app does. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class VxSentryInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        if (VxProviderRegistry.crashReporting == null) VxProviderRegistry.crashReporting = VxSentryProvider()
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
