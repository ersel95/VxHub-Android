package com.vxhub.ui.auth

import android.app.Activity
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.google.common.truth.Truth.assertThat
import com.vxhub.VxHubException
import com.vxhub.VxUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    /** [VxUser]'s constructor is internal to the core; tests reach it the way the JVM sees it. */
    private fun user(
        email: String? = "ada@example.com",
        isEmailVerified: Boolean = true,
        mustChangePassword: Boolean = false,
    ): VxUser = VxUser::class.java.declaredConstructors.single().newInstance(
        "user-1", email, isEmailVerified, "Ada", null, listOf("password"), false, null, null, mustChangePassword,
    ) as VxUser

    private inner class FakeAuth : AuthService {
        var policy = AuthPolicy(minPasswordLength = 8, requiresEmailVerification = false)
        var signedInUser: VxUser? = null
        var nextUser: VxUser = user()
        var signInError: Exception? = null
        var resetError: Exception? = null
        val calls = mutableListOf<String>()

        override fun policy(): AuthPolicy = policy

        override fun currentUser(): VxUser? = signedInUser

        override suspend fun signIn(email: String, password: String): VxUser {
            calls += "signIn($email, $password)"
            signInError?.let { throw it }
            return nextUser.also { signedInUser = it }
        }

        override suspend fun signUp(email: String, password: String, name: String?): VxUser {
            calls += "signUp($email, $password, $name)"
            return nextUser.also { signedInUser = it }
        }

        override suspend fun forgotPassword(email: String) {
            calls += "forgotPassword($email)"
        }

        override suspend fun resetPassword(email: String, code: String, newPassword: String) {
            calls += "resetPassword($email, $code, $newPassword)"
            resetError?.let { throw it }
        }

        override suspend fun verifyEmail(code: String) {
            calls += "verifyEmail($code)"
            signedInUser = user(isEmailVerified = true)
        }

        override suspend fun resendVerificationCode() {
            calls += "resendVerificationCode()"
        }

        override suspend fun changePassword(current: String, new: String) {
            calls += "changePassword($current, $new)"
            signedInUser = user(mustChangePassword = false)
        }

        override suspend fun signInWithGoogle(activity: Activity): Boolean = error("not used")
    }

    private val auth = FakeAuth()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun AuthViewModel.type(email: String? = null, password: String? = null, code: String? = null, name: String? = null) {
        email?.let { this.email.setTextAndPlaceCursorAtEnd(it) }
        password?.let { this.password.setTextAndPlaceCursorAtEnd(it) }
        code?.let { this.code.setTextAndPlaceCursorAtEnd(it) }
        name?.let { this.name.setTextAndPlaceCursorAtEnd(it) }
    }

    @Test
    fun `signing in finishes with the user`() = runTest {
        val viewModel = AuthViewModel(VxAuthStep.SIGN_IN, auth)
        val results = mutableListOf<VxAuthResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.results.toList(results) }

        viewModel.type(email = " ada@example.com ", password = "secret123")
        viewModel.submit()

        assertThat(auth.calls).containsExactly("signIn(ada@example.com, secret123)")
        assertThat(results).containsExactly(VxAuthResult.SignedIn(auth.nextUser))
    }

    @Test
    fun `the form is checked before the API is called`() {
        val viewModel = AuthViewModel(VxAuthStep.SIGN_IN, auth)
        viewModel.type(email = "ada@example.com", password = "short")
        viewModel.submit()
        assertThat(viewModel.state.value.message).isEqualTo(AuthMessage(AuthCopy.ERROR_PASSWORD_TOO_SHORT, argument = 8))

        viewModel.type(email = "ada")
        viewModel.submit()
        assertThat(viewModel.state.value.message).isEqualTo(AuthMessage(AuthCopy.ERROR_GENERIC))
        assertThat(auth.calls).isEmpty()
    }

    @Test
    fun `a temporary password leads to choosing a new one, then into the app`() = runTest {
        auth.nextUser = user(mustChangePassword = true)
        val viewModel = AuthViewModel(VxAuthStep.SIGN_IN, auth)
        val results = mutableListOf<VxAuthResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.results.toList(results) }

        viewModel.type(email = "ada@example.com", password = "Temp1234")
        viewModel.submit()

        assertThat(viewModel.state.value.step).isEqualTo(VxAuthStep.NEW_PASSWORD)
        assertThat(viewModel.state.value.message).isEqualTo(AuthMessage(AuthCopy.MUST_CHANGE_PASSWORD, isError = false))
        assertThat(viewModel.password.text.toString()).isEmpty()
        assertThat(results).isEmpty()

        viewModel.type(password = "Mine5678")
        viewModel.submit()

        assertThat(auth.calls.last()).isEqualTo("changePassword(Temp1234, Mine5678)")
        assertThat(results).containsExactly(VxAuthResult.SignedIn(auth.signedInUser!!))
    }

    @Test
    fun `a reset after a failed sign-in resets rather than changes the password`() = runTest {
        auth.signInError = VxHubException.AuthFailed("INVALID_CREDENTIALS", 400)
        val viewModel = AuthViewModel(VxAuthStep.SIGN_IN, auth)
        val results = mutableListOf<VxAuthResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.results.toList(results) }

        viewModel.type(email = "ada@example.com", password = "wrongpass")
        viewModel.submit()
        assertThat(viewModel.state.value.message).isEqualTo(AuthMessage(AuthCopy.ERROR_INVALID_CREDENTIALS))
        auth.signInError = null

        viewModel.forgotPassword()
        viewModel.submit()
        assertThat(viewModel.state.value.step).isEqualTo(VxAuthStep.ENTER_CODE)
        assertThat(viewModel.state.value.pendingEmail).isEqualTo("ada@example.com")
        assertThat(viewModel.state.value.message).isEqualTo(AuthMessage(AuthCopy.CODE_SENT, isError = false))

        viewModel.type(code = "123456")
        viewModel.submit()
        assertThat(viewModel.state.value.step).isEqualTo(VxAuthStep.NEW_PASSWORD)
        assertThat(viewModel.password.text.toString()).isEmpty()

        viewModel.type(password = "NewPass123")
        viewModel.submit()

        assertThat(auth.calls).containsExactly(
            "signIn(ada@example.com, wrongpass)",
            "forgotPassword(ada@example.com)",
            "resetPassword(ada@example.com, 123456, NewPass123)",
            "signIn(ada@example.com, NewPass123)",
        ).inOrder()
        assertThat(results).containsExactly(VxAuthResult.SignedIn(auth.nextUser))
    }

    @Test
    fun `a rejected code goes back to the code step and says so`() {
        auth.resetError = VxHubException.AuthFailed("INVALID_CODE", 400)
        val viewModel = AuthViewModel(VxAuthStep.FORGOT_PASSWORD, auth)
        viewModel.type(email = "ada@example.com")
        viewModel.submit()
        viewModel.type(code = "999999")
        viewModel.submit()
        viewModel.type(password = "NewPass123")

        viewModel.submit()

        assertThat(viewModel.state.value.step).isEqualTo(VxAuthStep.ENTER_CODE)
        assertThat(viewModel.state.value.message).isEqualTo(AuthMessage(AuthCopy.ERROR_INVALID_CODE))
        assertThat(viewModel.code.text.toString()).isEmpty()
        assertThat(viewModel.state.value.isBusy).isFalse()
    }

    @Test
    fun `signing up where verification is required asks for the code`() = runTest {
        auth.policy = auth.policy.copy(requiresEmailVerification = true)
        auth.nextUser = user(email = "ada@example.com", isEmailVerified = false)
        val viewModel = AuthViewModel(VxAuthStep.SIGN_UP, auth)
        val results = mutableListOf<VxAuthResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.results.toList(results) }

        viewModel.type(name = "  ", email = "ada@example.com", password = "secret123")
        viewModel.submit()

        assertThat(auth.calls).containsExactly("signUp(ada@example.com, secret123, null)")
        assertThat(viewModel.state.value.step).isEqualTo(VxAuthStep.VERIFY_EMAIL)
        assertThat(viewModel.state.value.pendingEmail).isEqualTo("ada@example.com")

        viewModel.type(code = "123")
        viewModel.submit()
        assertThat(viewModel.state.value.message).isEqualTo(AuthMessage(AuthCopy.ERROR_INVALID_CODE))

        viewModel.type(code = "1234")
        viewModel.submit()
        assertThat(auth.calls.last()).isEqualTo("verifyEmail(1234)")
        assertThat(results).containsExactly(VxAuthResult.SignedIn(auth.signedInUser!!))
    }

    @Test
    fun `resending on the verification step sends the verification email`() {
        val viewModel = AuthViewModel(VxAuthStep.VERIFY_EMAIL, auth)
        viewModel.resendCode()
        assertThat(auth.calls).containsExactly("resendVerificationCode()")
        assertThat(viewModel.state.value.message).isEqualTo(AuthMessage(AuthCopy.CODE_SENT, isError = false))
    }

    @Test
    fun `back goes one step up, and out from the first step, once`() = runTest {
        val viewModel = AuthViewModel(VxAuthStep.SIGN_IN, auth)
        val results = mutableListOf<VxAuthResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.results.toList(results) }

        viewModel.secondary()
        assertThat(viewModel.state.value.step).isEqualTo(VxAuthStep.SIGN_UP)
        viewModel.back()
        assertThat(viewModel.state.value.step).isEqualTo(VxAuthStep.SIGN_IN)
        viewModel.forgotPassword()
        viewModel.back()
        assertThat(viewModel.state.value.step).isEqualTo(VxAuthStep.SIGN_IN)

        viewModel.back()
        viewModel.close()
        viewModel.continueAsGuest()
        assertThat(results).containsExactly(VxAuthResult.Cancelled)
    }

    @Test
    fun `opened on verification, the subtitle names the signed-in address`() {
        auth.signedInUser = user(email = "grace@example.com", isEmailVerified = false)
        val viewModel = AuthViewModel(VxAuthStep.VERIFY_EMAIL, auth)
        assertThat(viewModel.state.value.pendingEmail).isEqualTo("grace@example.com")
    }
}
