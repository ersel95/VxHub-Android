package com.vxhub

/**
 * A signed-in account. Accounts belong to the VxHub *project*, so the iOS, Android and Web builds
 * of one product share them.
 */
public class VxUser internal constructor(
    public val id: String,
    public val email: String?,
    public val isEmailVerified: Boolean,
    public val name: String?,
    public val profilePicture: String?,
    /** How this account can sign in: `password`, `google`, `apple`. */
    public val providers: List<String>,
    public val isPremium: Boolean,
    public val premiumEndDate: String?,
    public val balance: Int?,
    /** An admin issued a temporary password; ask for a new one before anything else. */
    public val mustChangePassword: Boolean,
) {
    override fun toString(): String = "VxUser(id=$id, email=$email, verified=$isEmailVerified, providers=$providers)"
}

/**
 * Which sign-in methods the VxHub panel enabled for this project. Read at initialization, so
 * switching Google on needs no app release.
 */
public class VxAuthConfig internal constructor(
    public val isAuthEnabled: Boolean,
    public val isPasswordEnabled: Boolean,
    public val isGoogleEnabled: Boolean,
    /** Sign in with Apple. Shown on Android only when the app supplies an Apple provider. */
    public val isAppleEnabled: Boolean,
    public val requiresEmailVerification: Boolean,
    public val minPasswordLength: Int,
    public val requiresPasswordComplexity: Boolean,
    /** The *web* OAuth client id Google ID tokens must be issued for. */
    public val googleClientId: String?,
)

/** One of the account's active sessions, as listed by [VxHub.listAuthSessions]. */
public class VxAuthDeviceSession internal constructor(
    public val id: String,
    public val platform: String?,
    public val userAgent: String?,
    public val createdAt: String?,
    public val lastUsedAt: String?,
    public val expiresAt: String?,
)
