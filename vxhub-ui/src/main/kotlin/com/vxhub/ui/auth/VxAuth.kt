package com.vxhub.ui.auth

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vxhub.VxAuthConfig
import com.vxhub.VxHub
import com.vxhub.providers.VxProviderRegistry
import com.vxhub.ui.VxHubUI
import com.vxhub.ui.internal.VxScreenHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * The ready-made sign-in flow — iOS's `VxAuthView` / `VxAuthViewController`: sign in, sign up,
 * forgot password → code → new password, email verification when the project requires it, and a
 * new password when an admin issued a temporary one. API failures become sentences a person can
 * act on (`VxAuth_…` keys).
 *
 * Which methods it draws comes from the panel ([VxHub.authConfig]), and it redraws when that
 * arrives: email and password, and Google when the app also links `vxhub-google-signin`. Sign in
 * with Apple is not offered on Android.
 *
 * A plain composable; give it the whole screen. [onFinish] is called once.
 *
 * ```
 * VxAuth(configuration = VxAuthConfiguration(showsGuestOption = true), onFinish = { result ->
 *     if (result is VxAuthResult.SignedIn) println("Signed in as ${result.user.email}")
 *     showAuth = false
 * })
 * ```
 *
 * @param configuration colours, font, logo, guest option; the defaults match iOS.
 * @param startingAt usually [VxAuthStep.SIGN_IN]; [VxAuthStep.SIGN_UP] opens on registration.
 * @param onFinish how the flow ended.
 */
@Composable
public fun VxAuth(
    configuration: VxAuthConfiguration = VxAuthConfiguration(),
    startingAt: VxAuthStep = VxAuthStep.SIGN_IN,
    onFinish: (VxAuthResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One view model per appearance of the screen, kept across configuration changes.
    val instance = rememberSaveable { UUID.randomUUID().toString() }
    val viewModel = viewModel(key = "com.vxhub.ui.auth:$instance") { AuthViewModel(startingAt, VxHubAuthService) }
    val authConfigFlow: StateFlow<VxAuthConfig?> = remember {
        if (VxHub.Internal.isInitialized) VxHub.authConfig else MutableStateFlow(null)
    }
    // A screen shown early (a sign-in wall on first launch) renders before the configuration
    // arrives; collecting it draws the providers as soon as it does.
    val authConfig by authConfigFlow.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnFinish by rememberUpdatedState(onFinish)
    val autofill = LocalAutofillManager.current
    val activity = LocalActivity.current

    LaunchedEffect(viewModel) {
        viewModel.results.collect { result ->
            // Lets a password manager offer to save what was just used, and drops it otherwise.
            if (result is VxAuthResult.SignedIn) autofill?.commit() else autofill?.cancel()
            currentOnFinish(result)
        }
    }
    BackHandler(onBack = viewModel::back)

    val layout = AuthLayout.of(
        step = state.step,
        passwordEnabled = authConfig?.isPasswordEnabled ?: true,
        googleEnabled = authConfig?.isGoogleEnabled == true && VxProviderRegistry.googleSignIn != null,
        showsGuestOption = configuration.showsGuestOption,
        showsLegalLinks = configuration.showsLegalLinks,
    )
    AuthForm(
        viewModel = viewModel,
        state = state,
        layout = layout,
        configuration = configuration,
        onGoogle = { if (activity != null) viewModel.signInWithGoogle(activity) },
        modifier = modifier,
    )
}

/**
 * Presents the sign-in flow full screen from any context — iOS's
 * `showAuth(from:configuration:startingAt:completion:)`. Full screen because signing in is the
 * whole task while it shows; [onFinish] is called once, then the screen closes. For Compose apps
 * that present it themselves, see [VxAuth].
 */
public fun VxHubUI.showAuth(
    context: Context,
    configuration: VxAuthConfiguration = VxAuthConfiguration(),
    startingAt: VxAuthStep = VxAuthStep.SIGN_IN,
    onFinish: (VxAuthResult) -> Unit = {},
) {
    VxScreenHost.launch(context) {
        val colors = configuration.rememberColors()
        HostedSystemBars(activity, colors.background)
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
            VxAuth(
                configuration = configuration,
                startingAt = startingAt,
                onFinish = { result ->
                    onFinish(result)
                    dismiss()
                },
            )
        }
    }
}

/** Light or dark status and navigation bar icons for the screen's own background, when VxHub hosts it. */
@Composable
private fun HostedSystemBars(activity: Activity, background: Color) {
    val view = LocalView.current
    val lightBars = background.luminance() > 0.5f
    SideEffect {
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }
}
