package com.vxhub.ui.auth

import com.vxhub.ui.internal.vxString

/**
 * Every text of the sign-in screens: iOS's `VxLocalizables.Auth`, same keys and the same English
 * defaults, so one panel translation serves both platforms.
 */
internal enum class AuthCopy(val key: String, val english: String) {
    SIGN_IN_TITLE("VxAuth_SignInTitle", "Welcome back"),
    SIGN_IN_SUBTITLE("VxAuth_SignInSubtitle", "Sign in to continue"),
    SIGN_UP_TITLE("VxAuth_SignUpTitle", "Create your account"),
    SIGN_UP_SUBTITLE("VxAuth_SignUpSubtitle", "It only takes a moment"),
    FORGOT_TITLE("VxAuth_ForgotTitle", "Reset your password"),
    FORGOT_SUBTITLE("VxAuth_ForgotSubtitle", "We will email you a code"),
    CODE_TITLE("VxAuth_CodeTitle", "Enter the code"),
    CODE_SUBTITLE("VxAuth_CodeSubtitle", "We sent a 6-digit code to %@"),
    NEW_PASSWORD_TITLE("VxAuth_NewPasswordTitle", "Choose a new password"),
    VERIFY_TITLE("VxAuth_VerifyTitle", "Confirm your email"),

    EMAIL_PLACEHOLDER("VxAuth_EmailPlaceholder", "Email"),
    PASSWORD_PLACEHOLDER("VxAuth_PasswordPlaceholder", "Password"),
    NAME_PLACEHOLDER("VxAuth_NamePlaceholder", "Name (optional)"),
    NEW_PASSWORD_PLACEHOLDER("VxAuth_NewPasswordPlaceholder", "New password"),

    SIGN_IN_BUTTON("VxAuth_SignInButton", "Sign in"),
    SIGN_UP_BUTTON("VxAuth_SignUpButton", "Create account"),
    CONTINUE_BUTTON("VxAuth_ContinueButton", "Continue"),
    SEND_CODE_BUTTON("VxAuth_SendCodeButton", "Send code"),
    RESEND_CODE_BUTTON("VxAuth_ResendCodeButton", "Send it again"),
    SAVE_PASSWORD_BUTTON("VxAuth_SavePasswordButton", "Save password"),
    GOOGLE_BUTTON("VxAuth_GoogleButton", "Continue with Google"),
    GUEST_BUTTON("VxAuth_GuestButton", "Continue without an account"),

    NO_ACCOUNT_PROMPT("VxAuth_NoAccountPrompt", "No account yet? Create one"),
    HAS_ACCOUNT_PROMPT("VxAuth_HasAccountPrompt", "Already have an account? Sign in"),
    FORGOT_PROMPT("VxAuth_ForgotPrompt", "Forgot your password?"),
    SEPARATOR("VxAuth_Separator", "or"),
    LEGAL_NOTICE("VxAuth_LegalNotice", "By continuing you accept our Terms and Privacy Policy."),

    // Errors are phrased as what to do next, not as what the server called it.
    ERROR_INVALID_CREDENTIALS("VxAuth_ErrorInvalidCredentials", "That email and password do not match."),
    ERROR_EMAIL_TAKEN("VxAuth_ErrorEmailTaken", "An account with this email already exists."),
    ERROR_INVALID_CODE("VxAuth_ErrorInvalidCode", "That code is not valid. Ask for a new one if it expired."),
    ERROR_EMAIL_NOT_VERIFIED("VxAuth_ErrorEmailNotVerified", "Confirm your email address before signing in."),
    ERROR_TOO_MANY_ATTEMPTS("VxAuth_ErrorTooManyAttempts", "Too many attempts. Try again in a minute."),
    ERROR_PASSWORD_TOO_SHORT("VxAuth_ErrorPasswordTooShort", "Use at least %d characters."),
    ERROR_PASSWORD_TOO_SIMPLE("VxAuth_ErrorPasswordTooSimple", "Use both letters and numbers."),
    ERROR_NETWORK("VxAuth_ErrorNetwork", "No connection. Check your network and try again."),
    ERROR_GENERIC("VxAuth_ErrorGeneric", "Something went wrong. Please try again."),
    ERROR_ACCOUNT_UNAVAILABLE(
        "VxAuth_ErrorAccountUnavailable",
        "This account is no longer available. Contact support if you think this is a mistake.",
    ),
    ERROR_SIGN_IN_UNAVAILABLE("VxAuth_ErrorSignInUnavailable", "Signing in is turned off right now. Please try again later."),
    ERROR_USE_PASSWORDLESS("VxAuth_ErrorUsePasswordless", "This account signs in with Google or Apple. Use one of the buttons above."),
    ERROR_PROVIDER_UNAVAILABLE("VxAuth_ErrorProviderUnavailable", "This sign-in method is unavailable right now. Try another one."),

    CODE_SENT("VxAuth_CodeSent", "If that address has an account, a code is on its way."),
    MUST_CHANGE_PASSWORD("VxAuth_MustChangePassword", "Choose a password of your own to finish signing in."),
    CLOSE("VxAuth_Close", "Close"),

    // Android only: the password field's visibility toggle, which iOS does not have.
    SHOW_PASSWORD("VxAuth_ShowPassword", "Show password"),
    HIDE_PASSWORD("VxAuth_HidePassword", "Hide password"),
    ;

    /** The panel's translation, else VxHub's bundled English, else iOS's default. */
    fun text(): String = vxString(key, english)
}

/** A line under the form: an error, or a notice such as "a code is on its way". */
internal data class AuthMessage(
    val copy: AuthCopy,
    val isError: Boolean = true,
    /** Fills the text's `%d` / `%@`, as iOS's `String(format:)`. */
    val argument: Any? = null,
) {
    fun text(): String = copy.text().let { if (argument == null) it else it.iosFormat(argument) }
}

/**
 * iOS's `String(format:)` for what VxHub's texts use: `%@`, `%d`, `%i`, `%u`, `%s` (with an optional
 * `l`/`ll` length and an optional `n$` position) and `%%`. Panel translations are shared with iOS,
 * so they carry iOS's specifiers rather than Java's.
 */
internal fun String.iosFormat(vararg arguments: Any?): String {
    val out = StringBuilder(length + 16)
    var nextArgument = 0
    var i = 0
    while (i < length) {
        val c = this[i]
        if (c != '%' || i + 1 >= length) {
            out.append(c)
            i++
            continue
        }
        if (this[i + 1] == '%') {
            out.append('%')
            i += 2
            continue
        }
        var j = i + 1
        while (j < length && this[j].isDigit()) j++
        val position = if (j > i + 1 && j < length && this[j] == '$') substring(i + 1, j).toInt().also { j++ } else null
        if (position == null) j = i + 1
        while (j < length && this[j] == 'l') j++
        if (j < length && this[j] in "@diusDUS") {
            val index = if (position != null) position - 1 else nextArgument++
            out.append(arguments.getOrNull(index)?.toString().orEmpty())
            i = j + 1
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}
