package com.vxhub.ui.store

import com.vxhub.ui.VxAnalyticsEvent
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vxhub.purchases.VxProductType
import com.vxhub.ui.VxFont
import java.math.BigDecimal
import kotlin.time.Duration

/** How the store sells — iOS's `VxStorePurchaseMode`. */
public enum class VxStorePurchaseMode {
    /** Every card has its own buy button showing the price. */
    PER_CARD,

    /** Cards are selectable (the first one starts selected) and one button buys the selection. */
    SELECT_AND_BUY,
}

/**
 * An image the store draws where iOS takes an SF Symbol or asset-catalog name. iOS tints symbols
 * with the spot's accent colour and draws assets as they are; the two cases keep that difference.
 */
public sealed interface VxStoreImage {
    /**
     * One of the app's drawables — a bitmap or a vector drawable — drawn in its own colours, like
     * an iOS asset-catalog image.
     */
    public data class Resource(@param:DrawableRes val resId: Int) : VxStoreImage

    /** A vector tinted with the accent colour of the spot it is drawn in — an iOS SF Symbol. */
    public data class Vector(val imageVector: ImageVector) : VxStoreImage

    public companion object {
        /** iOS's `bag.fill`: the default product image. */
        public val Bag: VxStoreImage = Vector(StoreSymbols.Bag)

        /** iOS's `dollarsign.circle.fill`: the default balance icon. */
        public val Coin: VxStoreImage = Vector(StoreSymbols.Coin)

        /** iOS's `star.fill`: the default social-proof icon. */
        public val Star: VxStoreImage = Vector(StoreSymbols.Star)
    }
}

/**
 * A product to show when the store has no real ones, so a screen can be built before the panel
 * has products. Used only in debuggable builds of the app — iOS's `VxStoreDummyProduct`, which is
 * used only in `DEBUG`. Buying one does nothing but report success.
 */
public class VxStoreDummyProduct(
    public val identifier: String,
    public val title: String,
    public val description: String = "",
    public val localizedPrice: String,
    public val price: BigDecimal,
    public val productType: VxProductType = VxProductType.CONSUMABLE,
    public val initialBonus: Int? = null,
    public val renewalBonus: Int? = null,
)

/** A ribbon on one product's card — iOS's `VxStoreProductBadge`. */
public class VxStoreProductBadge(
    public val productIdentifier: String,
    public val badgeText: String,
    public val badgeColor: Color = StoreColors.systemBlue(isLightMode = true),
    public val badgeTextColor: Color = Color.White,
)

/** The image of one product's card — iOS's `VxStoreProductImage`. */
public class VxStoreProductImage(
    public val productIdentifier: String,
    public val image: VxStoreImage,
) {
    /** One of the app's drawables, drawn as it is. */
    public constructor(productIdentifier: String, @DrawableRes imageRes: Int) :
        this(productIdentifier, VxStoreImage.Resource(imageRes))
}

/** One line under a product in the V2 list — iOS's `(icon:, text:)` tuple. */
public class VxStoreProductFeature(
    public val icon: VxStoreImage,
    public val text: String,
)

/**
 * The look and behaviour of the coin and one-time-product store: [VxStoreV1Configuration] for the
 * grid, [VxStoreV2Configuration] for the list. iOS passes one of two optional configurations;
 * here the type says which store to draw.
 *
 * Colours default to iOS's system colours. iOS resolves those against the system appearance;
 * these follow [isLightMode], which is what the store's own defaults (text colour) follow too.
 */
public sealed interface VxStoreConfiguration {
    public val font: VxFont
    public val backgroundColor: Color
    public val isLightMode: Boolean
    public val textColor: Color

    /** A bitmap or vector drawable above the balance, at most 120 dp tall. */
    @get:DrawableRes
    public val heroImage: Int?
    public val headlineText: String?
    public val subtitleText: String?

    public val showBalance: Boolean
    public val balanceIcon: VxStoreImage?
    public val balanceLabel: String?

    public val cardCornerRadius: Dp
    public val cardBackgroundColor: Color
    public val cardBorderColor: Color
    public val cardBorderWidth: Dp
    public val selectedCardBorderColor: Color
    public val selectedCardBorderWidth: Dp

    public val productImageSize: Dp
    public val productImages: List<VxStoreProductImage>

    /** Image of a product with no entry in [productImages]. */
    public val defaultProductImage: VxStoreImage?
    public val showBonusLabel: Boolean
    public val bonusLabelColor: Color

    /** Buy buttons in [VxStorePurchaseMode.PER_CARD]; also the accent symbols are tinted with. */
    public val buyButtonColor: Color

    /** Makes the buy buttons a left-to-right gradient from [buyButtonColor]. */
    public val buyButtonGradientEndColor: Color?
    public val buyButtonTextColor: Color
    public val buyButtonCornerRadius: Dp

    public val productBadges: List<VxStoreProductBadge>
    public val purchaseMode: VxStorePurchaseMode

    /** The buy button of [VxStorePurchaseMode.SELECT_AND_BUY]. */
    public val ctaButtonColor: Color
    public val ctaGradientEndColor: Color?
    public val ctaButtonTextColor: Color
    public val ctaText: String?

    public val isCloseButtonEnabled: Boolean
    public val closeButtonColor: Color

    /** How long the close button stays hidden after the store opens. */
    public val closeButtonDelay: Duration
    public val analyticsEvents: Set<VxAnalyticsEvent>

    /** Leave out non-consumables the user already owns, instead of marking them [purchasedBadgeText]. */
    public val hideAlreadyPurchasedNonConsumables: Boolean
    public val purchasedBadgeText: String?
    public val purchasedBadgeColor: Color

    /** Shown in debuggable builds when there is no real product. */
    public val dummyProducts: List<VxStoreDummyProduct>
}

/** The grid store — iOS's `VxStoreV1Configuration`, with the same options and defaults. */
public class VxStoreV1Configuration(
    override val font: VxFont = VxFont.Rounded,
    override val backgroundColor: Color = Color.White,
    override val isLightMode: Boolean = true,
    override val textColor: Color = if (isLightMode) Color.Black else Color.White,
    @param:DrawableRes override val heroImage: Int? = null,
    override val headlineText: String? = null,
    override val subtitleText: String? = null,
    override val showBalance: Boolean = true,
    override val balanceIcon: VxStoreImage? = VxStoreImage.Coin,
    override val balanceLabel: String? = "Your Balance:",
    /** Cards per row. */
    public val columnsCount: Int = 2,
    override val cardCornerRadius: Dp = 16.dp,
    override val cardBackgroundColor: Color = StoreColors.secondarySystemBackground(isLightMode),
    override val cardBorderColor: Color = StoreColors.separator(isLightMode),
    override val cardBorderWidth: Dp = 1.dp,
    override val selectedCardBorderColor: Color = StoreColors.systemBlue(isLightMode),
    override val selectedCardBorderWidth: Dp = 2.5.dp,
    override val productImageSize: Dp = 48.dp,
    override val productImages: List<VxStoreProductImage> = emptyList(),
    override val defaultProductImage: VxStoreImage? = VxStoreImage.Bag,
    override val showBonusLabel: Boolean = true,
    override val bonusLabelColor: Color = StoreColors.systemGreen(isLightMode),
    override val buyButtonColor: Color = StoreColors.systemBlue(isLightMode),
    override val buyButtonGradientEndColor: Color? = null,
    override val buyButtonTextColor: Color = Color.White,
    override val buyButtonCornerRadius: Dp = 10.dp,
    override val productBadges: List<VxStoreProductBadge> = emptyList(),
    override val purchaseMode: VxStorePurchaseMode = VxStorePurchaseMode.PER_CARD,
    override val ctaButtonColor: Color = StoreColors.systemBlue(isLightMode),
    override val ctaGradientEndColor: Color? = null,
    override val ctaButtonTextColor: Color = Color.White,
    override val ctaText: String? = "Purchase",
    override val isCloseButtonEnabled: Boolean = true,
    override val closeButtonColor: Color = StoreColors.Gray,
    override val closeButtonDelay: Duration = Duration.ZERO,
    override val analyticsEvents: Set<VxAnalyticsEvent> = emptySet(),
    override val hideAlreadyPurchasedNonConsumables: Boolean = true,
    override val purchasedBadgeText: String? = "OWNED",
    override val purchasedBadgeColor: Color = StoreColors.systemGreen(isLightMode),
    override val dummyProducts: List<VxStoreDummyProduct> = emptyList(),
) : VxStoreConfiguration

/** The list store — iOS's `VxStoreV2Configuration`, with the same options and defaults. */
public class VxStoreV2Configuration(
    override val font: VxFont = VxFont.Rounded,
    override val backgroundColor: Color = Color.White,
    override val isLightMode: Boolean = true,
    override val textColor: Color = if (isLightMode) Color.Black else Color.White,
    @param:DrawableRes override val heroImage: Int? = null,
    override val headlineText: String? = null,
    override val subtitleText: String? = null,
    override val showBalance: Boolean = true,
    override val balanceIcon: VxStoreImage? = VxStoreImage.Coin,
    override val balanceLabel: String? = "Your Balance:",
    override val cardCornerRadius: Dp = 16.dp,
    override val cardBackgroundColor: Color = StoreColors.secondarySystemBackground(isLightMode),
    override val cardBorderColor: Color = StoreColors.separator(isLightMode),
    override val cardBorderWidth: Dp = 1.dp,
    override val selectedCardBorderColor: Color = StoreColors.systemBlue(isLightMode),
    override val selectedCardBorderWidth: Dp = 2.5.dp,
    override val productImageSize: Dp = 48.dp,
    override val productImages: List<VxStoreProductImage> = emptyList(),
    override val defaultProductImage: VxStoreImage? = VxStoreImage.Bag,
    override val showBonusLabel: Boolean = true,
    override val bonusLabelColor: Color = StoreColors.systemGreen(isLightMode),
    /** Show the store's product description under the title. */
    public val showProductDescription: Boolean = true,
    /** Feature lines per product identifier. */
    public val productFeatures: Map<String, List<VxStoreProductFeature>> = emptyMap(),
    /** A line such as "Trusted by 1M players" above the products. */
    public val socialProofText: String? = null,
    public val socialProofIcon: VxStoreImage? = VxStoreImage.Star,
    override val buyButtonColor: Color = StoreColors.systemBlue(isLightMode),
    override val buyButtonGradientEndColor: Color? = null,
    override val buyButtonTextColor: Color = Color.White,
    override val buyButtonCornerRadius: Dp = 10.dp,
    override val productBadges: List<VxStoreProductBadge> = emptyList(),
    override val purchaseMode: VxStorePurchaseMode = VxStorePurchaseMode.PER_CARD,
    override val ctaButtonColor: Color = StoreColors.systemBlue(isLightMode),
    override val ctaGradientEndColor: Color? = null,
    override val ctaButtonTextColor: Color = Color.White,
    override val ctaText: String? = "Purchase",
    override val isCloseButtonEnabled: Boolean = true,
    override val closeButtonColor: Color = StoreColors.Gray,
    override val closeButtonDelay: Duration = Duration.ZERO,
    override val analyticsEvents: Set<VxAnalyticsEvent> = emptySet(),
    override val hideAlreadyPurchasedNonConsumables: Boolean = true,
    override val purchasedBadgeText: String? = "OWNED",
    override val purchasedBadgeColor: Color = StoreColors.systemGreen(isLightMode),
    override val dummyProducts: List<VxStoreDummyProduct> = emptyList(),
) : VxStoreConfiguration

/** iOS's system colours in their light and dark variants, plus the store's fixed greys. */
internal object StoreColors {
    fun systemBlue(isLightMode: Boolean): Color = if (isLightMode) Color(0xFF007AFF) else Color(0xFF0A84FF)

    fun systemGreen(isLightMode: Boolean): Color = if (isLightMode) Color(0xFF34C759) else Color(0xFF30D158)

    fun systemYellow(isLightMode: Boolean): Color = if (isLightMode) Color(0xFFFFCC00) else Color(0xFFFFD60A)

    fun systemOrange(isLightMode: Boolean): Color = if (isLightMode) Color(0xFFFF9500) else Color(0xFFFF9F0A)

    fun secondarySystemBackground(isLightMode: Boolean): Color = if (isLightMode) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)

    fun separator(isLightMode: Boolean): Color = if (isLightMode) Color(0x4A3C3C43) else Color(0x99545458)

    /** `UIColor.gray`. */
    val Gray: Color = Color(0xFF808080)

    /** Balance label, subtitle and social proof: `UIColor(white: 0.45)` / `0.55`. */
    fun secondaryText(isLightMode: Boolean): Color = if (isLightMode) Color(0xFF737373) else Color(0xFF8C8C8C)

    /** V2 product description: `UIColor(white: 0.4)` / `0.6`. */
    fun descriptionText(isLightMode: Boolean): Color = if (isLightMode) Color(0xFF666666) else Color(0xFF999999)

    /** Close button circle: `UIColor(white: 0.9)` / `0.2`. */
    fun closeBackground(isLightMode: Boolean): Color = if (isLightMode) Color(0xFFE6E6E6) else Color(0xFF333333)
}
