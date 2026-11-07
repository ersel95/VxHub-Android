package com.vxhub.ui.auth

import com.vxhub.VxHubException

/** Turns failures into sentences a person can act on — iOS's `VxAuthViewController.show(_:)`, case for case. */
internal object AuthErrors {

    fun message(error: Throwable): AuthMessage {
        if (error !is VxHubException.AuthFailed) {
            return AuthMessage(if (error is VxHubException.NetworkUnavailable) AuthCopy.ERROR_NETWORK else AuthCopy.ERROR_GENERIC)
        }
        if (error.statusCode == 429) return AuthMessage(AuthCopy.ERROR_TOO_MANY_ATTEMPTS)
        // PASSWORD_TOO_SHORT carries the project's minimum after a colon.
        if (error.code.startsWith("PASSWORD_TOO_SHORT")) {
            val minimum = error.code.substringAfterLast(':').toIntOrNull() ?: AuthValidation.DEFAULT_MIN_PASSWORD_LENGTH
            return AuthMessage(AuthCopy.ERROR_PASSWORD_TOO_SHORT, argument = minimum)
        }
        val copy = when (error.code) {
            "INVALID_CREDENTIALS" -> AuthCopy.ERROR_INVALID_CREDENTIALS
            "EMAIL_ALREADY_REGISTERED" -> AuthCopy.ERROR_EMAIL_TAKEN
            "INVALID_CODE" -> AuthCopy.ERROR_INVALID_CODE
            "EMAIL_NOT_VERIFIED" -> AuthCopy.ERROR_EMAIL_NOT_VERIFIED
            "PASSWORD_TOO_SIMPLE" -> AuthCopy.ERROR_PASSWORD_TOO_SIMPLE
            // Banned or deleted: "try again" would send them round the same loop.
            "ACCOUNT_UNAVAILABLE" -> AuthCopy.ERROR_ACCOUNT_UNAVAILABLE
            "AUTH_NOT_ENABLED", "PASSWORD_AUTH_NOT_ENABLED" -> AuthCopy.ERROR_SIGN_IN_UNAVAILABLE
            // An account, just not a password one: point at the buttons that work.
            "NO_PASSWORD_SET" -> AuthCopy.ERROR_USE_PASSWORDLESS
            "GOOGLE_LOGIN_NOT_ENABLED", "APPLE_LOGIN_NOT_ENABLED",
            "GOOGLE_LOGIN_NOT_CONFIGURED", "APPLE_LOGIN_NOT_CONFIGURED",
            "INVALID_GOOGLE_TOKEN", "INVALID_APPLE_TOKEN",
            -> AuthCopy.ERROR_PROVIDER_UNAVAILABLE
            else -> AuthCopy.ERROR_GENERIC
        }
        return AuthMessage(copy)
    }

    /**
     * Whether a Google sign-in failed because the person dismissed the account picker — the most
     * common outcome and not worth an error (iOS's `showProviderError`). Credential Manager reports
     * it as `GetCredentialCancellationException`; the core passes provider failures on as their cause.
     */
    fun isUserCancellation(error: Throwable): Boolean = generateSequence(error) { it.cause }.take(8).any {
        it is VxHubException.SignInCancelled ||
            it.javaClass.simpleName == "GetCredentialCancellationException" ||
            (it is VxHubException.SignInFailed && it.reason.contains("cancel", ignoreCase = true))
    }
}

/** The checks iOS makes before calling the API. The server stays the authority. */
internal object AuthValidation {
    const val DEFAULT_MIN_PASSWORD_LENGTH: Int = 8

    /** Deliberately loose, as iOS: a strict pattern mostly rejects addresses that are in fact valid. */
    fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        return '@' in trimmed && '.' in trimmed && trimmed.length >= 5
    }

    /** Sign in and sign up. iOS answers an unusable address with the generic sentence. */
    fun credentialsProblem(email: String, password: String, minimumLength: Int): AuthMessage? = when {
        !isValidEmail(email) -> AuthMessage(AuthCopy.ERROR_GENERIC)
        password.length < minimumLength -> AuthMessage(AuthCopy.ERROR_PASSWORD_TOO_SHORT, argument = minimumLength)
        else -> null
    }

    fun codeProblem(code: String): AuthMessage? =
        if (code.trim().length >= 4) null else AuthMessage(AuthCopy.ERROR_INVALID_CODE)

    /** The new password itself is checked by the server, which answers with its own minimum. */
    fun newPasswordProblem(password: String): AuthMessage? =
        if (password.isEmpty()) AuthMessage(AuthCopy.ERROR_GENERIC) else null
}

/** Which parts of the form a step shows — iOS's `VxAuthRootView.render`. */
internal data class AuthLayout(
    val showsName: Boolean,
    val showsEmail: Boolean,
    val showsPassword: Boolean,
    val showsCode: Boolean,
    val showsPrimary: Boolean,
    val showsForgot: Boolean,
    val showsResend: Boolean,
    val showsSecondary: Boolean,
    val showsSeparator: Boolean,
    val showsGoogle: Boolean,
    val showsGuest: Boolean,
    val showsLegal: Boolean,
) {
    companion object {
        /**
         * @param passwordEnabled the panel offers email and password (iOS assumes so until the
         *   configuration arrives).
         * @param googleEnabled the panel offers Google and the app links `vxhub-google-signin`.
         *   Apple is never drawn on Android: there is no Apple provider.
         */
        fun of(
            step: VxAuthStep,
            passwordEnabled: Boolean,
            googleEnabled: Boolean,
            showsGuestOption: Boolean,
            showsLegalLinks: Boolean,
        ): AuthLayout {
            // Providers belong on the first screen only; inside a reset they would take the person
            // out of what they were doing.
            val onEntry = step == VxAuthStep.SIGN_IN || step == VxAuthStep.SIGN_UP
            // A project without password sign-in shows the providers alone.
            val formHidden = onEntry && !passwordEnabled
            return AuthLayout(
                showsName = step == VxAuthStep.SIGN_UP && !formHidden,
                showsEmail = step in EMAIL_STEPS && !formHidden,
                showsPassword = step in PASSWORD_STEPS && !formHidden,
                showsCode = step in CODE_STEPS,
                showsPrimary = !formHidden,
                showsForgot = step == VxAuthStep.SIGN_IN && passwordEnabled,
                showsResend = step in CODE_STEPS,
                showsSecondary = step in EMAIL_STEPS && !formHidden,
                // Only meaningful when there is something to separate password sign-in from.
                showsSeparator = onEntry && googleEnabled && passwordEnabled,
                showsGoogle = onEntry && googleEnabled,
                showsGuest = onEntry && showsGuestOption,
                showsLegal = onEntry && showsLegalLinks,
            )
        }

        private val EMAIL_STEPS = setOf(VxAuthStep.SIGN_IN, VxAuthStep.SIGN_UP, VxAuthStep.FORGOT_PASSWORD)
        private val PASSWORD_STEPS = setOf(VxAuthStep.SIGN_IN, VxAuthStep.SIGN_UP, VxAuthStep.NEW_PASSWORD)
        private val CODE_STEPS = setOf(VxAuthStep.ENTER_CODE, VxAuthStep.VERIFY_EMAIL)
    }
}

/** The texts that change with the step — iOS's `render(step:…)`. */
internal object AuthStepCopy {
    fun title(step: VxAuthStep): AuthCopy = when (step) {
        VxAuthStep.SIGN_IN -> AuthCopy.SIGN_IN_TITLE
        VxAuthStep.SIGN_UP -> AuthCopy.SIGN_UP_TITLE
        VxAuthStep.FORGOT_PASSWORD -> AuthCopy.FORGOT_TITLE
        VxAuthStep.ENTER_CODE -> AuthCopy.CODE_TITLE
        VxAuthStep.NEW_PASSWORD -> AuthCopy.NEW_PASSWORD_TITLE
        VxAuthStep.VERIFY_EMAIL -> AuthCopy.VERIFY_TITLE
    }

    /** Null on the new-password step, which has none. The code steps name the address: `%@`. */
    fun subtitle(step: VxAuthStep): AuthCopy? = when (step) {
        VxAuthStep.SIGN_IN -> AuthCopy.SIGN_IN_SUBTITLE
        VxAuthStep.SIGN_UP -> AuthCopy.SIGN_UP_SUBTITLE
        VxAuthStep.FORGOT_PASSWORD -> AuthCopy.FORGOT_SUBTITLE
        VxAuthStep.ENTER_CODE, VxAuthStep.VERIFY_EMAIL -> AuthCopy.CODE_SUBTITLE
        VxAuthStep.NEW_PASSWORD -> null
    }

    fun primaryButton(step: VxAuthStep): AuthCopy = when (step) {
        VxAuthStep.SIGN_IN -> AuthCopy.SIGN_IN_BUTTON
        VxAuthStep.SIGN_UP -> AuthCopy.SIGN_UP_BUTTON
        VxAuthStep.FORGOT_PASSWORD -> AuthCopy.SEND_CODE_BUTTON
        VxAuthStep.ENTER_CODE, VxAuthStep.VERIFY_EMAIL -> AuthCopy.CONTINUE_BUTTON
        VxAuthStep.NEW_PASSWORD -> AuthCopy.SAVE_PASSWORD_BUTTON
    }

    /** The link under the form; shown on the sign-in, sign-up and forgot steps. */
    fun secondaryButton(step: VxAuthStep): AuthCopy =
        if (step == VxAuthStep.SIGN_IN) AuthCopy.NO_ACCOUNT_PROMPT else AuthCopy.HAS_ACCOUNT_PROMPT

    /** The password field's placeholder: "New password" when choosing one. */
    fun passwordPlaceholder(step: VxAuthStep): AuthCopy =
        if (step == VxAuthStep.NEW_PASSWORD) AuthCopy.NEW_PASSWORD_PLACEHOLDER else AuthCopy.PASSWORD_PLACEHOLDER
}
