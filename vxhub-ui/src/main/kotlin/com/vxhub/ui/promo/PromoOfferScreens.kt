package com.vxhub.ui.promo

import androidx.annotation.RawRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vxhub.ui.R
import com.vxhub.ui.VxFont
import com.vxhub.ui.fontFamily
import com.vxhub.ui.internal.VxIcons
import com.vxhub.ui.internal.vxString
import com.vxhub.ui.store.RichText
import com.vxhub.ui.store.VxLoadingButton
import com.vxhub.ui.store.textStyle

/** What the buttons of a promo offer do. */
internal class PromoOfferActions(
    val onClose: () -> Unit,
    val onClaim: () -> Unit,
    val onRestore: () -> Unit,
    val onTerms: () -> Unit,
    val onPrivacy: () -> Unit,
)

/** iOS uses SF Rounded throughout the promo offer. */
private val Rounded: VxFont = VxFont.Rounded

/** iOS moves the showcase 1 pt every 0.03 s. */
private val MarqueeSpeed: Dp = 33.33.dp

// region V1

/** iOS's `PromoOfferRootView`. */
@Composable
internal fun PromoOfferV1Screen(
    content: PromoOfferContent,
    isLoading: Boolean,
    actions: PromoOfferActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(PromoColors.Navy)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            StretchColumn(
                viewportHeight = maxHeight - 32.dp,
                flexibleMinHeight = 0.dp,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                top = {
                    V1Header(isLoading, actions.onClose)
                    Spacer(Modifier.height(32.dp))
                    V1Discount(content.discountPercentage)
                    Spacer(Modifier.height(32.dp))
                    ShowcaseRow(reverse = false)
                    Spacer(Modifier.height(16.dp))
                    ShowcaseRow(reverse = true)
                    Spacer(Modifier.height(32.dp))
                    Description(content.discountPercentage, Rounded.textStyle(14.sp, FontWeight.Normal, PromoColors.Description))
                },
                flexible = {},
                bottom = {
                    OneTimeOffer(14.sp, FontWeight.Medium)
                    Spacer(Modifier.height(24.dp))
                    PriceComparison(content)
                    Spacer(Modifier.height(24.dp))
                    ClaimButton(isLoading, actions.onClaim, SolidColor(PromoColors.ClaimBlue), RoundedCornerShape(16.dp))
                    Spacer(Modifier.height(24.dp))
                    FooterLinks(PromoColors.FooterV1, actions)
                },
            )
        }
    }
}

@Composable
private fun V1Header(isLoading: Boolean, onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        RichText(
            text = vxString("PromoOffer_NavigationTitle"),
            style = Rounded.textStyle(20.sp, FontWeight.Bold, Color.White),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 40.dp),
        )
        // iOS fades the close button out (alpha 0) while busy, keeping the title centred.
        val context = LocalContext.current
        val customClose = remember(context) { context.appDrawable(CLOSE_DRAWABLE_NAME) }
        CloseButton(
            painter = if (customClose != 0) painterResource(customClose) else rememberVectorPainter(VxIcons.Close),
            tint = PromoColors.CloseV1,
            iconSize = 24.dp,
            enabled = !isLoading,
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = 12.dp).alpha(if (isLoading) 0f else 1f),
        )
    }
}

@Composable
private fun V1Discount(percentage: String) {
    val glowRadius = with(LocalDensity.current) { 16.dp.toPx() }
    BasicText(
        text = PromoOfferLogic.discountText(vxString("PromoOffer_DiscountAmountDescription"), percentage),
        modifier = Modifier.padding(horizontal = 24.dp).rotate(-7.13f),
        style = TextStyle(
            brush = Brush.horizontalGradient(PromoColors.DiscountGradient),
            fontFamily = Rounded.fontFamily,
            fontSize = 50.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            shadow = Shadow(color = PromoColors.DiscountGlow, offset = Offset.Zero, blurRadius = glowRadius),
        ),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 30.sp, maxFontSize = 50.sp),
    )
}

@Composable
private fun ShowcaseRow(reverse: Boolean) {
    Marquee(reverse = reverse, speed = MarqueeSpeed, modifier = Modifier.fillMaxWidth().height(70.dp)) {
        Row(Modifier.padding(end = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ShowcaseCategory.entries.forEach { ShowcaseItem(it) }
        }
    }
}

/** iOS's `ShowcaseItemView`. */
@Composable
private fun ShowcaseItem(category: ShowcaseCategory) {
    val context = LocalContext.current
    val icon = remember(category, context) { context.appDrawable(category.drawableName) }
    Row(
        Modifier
            .height(70.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(category.gradientStart, category.gradientEnd), start = Offset.Infinite, end = Offset.Zero))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != 0) {
            Image(painterResource(icon), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(48.dp))
        }
        RichText(
            text = vxString(category.titleKey),
            style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

// endregion

// region V2

/** iOS's `PromoOfferV2RootView`. */
@Composable
internal fun PromoOfferV2Screen(
    content: PromoOfferContent,
    @RawRes video: Int,
    isLoading: Boolean,
    actions: PromoOfferActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            StretchColumn(
                viewportHeight = maxHeight - 32.dp,
                flexibleMinHeight = 200.dp,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                top = {
                    Spacer(Modifier.height(40.dp))
                    V2Discount(content.discountPercentage)
                },
                flexible = { LoopingVideo(video, Modifier.fillMaxSize()) },
                bottom = {
                    Spacer(Modifier.height(4.dp))
                    Description(content.discountPercentage, Rounded.textStyle(16.sp, FontWeight.Medium, Color.White))
                    Spacer(Modifier.height(8.dp))
                    OneTimeOffer(12.sp, FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    PriceComparison(content)
                    Spacer(Modifier.height(20.dp))
                    ClaimButton(isLoading, actions.onClaim, Brush.horizontalGradient(PromoColors.ClaimGradient), RoundedCornerShape(8.dp))
                    Spacer(Modifier.height(12.dp))
                    SecureInfo()
                    Spacer(Modifier.height(16.dp))
                    FooterLinks(PromoColors.FooterV2, actions)
                },
            )
            // iOS: 32 pt button, 16 pt from the top and leading edges, hidden while busy.
            if (!isLoading) {
                CloseButton(
                    painter = rememberVectorPainter(VxIcons.Close),
                    tint = PromoColors.OldPrice,
                    iconSize = 16.dp,
                    enabled = true,
                    onClick = actions.onClose,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                )
            }
        }
    }
}

/** "Discount" over "50% OFF", tilted like V1's — the two overlap by 12 dp, as on iOS. */
@Composable
private fun V2Discount(percentage: String) {
    val glowRadius = with(LocalDensity.current) { 4.dp.toPx() }
    Box(Modifier.fillMaxWidth().height(85.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.rotate(-7.13f).padding(horizontal = 12.dp)) {
            BasicText(
                text = vxString("PromoOffer_DiscountTitle"),
                style = Rounded.textStyle(16.sp, FontWeight.SemiBold, Color.White),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 16.sp),
            )
            BasicText(
                text = PromoOfferLogic.discountText(vxString("PromoOffer_DiscountAmountDescription"), percentage),
                modifier = Modifier.overlapPrevious(12.dp),
                style = Rounded.textStyle(56.sp, FontWeight.Bold, Color.White).copy(
                    shadow = Shadow(color = Color.White.copy(alpha = 0.9f), offset = Offset.Zero, blurRadius = glowRadius),
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 56.sp),
            )
        }
    }
}

@Composable
private fun SecureInfo() {
    Row(
        Modifier.padding(horizontal = 24.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(PromoIcons.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
        RichText(
            text = vxString("PromoOffer_SecureInfoLabel"),
            style = Rounded.textStyle(12.sp, FontWeight.Normal, Color.White),
            textAlign = TextAlign.Center,
        )
    }
}

/** Moves the element up into the one above it: UIStackView's negative spacing. */
private fun Modifier.overlapPrevious(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val overlap = amount.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width, placeable.height - overlap) { placeable.place(0, -overlap) }
}

// endregion

// region Shared by V1 and V2

/** "Save {{value_1}}% on the yearly plan". */
@Composable
private fun Description(percentage: String, style: TextStyle) {
    RichText(
        text = PromoOfferLogic.discountText(vxString("PromoOffer_YearlyPlanDescription"), percentage),
        style = style,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

/** The green tick and "One-time offer". */
@Composable
private fun OneTimeOffer(fontSize: TextUnit, fontWeight: FontWeight) {
    Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(PromoIcons.Tick, contentDescription = null, tint = PromoColors.OfferGreen, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(vxString("PromoOffer_OnlyOnceLabel"), style = Rounded.textStyle(fontSize, fontWeight, PromoColors.OfferGreen))
    }
}

/** The compared product's price struck through, ">", the offered price. */
@Composable
private fun PriceComparison(content: PromoOfferContent) {
    val style = Rounded.textStyle(24.sp, FontWeight.SemiBold, Color.White)
    Row(
        Modifier.padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = content.oldPrice,
            style = style.copy(color = PromoColors.OldPrice, textDecoration = TextDecoration.LineThrough),
            maxLines = 1,
        )
        Text(">", style = style)
        Text(content.newPrice, style = style, maxLines = 1)
    }
}

@Composable
private fun ClaimButton(isLoading: Boolean, onClick: () -> Unit, background: Brush, shape: RoundedCornerShape) {
    VxLoadingButton(
        text = vxString("PromoOffer_ClaimOfferButtonLabel"),
        isLoading = isLoading,
        onClick = onClick,
        background = background,
        contentColor = Color.White,
        shape = shape,
        textStyle = Rounded.textStyle(16.sp, FontWeight.SemiBold),
        modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(48.dp),
    )
}

/** Restore | Terms of Use | Privacy Policy. */
@Composable
private fun FooterLinks(color: Color, actions: PromoOfferActions) {
    val style = Rounded.textStyle(12.sp, FontWeight.Medium, color)
    Row(
        Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        FooterLink(vxString("Subscription_RestorePurchase"), style, actions.onRestore)
        Text("|", style = style)
        FooterLink(vxString("Subscription_TermsOfUse"), style, actions.onTerms)
        Text("|", style = style)
        FooterLink(vxString("Subscription_PrivacyPol"), style, actions.onPrivacy)
    }
}

@Composable
private fun FooterLink(text: String, style: TextStyle, onClick: () -> Unit) {
    RichText(
        text = text,
        style = style,
        maxLines = 1,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
    )
}

/** A close glyph centred in a 48 dp touch target. */
@Composable
private fun CloseButton(
    painter: Painter,
    tint: Color,
    iconSize: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 20.dp),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter, contentDescription = stringResource(R.string.vxhub_close), tint = tint, modifier = Modifier.size(iconSize))
    }
}

// endregion
