package com.vxhub.googlesignin

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.startup.Initializer
import com.vxhub.providers.VxProviderRegistry

/** Registers [VxCredentialManagerGoogleSignIn] at process start. Linking `vxhub-google-signin` is all an app does. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class VxGoogleSignInInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        if (VxProviderRegistry.googleSignIn == null) VxProviderRegistry.googleSignIn = VxCredentialManagerGoogleSignIn()
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
