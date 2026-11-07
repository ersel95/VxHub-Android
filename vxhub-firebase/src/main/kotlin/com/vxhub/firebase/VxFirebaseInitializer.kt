package com.vxhub.firebase

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.startup.Initializer
import com.vxhub.providers.VxProviderRegistry

/** Registers [VxFirebaseAnalyticsProvider] at process start. Linking `vxhub-firebase` is all an app does. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class VxFirebaseInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        if (VxProviderRegistry.firebase == null) VxProviderRegistry.firebase = VxFirebaseAnalyticsProvider(context.applicationContext)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
