package com.vxhub.revenuecat

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.startup.Initializer
import com.vxhub.providers.VxProviderRegistry

/** Registers [VxRevenueCatProvider] at process start. Linking `vxhub-revenuecat` is all an app does. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class VxRevenueCatInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        if (VxProviderRegistry.purchase == null) VxProviderRegistry.purchase = VxRevenueCatProvider()
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
