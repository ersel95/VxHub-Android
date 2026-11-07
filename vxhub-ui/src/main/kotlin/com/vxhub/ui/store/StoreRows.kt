package com.vxhub.ui.store

import com.vxhub.purchases.VxProduct
import com.vxhub.purchases.VxProductType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.math.RoundingMode

/** One product card — iOS's `VxStoreDataSourceModel`, minus what the configuration already says. */
internal data class StoreRow(
    val identifier: String,
    val title: String,
    val description: String,
    val localizedPrice: String,
    val price: BigDecimal,
    val productType: VxProductType,
    val initialBonus: Int?,
    val renewalBonus: Int?,
    val isAlreadyPurchased: Boolean,
    val badge: VxStoreProductBadge?,
    val image: VxStoreImage?,
    /** What [com.vxhub.VxHub.purchase] buys; null for a dummy product. */
    val product: VxProduct?,
)

internal data class StoreRows(val rows: List<StoreRow>, val isDummyMode: Boolean) {
    fun row(identifier: String?): StoreRow? = identifier?.let { id -> rows.firstOrNull { it.identifier == id } }

    companion object {
        val EMPTY: StoreRows = StoreRows(emptyList(), isDummyMode = false)

        /**
         * The store's cards — iOS's `VxStoreUtil`: the consumable and non-consumable products, in
         * the order VxHub lists them. A non-consumable in [owned] is left out, or marked owned when
         * [VxStoreConfiguration.hideAlreadyPurchasedNonConsumables] is off. Without a single real
         * product, a debuggable app gets the configuration's dummy products instead.
         */
        fun build(
            configuration: VxStoreConfiguration,
            products: List<VxProduct>,
            owned: Set<String>,
            allowDummyProducts: Boolean,
        ): StoreRows {
            val sellable = products.filter { it.type == VxProductType.CONSUMABLE || it.type == VxProductType.NON_CONSUMABLE }
            if (sellable.isNotEmpty()) {
                val rows = sellable.mapNotNull { product ->
                    val store = product.storeProduct
                    configuration.row(
                        identifier = store.id,
                        isAlreadyPurchased = product.type == VxProductType.NON_CONSUMABLE && (store.id in owned || store.productId in owned),
                    ) { isAlreadyPurchased, badge, image ->
                        StoreRow(
                            identifier = store.id,
                            title = store.title,
                            description = store.description,
                            localizedPrice = store.formattedPrice,
                            price = BigDecimal.valueOf(store.priceAmountMicros).divide(MICROS, 6, RoundingMode.HALF_UP),
                            productType = product.type,
                            initialBonus = product.initialBonus,
                            renewalBonus = product.renewalBonus,
                            isAlreadyPurchased = isAlreadyPurchased,
                            badge = badge,
                            image = image,
                            product = product,
                        )
                    }
                }
                return StoreRows(rows, isDummyMode = false)
            }
            if (!allowDummyProducts || configuration.dummyProducts.isEmpty()) return EMPTY
            val rows = configuration.dummyProducts.mapNotNull { dummy ->
                configuration.row(
                    identifier = dummy.identifier,
                    isAlreadyPurchased = dummy.productType == VxProductType.NON_CONSUMABLE && dummy.identifier in owned,
                ) { isAlreadyPurchased, badge, image ->
                    StoreRow(
                        identifier = dummy.identifier,
                        title = dummy.title,
                        description = dummy.description,
                        localizedPrice = dummy.localizedPrice,
                        price = dummy.price,
                        productType = dummy.productType,
                        initialBonus = dummy.initialBonus,
                        renewalBonus = dummy.renewalBonus,
                        isAlreadyPurchased = isAlreadyPurchased,
                        badge = badge,
                        image = image,
                        product = null,
                    )
                }
            }
            return StoreRows(rows, isDummyMode = true)
        }

        private inline fun VxStoreConfiguration.row(
            identifier: String,
            isAlreadyPurchased: Boolean,
            create: (isAlreadyPurchased: Boolean, badge: VxStoreProductBadge?, image: VxStoreImage?) -> StoreRow,
        ): StoreRow? {
            if (hideAlreadyPurchasedNonConsumables && isAlreadyPurchased) return null
            return create(
                isAlreadyPurchased,
                productBadges.firstOrNull { it.productIdentifier == identifier },
                productImages.firstOrNull { it.productIdentifier == identifier }?.image ?: defaultProductImage,
            )
        }

        private val MICROS = BigDecimal(1_000_000)
    }
}

/** iOS's bonus line, hard-coded there too: "+20% Bonus". Null when there is none to show. */
internal fun bonusText(showBonusLabel: Boolean, initialBonus: Int?): String? =
    if (showBonusLabel && initialBonus != null && initialBonus > 0) "+$initialBonus% Bonus" else null

/**
 * Non-consumables bought through a store in this process. VxHub leaves owned non-consumables out
 * of [com.vxhub.VxHub.products] when it loads them; one bought since stays listed until the next
 * load, which is when iOS's keychain check catches it. Core has no public "is owned" query, so
 * the store remembers its own purchases.
 */
internal object StoreOwnedProducts {
    private val owned = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = owned.asStateFlow()

    fun add(productId: String) {
        owned.update { it + productId }
    }
}
