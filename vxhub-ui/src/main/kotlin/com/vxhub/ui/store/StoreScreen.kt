package com.vxhub.ui.store

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vxhub.ui.R
import com.vxhub.ui.VxFont
import com.vxhub.ui.internal.VxIcons

/**
 * The store's layout — iOS's `VxStoreV1RootView` and `VxStoreV2RootView`, which share everything
 * but the product list. Stateless: [VxStore] feeds it.
 */
@Composable
internal fun StoreScreen(
    configuration: VxStoreConfiguration,
    rows: StoreRows,
    state: StoreUiState,
    balance: Int,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onBuy: (String?) -> Unit,
    onDismissAlert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val secondaryText = StoreColors.secondaryText(configuration.isLightMode)
    Box(modifier.fillMaxSize().background(configuration.backgroundColor)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(48.dp))
            if (rows.isDummyMode) {
                DummyModeBanner(configuration.isLightMode, Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(12.dp))
            }
            configuration.heroImage?.let { hero ->
                Image(
                    painter = painterResource(hero),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(max = 120.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
            if (configuration.showBalance) {
                BalanceRow(configuration, balance, secondaryText)
                Spacer(Modifier.height(12.dp))
            }
            configuration.headlineText?.let { headline ->
                RichText(
                    text = headline,
                    style = configuration.font.textStyle(26.sp, FontWeight.Bold, configuration.textColor),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
            configuration.subtitleText?.let { subtitle ->
                RichText(
                    text = subtitle,
                    style = configuration.font.textStyle(16.sp, FontWeight.Normal, secondaryText),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
            if (configuration is VxStoreV2Configuration && configuration.socialProofText != null) {
                SocialProofRow(configuration, configuration.socialProofText, secondaryText)
                Spacer(Modifier.height(16.dp))
            }
            val cards = StoreCards(configuration, state, onSelect, onBuy)
            when (configuration) {
                is VxStoreV1Configuration -> StoreGrid(configuration, rows.rows, cards)
                is VxStoreV2Configuration -> StoreList(configuration, rows.rows, cards)
            }
            if (configuration.purchaseMode == VxStorePurchaseMode.SELECT_AND_BUY) {
                Spacer(Modifier.height(24.dp))
                VxLoadingButton(
                    text = configuration.ctaText ?: "Purchase",
                    isLoading = state.isLoading,
                    onClick = { onBuy(null) },
                    background = brush(configuration.ctaButtonColor, configuration.ctaGradientEndColor),
                    contentColor = configuration.ctaButtonTextColor,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = configuration.font.textStyle(17.sp, FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(54.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        CloseButton(
            visible = configuration.isCloseButtonEnabled && state.isCloseDelayOver && !state.isLoading,
            isLightMode = configuration.isLightMode,
            tint = configuration.closeButtonColor,
            onClick = onClose,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        )
        state.alert?.let { VxAlert(it, onDismissAlert) }
    }
}

/** What every card needs from the screen. */
private class StoreCards(
    val configuration: VxStoreConfiguration,
    val state: StoreUiState,
    val onSelect: (String) -> Unit,
    val onBuy: (String?) -> Unit,
) {
    val isSelectable: Boolean get() = configuration.purchaseMode == VxStorePurchaseMode.SELECT_AND_BUY

    fun isSelected(row: StoreRow): Boolean = isSelectable && state.selectedId == row.identifier

    /** Card container: background, border (selected or not), owned dimming, selection. */
    fun Modifier.card(row: StoreRow): Modifier {
        val shape = RoundedCornerShape(configuration.cardCornerRadius)
        val border = if (isSelected(row)) {
            BorderStroke(configuration.selectedCardBorderWidth, configuration.selectedCardBorderColor)
        } else {
            BorderStroke(configuration.cardBorderWidth, configuration.cardBorderColor)
        }
        val base = this
            .alpha(if (row.isAlreadyPurchased) 0.7f else 1f)
            .clip(shape)
            .background(configuration.cardBackgroundColor)
            .border(border, shape)
        return if (isSelectable) {
            base.selectable(
                selected = isSelected(row),
                enabled = !state.isLoading,
                role = Role.RadioButton,
                onClick = { onSelect(row.identifier) },
            )
        } else {
            base
        }
    }
}

// region V1: grid

@Composable
private fun StoreGrid(configuration: VxStoreV1Configuration, rows: List<StoreRow>, cards: StoreCards) {
    val columns = configuration.columnsCount.coerceAtLeast(1)
    // iOS: 16 pt container padding plus the section's 8 pt inset, then 4 pt around each card.
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        rows.chunked(columns).forEach { line ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                line.forEach { row ->
                    StoreGridCard(row, configuration, cards, Modifier.weight(1f).fillMaxHeight().padding(4.dp))
                }
                repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StoreGridCard(row: StoreRow, configuration: VxStoreV1Configuration, cards: StoreCards, modifier: Modifier) {
    val font = configuration.font
    Box(with(cards) { modifier.card(row) }) {
        Column(
            Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 22.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProductImage(row.image, configuration.buyButtonColor, configuration.productImageSize)
            Spacer(Modifier.height(6.dp))
            RichText(
                text = row.title,
                style = font.textStyle(15.sp, FontWeight.Bold, configuration.textColor),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            bonusText(configuration.showBonusLabel, row.initialBonus)?.let { bonus ->
                Spacer(Modifier.height(6.dp))
                Text(bonus, style = font.textStyle(12.sp, FontWeight.SemiBold, configuration.bonusLabelColor), maxLines = 1)
            }
            Spacer(Modifier.weight(1f).defaultMinSize(minHeight = 6.dp))
            val buttonModifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(34.dp)
            if (cards.isSelectable) {
                PriceLabel(row, configuration, buttonModifier.alpha(if (row.isAlreadyPurchased) 0.5f else 1f))
            } else {
                BuyButton(row, configuration, cards, buttonModifier.alpha(if (row.isAlreadyPurchased) 0.5f else 1f))
            }
        }
        row.badge?.let { BadgeRibbon(it, font, Modifier.align(Alignment.TopCenter)) }
        if (row.isAlreadyPurchased) OwnedBadge(configuration, Modifier.align(Alignment.Center))
    }
}

// endregion

// region V2: list

@Composable
private fun StoreList(configuration: VxStoreV2Configuration, rows: List<StoreRow>, cards: StoreCards) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        rows.forEach { row -> StoreListCard(row, configuration, cards, Modifier.fillMaxWidth().padding(vertical = 6.dp)) }
    }
}

@Composable
private fun StoreListCard(row: StoreRow, configuration: VxStoreV2Configuration, cards: StoreCards, modifier: Modifier) {
    val font = configuration.font
    Box(with(cards) { modifier.card(row) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProductImage(row.image, configuration.buyButtonColor, configuration.productImageSize)
                Spacer(Modifier.width(8.dp))
                RichText(
                    text = row.title,
                    style = font.textStyle(16.sp, FontWeight.SemiBold, configuration.textColor),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                val buttonModifier = Modifier.padding(start = 8.dp).defaultMinSize(minWidth = 70.dp).height(32.dp)
                when {
                    // The owned badge takes the button's place, in both modes.
                    row.isAlreadyPurchased -> Spacer(Modifier.height(32.dp))
                    cards.isSelectable -> PriceLabel(row, configuration, buttonModifier)
                    else -> BuyButton(row, configuration, cards, buttonModifier)
                }
            }
            val description = row.description.takeIf { configuration.showProductDescription && it.isNotEmpty() }
            val bonus = bonusText(configuration.showBonusLabel, row.initialBonus)
            val features = configuration.productFeatures[row.identifier].orEmpty()
            if (description != null || bonus != null || features.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    description?.let {
                        RichText(
                            text = it,
                            style = font.textStyle(13.sp, FontWeight.Normal, StoreColors.descriptionText(configuration.isLightMode)),
                            maxLines = 2,
                        )
                    }
                    bonus?.let { Text(it, style = font.textStyle(12.sp, FontWeight.SemiBold, configuration.bonusLabelColor), maxLines = 1) }
                    if (features.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            features.forEach { feature -> FeatureLine(feature, configuration) }
                        }
                    }
                }
            }
        }
        row.badge?.let { BadgeRibbon(it, font, Modifier.align(Alignment.TopEnd).padding(end = 12.dp)) }
        if (row.isAlreadyPurchased) OwnedBadge(configuration, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp))
    }
}

@Composable
private fun FeatureLine(feature: VxStoreProductFeature, configuration: VxStoreV2Configuration) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StoreImage(feature.icon, configuration.buyButtonColor, Modifier.size(16.dp))
        RichText(
            text = feature.text,
            style = configuration.font.textStyle(12.sp, FontWeight.Medium, configuration.textColor),
            maxLines = 1,
        )
    }
}

@Composable
private fun SocialProofRow(configuration: VxStoreV2Configuration, text: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        configuration.socialProofIcon?.let { StoreImage(it, StoreColors.systemYellow(configuration.isLightMode), Modifier.size(18.dp)) }
        RichText(text = text, style = configuration.font.textStyle(14.sp, FontWeight.Medium, color), maxLines = 1)
    }
}

// endregion

// region Shared pieces

@Composable
private fun BalanceRow(configuration: VxStoreConfiguration, balance: Int, labelColor: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        configuration.balanceIcon?.let { StoreImage(it, StoreColors.systemYellow(configuration.isLightMode), Modifier.size(24.dp)) }
        configuration.balanceLabel?.let {
            RichText(text = it, style = configuration.font.textStyle(15.sp, FontWeight.Medium, labelColor), maxLines = 1)
        }
        Text(
            text = balance.toString(),
            style = configuration.font.textStyle(15.sp, FontWeight.Bold, configuration.textColor),
            maxLines = 1,
        )
    }
}

/** The product's image; keeps its square even without one, as the iOS image view does. */
@Composable
private fun ProductImage(image: VxStoreImage?, accent: Color, size: Dp) {
    if (image == null) Spacer(Modifier.size(size)) else StoreImage(image, accent, Modifier.size(size))
}

/** A [VxStoreImage]: drawables as they are, vectors tinted with [accent]. */
@Composable
internal fun StoreImage(image: VxStoreImage, accent: Color, modifier: Modifier = Modifier) {
    when (image) {
        is VxStoreImage.Resource -> Image(
            painter = painterResource(image.resId),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
        is VxStoreImage.Vector -> Icon(image.imageVector, contentDescription = null, tint = accent, modifier = modifier)
    }
}

/** The price on its own button ([VxStorePurchaseMode.PER_CARD]); a spinner while this card is being bought. */
@Composable
private fun BuyButton(row: StoreRow, configuration: VxStoreConfiguration, cards: StoreCards, modifier: Modifier) {
    val shape = RoundedCornerShape(configuration.buyButtonCornerRadius)
    Box(
        modifier
            .clip(shape)
            .background(brush(configuration.buyButtonColor, configuration.buyButtonGradientEndColor))
            .clickable(
                enabled = !cards.state.isLoading && !row.isAlreadyPurchased,
                role = Role.Button,
                onClick = { cards.onBuy(row.identifier) },
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (cards.state.purchasingId == row.identifier) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = configuration.buyButtonTextColor,
                strokeWidth = 2.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round,
            )
        } else {
            Text(
                text = row.localizedPrice,
                style = configuration.font.textStyle(14.sp, FontWeight.Bold, configuration.buyButtonTextColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The price as plain text ([VxStorePurchaseMode.SELECT_AND_BUY]). */
@Composable
private fun PriceLabel(row: StoreRow, configuration: VxStoreConfiguration, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = row.localizedPrice,
            style = configuration.font.textStyle(14.sp, FontWeight.SemiBold, configuration.textColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BadgeRibbon(badge: VxStoreProductBadge, font: VxFont, modifier: Modifier) {
    Box(
        modifier
            .height(18.dp)
            .background(badge.badgeColor, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(badge.badgeText, style = font.textStyle(9.sp, FontWeight.Bold, badge.badgeTextColor), maxLines = 1)
    }
}

@Composable
private fun OwnedBadge(configuration: VxStoreConfiguration, modifier: Modifier) {
    Text(
        text = configuration.purchasedBadgeText ?: "OWNED",
        style = configuration.font.textStyle(11.sp, FontWeight.Bold, Color.White),
        maxLines = 1,
        modifier = modifier
            .background(configuration.purchasedBadgeColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** iOS's "DEBUG MODE" strip, shown over dummy products (hard-coded English there too). */
@Composable
private fun DummyModeBanner(isLightMode: Boolean, modifier: Modifier) {
    val orange = StoreColors.systemOrange(isLightMode)
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(orange.copy(alpha = 0.15f), shape)
            .border(1.dp, orange.copy(alpha = 0.4f), shape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(VxIcons.Warning, contentDescription = null, tint = orange, modifier = Modifier.size(16.dp))
        Text(
            text = "DEBUG MODE — Showing dummy products",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = orange,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 28 dp circle at the top leading corner; fades in when it becomes available, as on iOS. */
@Composable
private fun CloseButton(visible: Boolean, isLightMode: Boolean, tint: Color, onClick: () -> Unit, modifier: Modifier) {
    // iOS draws it 12 pt from the top and 20 pt from the leading edge; the 48 dp touch target is centred on it.
    Box(modifier.padding(start = 10.dp, top = 2.dp)) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(300)), exit = ExitTransition.None) {
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 20.dp),
                        role = Role.Button,
                        onClick = onClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(28.dp).background(StoreColors.closeBackground(isLightMode), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        VxIcons.Close,
                        contentDescription = stringResource(R.string.vxhub_close),
                        tint = tint,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

internal fun brush(color: Color, gradientEnd: Color?): Brush =
    if (gradientEnd == null) SolidColor(color) else Brush.horizontalGradient(listOf(color, gradientEnd))

// endregion
