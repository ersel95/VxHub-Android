package com.vxhub.ui.auth

import android.app.Activity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vxhub.VxHub
import com.vxhub.VxHubException
import com.vxhub.VxUser
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** The panel's rules the flow applies itself. */
internal data class AuthPolicy(val minPasswordLength: Int, val requiresEmailVerification: Boolean)

/** What the sign-in flow needs from VxHub; a seam so the view model can be tested. */
internal interface AuthService {
    fun policy(): AuthPolicy

    fun currentUser(): VxUser?

    suspend fun signIn(email: String, password: String): VxUser

    suspend fun signUp(email: String, password: String, name: String?): VxUser

    suspend fun forgotPassword(email: String)

    suspend fun resetPassword(email: String, code: String, newPassword: String)

    suspend fun verifyEmail(code: String)

    suspend fun resendVerificationCode()

    suspend fun changePassword(current: String, new: String)

    /** True when the device signed in; [currentUser] tells whether an account session came with it. */
    suspend fun signInWithGoogle(activity: Activity): Boolean
}

internal object VxHubAuthService : AuthService {
    override fun policy(): AuthPolicy {
        val config = if (VxHub.Internal.isInitialized) VxHub.authConfig.value else null
        return AuthPolicy(
            minPasswordLength = config?.minPasswordLength ?: AuthValidation.DEFAULT_MIN_PASSWORD_LENGTH,
            requiresEmailVerification = config?.requiresEmailVerification == true,
        )
    }

    override fun currentUser(): VxUser? = if (VxHub.Internal.isInitialized) VxHub.currentUser.value else null

    override suspend fun signIn(email: String, password: String): VxUser = VxHub.signIn(email, password)

    override suspend fun signUp(email: String, password: String, name: String?): VxUser = VxHub.signUp(email, password, name)

    override suspend fun forgotPassword(email: String) = VxHub.forgotPassword(email)

    override suspend fun resetPassword(email: String, code: String, newPassword: String) =
        VxHub.resetPassword(email, code, newPassword)

    override suspend fun verifyEmail(code: String) = VxHub.verifyEmail(code)

    override suspend fun resendVerificationCode() = VxHub.resendVerificationCode()

    override suspend fun changePassword(current: String, new: String) = VxHub.changePassword(current, new)

    override suspend fun signInWithGoogle(activity: Activity): Boolean = VxHub.signInWithGoogle(activity)
}

internal data class AuthUiState(
    val step: VxAuthStep,
    /** The address a code went to; the code steps' subtitle names it. */
    val pendingEmail: String = "",
    val isBusy: Boolean = false,
    val message: AuthMessage? = null,
)

/**
 * The sign-in flow — iOS's `VxAuthViewController`: one form whose step decides the fields and the
 * copy, moving between steps rather than screens. A view model so the flow, and what was typed,
 * survive configuration changes; the fields stay in memory only, never in saved state.
 */
internal class AuthViewModel(
    private val startingAt: VxAuthStep,
    private val service: AuthService,
) : ViewModel() {

    val email: TextFieldState = TextFieldState()
    val password: TextFieldState = TextFieldState()
    val name: TextFieldState = TextFieldState()
    val code: TextFieldState = TextFieldState()

    private val _state = MutableStateFlow(
        AuthUiState(
            step = startingAt,
            // Opened straight on verification: the address is the signed-in account's (iOS leaves it blank).
            pendingEmail = if (startingAt == VxAuthStep.VERIFY_EMAIL) service.currentUser()?.email.orEmpty() else "",
        ),
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val outcomes = Channel<VxAuthResult>(Channel.BUFFERED)

    /** How the flow ended; emits once. Buffered, so a result is not lost across a configuration change. */
    val results: Flow<VxAuthResult> = outcomes.receiveAsFlow()

    /** Guards against a second result, e.g. a slow request finishing after the person closed the screen. */
    private var hasFinished = false

    /** The code typed on the code step; spent by the reset call, so it is only held until then. */
    private var pendingCode = ""

    /**
     * The temporary password someone just signed in with, kept only while they are asked to replace
     * it: changing a password needs the current one, and asking for it again on the very next
     * screen ends with temporary passwords kept forever.
     *
     * Set only once such a sign-in has succeeded. iOS sets it before every sign-in attempt and
     * never clears it, so a failed attempt followed by "Forgot your password?" sends the reset
     * through `changePassword` without a session, and the reset fails.
     */
    private var passwordAwaitingChange: String? = null

    /** The primary button, and the keyboard's done action on the last field. */
    fun submit() {
        if (hasFinished || _state.value.isBusy) return
        clearMessage()
        when (_state.value.step) {
            VxAuthStep.SIGN_IN -> signIn()
            VxAuthStep.SIGN_UP -> signUp()
            VxAuthStep.FORGOT_PASSWORD -> requestResetCode()
            VxAuthStep.ENTER_CODE -> acceptCode()
            VxAuthStep.NEW_PASSWORD -> saveNewPassword()
            VxAuthStep.VERIFY_EMAIL -> verifyEmail()
        }
    }

    /** The link under the form: the other entry step, or back to signing in. */
    fun secondary() {
        if (_state.value.isBusy) return
        moveTo(secondaryTarget(_state.value.step))
    }

    fun forgotPassword() {
        if (_state.value.isBusy) return
        moveTo(VxAuthStep.FORGOT_PASSWORD)
    }

    /**
     * System back, which iOS's full-screen flow does not have: leaves the flow from its first step
     * or from signing in, and otherwise goes one step up — where the secondary link leads.
     */
    fun back() {
        val step = _state.value.step
        when {
            step == startingAt || step == VxAuthStep.SIGN_IN -> finish(VxAuthResult.Cancelled)
            !_state.value.isBusy -> moveTo(secondaryTarget(step))
        }
    }

    fun close() = finish(VxAuthResult.Cancelled)

    fun continueAsGuest() = finish(VxAuthResult.ContinuedAsGuest)

    /** "Send it again": the verification email, or another reset code. */
    fun resendCode() {
        if (hasFinished || _state.value.isBusy) return
        val step = _state.value.step
        perform {
            if (step == VxAuthStep.VERIFY_EMAIL) service.resendVerificationCode() else service.forgotPassword(_state.value.pendingEmail)
            notice(AuthCopy.CODE_SENT)
        }
    }

    fun signInWithGoogle(activity: Activity) {
        if (hasFinished || _state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                if (service.signInWithGoogle(activity)) {
                    // Without accounts for Google the device signed in, which is all the app asked for.
                    finish(service.currentUser()?.let { VxAuthResult.SignedIn(it) } ?: VxAuthResult.Cancelled)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Our own sentence rather than the system's, which is neither localized nor meaningful.
                if (!AuthErrors.isUserCancellation(e)) show(AuthMessage(AuthCopy.ERROR_PROVIDER_UNAVAILABLE))
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun signIn() {
        val address = emailText()
        val secret = password.text.toString()
        AuthValidation.credentialsProblem(address, secret, service.policy().minPasswordLength)?.let {
            show(it)
            return
        }
        perform { signedIn(service.signIn(address, secret), passwordUsed = secret) }
    }

    private fun signUp() {
        val address = emailText()
        val secret = password.text.toString()
        AuthValidation.credentialsProblem(address, secret, service.policy().minPasswordLength)?.let {
            show(it)
            return
        }
        val displayName = name.text.toString().trim().ifEmpty { null }
        perform {
            val user = service.signUp(address, secret, displayName)
            // Signing up already returns a session; the address is confirmed afterwards, and only
            // blocks anything when the project requires it.
            if (service.policy().requiresEmailVerification && !user.isEmailVerified) {
                _state.update { it.copy(pendingEmail = user.email ?: address) }
                moveTo(VxAuthStep.VERIFY_EMAIL)
            } else {
                finish(VxAuthResult.SignedIn(user))
            }
        }
    }

    private fun requestResetCode() {
        val address = emailText()
        if (!AuthValidation.isValidEmail(address)) {
            show(AuthMessage(AuthCopy.ERROR_GENERIC))
            return
        }
        perform {
            service.forgotPassword(address)
            // The API will not say whether the address has an account, so neither does this
            // screen: it moves on either way.
            passwordAwaitingChange = null
            _state.update { it.copy(pendingEmail = address) }
            moveTo(VxAuthStep.ENTER_CODE)
            // After the step change, which clears the message area.
            notice(AuthCopy.CODE_SENT)
        }
    }

    private fun acceptCode() {
        val typedCode = code.text.toString().trim()
        AuthValidation.codeProblem(typedCode)?.let {
            show(it)
            return
        }
        // Checked by the reset call itself; holding it here avoids spending it twice.
        pendingCode = typedCode
        moveTo(VxAuthStep.NEW_PASSWORD)
    }

    private fun saveNewPassword() {
        val newPassword = password.text.toString()
        AuthValidation.newPasswordProblem(newPassword)?.let {
            show(it)
            return
        }
        val current = passwordAwaitingChange
        perform {
            // From a temporary password the person is signed in already, so it is a change, not a
            // reset — a reset needs a code they were never sent.
            if (current != null) {
                service.changePassword(current, newPassword)
                passwordAwaitingChange = null
                finish(service.currentUser()?.let { VxAuthResult.SignedIn(it) } ?: VxAuthResult.Cancelled)
                return@perform
            }
            val address = _state.value.pendingEmail
            try {
                service.resetPassword(address, pendingCode, newPassword)
            } catch (e: VxHubException.AuthFailed) {
                // A rejected code belongs to the code step. Moving first, because a step change
                // clears the message (iOS shows the error and then clears it by moving).
                if (e.code == "INVALID_CODE") moveTo(VxAuthStep.ENTER_CODE)
                show(AuthErrors.message(e))
                return@perform
            }
            // Straight into the app rather than back to a form: the person just proved both the
            // address and the new password.
            signedIn(service.signIn(address, newPassword), passwordUsed = null)
        }
    }

    private fun verifyEmail() {
        val typedCode = code.text.toString().trim()
        AuthValidation.codeProblem(typedCode)?.let {
            show(it)
            return
        }
        perform {
            service.verifyEmail(typedCode)
            val user = service.currentUser()
            if (user != null) finish(VxAuthResult.SignedIn(user)) else moveTo(VxAuthStep.SIGN_IN)
        }
    }

    /** iOS's `handle(_:)`: a temporary password goes to the new-password step before anything else. */
    private fun signedIn(user: VxUser, passwordUsed: String?) {
        if (user.mustChangePassword && passwordUsed != null) {
            passwordAwaitingChange = passwordUsed
            moveTo(VxAuthStep.NEW_PASSWORD)
            notice(AuthCopy.MUST_CHANGE_PASSWORD)
        } else {
            finish(VxAuthResult.SignedIn(user))
        }
    }

    /** The secondary link's destination, which is also where back goes. */
    private fun secondaryTarget(step: VxAuthStep): VxAuthStep = when (step) {
        VxAuthStep.SIGN_IN -> VxAuthStep.SIGN_UP
        VxAuthStep.SIGN_UP, VxAuthStep.FORGOT_PASSWORD -> VxAuthStep.SIGN_IN
        // The only way to correct a mistyped address without abandoning the flow.
        VxAuthStep.ENTER_CODE -> VxAuthStep.FORGOT_PASSWORD
        VxAuthStep.NEW_PASSWORD -> if (passwordAwaitingChange == null) VxAuthStep.ENTER_CODE else VxAuthStep.SIGN_IN
        VxAuthStep.VERIFY_EMAIL -> VxAuthStep.SIGN_IN
    }

    /**
     * A genuine step change clears the message and the code, as iOS's `step.didSet`. Arriving at
     * the new-password step also clears the password field, which still holds the password just
     * used (iOS leaves it, so "Save password" could re-save the temporary one); leaving that step
     * drops the temporary password.
     */
    private fun moveTo(step: VxAuthStep) {
        val from = _state.value.step
        if (from == step) return
        code.clearText()
        if (step == VxAuthStep.NEW_PASSWORD) password.clearText()
        if (from == VxAuthStep.NEW_PASSWORD) passwordAwaitingChange = null
        _state.update { it.copy(step = step, message = null) }
    }

    /** Runs [block] as the busy state, showing any failure as a sentence. */
    private fun perform(block: suspend () -> Unit) {
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                show(AuthErrors.message(e))
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun finish(result: VxAuthResult) {
        if (hasFinished) return
        hasFinished = true
        outcomes.trySend(result)
    }

    private fun show(message: AuthMessage) {
        _state.update { it.copy(message = message) }
    }

    private fun notice(copy: AuthCopy) {
        _state.update { it.copy(message = AuthMessage(copy, isError = false)) }
    }

    private fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun emailText(): String = email.text.toString().trim()
}
