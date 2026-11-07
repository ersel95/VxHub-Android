package com.vxhub.ui.promo

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

private val previewContent = PromoOfferContent(oldPrice = "$59.99", newPrice = "$29.99", discountPercentage = "50%")

private val previewActions = PromoOfferActions(onClose = {}, onClaim = {}, onRestore = {}, onTerms = {}, onPrivacy = {})

@Preview(name = "Promo offer V1", widthDp = 393, heightDp = 852)
@Composable
private fun PromoOfferV1Preview() {
    PromoOfferV1Screen(previewContent, isLoading = false, actions = previewActions)
}

@Preview(name = "Promo offer V1, buying", widthDp = 393, heightDp = 852)
@Composable
private fun PromoOfferV1LoadingPreview() {
    PromoOfferV1Screen(previewContent, isLoading = true, actions = previewActions)
}

@Preview(name = "Promo offer V2", widthDp = 393, heightDp = 852)
@Composable
private fun PromoOfferV2Preview() {
    PromoOfferV2Screen(previewContent, video = 0, isLoading = false, actions = previewActions)
}

@Preview(name = "Promo offer V2, small screen", widthDp = 320, heightDp = 568)
@Composable
private fun PromoOfferV2SmallPreview() {
    PromoOfferV2Screen(previewContent, video = 0, isLoading = false, actions = previewActions)
}
