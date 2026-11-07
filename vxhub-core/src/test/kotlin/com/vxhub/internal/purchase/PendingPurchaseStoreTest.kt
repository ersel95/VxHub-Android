package com.vxhub.internal.purchase

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class PendingPurchaseStoreTest {
    private class InMemory : PendingPurchasePersistence {
        var json: String? = null
        override fun read(): String? = json
        override suspend fun write(json: String?) {
            this.json = json
        }
    }

    private var now = 1_000_000L
    private val persistence = InMemory()
    private fun store() = PendingPurchaseStore(persistence) { now }

    @Test
    fun addedTransactionSurvivesANewStoreInstance() = runBlocking<Unit> {
        store().add("GPA.1", "premium:yearly")
        val restored = store().all()
        assertThat(restored.map { it.transactionId }).containsExactly("GPA.1")
        assertThat(restored.single().productId).isEqualTo("premium:yearly")
    }

    @Test
    fun sameTransactionIsNotQueuedTwice() = runBlocking<Unit> {
        val store = store()
        store.add("GPA.1", "p")
        store.add("GPA.1", "p")
        assertThat(store.all()).hasSize(1)
    }

    @Test
    fun removeOnlyDropsThatTransaction() = runBlocking<Unit> {
        val store = store()
        store.add("GPA.1", "p")
        store.add("GPA.2", "p")
        store.remove("GPA.1")
        assertThat(store.all().map { it.transactionId }).containsExactly("GPA.2")
    }

    @Test
    fun failedAttemptsAreCountedAndCapped() = runBlocking<Unit> {
        val store = store()
        store.add("GPA.1", "p")
        repeat(PendingPurchaseStore.MAX_ATTEMPTS - 1) { assertThat(store.recordFailedAttempt("GPA.1")).isTrue() }
        assertThat(store.all().single().attempts).isEqualTo(PendingPurchaseStore.MAX_ATTEMPTS - 1)
        assertThat(store.recordFailedAttempt("GPA.1")).isFalse()
        assertThat(store.all()).isEmpty()
        assertThat(persistence.json).isNull()
    }

    @Test
    fun entriesOlderThanMaxAgeAreDropped() = runBlocking<Unit> {
        val store = store()
        store.add("GPA.1", "p")
        now += PendingPurchaseStore.MAX_AGE_MS + 1
        assertThat(store.recordFailedAttempt("GPA.1")).isFalse()
        assertThat(store.all()).isEmpty()
    }

    @Test
    fun retryPassesAreCoalesced() {
        val store = store()
        assertThat(store.beginRetryPass()).isTrue()
        assertThat(store.beginRetryPass()).isFalse()
        store.endRetryPass()
        assertThat(store.beginRetryPass()).isTrue()
        store.endRetryPass()
    }

    @Test
    fun unreadableQueueReadsAsEmpty() = runBlocking<Unit> {
        persistence.json = "{broken"
        assertThat(store().all()).isEmpty()
    }
}
