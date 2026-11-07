package com.vxhub.amplitude

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.startup.Initializer
import com.vxhub.providers.VxProviderRegistry

/** Registers [VxAmplitudeProvider] at process start. Linking `vxhub-amplitude` is all an app does. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class VxAmplitudeInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        if (VxProviderRegistry.analytics == null) VxProviderRegistry.analytics = VxAmplitudeProvider()
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
