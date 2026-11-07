package com.vxhub.appsflyer

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.startup.Initializer
import com.vxhub.providers.VxProviderRegistry

/** Registers [VxAppsFlyerProvider] at process start. Linking `vxhub-appsflyer` is all an app does. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class VxAppsFlyerInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        if (VxProviderRegistry.attribution == null) VxProviderRegistry.attribution = VxAppsFlyerProvider()
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
