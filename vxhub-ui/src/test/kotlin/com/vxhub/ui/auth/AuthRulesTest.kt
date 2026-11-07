package com.vxhub.ui.auth

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.vxhub.VxHubException
import org.junit.Test

class AuthErrorsTest {
    private fun copyFor(error: Throwable) = AuthErrors.message(error).copy

    private fun failed(code: String, status: Int = 400) = VxHubException.AuthFailed(code, status)

    @Test
    fun `maps every API code iOS knows to its sentence`() {
        val expected = mapOf(
            "INVALID_CREDENTIALS" to AuthCopy.ERROR_INVALID_CREDENTIALS,
            "EMAIL_ALREADY_REGISTERED" to AuthCopy.ERROR_EMAIL_TAKEN,
            "INVALID_CODE" to AuthCopy.ERROR_INVALID_CODE,
            "EMAIL_NOT_VERIFIED" to AuthCopy.ERROR_EMAIL_NOT_VERIFIED,
            "PASSWORD_TOO_SIMPLE" to AuthCopy.ERROR_PASSWORD_TOO_SIMPLE,
            "ACCOUNT_UNAVAILABLE" to AuthCopy.ERROR_ACCOUNT_UNAVAILABLE,
            "AUTH_NOT_ENABLED" to AuthCopy.ERROR_SIGN_IN_UNAVAILABLE,
            "PASSWORD_AUTH_NOT_ENABLED" to AuthCopy.ERROR_SIGN_IN_UNAVAILABLE,
            "NO_PASSWORD_SET" to AuthCopy.ERROR_USE_PASSWORDLESS,
            "GOOGLE_LOGIN_NOT_ENABLED" to AuthCopy.ERROR_PROVIDER_UNAVAILABLE,
            "APPLE_LOGIN_NOT_ENABLED" to AuthCopy.ERROR_PROVIDER_UNAVAILABLE,
            "GOOGLE_LOGIN_NOT_CONFIGURED" to AuthCopy.ERROR_PROVIDER_UNAVAILABLE,
            "APPLE_LOGIN_NOT_CONFIGURED" to AuthCopy.ERROR_PROVIDER_UNAVAILABLE,
            "INVALID_GOOGLE_TOKEN" to AuthCopy.ERROR_PROVIDER_UNAVAILABLE,
            "INVALID_APPLE_TOKEN" to AuthCopy.ERROR_PROVIDER_UNAVAILABLE,
            "SOMETHING_NEW" to AuthCopy.ERROR_GENERIC,
            "REQUEST_FAILED" to AuthCopy.ERROR_GENERIC,
        )
        expected.forEach { (code, copy) -> assertThat(copyFor(failed(code))).isEqualTo(copy) }
    }

    @Test
    fun `a rate limit wins over the code`() {
        assertThat(copyFor(failed("INVALID_CREDENTIALS", status = 429))).isEqualTo(AuthCopy.ERROR_TOO_MANY_ATTEMPTS)
        assertThat(copyFor(failed("TOO_MANY_REQUESTS", status = 429))).isEqualTo(AuthCopy.ERROR_TOO_MANY_ATTEMPTS)
    }

    @Test
    fun `password too short carries the project's minimum, 8 when it is missing`() {
        assertThat(AuthErrors.message(failed("PASSWORD_TOO_SHORT:10"))).isEqualTo(AuthMessage(AuthCopy.ERROR_PASSWORD_TOO_SHORT, argument = 10))
        assertThat(AuthErrors.message(failed("PASSWORD_TOO_SHORT"))).isEqualTo(AuthMessage(AuthCopy.ERROR_PASSWORD_TOO_SHORT, argument = 8))
        assertThat(AuthErrors.message(failed("PASSWORD_TOO_SHORT:"))).isEqualTo(AuthMessage(AuthCopy.ERROR_PASSWORD_TOO_SHORT, argument = 8))
        assertThat(AuthErrors.message(failed("PASSWORD_TOO_SHORT:ten"))).isEqualTo(AuthMessage(AuthCopy.ERROR_PASSWORD_TOO_SHORT, argument = 8))
    }

    @Test
    fun `no connection reads as the network sentence, anything else as the generic one`() {
        assertThat(copyFor(VxHubException.NetworkUnavailable())).isEqualTo(AuthCopy.ERROR_NETWORK)
        assertThat(copyFor(VxHubException.RequestFailed(401))).isEqualTo(AuthCopy.ERROR_GENERIC)
        assertThat(copyFor(VxHubException.SessionExpired())).isEqualTo(AuthCopy.ERROR_GENERIC)
        assertThat(copyFor(VxHubException.NotInitialized("not yet"))).isEqualTo(AuthCopy.ERROR_GENERIC)
        assertThat(copyFor(IllegalStateException("boom"))).isEqualTo(AuthCopy.ERROR_GENERIC)
    }

    @Test
    fun `errors are errors`() {
        assertThat(AuthErrors.message(failed("INVALID_CODE")).isError).isTrue()
    }

    @Test
    fun `the sentences read as iOS's English`() {
        assertThat(AuthErrors.message(failed("PASSWORD_TOO_SHORT:10")).text()).isEqualTo("Use at least 10 characters.")
        assertThat(AuthErrors.message(failed("INVALID_CREDENTIALS")).text()).isEqualTo("That email and password do not match.")
        assertThat(AuthErrors.message(failed("ACCOUNT_UNAVAILABLE")).text())
            .isEqualTo("This account is no longer available. Contact support if you think this is a mistake.")
        assertThat(AuthErrors.message(VxHubException.NetworkUnavailable()).text()).isEqualTo("No connection. Check your network and try again.")
    }

    @Test
    fun `a dismissed Google picker is not an error`() {
        assertThat(AuthErrors.isUserCancellation(VxHubException.SignInFailed("Google", "User cancelled the selector"))).isTrue()
        assertThat(AuthErrors.isUserCancellation(VxHubException.SignInFailed("Google", "failed", GetCredentialCancellationException()))).isTrue()
        assertThat(AuthErrors.isUserCancellation(VxHubException.SignInFailed("Google", "No credentials available"))).isFalse()
        assertThat(AuthErrors.isUserCancellation(VxHubException.ModuleMissing("vxhub-google-signin"))).isFalse()
    }

    /** Named as Credential Manager's, which this module does not depend on. */
    private class GetCredentialCancellationException : Exception()
}

class AuthValidationTest {
    @Test
    fun `email check is as loose as iOS's`() {
        assertThat(AuthValidation.isValidEmail("ada@example.com")).isTrue()
        assertThat(AuthValidation.isValidEmail("  ada@example.com ")).isTrue()
        assertThat(AuthValidation.isValidEmail("a@b.c")).isTrue()
        assertThat(AuthValidation.isValidEmail("a@b.")).isFalse()
        assertThat(AuthValidation.isValidEmail("ada.example.com")).isFalse()
        assertThat(AuthValidation.isValidEmail("ada@examplecom")).isFalse()
        assertThat(AuthValidation.isValidEmail("")).isFalse()
    }

    @Test
    fun `credentials need an address and the project's minimum length`() {
        assertThat(AuthValidation.credentialsProblem("ada@example.com", "12345678", minimumLength = 8)).isNull()
        assertThat(AuthValidation.credentialsProblem("ada@example.com", "1234567", minimumLength = 8))
            .isEqualTo(AuthMessage(AuthCopy.ERROR_PASSWORD_TOO_SHORT, argument = 8))
        assertThat(AuthValidation.credentialsProblem("ada@example.com", "123456789", minimumLength = 10))
            .isEqualTo(AuthMessage(AuthCopy.ERROR_PASSWORD_TOO_SHORT, argument = 10))
        // iOS answers an unusable address with the generic sentence.
        assertThat(AuthValidation.credentialsProblem("ada", "12345678", minimumLength = 8)).isEqualTo(AuthMessage(AuthCopy.ERROR_GENERIC))
    }

    @Test
    fun `a code needs four characters`() {
        assertThat(AuthValidation.codeProblem("123456")).isNull()
        assertThat(AuthValidation.codeProblem(" 1234 ")).isNull()
        assertThat(AuthValidation.codeProblem("123")).isEqualTo(AuthMessage(AuthCopy.ERROR_INVALID_CODE))
        assertThat(AuthValidation.codeProblem("  12  ")).isEqualTo(AuthMessage(AuthCopy.ERROR_INVALID_CODE))
    }

    @Test
    fun `a new password only has to be there`() {
        assertThat(AuthValidation.newPasswordProblem("x")).isNull()
        assertThat(AuthValidation.newPasswordProblem("")).isEqualTo(AuthMessage(AuthCopy.ERROR_GENERIC))
    }
}

class AuthLayoutTest {
    private fun layout(
        step: VxAuthStep,
        password: Boolean = true,
        google: Boolean = false,
        guest: Boolean = false,
        legal: Boolean = true,
    ) = AuthLayout.of(step, passwordEnabled = password, googleEnabled = google, showsGuestOption = guest, showsLegalLinks = legal)

    @Test
    fun `sign in shows email, password, forgot and the sign-up link`() {
        val signIn = layout(VxAuthStep.SIGN_IN)
        assertThat(signIn.showsEmail && signIn.showsPassword && signIn.showsForgot && signIn.showsSecondary && signIn.showsPrimary).isTrue()
        assertThat(signIn.showsName || signIn.showsCode || signIn.showsResend || signIn.showsGoogle || signIn.showsSeparator).isFalse()
        assertThat(signIn.showsLegal).isTrue()
    }

    @Test
    fun `sign up adds the name and drops forgot`() {
        val signUp = layout(VxAuthStep.SIGN_UP)
        assertThat(signUp.showsName && signUp.showsEmail && signUp.showsPassword && signUp.showsSecondary).isTrue()
        assertThat(signUp.showsForgot).isFalse()
    }

    @Test
    fun `Google and its separator show on the entry steps only, when enabled`() {
        assertThat(layout(VxAuthStep.SIGN_IN, google = true).showsGoogle).isTrue()
        assertThat(layout(VxAuthStep.SIGN_IN, google = true).showsSeparator).isTrue()
        assertThat(layout(VxAuthStep.SIGN_UP, google = true).showsGoogle).isTrue()
        assertThat(layout(VxAuthStep.FORGOT_PASSWORD, google = true).showsGoogle).isFalse()
        assertThat(layout(VxAuthStep.VERIFY_EMAIL, google = true).showsGoogle).isFalse()
    }

    @Test
    fun `without password sign-in the entry steps show the providers alone`() {
        val signIn = layout(VxAuthStep.SIGN_IN, password = false, google = true)
        assertThat(signIn.showsGoogle).isTrue()
        assertThat(signIn.showsEmail || signIn.showsPassword || signIn.showsPrimary || signIn.showsSecondary).isFalse()
        assertThat(signIn.showsForgot || signIn.showsSeparator).isFalse()
        assertThat(layout(VxAuthStep.SIGN_UP, password = false).showsName).isFalse()
    }

    @Test
    fun `code steps show the code field and resend, nothing to switch away with`() {
        listOf(VxAuthStep.ENTER_CODE, VxAuthStep.VERIFY_EMAIL).forEach { step ->
            val code = layout(step, guest = true)
            assertThat(code.showsCode && code.showsResend && code.showsPrimary).isTrue()
            assertThat(code.showsEmail || code.showsPassword || code.showsSecondary || code.showsGuest || code.showsLegal).isFalse()
        }
    }

    @Test
    fun `new password shows the password field alone`() {
        val newPassword = layout(VxAuthStep.NEW_PASSWORD)
        assertThat(newPassword.showsPassword && newPassword.showsPrimary).isTrue()
        assertThat(newPassword.showsEmail || newPassword.showsCode || newPassword.showsSecondary || newPassword.showsForgot).isFalse()
    }

    @Test
    fun `guest and legal follow the configuration on the entry steps`() {
        assertThat(layout(VxAuthStep.SIGN_IN, guest = true).showsGuest).isTrue()
        assertThat(layout(VxAuthStep.SIGN_IN, guest = false).showsGuest).isFalse()
        assertThat(layout(VxAuthStep.FORGOT_PASSWORD, guest = true).showsGuest).isFalse()
        assertThat(layout(VxAuthStep.SIGN_UP, legal = false).showsLegal).isFalse()
    }
}

class AuthCopyTest {
    @Test
    fun `iOS format specifiers are filled`() {
        assertThat("We sent a 6-digit code to %@".iosFormat("ada@example.com")).isEqualTo("We sent a 6-digit code to ada@example.com")
        assertThat("Use at least %d characters.".iosFormat(10)).isEqualTo("Use at least 10 characters.")
        assertThat("%2\$@ then %1\$@".iosFormat("a", "b")).isEqualTo("b then a")
        assertThat("%ld left, 100%% sure".iosFormat(3)).isEqualTo("3 left, 100% sure")
        assertThat("No specifiers, 50% off".iosFormat("unused")).isEqualTo("No specifiers, 50% off")
        assertThat("Missing %@".iosFormat()).isEqualTo("Missing ")
    }

    @Test
    fun `step texts follow iOS`() {
        assertThat(AuthStepCopy.subtitle(VxAuthStep.NEW_PASSWORD)).isNull()
        assertThat(AuthStepCopy.subtitle(VxAuthStep.VERIFY_EMAIL)).isEqualTo(AuthCopy.CODE_SUBTITLE)
        assertThat(AuthStepCopy.primaryButton(VxAuthStep.FORGOT_PASSWORD)).isEqualTo(AuthCopy.SEND_CODE_BUTTON)
        assertThat(AuthStepCopy.secondaryButton(VxAuthStep.SIGN_IN)).isEqualTo(AuthCopy.NO_ACCOUNT_PROMPT)
        assertThat(AuthStepCopy.secondaryButton(VxAuthStep.FORGOT_PASSWORD)).isEqualTo(AuthCopy.HAS_ACCOUNT_PROMPT)
        assertThat(AuthStepCopy.passwordPlaceholder(VxAuthStep.NEW_PASSWORD)).isEqualTo(AuthCopy.NEW_PASSWORD_PLACEHOLDER)
    }

    @Test
    fun `every key is iOS's VxAuth key, with its English default`() {
        assertThat(AuthCopy.entries.map { it.key }.distinct()).hasSize(AuthCopy.entries.size)
        AuthCopy.entries.forEach { assertThat(it.key).startsWith("VxAuth_") }
        assertThat(AuthCopy.MUST_CHANGE_PASSWORD.text()).isEqualTo("Choose a password of your own to finish signing in.")
        assertThat(AuthCopy.CODE_SUBTITLE.text()).isEqualTo("We sent a 6-digit code to %@")
    }

    @Test
    fun `unspecified colours take iOS's light and dark defaults`() {
        val light = VxAuthConfiguration().colors(dark = false)
        val dark = VxAuthConfiguration().colors(dark = true)
        assertThat(light.primaryButton).isEqualTo(Color.Black)
        assertThat(dark.primaryButton).isEqualTo(Color.White)
        assertThat(light.fieldBackground).isEqualTo(Color(0xFFF5F6F8))
        assertThat(dark.fieldBackground).isEqualTo(Color(0xFF1C1C1E))
        assertThat(light.error).isEqualTo(Color(0xFFD70015))
        assertThat(dark.error).isEqualTo(Color(0xFFFF453A))
        assertThat(dark.link).isEqualTo(Color(0xFF0A84FF))
        assertThat(VxAuthConfiguration(linkColor = Color.Red).colors(dark = true).link).isEqualTo(Color.Red)
    }
}
