package com.vxhub.internal.purchase

import com.vxhub.internal.VxLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A store transaction the backend has not verified yet. */
@Serializable
internal data class PendingPurchase(
    val transactionId: String,
    val productId: String,
    val createdAtEpochMs: Long,
    val attempts: Int = 0,
)

/** Where the queue lives; DataStore in production, a variable in tests. */
internal interface PendingPurchasePersistence {
    fun read(): String?
    suspend fun write(json: String?)
}

/**
 * Persists unverified transactions so a timeout, a crash or a killed process can never turn a
 * charged purchase into a silent loss. Written *before* the first network call. Port of iOS's
 * `VxPendingPurchaseStore`, same caps.
 */
internal class PendingPurchaseStore(
    private val persistence: PendingPurchasePersistence,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val retryPass = Mutex()

    suspend fun all(): List<PendingPurchase> = mutex.withLock { load() }

    fun isEmptyBlocking(): Boolean = load().isEmpty()

    suspend fun add(transactionId: String, productId: String) = mutex.withLock {
        val items = load()
        if (items.any { it.transactionId == transactionId }) return@withLock
        save(items + PendingPurchase(transactionId, productId, clock()))
    }

    suspend fun remove(transactionId: String) = mutex.withLock {
        save(load().filterNot { it.transactionId == transactionId })
    }

    /**
     * Records a failed attempt. Returns false when the entry was dropped for exceeding the attempt
     * cap or the maximum age — by then support has to step in.
     */
    suspend fun recordFailedAttempt(transactionId: String): Boolean = mutex.withLock {
        val items = load().toMutableList()
        val index = items.indexOfFirst { it.transactionId == transactionId }
        if (index < 0) return@withLock false
        val updated = items[index].copy(attempts = items[index].attempts + 1)
        val expired = updated.attempts >= MAX_ATTEMPTS || clock() - updated.createdAtEpochMs > MAX_AGE_MS
        if (expired) {
            VxLog.e("Dropping unverified purchase $transactionId after ${updated.attempts} attempts")
            items.removeAt(index)
        } else {
            items[index] = updated
        }
        save(items)
        !expired
    }

    /** Coalesces retry passes: returns false when one is already running. */
    fun beginRetryPass(): Boolean = retryPass.tryLock()

    fun endRetryPass() {
        if (retryPass.isLocked) retryPass.unlock()
    }

    private fun load(): List<PendingPurchase> {
        val json = persistence.read() ?: return emptyList()
        return runCatching { JSON.decodeFromString(LIST, json) }.getOrElse {
            VxLog.e("Pending purchase queue unreadable; keeping the raw value for support", it)
            emptyList()
        }
    }

    private suspend fun save(items: List<PendingPurchase>) {
        persistence.write(if (items.isEmpty()) null else JSON.encodeToString(LIST, items))
    }

    companion object {
        const val MAX_ATTEMPTS = 25
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1_000
        private val LIST = ListSerializer(PendingPurchase.serializer())
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
