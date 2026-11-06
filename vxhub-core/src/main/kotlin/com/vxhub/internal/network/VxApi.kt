package com.vxhub.internal.network

import com.vxhub.VxHubException
import com.vxhub.VxPromoCodeAction
import com.vxhub.VxPromoCodeResult
import com.vxhub.VxRetentionCoinGift
import com.vxhub.VxSpendCoinsResult
import com.vxhub.internal.VxLog
import com.vxhub.internal.purchase.VerificationOutcome
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException

/**
 * The device-scoped VxHub API (everything except `app-auth/`, which lives in [VxAuthApi]).
 * Paths, methods and bodies match the iOS SDK's `VxHubApi` one for one.
 */
internal class VxApi(private val http: VxHttpClient) {

    // region Device

    suspend fun registerDevice(request: DeviceRegisterRequest): DeviceRegisterResponseDto =
        call(VxEndpoint(HttpMethod.POST, "device/register", encode(DeviceRegisterRequest.serializer(), request)))
            .decode(DeviceRegisterResponseDto.serializer())

    suspend fun deviceSocialLogin(request: DeviceSocialLoginRequest): DeviceRegisterResponseDto =
        call(VxEndpoint(HttpMethod.POST, "device/social-login", encode(DeviceSocialLoginRequest.serializer(), request)))
            .decode(DeviceRegisterResponseDto.serializer())

    suspend fun deleteDevice(): Boolean {
        val result = call(VxEndpoint(HttpMethod.DELETE, "device"))
        val body = result.decode(SuccessDto.serializer())
        return body.success == true
    }

    /** AppsFlyer conversion data, forwarded as-is. Fire-and-forget, as on iOS. */
    suspend fun sendConversionData(data: JsonObject) {
        runCatching { http.execute(VxEndpoint(HttpMethod.POST, "device/conversion", data.toString())) }
            .onFailure { VxLog.w("Conversion data upload failed: ${it.message}") }
    }

    suspend fun approveQrLogin(token: String) {
        val result = call(VxEndpoint(HttpMethod.POST, "device/qr-login/approve", encode(TokenRequest.serializer(), TokenRequest(token))))
        if (result.isSuccessful) {
            val body = result.decode(SuccessDto.serializer())
            if (body.success != true) throw VxHubException.Unknown("QR approval failed")
            return
        }
        val error = result.errorBody()
        throw VxHubException.Unknown(
            error?.messages?.firstOrNull() ?: error?.error ?: "QR login failed with status: ${result.code}",
        )
    }

    suspend fun claimRetentionCoin(): VxRetentionCoinGift {
        val result = call(VxEndpoint(HttpMethod.POST, "device/retention/claim"))
        if (!result.isSuccessful) {
            val error = result.errorBody()
            throw VxHubException.Unknown(error?.messages?.firstOrNull() ?: "Retention claim failed (${result.code})")
        }
        val body = result.decode(RetentionClaimDto.serializer())
        return VxRetentionCoinGift(claimed = body.success ?: (body.status == "success"), amount = body.amount)
    }

    // endregion

    // region Products, purchases, coins

    suspend fun backendProducts(): List<BackendProductDto> =
        call(VxEndpoint(HttpMethod.GET, "product/app")).decode(ListSerializer(BackendProductDto.serializer()))

    /**
     * `device/after-purchase`. Never throws; every failure becomes an outcome the caller can queue
     * on. No transport retries: a pending purchase is re-sent from its persistent queue, so the
     * buyer should not wait on three timeouts here.
     */
    suspend fun verifyPurchase(transactionId: String, productId: String): VerificationOutcome =
        verify(
            VxEndpoint(
                HttpMethod.POST,
                "device/after-purchase",
                encode(PurchaseDataRequest.serializer(), PurchaseDataRequest(transactionId, productId)),
            ),
            requireTransactionMatch = true,
        )

    /** `device/restore`: re-verify this device's purchases with the store. */
    suspend fun verifyRestore(): VerificationOutcome =
        verify(VxEndpoint(HttpMethod.POST, "device/restore", "{}"), requireTransactionMatch = false)

    private suspend fun verify(endpoint: VxEndpoint, requireTransactionMatch: Boolean): VerificationOutcome {
        val outcome = try {
            val result = http.execute(endpoint, maxRetries = 0)
            VerificationOutcome.classify(result.code, result.body, null, requireTransactionMatch)
        } catch (e: IOException) {
            VerificationOutcome.classify(null, null, e)
        }
        VxLog.i("${endpoint.path} → $outcome")
        return outcome
    }

    suspend fun spendCoins(amount: Int, reason: String, parameters: Map<String, String>?): VxSpendCoinsResult {
        val result = try {
            http.execute(
                VxEndpoint(
                    HttpMethod.POST,
                    "device/balance/spend",
                    encode(SpendCoinsRequest.serializer(), SpendCoinsRequest(amount, reason, parameters)),
                ),
            )
        } catch (e: IOException) {
            return VxSpendCoinsResult.Failed(e.message ?: "Network unavailable")
        }
        return parseSpendCoins(result.code, result.body)
    }

    // endregion

    // region Promo codes

    suspend fun usePromoCode(code: String): VxPromoCodeResult {
        val result = call(VxEndpoint(HttpMethod.POST, "promo-codes/use", encode(PromoCodeRequest.serializer(), PromoCodeRequest(code))))
        return parsePromoCode(result.code, result.body)
    }

    // endregion

    // region Support

    suspend fun tickets(): List<TicketDto> =
        call(VxEndpoint(HttpMethod.GET, "support/tickets")).decode(ListSerializer(TicketDto.serializer()))

    suspend fun createTicket(category: String, message: String): TicketDto =
        call(
            VxEndpoint(
                HttpMethod.POST,
                "support/tickets",
                encode(CreateTicketRequest.serializer(), CreateTicketRequest(category, message)),
            ),
        ).decode(TicketDto.serializer())

    suspend fun ticketDetail(ticketId: String): TicketDetailDto =
        call(VxEndpoint(HttpMethod.GET, "support/tickets/${ticketId.pathSegment()}")).decode(TicketDetailDto.serializer())

    suspend fun sendTicketMessage(ticketId: String, message: String): TicketMessageDto =
        call(
            VxEndpoint(
                HttpMethod.POST,
                "support/tickets/${ticketId.pathSegment()}/messages",
                encode(MessageRequest.serializer(), MessageRequest(message)),
            ),
        ).decode(TicketMessageDto.serializer())

    suspend fun ticketsUnseenStatus(): Boolean {
        val result = call(VxEndpoint(HttpMethod.GET, "support/unseen"))
        if (!result.isSuccessful) {
            val error = result.errorBody()
            throw VxHubException.Unknown(
                error?.messages?.firstOrNull() ?: error?.error ?: "Unseen status failed with status: ${result.code}",
            )
        }
        val body = result.decode(UnseenStatusDto.serializer())
        if (body.status == "success" && body.unseenResponse != null) return body.unseenResponse
        throw VxHubException.Unknown("Unseen status failed")
    }

    // endregion

    // region Session analytics

    suspend fun sessionStart(request: SessionStartRequest): Boolean =
        fireAndReport(VxEndpoint(HttpMethod.POST, "session-analytics/session/start", encode(SessionStartRequest.serializer(), request)))

    suspend fun sessionEnd(request: SessionEndRequest): Boolean =
        fireAndReport(VxEndpoint(HttpMethod.POST, "session-analytics/session/end", encode(SessionEndRequest.serializer(), request)))

    suspend fun sessionEvents(request: SessionEventsRequest): Boolean =
        fireAndReport(VxEndpoint(HttpMethod.POST, "session-analytics/events", encode(SessionEventsRequest.serializer(), request)))

    private suspend fun fireAndReport(endpoint: VxEndpoint): Boolean = try {
        http.execute(endpoint).isSuccessful
    } catch (e: IOException) {
        VxLog.d { "${endpoint.path} failed: ${e.message}" }
        false
    }

    // endregion

    private suspend fun call(endpoint: VxEndpoint): HttpResult = try {
        http.execute(endpoint)
    } catch (e: IOException) {
        throw VxHubException.NetworkUnavailable(e)
    }

    internal companion object {
        fun parseSpendCoins(statusCode: Int, body: String): VxSpendCoinsResult {
            if (statusCode in 200..299) {
                val decoded = decodeOrNull(SpendCoinsResponseDto.serializer(), body)
                    ?: return VxSpendCoinsResult.Failed("Unreadable spend response")
                val balance = decoded.balance ?: return VxSpendCoinsResult.Failed("Unreadable spend response")
                return if (decoded.premium == true || decoded.status == "premium") {
                    VxSpendCoinsResult.Premium(balance)
                } else {
                    VxSpendCoinsResult.Spent(balance)
                }
            }
            if (statusCode == 403) {
                val failure = decodeOrNull(SpendCoinsFailureDto.serializer(), body)
                if (failure?.errorCode == "INSUFFICIENT_CREDITS") {
                    return VxSpendCoinsResult.Insufficient(failure.balance ?: 0)
                }
            }
            return VxSpendCoinsResult.Failed("Spend failed ($statusCode)")
        }

        fun parsePromoCode(statusCode: Int, body: String): VxPromoCodeResult {
            if (statusCode in 200..299) {
                val decoded = decodeOrNull(PromoCodeSuccessDto.serializer(), body)
                    ?: return VxPromoCodeResult.Invalid(listOf("Unreadable promo code response"))
                if (decoded.success != true) {
                    return VxPromoCodeResult.Invalid(listOf("Network Request is returned failed"))
                }
                val action = when (decoded.actionType?.lowercase()) {
                    "discount" -> VxPromoCodeAction.DISCOUNT
                    "coin" -> VxPromoCodeAction.COIN
                    else -> VxPromoCodeAction.PREMIUM
                }
                val meta = (decoded.actionMeta as? JsonPrimitive)?.contentOrNull
                return VxPromoCodeResult.Redeemed(
                    actionType = action,
                    packageName = meta.takeIf { action == VxPromoCodeAction.DISCOUNT },
                    durationInDays = meta?.trim()?.toIntOrNull().takeIf { action == VxPromoCodeAction.PREMIUM },
                    coinAmount = meta?.trim()?.toIntOrNull().takeIf { action == VxPromoCodeAction.COIN },
                    extraData = decoded.extraData.orEmpty(),
                )
            }
            val error = decodeOrNull(ApiErrorDto.serializer(), body)
            return VxPromoCodeResult.Invalid(error?.messages?.takeIf { it.isNotEmpty() } ?: listOf("Unknown error"))
        }

        private fun <T> decodeOrNull(deserializer: DeserializationStrategy<T>, body: String): T? =
            if (body.isBlank()) null else runCatching { VxJson.decodeFromString(deserializer, body) }.getOrNull()
    }
}

internal fun <T> encode(serializer: kotlinx.serialization.SerializationStrategy<T>, value: T): String =
    VxJson.encodeToString(serializer, value)

/** Decodes a 2xx body, mapping every other outcome onto the iOS error cases. */
internal fun <T> HttpResult.decode(deserializer: DeserializationStrategy<T>): T {
    if (!isSuccessful) throw VxHubException.RequestFailed(code)
    if (body.isBlank()) throw VxHubException.NoData()
    return try {
        VxJson.decodeFromString(deserializer, body)
    } catch (e: SerializationException) {
        VxLog.e("Decoding failed: ${e.message}")
        throw VxHubException.DecodingFailed(e)
    } catch (e: IllegalArgumentException) {
        VxLog.e("Decoding failed: ${e.message}")
        throw VxHubException.DecodingFailed(e)
    }
}

internal fun HttpResult.errorBody(): ApiErrorDto? =
    if (body.isBlank()) null else runCatching { VxJson.decodeFromString(ApiErrorDto.serializer(), body) }.getOrNull()

private fun String.pathSegment(): String = java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
