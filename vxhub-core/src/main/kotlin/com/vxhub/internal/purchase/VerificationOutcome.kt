package com.vxhub.internal.purchase

import com.vxhub.internal.network.PurchaseVerificationDto
import com.vxhub.internal.network.VxJson

/**
 * How a `device/after-purchase` or `device/restore` call ended, independent of networking so it
 * can be unit tested. Ported case for case from iOS's `VxVerificationOutcome`.
 */
internal sealed interface VerificationOutcome {
    /** Definitive backend answer. */
    data class Verified(val isPremium: Boolean, val balance: Int?) : VerificationOutcome

    /** Try again later: transport error, timeout, 408/429/5xx (503 = `pending`), or an unusable body. */
    data class RetryLater(val reason: String) : VerificationOutcome

    /** The endpoint does not exist on this backend (older deployments). */
    data object Unsupported : VerificationOutcome

    /** The backend refused the request (4xx). Kept queued until the attempt cap. */
    data class Rejected(val statusCode: Int) : VerificationOutcome

    companion object {
        /**
         * @param requireTransactionMatch for after-purchase. A 200 with `"verified": false` means
         *   the backend could not find the transaction yet (RevenueCat has not caught up), which
         *   must not drop a charged purchase from the queue. Restore passes false: there
         *   `premium_status` is the answer. Older backends that omit `verified` are taken at
         *   their word.
         */
        fun classify(
            statusCode: Int?,
            body: String?,
            transportError: Throwable?,
            requireTransactionMatch: Boolean = false,
        ): VerificationOutcome {
            if (transportError != null) {
                return RetryLater("transport: ${transportError.javaClass.simpleName}: ${transportError.message}")
            }
            statusCode ?: return RetryLater("no response")
            return when (statusCode) {
                in 200..299 -> {
                    val decoded = body?.takeIf { it.isNotBlank() }?.let {
                        runCatching { VxJson.decodeFromString(PurchaseVerificationDto.serializer(), it) }.getOrNull()
                    } ?: return RetryLater("undecodable $statusCode body")
                    if (requireTransactionMatch && decoded.verified == false) {
                        RetryLater("transaction not verified yet")
                    } else {
                        Verified(decoded.device.premiumStatus, decoded.device.balance)
                    }
                }
                404 -> Unsupported
                408, 429, in 500..599 -> RetryLater("status $statusCode")
                else -> Rejected(statusCode)
            }
        }
    }
}
