package com.vxhub.ui.paywall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vxhub.ui.VxFont
import com.vxhub.ui.internal.VxIcons
import com.vxhub.ui.internal.paywall.PeriodText
import com.vxhub.ui.internal.vxString

internal enum class HeroVariant { V3, V4 }

/** V3's and V4's options — the two configurations are identical on purpose — and their colours. */
internal class HeroStyle(
    val variant: HeroVariant,
    val font: VxFont,
    val heroImage: Int?,
    val backgroundColor: Color,
    val isLightMode: Boolean,
    val textColor: Color,
    val headlineText: String?,
    val subtitleText: String?,
    val featureItems: List<VxPaywallFeature>,
    val ratingValue: String?,
    val ratingCount: String?,
    val ctaButtonColor: Color,
    val ctaGradientEndColor: Color?,
    val trustText: String?,
    val closeButtonColor: Color,
    val isRedeemCodeEnabled: Boolean,
) {
    val secondaryColor: Color get() = if (isLightMode) Color(0xFF737373) else Color(0xFF8C8C8C)
    val footerColor: Color get() = if (isLightMode) Color(0xFF737373) else Color(0xFF666666)
    val cardColor: Color get() = if (isLightMode) Color(0xFFF7F7F7) else Color(0xFF1A1A1A)
    val idleBorderColor: Color get() = if (isLightMode) Color(0xFFD1D1D1) else Color(0xFF4D4D4D)
    val idleRadioColor: Color get() = if (isLightMode) Color(0xFFB3B3B3) else Color(0xFF666666)
}

internal fun VxMainPaywallV3Configuration.toHeroStyle(): HeroStyle = HeroStyle(
    variant = HeroVariant.V3,
    font = font,
    heroImage = heroImage,
    backgroundColor = backgroundColor,
    isLightMode = isLightMode,
    textColor = textColor,
    headlineText = headlineText,
    subtitleText = subtitleText,
    featureItems = featureItems,
    ratingValue = ratingValue,
    ratingCount = ratingCount,
    ctaButtonColor = ctaButtonColor,
    ctaGradientEndColor = ctaGradientEndColor,
    trustText = trustText,
    closeButtonColor = closeButtonColor,
    isRedeemCodeEnabled = isRedeemCodeEnabled,
)

internal fun VxMainPaywallV4Configuration.toHeroStyle(): HeroStyle = HeroStyle(
    variant = HeroVariant.V4,
    font = font,
    heroImage = heroImage,
    backgroundColor = backgroundColor,
    isLightMode = isLightMode,
    textColor = textColor,
    headlineText = headlineText,
    subtitleText = subtitleText,
    featureItems = featureItems,
    ratingValue = ratingValue,
    ratingCount = ratingCount,
    ctaButtonColor = ctaButtonColor,
    ctaGradientEndColor = ctaGradientEndColor,
    trustText = trustText,
    closeButtonColor = closeButtonColor,
    isRedeemCodeEnabled = isRedeemCodeEnabled,
)

private val StarYellow = Color(0xFFFFCC00)

/**
 * V3 and V4 — iOS's `VxMainSubscriptionV3RootView` and `VxMainSubscriptionV4RootView`: rating,
 * hero image, headline, benefits, product cards, call to action, trust line and legal links in one
 * scrolling column. They differ in the cards and the call to action only.
 */
@Composable
internal fun HeroPaywall(
    style: HeroStyle,
    state: PaywallUiState,
    showsCloseButton: Boolean,
    actions: PaywallActions,
    modifier: Modifier = Modifier,
) {
    val font = style.font
    Box(modifier.fillMaxSize().background(style.backgroundColor)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()),
        ) {
            // Room for the close button.
            Spacer(Modifier.height(48.dp))
            style.ratingValue?.let { rating ->
                RatingRow(rating, style.ratingCount, style, Modifier.align(Alignment.CenterHorizontally))
            }
            Spacer(Modifier.height(20.dp))
            style.heroImage?.let { image ->
                Image(
                    painter = painterResource(image),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 24.dp)
                        .heightIn(max = 120.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(16.dp))
            }
            PaywallText(
                text = style.headlineText ?: vxString("Subscription_V3_HeadlineLabel", "Unlock Full Access"),
                style = font.textStyle(26, FontWeight.Bold, style.textColor),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            PaywallText(
                text = style.subtitleText ?: vxString("Subscription_V3_SubtitleLabel", "Start your free trial today"),
                style = font.textStyle(16, FontWeight.Normal, style.secondaryColor),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Column(Modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                style.featureItems.forEach { feature ->
                    PaywallFeatureRow(feature, font.textStyle(15, FontWeight.Medium, style.textColor), style.ctaButtonColor)
                }
            }
            Spacer(Modifier.height(24.dp))
            val selectedId = state.selected?.id
            Column(Modifier.padding(horizontal = 24.dp).selectableGroup()) {
                state.items.forEach { item ->
                    key(item.id) {
                        val selected = item.id == selectedId
                        when (style.variant) {
                            HeroVariant.V3 -> V3ProductCard(item, selected, style) { actions.onSelect(item) }
                            HeroVariant.V4 -> V4ProductCard(item, selected, style) { actions.onSelect(item) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            val callToAction = when (style.variant) {
                HeroVariant.V3 -> PaywallTexts.v3CallToAction(state.selected)
                HeroVariant.V4 -> PaywallTexts.v4CallToAction(state.selected)
            }
            val gradientEnd = style.ctaGradientEndColor
            PaywallButton(
                text = callToAction,
                textStyle = font.textStyle(17, FontWeight.Bold, Color.White),
                background = if (gradientEnd != null) Brush.horizontalGradient(listOf(style.ctaButtonColor, gradientEnd)) else SolidColor(style.ctaButtonColor),
                shape = RoundedCornerShape(12.dp),
                minHeight = 54.dp,
                isLoading = state.isLoading,
                onClick = actions.onPurchase,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PaywallIcons.LockShield, contentDescription = null, modifier = Modifier.size(14.dp), tint = style.secondaryColor)
                Spacer(Modifier.width(6.dp))
                PaywallText(
                    text = style.trustText ?: vxString("Subscription_V3_TrustLineLabel", "No payment now · Cancel anytime"),
                    style = font.textStyle(13, FontWeight.Medium, style.secondaryColor),
                    modifier = Modifier.weight(1f, fill = false),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))
            val links = buildList {
                add(vxString("Subscription_RestorePurchase") to actions.onRestore)
                add(vxString("Subscription_TermsOfUse") to actions.onTerms)
                add(vxString("Subscription_PrivacyPol") to actions.onPrivacy)
                if (style.isRedeemCodeEnabled) add(vxString("Subscription_ReedemCode") to actions.onRedeemCode)
            }
            PaywallFooter(
                links = links,
                linkStyle = font.textStyle(12, FontWeight.Medium, style.footerColor),
                separator = " | ",
                separatorStyle = font.textStyle(12, FontWeight.Medium, style.footerColor),
                spacing = 4.dp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
        AnimatedVisibility(visible = showsCloseButton, enter = fadeIn(tween(durationMillis = 300)), exit = ExitTransition.None) {
            PaywallCloseButton(
                start = 20.dp,
                top = 12.dp,
                visualSize = 28.dp,
                iconSize = 16.dp,
                tint = style.closeButtonColor,
                background = if (style.isLightMode) Color(0xFFE6E6E6) else Color(0xFF333333),
                onClick = actions.onClose,
            )
        }
    }
}

/** Five stars, the rating and "(count)". */
@Composable
private fun RatingRow(rating: String, count: String?, style: HeroStyle, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ratingStars(rating).forEach { fill ->
                val star = when (fill) {
                    StarFill.FULL -> PaywallIcons.Star
                    StarFill.HALF -> PaywallIcons.StarHalf
                    StarFill.EMPTY -> PaywallIcons.StarOutline
                }
                Icon(star, contentDescription = null, modifier = Modifier.size(16.dp), tint = StarYellow)
            }
        }
        PaywallText(rating, style.font.textStyle(15, FontWeight.Bold, style.textColor), maxLines = 1)
        if (count != null) {
            PaywallText("($count)", style.font.textStyle(14, FontWeight.Normal, style.secondaryColor), maxLines = 1)
        }
    }
}

/** The card both variants draw a product in: 12 dp corners, 2 dp border, 6 dp apart. */
@Composable
private fun HeroCard(
    selected: Boolean,
    style: HeroStyle,
    minHeight: Int,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .heightIn(min = minHeight.dp)
            .clip(shape)
            .background(style.cardColor)
            .border(2.dp, if (selected) style.ctaButtonColor else style.idleBorderColor, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

/**
 * iOS's `VxV3PaywallProductCell`: plan and accent "FREE for 1 Week" on top, "then $9.99/month"
 * and the monthly equivalent below, a "BEST VALUE" tab on the best offer.
 */
@Composable
private fun V3ProductCard(item: PaywallItem, selected: Boolean, style: HeroStyle, onClick: () -> Unit) {
    val font = style.font
    HeroCard(selected, style, minHeight = 72, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            HeroRadio(selected, style)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PaywallText(planName(item), font.textStyle(16, FontWeight.SemiBold, style.textColor), maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        PaywallTexts.v3FreeTrialLabel(item)?.let { trial ->
                            PaywallText(trial, font.textStyle(13, FontWeight.Bold, style.ctaButtonColor), maxLines = 1)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val priceColor = if (style.isLightMode) Color(0xFF666666) else Color(0xFF999999)
                    PaywallText(PaywallTexts.v3PriceAfterTrial(item), font.textStyle(13, FontWeight.Normal, priceColor), maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        PaywallTexts.v3PerMonth(item)?.let { perMonth ->
                            PaywallText(perMonth, font.textStyle(11, FontWeight.Medium, Color(0xFF808080)), maxLines = 1)
                        }
                    }
                }
            }
        }
        // A tab hanging from the card's top edge, inside its clip, as on iOS.
        if (item.isBestOffer) {
            HeroBadge(
                font = font,
                color = style.ctaButtonColor,
                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp),
            )
        }
    }
}

/**
 * iOS's `VxV4PaywallProductCell`, after Apple guideline 3.1.2(c): the billed price is the boldest
 * text, the trial a small grey note; 60 dp with that note, 44 dp without.
 */
@Composable
private fun V4ProductCard(item: PaywallItem, selected: Boolean, style: HeroStyle, onClick: () -> Unit) {
    val font = style.font
    val details = PaywallTexts.v4Details(item)
    HeroCard(selected, style, minHeight = if (details != null) 60 else 44, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            HeroRadio(selected, style)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The price is measured first and never shrinks; the badge gives way first.
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        PaywallText(planName(item), font.textStyle(15, FontWeight.SemiBold, style.textColor), maxLines = 1)
                        if (item.isBestOffer) {
                            Spacer(Modifier.width(4.dp))
                            Box(Modifier.weight(1f)) {
                                HeroBadge(font, style.ctaButtonColor, RoundedCornerShape(9.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    PaywallText(PaywallTexts.v4BilledPrice(item), font.textStyle(16, FontWeight.Bold, style.textColor), maxLines = 1)
                }
                if (details != null) {
                    // Never the accent colour, never bold.
                    PaywallText(details, font.textStyle(12, FontWeight.Normal, Color(0xFF808080)), maxLines = 1)
                }
            }
        }
    }
}

/** "BEST VALUE" on the accent colour. */
@Composable
private fun HeroBadge(font: VxFont, color: Color, shape: Shape, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(18.dp)
            .background(color, shape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        PaywallText(PaywallTexts.bestValueBadge(), font.textStyle(9, FontWeight.Bold, Color.White), maxLines = 1)
    }
}

/** SF `checkmark.circle.fill` in the accent colour when selected, a grey ring otherwise. */
@Composable
private fun HeroRadio(selected: Boolean, style: HeroStyle) {
    if (selected) {
        Icon(VxIcons.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp), tint = style.ctaButtonColor)
    } else {
        val color = style.idleRadioColor
        Canvas(Modifier.size(22.dp)) {
            val stroke = 1.5.dp.toPx()
            // The same circle the check-mark icon draws: 20 of its 24 units.
            drawCircle(color = color, radius = size.minDimension * 10f / 24f - stroke / 2f, style = Stroke(width = stroke))
        }
    }
}

/** "Monthly", "Yearly", … — iOS's `subPeriod?.periodString`. */
private fun planName(item: PaywallItem): String = item.period?.let(PeriodText::period).orEmpty()
