package com.vxhub.ui.support

import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import com.vxhub.ui.VxFont

/**
 * Appearance of the support screens ([VxSupport], [showSupport]) — iOS's `VxSupportConfiguration`,
 * option for option, with the same defaults.
 *
 * iOS's defaults are dynamic colours with a light and a dark value. Here every colour defaults to
 * [Color.Unspecified], which stands for that same pair: the light value in light mode, the dark
 * value in dark mode (noted on each option as light / dark). A colour you pass is used in both; to
 * vary it, build the configuration in composition and choose with `isSystemInDarkTheme()`.
 *
 * Images are drawable resources, so a configuration can be built outside composition for
 * [showSupport]; `null` keeps VxHub's own artwork.
 */
@Immutable
public class VxSupportConfiguration(
    /** Typeface of the screens' texts. */
    public val font: VxFont = VxFont.Rounded,
    /** Screen background. White / black. */
    public val backgroundColor: Color = Color.Unspecified,
    /** Title, back and new-chat icons, loading indicators. Black / white. */
    public val navigationTintColor: Color = Color.Unspecified,
    /** The "New Chat" button of the empty list. Black / white. */
    public val listingActionColor: Color = Color.Unspecified,
    /** Text of the "New Chat" button. White / black. */
    public val listingActionTextColor: Color = Color.Unspecified,
    /** A ticket's category in the list. Black / white. */
    public val listingItemTitleColor: Color = Color.Unspecified,
    /** A ticket's last message in the list, and the empty-list text. #636973 / #808080. */
    public val listingDescriptionColor: Color = Color.Unspecified,
    /** A ticket's time in the list. #9E9E9E / #808080. */
    public val listingDateColor: Color = Color.Unspecified,
    /** The dot on a ticket with an answer the user has not seen. Black / white. */
    public val listingUnreadColor: Color = Color.Unspecified,
    /** Border of a support agent's message. #E9EBF0 / #3C3C43. */
    public val detailAdminTicketBorderColor: Color = Color.Unspecified,
    /** Background of a support agent's message. White / black. */
    public val detailAdminTicketBackgroundColor: Color = Color.Unspecified,
    /** Text of a support agent's message. Black / white. */
    public val detailAdminTicketMessageColor: Color = Color.Unspecified,
    /** Time of a support agent's message. #9E9E9E / #808080. */
    public val detailAdminTicketDateColor: Color = Color.Unspecified,
    /** Background of the user's message, and of the default send button. #478AFF / white. */
    public val detailUserTicketBackgroundColor: Color = Color.Unspecified,
    /** Text of the user's message, and the default send button's arrow. White / black. */
    public val detailUserTicketMessageColor: Color = Color.Unspecified,
    /** Time of the user's message. #FFD8F0 / #808080. */
    public val detailUserTicketDateColor: Color = Color.Unspecified,
    /** Replaces the send button (a 32 dp circle); `null` draws VxHub's. */
    @param:DrawableRes public val detailSendButtonActiveImage: Int? = null,
    /** Placeholder of the message field. #636973 / #333333. */
    public val detailPlaceholderColor: Color = Color.Unspecified,
    /** Artwork above "How can we help you?" on a new ticket; `null` draws VxHub's. */
    @param:DrawableRes public val detailHelpImage: Int? = null,
    /** "How can we help you?" on a new ticket. #636973 / #808080. */
    public val detailHelpColor: Color = Color.Unspecified,
    /** Background of the category sheet. #E9EBF0 / #111111. */
    public val ticketSheetBackgroundColor: Color = Color.Unspecified,
    /** Categories in the sheet. Black / white. */
    public val ticketSheetTextColor: Color = Color.Unspecified,
    /**
     * Declared for parity with iOS, which does not draw it either: the sheet's row separators are
     * a fixed translucent grey on both platforms. #E9EBF0 / #1C1C1C.
     */
    public val ticketSheetLineColor: Color = Color.Unspecified,
    /** Shadow above the category sheet. #636973 / #838383. */
    public val ticketSheetShadowColor: Color = Color.Unspecified,
    /** Background of the message field. #E9EBF0 / #0E0E0E. */
    public val messageTextFieldBackgroundColor: Color = Color.Unspecified,
    /** Text and cursor of the message field. Black / white. */
    public val messageTextFieldTextColor: Color = Color.Unspecified,
    /** Line under the title bar. #E9EBF0 / #1C1C1C. */
    public val headerLineViewColor: Color = Color.Unspecified,
    /** Line above the message bar. #E9EBF0 / #131313. */
    public val bottomLineViewColor: Color = Color.Unspecified,
    /** Background of the message bar. White / black. */
    public val messageBarBackgroundColor: Color = Color.Unspecified,
)

/** [VxSupportConfiguration]'s colours with every unspecified one replaced by iOS's default. */
@Immutable
internal data class SupportColors(
    val background: Color,
    val navigationTint: Color,
    val listingAction: Color,
    val listingActionText: Color,
    val listingItemTitle: Color,
    val listingDescription: Color,
    val listingDate: Color,
    val listingUnread: Color,
    val adminBorder: Color,
    val adminBackground: Color,
    val adminMessage: Color,
    val adminDate: Color,
    val userBackground: Color,
    val userMessage: Color,
    val userDate: Color,
    val placeholder: Color,
    val help: Color,
    val sheetBackground: Color,
    val sheetText: Color,
    val sheetLine: Color,
    val sheetShadow: Color,
    val messageFieldBackground: Color,
    val messageFieldText: Color,
    val headerLine: Color,
    val bottomLine: Color,
    val messageBar: Color,
)

/** The colours to draw with in light ([dark] false) or dark mode. */
internal fun VxSupportConfiguration.colors(dark: Boolean): SupportColors {
    fun Color.or(light: Color, darkDefault: Color): Color = takeOrElse { if (dark) darkDefault else light }
    return SupportColors(
        background = backgroundColor.or(Color.White, Color.Black),
        navigationTint = navigationTintColor.or(Color.Black, Color.White),
        listingAction = listingActionColor.or(Color.Black, Color.White),
        listingActionText = listingActionTextColor.or(Color.White, Color.Black),
        listingItemTitle = listingItemTitleColor.or(Color.Black, Color.White),
        listingDescription = listingDescriptionColor.or(Color(0xFF636973), Color(0xFF808080)),
        listingDate = listingDateColor.or(Color(0xFF9E9E9E), Color(0xFF808080)),
        listingUnread = listingUnreadColor.or(Color.Black, Color.White),
        adminBorder = detailAdminTicketBorderColor.or(Color(0xFFE9EBF0), Color(0xFF3C3C43)),
        adminBackground = detailAdminTicketBackgroundColor.or(Color.White, Color.Black),
        adminMessage = detailAdminTicketMessageColor.or(Color.Black, Color.White),
        adminDate = detailAdminTicketDateColor.or(Color(0xFF9E9E9E), Color(0xFF808080)),
        userBackground = detailUserTicketBackgroundColor.or(Color(0xFF478AFF), Color.White),
        userMessage = detailUserTicketMessageColor.or(Color.White, Color.Black),
        userDate = detailUserTicketDateColor.or(Color(0xFFFFD8F0), Color(0xFF808080)),
        placeholder = detailPlaceholderColor.or(Color(0xFF636973), Color(0xFF333333)),
        help = detailHelpColor.or(Color(0xFF636973), Color(0xFF808080)),
        sheetBackground = ticketSheetBackgroundColor.or(Color(0xFFE9EBF0), Color(0xFF111111)),
        sheetText = ticketSheetTextColor.or(Color.Black, Color.White),
        sheetLine = ticketSheetLineColor.or(Color(0xFFE9EBF0), Color(0xFF1C1C1C)),
        sheetShadow = ticketSheetShadowColor.or(Color(0xFF636973), Color(0xFF838383)),
        messageFieldBackground = messageTextFieldBackgroundColor.or(Color(0xFFE9EBF0), Color(0xFF0E0E0E)),
        messageFieldText = messageTextFieldTextColor.or(Color.Black, Color.White),
        headerLine = headerLineViewColor.or(Color(0xFFE9EBF0), Color(0xFF1C1C1C)),
        bottomLine = bottomLineViewColor.or(Color(0xFFE9EBF0), Color(0xFF131313)),
        messageBar = messageBarBackgroundColor.or(Color.White, Color.Black),
    )
}

/** The colours for the current light or dark mode. */
@Composable
internal fun VxSupportConfiguration.rememberColors(): SupportColors {
    val dark = isSystemInDarkTheme()
    return remember(this, dark) { colors(dark) }
}
