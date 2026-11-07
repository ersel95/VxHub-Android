package com.vxhub.ui.paywall

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vxhub.ui.VxFont
import com.vxhub.ui.internal.VxIcons
import com.vxhub.ui.internal.vxString

private val IdleBorder = Color(0xFFA7A7A7)
private val V1Accent = Color(0xFF148CBE)
private val V2Accent = Color(0xFFBE0DA7)
private val V2ButtonGradient = Brush.horizontalGradient(listOf(Color(0xFFBE0DA7), Color(0xFFF0303E)))
private val Grey = Color(0xFF808080)
private val IosGreen = Color(0xFF34C759)

// U+1F381 WRAPPED GIFT and U+1F512 LOCK: the iOS assets are the Noto emoji, which Android draws natively.
private const val GIFT_EMOJI = "🎁"
private const val LOCK_EMOJI = "🔒"

/**
 * V1 — iOS's `VxMainSubscriptionRootView`: logo and wordmark in whatever height is left (120 to
 * 250 dp), benefits, the free-trial switch, two visible products, the button, "Cancel anytime" and
 * the legal links, scrolling when the screen is short.
 */
@Composable
internal fun PaywallV1(
    configuration: VxMainPaywallConfiguration,
    state: PaywallUiState,
    showsCloseButton: Boolean,
    actions: PaywallActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(configuration.backgroundColor)) {
        configuration.backgroundImage?.let { image ->
            Image(painterResource(image), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        val screenHeight = if (constraints.hasBoundedHeight) maxHeight else DesignHeight
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val topMargin = adaptiveHeight(42f, screenHeight.value).dp
        val viewport = screenHeight - insets.calculateTopPadding() - insets.calculateBottomPadding() - topMargin
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = topMargin),
        ) {
            FlexibleHeaderLayout(
                viewport = viewport.coerceAtLeast(0.dp),
                header = { V1Header(configuration) },
                body = { V1Body(configuration, state, actions) },
            )
        }
        if (showsCloseButton) {
            PaywallCloseButton(
                start = 32.dp,
                top = 16.dp,
                visualSize = 32.dp,
                iconSize = 14.dp,
                tint = configuration.dismissButtonColor ?: Grey,
                background = Color.Transparent,
                onClick = actions.onClose,
            )
        }
    }
}

private val DesignHeight = 812.dp

/** iOS V1's top section: the height the rest leaves on screen, kept between 120 and 250 dp. */
@Composable
private fun FlexibleHeaderLayout(viewport: Dp, header: @Composable () -> Unit, body: @Composable () -> Unit) {
    Layout(content = {
        Box { header() }
        Box { body() }
    }) { measurables, constraints ->
        val width = constraints.maxWidth
        val bodyPlaceable = measurables[1].measure(Constraints(minWidth = width, maxWidth = width))
        val headerHeight = (viewport.roundToPx() - bodyPlaceable.height).coerceIn(120.dp.roundToPx(), 250.dp.roundToPx())
        val headerPlaceable = measurables[0].measure(Constraints.fixed(width, headerHeight))
        layout(width, headerHeight + bodyPlaceable.height) {
            headerPlaceable.place(0, 0)
            bodyPlaceable.place(0, headerHeight)
        }
    }
}

/** Logo above wordmark, sharing the height in proportion to their sizes (iOS's `fillProportionally`). */
@Composable
private fun V1Header(configuration: VxMainPaywallConfiguration) {
    val images = listOfNotNull(configuration.appLogoImage, configuration.appNameImage)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        images.forEach { image ->
            val painter = painterResource(image)
            val share = painter.intrinsicSize.height.takeIf { it.isFinite() && it > 0f } ?: 1f
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().weight(share),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun V1Body(configuration: VxMainPaywallConfiguration, state: PaywallUiState, actions: PaywallActions) {
    val font = configuration.font
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        configuration.descriptionItems.forEach { feature ->
            PaywallDescriptionItem(
                feature = feature,
                style = configuration.descriptionFont.textStyle(16, FontWeight.Bold, configuration.textColor),
                iconFrame = 24.dp,
                iconSize = 18.dp,
                tint = configuration.mainButtonColor,
            )
        }
        if (state.isTrialSwitchVisible) {
            Spacer(Modifier.height(16.dp))
            TrialSwitch(configuration, checked = state.isTrialSwitchOn, onCheckedChange = actions.onTrialToggle)
        }
        Spacer(Modifier.height(12.dp))
        ClassicProductList(VxMainPaywallType.V1, configuration, state, actions.onSelect)
        Spacer(Modifier.height(8.dp))
        PaywallButton(
            text = vxString("Subscription_SubscribeButtonLabel"),
            textStyle = font.textStyle(16, FontWeight.SemiBold, Color.White),
            background = SolidColor(configuration.mainButtonColor),
            shape = RoundedCornerShape(16.dp),
            minHeight = 48.dp,
            isLoading = state.isLoading,
            onClick = actions.onPurchase,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(PaywallIcons.History, contentDescription = null, modifier = Modifier.size(15.dp), tint = Grey)
            Spacer(Modifier.width(4.dp))
            PaywallText(
                text = vxString("Subscription_CancelableInfoText"),
                style = font.textStyle(12, FontWeight.Medium, Color(0xFF929292)),
                modifier = Modifier.weight(1f, fill = false),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))
        // V1 has no "Redeem Code" link, as on iOS.
        PaywallFooter(
            links = listOf(
                vxString("Subscription_RestorePurchase") to actions.onRestore,
                vxString("Subscription_TermsOfUse") to actions.onTerms,
                vxString("Subscription_PrivacyPol") to actions.onPrivacy,
            ),
            linkStyle = font.textStyle(12, FontWeight.Medium, Grey),
            separator = "|",
            separatorStyle = font.textStyle(12, FontWeight.Medium, Grey),
            spacing = 6.dp,
        )
    }
}

/** "Enable Free Trial" with an iOS-coloured switch that follows the selected product. */
@Composable
private fun TrialSwitch(configuration: VxMainPaywallConfiguration, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val offTrack = if (configuration.isLightMode) Color(0xFFE9E9EA) else Color(0xFF39393D)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 47.dp)
            .border(1.dp, IdleBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaywallText(
            text = vxString("Subscription_FreeTrailEnabledLabel"),
            style = configuration.font.textStyle(14, FontWeight.Medium, configuration.textColor),
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = IosGreen,
                checkedBorderColor = IosGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = offTrack,
                uncheckedBorderColor = offTrack,
            ),
        )
    }
}

/**
 * V2 — iOS's `VxMainSubscriptionV2RootView`: everything sits at the bottom over the background
 * image or the looping video, under a 290 dp wide title and benefit list.
 */
@Composable
internal fun PaywallV2(
    configuration: VxMainPaywallConfiguration,
    state: PaywallUiState,
    showsCloseButton: Boolean,
    actions: PaywallActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(configuration.backgroundColor)) {
        val video = configuration.video
        if (video != null) {
            Column(Modifier.fillMaxSize()) {
                PaywallVideo(video, Modifier.fillMaxWidth().fillMaxHeight(configuration.videoHeightMultiplier.coerceIn(0f, 1f)))
                Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black))
            }
            if (configuration.showGradientVideoBackground) {
                Box(Modifier.fillMaxSize().background(PaywallLandingGradient))
            }
        } else {
            configuration.backgroundImage?.let { image ->
                Image(painterResource(image), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val viewport = if (constraints.hasBoundedHeight) maxHeight - insets.calculateTopPadding() - insets.calculateBottomPadding() else 0.dp
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()),
        ) {
            // Bottom-aligned like iOS's flexible spacer on top, but scrolling on a short screen.
            Column(Modifier.fillMaxWidth().heightIn(min = viewport.coerceAtLeast(0.dp)), verticalArrangement = Arrangement.Bottom) {
                V2Body(configuration, state, actions)
            }
        }
        if (showsCloseButton) {
            PaywallCloseButton(
                start = 24.dp,
                top = 16.dp,
                visualSize = 32.dp,
                iconSize = 14.dp,
                tint = configuration.closeButtonColor,
                background = Color.Transparent,
                onClick = actions.onClose,
            )
        }
    }
}

@Composable
private fun ColumnScope.V2Body(configuration: VxMainPaywallConfiguration, state: PaywallUiState, actions: PaywallActions) {
    val font = configuration.font
    Column(
        Modifier
            .align(Alignment.CenterHorizontally)
            .widthIn(max = 290.dp)
            .fillMaxWidth(),
    ) {
        PaywallText(
            text = vxString("Subscription_V2_UnlockPremiumLabel"),
            style = font.textStyle(25, FontWeight.Bold, configuration.textColor),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        configuration.descriptionItems.forEach { feature ->
            PaywallDescriptionItem(
                feature = feature,
                style = configuration.descriptionFont.textStyle(14, FontWeight.Medium, configuration.textColor),
                iconFrame = 16.dp,
                iconSize = 16.dp,
                tint = configuration.mainButtonColor,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    ClassicProductList(VxMainPaywallType.V2, configuration, state, actions.onSelect, Modifier.padding(horizontal = 24.dp))
    // Keeps its space while hidden, as iOS fades it out instead of removing it.
    val renewalBonus = state.selected?.renewalBonus?.takeIf { it != 0 }
    PaywallText(
        text = PaywallTexts.v2RenewalText(renewalBonus ?: 0),
        style = font.textStyle(8, FontWeight.Medium, Color(0xFFB4B4B4)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .alpha(if (renewalBonus != null) 1f else 0f)
            .then(if (renewalBonus != null) Modifier else Modifier.clearAndSetSemantics {}),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Column(Modifier.padding(horizontal = 24.dp)) {
        PaywallButton(
            text = vxString("Subscription_SubscribeButtonLabel"),
            textStyle = font.textStyle(16, FontWeight.SemiBold, Color.White),
            background = V2ButtonGradient,
            shape = RoundedCornerShape(8.dp),
            minHeight = 48.dp,
            isLoading = state.isLoading,
            onClick = actions.onPurchase,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(LOCK_EMOJI, style = TextStyle(fontSize = 10.sp), modifier = Modifier.width(16.dp))
            PaywallText(
                text = vxString("Subscription_CancelableInfoText"),
                style = font.textStyle(12, FontWeight.Medium, Color.White),
                modifier = Modifier.weight(1f, fill = false),
                textAlign = TextAlign.Center,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    val links = buildList {
        add(vxString("Subscription_RestorePurchase") to actions.onRestore)
        add(vxString("Subscription_TermsOfUse") to actions.onTerms)
        add(vxString("Subscription_PrivacyPol") to actions.onPrivacy)
        if (configuration.isRedeemCodeEnabled) add(vxString("Subscription_ReedemCode") to actions.onRedeemCode)
    }
    PaywallFooter(
        links = links,
        linkStyle = font.textStyle(12, FontWeight.Medium, Color(0xFF535353)),
        separator = "|",
        separatorStyle = font.textStyle(12, FontWeight.Medium, Grey),
        spacing = 3.dp,
    )
}

/** The 148 dp product list of V1 and V2: two rows show, more scroll. */
@Composable
private fun ClassicProductList(
    type: VxMainPaywallType,
    configuration: VxMainPaywallConfiguration,
    state: PaywallUiState,
    onSelect: (PaywallItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val selectedId = state.selected?.id
    Column(
        modifier
            .fillMaxWidth()
            .height(148.dp)
            .verticalScroll(scroll, enabled = scroll.maxValue > 0)
            .selectableGroup(),
    ) {
        state.items.forEach { item ->
            key(item.id) {
                ClassicProductRow(item, selected = item.id == selectedId, type = type, configuration = configuration) { onSelect(item) }
            }
        }
    }
}

/** iOS's `VxMainPaywallTableViewCell` in its V1 and V2 configurations: a 72 dp row. */
@Composable
private fun ClassicProductRow(
    item: PaywallItem,
    selected: Boolean,
    type: VxMainPaywallType,
    configuration: VxMainPaywallConfiguration,
    onClick: () -> Unit,
) {
    val isV1 = type == VxMainPaywallType.V1
    val font = configuration.font
    val textColor = configuration.textColor
    val accent = if (isV1) V1Accent else V2Accent
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        Row(
            Modifier
                .padding(top = 4.dp, bottom = 8.dp)
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clip(shape)
                .border(1.dp, if (selected) accent else IdleBorder, shape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(start = 20.dp, end = 20.dp, top = 9.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClassicDot(selected = selected, accent = accent, filledWhenIdle = isV1, isLightMode = configuration.isLightMode)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                PaywallText(PaywallTexts.classicTitle(item, type), font.textStyle(14, FontWeight.Medium, textColor), maxLines = 2)
                if (isV1) {
                    if (!item.isEligibleForTrial) {
                        PaywallText(PaywallTexts.v1Subtitle(item), font.textStyle(12, FontWeight.Normal, textColor), maxLines = 1)
                    }
                } else {
                    PaywallTexts.v2BonusText(item)?.let { bonus ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(GIFT_EMOJI, style = TextStyle(fontSize = 13.sp))
                            Spacer(Modifier.width(4.dp))
                            PaywallText(bonus, font.textStyle(14, FontWeight.SemiBold, textColor), maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                if (isV1) {
                    PaywallText(PaywallTexts.v1PriceTitle(item), font.textStyle(14, FontWeight.Bold, textColor), maxLines = 1)
                    PaywallText(PaywallTexts.v1PriceSubtitle(item), font.textStyle(12, FontWeight.Normal, textColor), maxLines = 1)
                } else {
                    PaywallText(PaywallTexts.v2PriceTitle(item), font.textStyle(12, FontWeight.Normal, textColor), maxLines = 1)
                    PaywallTexts.v2PriceSubtitle(item)?.let { weekly ->
                        PaywallText(weekly, font.textStyle(10, FontWeight.Normal, textColor), maxLines = 1)
                    }
                }
            }
        }
        if (item.isBestOffer) {
            BestOfferBadge(
                artwork = if (isV1) PaywallIcons.BestOfferBadgeV1 else PaywallIcons.BestOfferBadgeV2,
                font = font,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .offset(y = (-1).dp),
            )
        }
    }
}

/** The selection dot: V1 a filled grey dot, V2 a ring; a check-mark disc once selected. */
@Composable
private fun ClassicDot(selected: Boolean, accent: Color, filledWhenIdle: Boolean, isLightMode: Boolean) {
    val idle = if (isLightMode) Color(0xFFD7D7D7) else Color(0xFF2D2D2D)
    val look = when {
        selected -> Modifier.background(accent, CircleShape)
        filledWhenIdle -> Modifier.background(idle, CircleShape)
        else -> Modifier.border(1.dp, idle, CircleShape)
    }
    Box(Modifier.size(13.dp).then(look), contentAlignment = Alignment.Center) {
        if (selected) Icon(VxIcons.Check, contentDescription = null, modifier = Modifier.size(9.dp), tint = Color.White)
    }
}

@Composable
private fun BestOfferBadge(artwork: ImageVector, font: VxFont, modifier: Modifier = Modifier) {
    Box(modifier.size(width = 115.dp, height = 19.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = rememberVectorPainter(artwork),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds,
        )
        PaywallText(
            text = PaywallTexts.bestOfferBadge(),
            style = font.textStyle(10, FontWeight.SemiBold, Color.White),
            modifier = Modifier.padding(horizontal = 4.dp),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
