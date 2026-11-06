package com.vxhub

/** Outcome of [VxHub.usePromoCode]. */
public sealed interface VxPromoCodeResult {
    /** The code was accepted and its action applied on the backend. */
    public data class Redeemed(
        val actionType: VxPromoCodeAction,
        /** For [VxPromoCodeAction.DISCOUNT]: the discounted package to offer. */
        val packageName: String?,
        /** For [VxPromoCodeAction.PREMIUM]: how long premium lasts. */
        val durationInDays: Int?,
        /** For [VxPromoCodeAction.COIN]: how many coins were granted. */
        val coinAmount: Int?,
        val extraData: Map<String, String>,
    ) : VxPromoCodeResult

    /** The backend refused the code; [messages] are its reasons, ready to show. */
    public data class Invalid(val messages: List<String>) : VxPromoCodeResult
}

public enum class VxPromoCodeAction { DISCOUNT, PREMIUM, COIN }

/** Outcome of [VxHub.spendCoins]. Coins live on the backend; this is the only way an app spends them. */
public sealed interface VxSpendCoinsResult {
    /** Coins were deducted; [balance] is what is left. */
    public data class Spent(val balance: Int) : VxSpendCoinsResult

    /** A premium device is never charged. */
    public data class Premium(val balance: Int) : VxSpendCoinsResult

    /** The device cannot pay. Nothing was deducted. */
    public data class Insufficient(val balance: Int) : VxSpendCoinsResult

    /**
     * Network or server failure. Nothing is known to have been deducted; the app decides whether
     * to let the action through (local-first) or not.
     */
    public data class Failed(val message: String?) : VxSpendCoinsResult
}

/** The balance the backend reported, when it reported one. */
public val VxSpendCoinsResult.balance: Int?
    get() = when (this) {
        is VxSpendCoinsResult.Spent -> balance
        is VxSpendCoinsResult.Premium -> balance
        is VxSpendCoinsResult.Insufficient -> balance
        is VxSpendCoinsResult.Failed -> null
    }

/** Outcome of [VxHub.claimRetentionCoinGift]. */
public data class VxRetentionCoinGift(
    val claimed: Boolean,
    /** Coins granted, when the backend says. */
    val amount: Int?,
)
