package com.vxhub.facebook

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.startup.Initializer
import com.vxhub.providers.VxProviderRegistry

/** Registers [VxFacebookAppEventsProvider] at process start. Linking `vxhub-facebook` is all an app does. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class VxFacebookInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        if (VxProviderRegistry.facebook == null) VxProviderRegistry.facebook = VxFacebookAppEventsProvider()
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
