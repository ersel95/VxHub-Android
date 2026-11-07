package com.vxhub.ui.promo

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The promo offer's fixed colours (iOS's `UIColor.bx…` extensions). */
internal object PromoColors {
    /** `bxNavyBlue`: V1 background. */
    val Navy = Color(0xFF14213D)

    /** `bx478AFF`: V1 claim button. */
    val ClaimBlue = Color(0xFF478AFF)

    /** `bx455288`: V1 footer links. */
    val FooterV1 = Color(0xFF455288)

    /** V2 footer links. */
    val FooterV2 = Color(0xFF535353)

    /** `bxCFCEE9`: V1 description. */
    val Description = Color(0xFFCFCEE9)

    /** `bx4BE162`: the one-time offer line. */
    val OfferGreen = Color(0xFF4BE162)

    /** `bx9494A8`: struck-through old price, V2 close button. */
    val OldPrice = Color(0xFF9494A8)

    /** `bx48486B`: V1 close button. */
    val CloseV1 = Color(0xFF48486B)

    /** V1 discount text gradient: #9AD5FF → #94A6FF → #9CC7FF, and its glow. */
    val DiscountGradient = listOf(Color(0xFF9AD5FF), Color(0xFF94A6FF), Color(0xFF9CC7FF))
    val DiscountGlow = Color(113, 170, 226).copy(alpha = 0.5f)

    /** V2 claim button gradient. */
    val ClaimGradient = listOf(Color(190, 13, 167), Color(240, 48, 62))
}

/**
 * The V1 showcase items — iOS's `SpecialOfferCategories`: a title key, the app drawable iOS reads
 * as `UIImage(named:)`, and the gradient drawn from the bottom-trailing to the top-leading corner.
 */
internal enum class ShowcaseCategory(
    val titleKey: String,
    val drawableName: String,
    val gradientStart: Color,
    val gradientEnd: Color,
) {
    SCAM("PromoOffer_Scam", "special_offer_scam", Color(0xFFE84F66), Color(0xFFA4293A)),
    TRAVEL("PromoOffer_Travel", "special_offer_travel", Color(0xFFE453F5), Color(0xFFAD25BE)),
    EDUCATION("PromoOffer_Education", "special_offer_education", Color(0xFF82C437), Color(0xFF436B22)),
    COMMUNITY("PromoOffer_Community", "special_offer_community", Color(0xFFEED745), Color(0xFFDFC836)),
    SHOPPING("PromoOffer_Shopping", "special_offer_shopping", Color(0xFF1BBBB7), Color(0xFF147C75)),
    BET("PromoOffer_Bet", "special_offer_bet", Color(0xFF9B51F1), Color(0xFF5D1AAA)),
    ADS("PromoOffer_Ads", "special_offer_ads", Color(0xFFF69956), Color(0xFFBA5B17)),
    LIFESTYLE("PromoOffer_Lifestyle", "special_offer_lifestyle", Color(0xFFC43774), Color(0xFFA2295F)),
    FINANCE("PromoOffer_Finance", "special_offer_finance", Color(0xFF375AC4), Color(0xFF2D388A)),
    NETWORK("PromoOffer_Network", "special_offer_network", Color(0xFF3789C4), Color(0xFF3473B1)),
}

/** The V1 close button image iOS reads from the app's catalog. */
internal const val CLOSE_DRAWABLE_NAME: String = "special_offer_x_mark"

/**
 * The app's drawable called [name], or 0. iOS's `UIImage(named:)` reads the app's own assets by
 * name; these names are fixed string constants, which the resource shrinker keeps.
 */
@SuppressLint("DiscouragedApi")
internal fun Context.appDrawable(name: String): Int = resources.getIdentifier(name, "drawable", packageName)

/** Icons iOS ships as images in the SDK (`gren_tick_icon`, `secure_restore_icon`), drawn as vectors. */
internal object PromoIcons {
    /** Outlined circle with a check; drawn tinted. */
    val Tick: ImageVector by lazy {
        ImageVector.Builder(name = "VxPromo.Tick", defaultWidth = 16.dp, defaultHeight = 16.dp, viewportWidth = 48f, viewportHeight = 48f)
            .addPath(
                pathData = PathParser().parsePathString("M24,44C35.05,44 44,35.05 44,24C44,12.95 35.05,4 24,4C12.95,4 4,12.95 4,24C4,35.05 12.95,44 24,44Z").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 4f,
                strokeLineJoin = StrokeJoin.Round,
            )
            .addPath(
                pathData = PathParser().parsePathString("M16,24L22,30L34,18").toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
    }

    /** A golden padlock in its own colours (not tinted), like the fxemoji lock iOS ships. */
    val Lock: ImageVector by lazy {
        ImageVector.Builder(name = "VxPromo.Lock", defaultWidth = 16.dp, defaultHeight = 16.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = PathParser().parsePathString("M7.5,11V7.5C7.5,5.01 9.51,3 12,3C14.49,3 16.5,5.01 16.5,7.5V11").toNodes(),
                stroke = SolidColor(Color(0xFFB0B7BC)),
                strokeLineWidth = 2.6f,
                strokeLineCap = StrokeCap.Round,
            )
            .addPath(
                pathData = PathParser().parsePathString("M6,10H18A2,2 0,0 1,20 12V20A2,2 0,0 1,18 22H6A2,2 0,0 1,4 20V12A2,2 0,0 1,6 10Z").toNodes(),
                fill = SolidColor(Color(0xFFFFB636)),
            )
            .addPath(
                pathData = PathParser().parsePathString("M12,14A1.75,1.75 0,1 1,12 17.5A1.75,1.75 0,1 1,12 14ZM11.3,16.5H12.7V19.3H11.3Z").toNodes(),
                fill = SolidColor(Color(0xFFE38F00)),
            )
            .build()
    }
}
