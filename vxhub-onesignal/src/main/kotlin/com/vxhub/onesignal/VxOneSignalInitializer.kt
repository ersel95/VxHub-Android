package com.vxhub.onesignal

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.startup.Initializer
import com.vxhub.providers.VxProviderRegistry

/** Registers [VxOneSignalProvider] at process start. Linking `vxhub-onesignal` is all an app does. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class VxOneSignalInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        if (VxProviderRegistry.push == null) VxProviderRegistry.push = VxOneSignalProvider()
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
