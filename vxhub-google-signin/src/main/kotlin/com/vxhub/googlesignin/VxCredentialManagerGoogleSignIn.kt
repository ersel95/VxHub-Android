package com.vxhub.googlesignin

import android.app.Activity
import android.content.Context
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.vxhub.VxHubException
import com.vxhub.providers.VxGoogleCredential
import com.vxhub.providers.VxGoogleSignInProvider
import org.json.JSONObject

/**
 * "Sign in with Google" through Credential Manager — Google's replacement for the deprecated
 * GoogleSignInClient, and the Android side of iOS's `GIDSignIn`.
 *
 * The token is requested for the panel's *web* client id, which is the audience the VxHub backend
 * verifies; the app's Android OAuth client (package + SHA-1) only has to exist in the same Google
 * Cloud project.
 */
public class VxCredentialManagerGoogleSignIn : VxGoogleSignInProvider {

    override suspend fun signIn(activity: Activity, serverClientId: String): VxGoogleCredential {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()
        val credential = try {
            CredentialManager.create(activity).getCredential(activity, request).credential
        } catch (e: GetCredentialCancellationException) {
            throw VxHubException.SignInFailed(PROVIDER, "cancelled", e)
        } catch (e: GetCredentialException) {
            throw VxHubException.SignInFailed(PROVIDER, e.message ?: e.type, e)
        }
        if (credential !is CustomCredential || credential.type !in GOOGLE_TYPES) {
            throw VxHubException.SignInFailed(PROVIDER, "Unexpected credential type ${credential.type}")
        }
        val google = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: GoogleIdTokenParsingException) {
            throw VxHubException.SignInFailed(PROVIDER, "Unreadable Google ID token", e)
        }
        return VxGoogleCredential(
            idToken = google.idToken,
            // iOS sends Google's stable user id (the token's `sub`).
            accountId = google.uniqueId.takeIf { it.isNotBlank() } ?: jwtSubject(google.idToken) ?: google.email.orEmpty(),
            displayName = google.displayName,
            email = google.email,
            photoUrl = google.profilePictureUri?.toString(),
        )
    }

    override suspend fun signOut(context: Context) {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: ClearCredentialException) {
            // Nothing remembered to clear.
        }
    }

    internal companion object {
        private const val PROVIDER = "Google"
        private val GOOGLE_TYPES = setOf(
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL,
        )

        fun jwtSubject(jwt: String): String? = runCatching {
            val payload = jwt.split('.').getOrNull(1) ?: return null
            val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)
            JSONObject(json).optString("sub").takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
