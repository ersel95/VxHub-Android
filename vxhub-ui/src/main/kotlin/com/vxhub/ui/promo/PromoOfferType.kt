package com.vxhub.ui.promo

import androidx.annotation.RawRes

/** Which promo offer design to show — iOS's `PromoOfferType`. */
public sealed interface PromoOfferType {
    /**
     * Navy screen: the discount, two rows of showcase items scrolling in opposite directions, the
     * old and new price.
     *
     * The showcase icons are the app's (bitmap or vector) drawables named `special_offer_scam`, `special_offer_travel`,
     * `special_offer_education`, `special_offer_community`, `special_offer_shopping`,
     * `special_offer_bet`, `special_offer_ads`, `special_offer_lifestyle`, `special_offer_finance`
     * and `special_offer_network`, and the close button is `special_offer_x_mark` — the asset names
     * iOS reads from the app's catalog. A missing icon is left out; a missing close image falls back
     * to a built-in cross.
     */
    public data object V1 : PromoOfferType

    /**
     * Black screen around a muted video that loops, stretched to fill the space between the
     * discount and the price — iOS's `.v2(videoBundleName:)`.
     *
     * @property video the app's raw resource, e.g. `R.raw.promo` for `res/raw/promo.mp4`.
     */
    public data class V2(@param:RawRes val video: Int) : PromoOfferType
}
