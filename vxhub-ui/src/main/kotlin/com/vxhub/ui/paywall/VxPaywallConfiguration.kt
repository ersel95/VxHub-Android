package com.vxhub.ui.paywall

import com.vxhub.ui.VxAnalyticsEvent
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.vxhub.ui.VxFont
import kotlin.time.Duration

/**
 * How a VxHub main paywall looks. The type picks the layout:
 *
 * - [VxMainPaywallConfiguration] — V1 or V2, by [VxMainPaywallConfiguration.paywallType];
 * - [VxMainPaywallV3Configuration] — V3;
 * - [VxMainPaywallV4Configuration] — V4, V3's layout made to follow Apple guideline 3.1.2(c):
 *   the billed price is the most prominent text and the call to action always states it.
 *
 * Pass any of them to [VxPaywall] or [com.vxhub.ui.VxHubUI.showPaywall].
 */
public sealed interface VxPaywallConfiguration

/** The layouts [VxMainPaywallConfiguration] draws — iOS's `VxMainPaywallTypes.v1` and `.v2`. */
public enum class VxMainPaywallType {
    /** App logo and name, benefit list, free-trial switch, product list and a solid button. */
    V1,

    /** Benefits and products at the bottom over a background image or looping video. */
    V2,
}

/**
 * One benefit line: V1/V2's `descriptionItems`, V3/V4's `featureItems`.
 *
 * @property icon a drawable; null draws a check-mark in the paywall's accent colour.
 * @property tintIcon paint [icon] in the accent colour, as iOS does for SF Symbols. Off by default
 *   for a drawable, so a full-colour image keeps its colours (iOS's named images).
 */
@Immutable
public data class VxPaywallFeature(
    val text: String,
    @param:DrawableRes val icon: Int? = null,
    val tintIcon: Boolean = icon == null,
)

/**
 * The V1 and V2 main paywall — iOS's `VxMainPaywallConfiguration`, with the same options and
 * defaults. Images are drawable resources where iOS takes asset names.
 *
 * @property paywallType V1 or V2. V3 and V4 have their own configuration types.
 * @property font text of the screen.
 * @property appLogoImage V1's logo, at the top (iOS `appLogoImageName`).
 * @property appNameImage V1's wordmark under the logo (iOS `appNameImageName`).
 * @property descriptionFont text of [descriptionItems].
 * @property mainButtonColor V1's purchase button. V2's button is VxHub's own pink gradient, as on iOS.
 * @property backgroundImage drawn behind everything, cropped to fill.
 * @property video V2 only: a looping, muted `res/raw` video at the top of the screen, replacing
 *   [backgroundImage] (iOS `videoBundleName`).
 * @property videoHeightMultiplier share of the screen height the video covers; black below it.
 * @property showGradientVideoBackground fade the video into black behind the products.
 * @property isLightMode light or dark variants of the neutral colours (dots, borders, status bar).
 * @property textColor primary text; black in light mode and white in dark mode unless given.
 * @property analyticsEvents events to log; none by default.
 * @property dismissButtonColor V1's close button; grey when null.
 * @property isCloseButtonEnabled show the close button. Without it, Back does not leave either.
 * @property closeButtonColor V2's close button.
 * @property isRedeemCodeEnabled V2's "Redeem Code" link. V1 has none, as on iOS.
 */
@Immutable
public class VxMainPaywallConfiguration(
    public val paywallType: VxMainPaywallType,
    public val font: VxFont = VxFont.Rounded,
    @param:DrawableRes public val appLogoImage: Int? = null,
    @param:DrawableRes public val appNameImage: Int? = null,
    public val descriptionFont: VxFont = font,
    public val descriptionItems: List<VxPaywallFeature> = emptyList(),
    public val mainButtonColor: Color = Color(0xFF800080),
    public val backgroundColor: Color = Color.White,
    @param:DrawableRes public val backgroundImage: Int? = null,
    @param:RawRes public val video: Int? = null,
    public val videoHeightMultiplier: Float = 0.72f,
    public val showGradientVideoBackground: Boolean = false,
    public val isLightMode: Boolean = true,
    textColor: Color? = null,
    public val analyticsEvents: Set<VxAnalyticsEvent> = emptySet(),
    public val dismissButtonColor: Color? = null,
    public val isCloseButtonEnabled: Boolean = true,
    public val closeButtonColor: Color = Color(0xFF0000FF),
    public val isRedeemCodeEnabled: Boolean = true,
) : VxPaywallConfiguration {
    public val textColor: Color = textColor ?: if (isLightMode) Color.Black else Color.White
}

/**
 * The V3 main paywall: rating, hero image, headline, benefits, product cards and a call to action
 * that offers the free trial — iOS's `VxMainPaywallV3Configuration`, with the same options and
 * defaults. [VxMainPaywallV4Configuration] takes the same options and puts the price first.
 *
 * @property heroImage drawn above the headline, at most 120 dp tall (iOS `heroImageName`).
 * @property textColor primary text; black in light mode and white in dark mode unless given.
 * @property headlineText the panel's `Subscription_V3_HeadlineLabel` when null.
 * @property subtitleText the panel's `Subscription_V3_SubtitleLabel` when null.
 * @property ratingValue e.g. "4.8"; the rating row shows only when set.
 * @property ratingCount e.g. "12.5K", shown in parentheses after [ratingValue].
 * @property ctaButtonColor the call to action, selection borders, badges and tinted icons.
 * @property ctaGradientEndColor turns the call to action into a gradient from [ctaButtonColor].
 * @property trustText the line under the call to action; the panel's `Subscription_V3_TrustLineLabel` when null.
 * @property closeButtonDelay keep the close button hidden this long, then fade it in.
 */
@Immutable
public class VxMainPaywallV3Configuration(
    public val font: VxFont = VxFont.Rounded,
    @param:DrawableRes public val heroImage: Int? = null,
    public val backgroundColor: Color = Color.White,
    public val isLightMode: Boolean = true,
    textColor: Color? = null,
    public val headlineText: String? = null,
    public val subtitleText: String? = null,
    public val featureItems: List<VxPaywallFeature> = emptyList(),
    public val ratingValue: String? = null,
    public val ratingCount: String? = null,
    public val ctaButtonColor: Color = Color(0xFF007AFF),
    public val ctaGradientEndColor: Color? = null,
    public val trustText: String? = null,
    public val isCloseButtonEnabled: Boolean = true,
    public val closeButtonColor: Color = Color(0xFF808080),
    public val analyticsEvents: Set<VxAnalyticsEvent> = emptySet(),
    public val closeButtonDelay: Duration = Duration.ZERO,
    public val isRedeemCodeEnabled: Boolean = true,
) : VxPaywallConfiguration {
    public val textColor: Color = textColor ?: if (isLightMode) Color.Black else Color.White
}

/**
 * The V4 main paywall — iOS's `VxMainPaywallV4Configuration`: the same options as
 * [VxMainPaywallV3Configuration] (switching is a type change), drawn to follow Apple guideline
 * 3.1.2(c), after Apple rejected V3 for promoting the free trial over the price:
 *
 * - the billed price ("$9.99/month") is the largest, boldest text of each product;
 * - trial information is small, regular-weight and grey, never in the accent colour;
 * - the call to action always states the billed price.
 *
 * See [VxMainPaywallV3Configuration] for the meaning of each option.
 */
@Immutable
public class VxMainPaywallV4Configuration(
    public val font: VxFont = VxFont.Rounded,
    @param:DrawableRes public val heroImage: Int? = null,
    public val backgroundColor: Color = Color.White,
    public val isLightMode: Boolean = true,
    textColor: Color? = null,
    public val headlineText: String? = null,
    public val subtitleText: String? = null,
    public val featureItems: List<VxPaywallFeature> = emptyList(),
    public val ratingValue: String? = null,
    public val ratingCount: String? = null,
    public val ctaButtonColor: Color = Color(0xFF007AFF),
    public val ctaGradientEndColor: Color? = null,
    public val trustText: String? = null,
    public val isCloseButtonEnabled: Boolean = true,
    public val closeButtonColor: Color = Color(0xFF808080),
    public val analyticsEvents: Set<VxAnalyticsEvent> = emptySet(),
    public val closeButtonDelay: Duration = Duration.ZERO,
    public val isRedeemCodeEnabled: Boolean = true,
) : VxPaywallConfiguration {
    public val textColor: Color = textColor ?: if (isLightMode) Color.Black else Color.White
}

internal val VxPaywallConfiguration.analyticsEventSet: Set<VxAnalyticsEvent>
    get() = when (this) {
        is VxMainPaywallConfiguration -> analyticsEvents
        is VxMainPaywallV3Configuration -> analyticsEvents
        is VxMainPaywallV4Configuration -> analyticsEvents
    }

internal val VxPaywallConfiguration.closeEnabled: Boolean
    get() = when (this) {
        is VxMainPaywallConfiguration -> isCloseButtonEnabled
        is VxMainPaywallV3Configuration -> isCloseButtonEnabled
        is VxMainPaywallV4Configuration -> isCloseButtonEnabled
    }

/** V1 and V2 show the close button at once; V3 and V4 may hold it back. */
internal val VxPaywallConfiguration.closeDelay: Duration
    get() = when (this) {
        is VxMainPaywallConfiguration -> Duration.ZERO
        is VxMainPaywallV3Configuration -> closeButtonDelay
        is VxMainPaywallV4Configuration -> closeButtonDelay
    }

internal val VxPaywallConfiguration.lightMode: Boolean
    get() = when (this) {
        is VxMainPaywallConfiguration -> isLightMode
        is VxMainPaywallV3Configuration -> isLightMode
        is VxMainPaywallV4Configuration -> isLightMode
    }
