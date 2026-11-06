package com.vxhub.ui

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.startup.Initializer
import com.vxhub.VxHubInitializer
import com.vxhub.providers.VxProviderRegistry
import com.vxhub.ui.banner.VxBannerProviderImpl
import com.vxhub.ui.web.VxWebPresenterImpl

/** Registers the UI module's banner and in-app web page providers at process start. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class VxUiInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        if (VxProviderRegistry.banner == null) VxProviderRegistry.banner = VxBannerProviderImpl
        if (VxProviderRegistry.webPresenter == null) VxProviderRegistry.webPresenter = VxWebPresenterImpl
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = listOf(VxHubInitializer::class.java)
}
