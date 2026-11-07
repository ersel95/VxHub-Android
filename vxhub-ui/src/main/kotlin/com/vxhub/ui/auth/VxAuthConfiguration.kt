package com.vxhub.ui.auth

import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import com.vxhub.VxUser
import com.vxhub.ui.VxFont

/**
 * Appearance of the ready-made sign-in screens ([VxAuth], [showAuth]) — iOS's
 * `VxAuthConfiguration`, option for option, with the same defaults. Present the screens with
 * `VxAuthConfiguration()` and override only what the brand needs.
 *
 * iOS's defaults are dynamic colours with a light and a dark value. Here every colour defaults to
 * [Color.Unspecified], which stands for that same pair: the light value in light mode, the dark
 * value in dark mode (noted on each option as light / dark). A colour you pass is used in both; to
 * vary it, build the configuration in composition and choose with `isSystemInDarkTheme()`.
 */
@Immutable
public class VxAuthConfiguration(
    /** Typeface of the screens' texts. */
    public val font: VxFont = VxFont.Rounded,
    /** Screen background. White / black. */
    public val backgroundColor: Color = Color.Unspecified,
    /** The step's title. Black / white. */
    public val titleColor: Color = Color.Unspecified,
    /** Subtitle, notices, "or", the guest link and the legal notice. #636973 / #9E9E9E. */
    public val subtitleColor: Color = Color.Unspecified,
    /** Background of the text fields. #F5F6F8 / #1C1C1E. */
    public val fieldBackgroundColor: Color = Color.Unspecified,
    /** Text and cursor of the text fields. Black / white. */
    public val fieldTextColor: Color = Color.Unspecified,
    /** Placeholders, and the password field's visibility toggle. #9E9E9E / #6E6E73. */
    public val fieldPlaceholderColor: Color = Color.Unspecified,
    /** Border of the text fields. #E5E5EA / #2C2C2E. */
    public val fieldBorderColor: Color = Color.Unspecified,
    /** Border of the focused text field. Black / white. */
    public val fieldFocusedBorderColor: Color = Color.Unspecified,
    /** The primary button ("Sign in", "Create account", …). Black / white. */
    public val primaryButtonColor: Color = Color.Unspecified,
    /** Text and progress indicator of the primary button. White / black. */
    public val primaryButtonTextColor: Color = Color.Unspecified,
    /** "No account yet? Create one" and "Already have an account? Sign in". Black / white. */
    public val secondaryButtonTextColor: Color = Color.Unspecified,
    /** Background of "Continue with Google". White / #1C1C1E. */
    public val providerButtonBackgroundColor: Color = Color.Unspecified,
    /** Text and mark of "Continue with Google". Black / white. */
    public val providerButtonTextColor: Color = Color.Unspecified,
    /** Border of "Continue with Google". #E5E5EA / #2C2C2E. */
    public val providerButtonBorderColor: Color = Color.Unspecified,
    /** "Forgot your password?" and "Send it again". #0A84FF in both. */
    public val linkColor: Color = Color.Unspecified,
    /** Error messages. #D70015 / #FF453A. */
    public val errorColor: Color = Color.Unspecified,
    /** The lines either side of "or". #E5E5EA / #2C2C2E. */
    public val separatorColor: Color = Color.Unspecified,
    /** The close button. #636973 / #9E9E9E. */
    public val closeButtonTintColor: Color = Color.Unspecified,
    /** Shown above the title, 44 dp tall, when set. A drawable resource. */
    @param:DrawableRes public val logo: Int? = null,
    /** Lets someone skip signing in and keep using the app without an account. */
    public val showsGuestOption: Boolean = false,
    /** Shows the Terms and Privacy notice under the sign-in and sign-up forms. */
    public val showsLegalLinks: Boolean = true,
)

/** Which screen of the flow is showing — iOS's `VxAuthStep`. */
public enum class VxAuthStep {
    SIGN_IN,
    SIGN_UP,
    FORGOT_PASSWORD,

    /** Code entry for a password reset. */
    ENTER_CODE,
    NEW_PASSWORD,

    /** Code entry confirming the signed-in account's email address. */
    VERIFY_EMAIL,
}

/** How the flow ended — iOS's `VxAuthResult`. */
public sealed interface VxAuthResult {
    /** Signed in, signed up, or reset and signed in. */
    public data class SignedIn(val user: VxUser) : VxAuthResult

    /**
     * Closed without an account session: the close button or back on the first step. Also how a
     * Google sign-in ends when the panel has accounts off for Google, as on iOS — the device itself
     * is then signed in with Google.
     */
    public data object Cancelled : VxAuthResult

    /** The person chose to keep using the app without an account. */
    public data object ContinuedAsGuest : VxAuthResult
}

/** [VxAuthConfiguration]'s colours with every unspecified one replaced by iOS's default. */
@Immutable
internal data class AuthColors(
    val background: Color,
    val title: Color,
    val subtitle: Color,
    val fieldBackground: Color,
    val fieldText: Color,
    val fieldPlaceholder: Color,
    val fieldBorder: Color,
    val fieldFocusedBorder: Color,
    val primaryButton: Color,
    val primaryButtonText: Color,
    val secondaryButtonText: Color,
    val providerBackground: Color,
    val providerText: Color,
    val providerBorder: Color,
    val link: Color,
    val error: Color,
    val separator: Color,
    val closeTint: Color,
)

/** The colours to draw with in light ([dark] false) or dark mode. */
internal fun VxAuthConfiguration.colors(dark: Boolean): AuthColors {
    fun Color.or(light: Color, darkDefault: Color): Color = takeOrElse { if (dark) darkDefault else light }
    return AuthColors(
        background = backgroundColor.or(Color.White, Color.Black),
        title = titleColor.or(Color.Black, Color.White),
        subtitle = subtitleColor.or(Color(0xFF636973), Color(0xFF9E9E9E)),
        fieldBackground = fieldBackgroundColor.or(Color(0xFFF5F6F8), Color(0xFF1C1C1E)),
        fieldText = fieldTextColor.or(Color.Black, Color.White),
        fieldPlaceholder = fieldPlaceholderColor.or(Color(0xFF9E9E9E), Color(0xFF6E6E73)),
        fieldBorder = fieldBorderColor.or(Color(0xFFE5E5EA), Color(0xFF2C2C2E)),
        fieldFocusedBorder = fieldFocusedBorderColor.or(Color.Black, Color.White),
        primaryButton = primaryButtonColor.or(Color.Black, Color.White),
        primaryButtonText = primaryButtonTextColor.or(Color.White, Color.Black),
        secondaryButtonText = secondaryButtonTextColor.or(Color.Black, Color.White),
        providerBackground = providerButtonBackgroundColor.or(Color.White, Color(0xFF1C1C1E)),
        providerText = providerButtonTextColor.or(Color.Black, Color.White),
        providerBorder = providerButtonBorderColor.or(Color(0xFFE5E5EA), Color(0xFF2C2C2E)),
        link = linkColor.or(Color(0xFF0A84FF), Color(0xFF0A84FF)),
        error = errorColor.or(Color(0xFFD70015), Color(0xFFFF453A)),
        separator = separatorColor.or(Color(0xFFE5E5EA), Color(0xFF2C2C2E)),
        closeTint = closeButtonTintColor.or(Color(0xFF636973), Color(0xFF9E9E9E)),
    )
}

/** The colours for the current light or dark mode. */
@Composable
internal fun VxAuthConfiguration.rememberColors(): AuthColors {
    val dark = isSystemInDarkTheme()
    return remember(this, dark) { colors(dark) }
}
