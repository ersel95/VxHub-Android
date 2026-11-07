package com.vxhub.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vxhub.ui.fontFamily
import com.vxhub.ui.internal.VxIcons

private enum class AuthField { NAME, EMAIL, PASSWORD, CODE }

/**
 * The form behind every step — iOS's `VxAuthRootView`: one view whose fields and copy change per
 * step, so the keyboard stays up between steps. Spacing and sizes follow iOS (24 dp margins,
 * 52 dp fields and buttons, 12 dp between rows).
 */
@Composable
internal fun AuthForm(
    viewModel: AuthViewModel,
    state: AuthUiState,
    layout: AuthLayout,
    configuration: VxAuthConfiguration,
    onGoogle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = configuration.rememberColors()
    val font = configuration.font.fontFamily
    val focusManager = LocalFocusManager.current
    val step = state.step
    val busy = state.isBusy

    // Moving to another step puts the keyboard on its first field, as iOS's single form keeps it
    // up; the screen's first appearance leaves the keyboard down.
    val firstFieldFocus = remember { FocusRequester() }
    val firstField = when {
        layout.showsName -> AuthField.NAME
        layout.showsEmail -> AuthField.EMAIL
        layout.showsPassword -> AuthField.PASSWORD
        layout.showsCode -> AuthField.CODE
        else -> null
    }
    var focusedStep by rememberSaveable { mutableStateOf(step) }
    LaunchedEffect(step) {
        if (step != focusedStep) {
            focusedStep = step
            if (firstField != null) {
                try {
                    firstFieldFocus.requestFocus()
                } catch (e: IllegalStateException) {
                    // The field was not laid out yet; the person can tap it.
                }
            }
        }
    }
    fun Modifier.focusIfFirst(field: AuthField): Modifier = if (field == firstField) focusRequester(firstFieldFocus) else this

    // The host's typography (line heights, letter spacing) must not leak into iOS's metrics.
    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = font)) {
        Box(
            modifier
                .fillMaxSize()
                .background(colors.background)
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 44.dp, bottom = 32.dp),
            ) {
                val logo = configuration.logo
                if (logo != null) {
                    Image(
                        painter = painterResource(logo),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.height(GAP))
                }
                Text(
                    text = AuthStepCopy.title(step).text(),
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                    color = colors.title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                val subtitle = AuthStepCopy.subtitle(step)
                if (subtitle != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = subtitle.text().iosFormat(state.pendingEmail),
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.subtitle,
                        fontSize = 15.sp,
                    )
                }
                Spacer(Modifier.height(24.dp))

                if (layout.showsName) {
                    AuthTextField(
                        state = viewModel.name,
                        placeholder = AuthCopy.NAME_PLACEHOLDER.text(),
                        autofill = ContentType.PersonFullName,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        onKeyboardAction = null,
                        colors = colors,
                        font = font,
                        modifier = Modifier.focusIfFirst(AuthField.NAME),
                    )
                    Spacer(Modifier.height(GAP))
                }
                if (layout.showsEmail) {
                    // On the forgot step the address is the whole form, so return sends the code.
                    val submits = step == VxAuthStep.FORGOT_PASSWORD
                    AuthTextField(
                        state = viewModel.email,
                        placeholder = AuthCopy.EMAIL_PLACEHOLDER.text(),
                        autofill = if (step == VxAuthStep.SIGN_UP) {
                            ContentType.NewUsername + ContentType.EmailAddress
                        } else {
                            ContentType.Username + ContentType.EmailAddress
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Email,
                            imeAction = if (submits) ImeAction.Done else ImeAction.Next,
                        ),
                        onKeyboardAction = if (submits) KeyboardActionHandler { viewModel.submit() } else null,
                        colors = colors,
                        font = font,
                        modifier = Modifier.focusIfFirst(AuthField.EMAIL),
                    )
                    Spacer(Modifier.height(GAP))
                }
                if (layout.showsPassword) {
                    AuthPasswordField(
                        state = viewModel.password,
                        placeholder = AuthStepCopy.passwordPlaceholder(step).text(),
                        // A password being chosen is offered for saving rather than filled in.
                        isNew = step == VxAuthStep.SIGN_UP || step == VxAuthStep.NEW_PASSWORD,
                        onSubmit = viewModel::submit,
                        colors = colors,
                        font = font,
                        modifier = Modifier.focusIfFirst(AuthField.PASSWORD),
                    )
                    Spacer(Modifier.height(GAP))
                }
                if (layout.showsCode) {
                    AuthTextField(
                        state = viewModel.code,
                        placeholder = "000000",
                        autofill = ContentType.SmsOtpCode,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        onKeyboardAction = KeyboardActionHandler { viewModel.submit() },
                        colors = colors,
                        font = font,
                        modifier = Modifier.focusIfFirst(AuthField.CODE),
                    )
                    Spacer(Modifier.height(GAP))
                }

                // Always present, so screen readers announce each new message as iOS posts it.
                val message = state.message
                Box(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                    if (message != null) {
                        Text(
                            text = message.text(),
                            color = if (message.isError) colors.error else colors.subtitle,
                            fontSize = 13.sp,
                        )
                    }
                }
                if (message != null) Spacer(Modifier.height(GAP))

                if (layout.showsPrimary) {
                    PrimaryButton(AuthStepCopy.primaryButton(step).text(), busy, colors, onClick = viewModel::submit)
                    Spacer(Modifier.height(20.dp))
                }
                if (layout.showsForgot) {
                    LinkButton(AuthCopy.FORGOT_PROMPT.text(), colors.link, enabled = !busy, onClick = viewModel::forgotPassword)
                    Spacer(Modifier.height(GAP))
                }
                if (layout.showsResend) {
                    LinkButton(AuthCopy.RESEND_CODE_BUTTON.text(), colors.link, enabled = !busy, onClick = viewModel::resendCode)
                    Spacer(Modifier.height(GAP))
                }
                if (layout.showsSeparator) {
                    OrSeparator(colors)
                    Spacer(Modifier.height(20.dp))
                }
                if (layout.showsGoogle) {
                    GoogleButton(colors, enabled = !busy, onClick = onGoogle)
                    Spacer(Modifier.height(GAP))
                }
                if (layout.showsSecondary) {
                    LinkButton(AuthStepCopy.secondaryButton(step).text(), colors.secondaryButtonText, enabled = !busy, onClick = viewModel::secondary)
                    Spacer(Modifier.height(GAP))
                }
                if (layout.showsGuest) {
                    LinkButton(AuthCopy.GUEST_BUTTON.text(), colors.subtitle, enabled = true, onClick = viewModel::continueAsGuest)
                    Spacer(Modifier.height(GAP))
                }
                if (layout.showsLegal) {
                    Text(
                        text = AuthCopy.LEGAL_NOTICE.text(),
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.subtitle,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            // On the code steps this is the only way out of the flow besides back.
            IconButton(
                onClick = viewModel::close,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                    .padding(top = 8.dp, end = 16.dp)
                    .size(44.dp),
            ) {
                Icon(VxIcons.Close, contentDescription = AuthCopy.CLOSE.text(), tint = colors.closeTint)
            }
        }
    }
}

@Composable
private fun AuthTextField(
    state: TextFieldState,
    placeholder: String,
    autofill: ContentType,
    keyboardOptions: KeyboardOptions,
    onKeyboardAction: KeyboardActionHandler?,
    colors: AuthColors,
    font: FontFamily,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    BasicTextField(
        state = state,
        modifier = modifier.fillMaxWidth().semantics { contentType = autofill },
        textStyle = TextStyle(color = colors.fieldText, fontFamily = font, fontSize = 16.sp),
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = TextFieldLineLimits.SingleLine,
        interactionSource = interactions,
        cursorBrush = SolidColor(colors.fieldText),
        decorator = { innerField ->
            FieldFrame(focused, colors) {
                Placeholder(state, placeholder, colors, font)
                innerField()
            }
        },
    )
}

/** The password field, with a visibility toggle (iOS has none) and the platform's password handling. */
@Composable
private fun AuthPasswordField(
    state: TextFieldState,
    placeholder: String,
    isNew: Boolean,
    onSubmit: () -> Unit,
    colors: AuthColors,
    font: FontFamily,
    modifier: Modifier = Modifier,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    BasicSecureTextField(
        state = state,
        modifier = modifier.fillMaxWidth().semantics { contentType = if (isNew) ContentType.NewPassword else ContentType.Password },
        textStyle = TextStyle(color = colors.fieldText, fontFamily = font, fontSize = 16.sp),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        onKeyboardAction = { onSubmit() },
        interactionSource = interactions,
        cursorBrush = SolidColor(colors.fieldText),
        decorator = { innerField ->
            FieldFrame(
                focused = focused,
                colors = colors,
                trailing = {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            imageVector = if (revealed) VxIcons.VisibilityOff else VxIcons.Visibility,
                            contentDescription = (if (revealed) AuthCopy.HIDE_PASSWORD else AuthCopy.SHOW_PASSWORD).text(),
                            tint = colors.fieldPlaceholder,
                        )
                    }
                },
            ) {
                Placeholder(state, placeholder, colors, font)
                innerField()
            }
        },
        textObfuscationMode = if (revealed) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
    )
}

/** iOS's field: 52 dp, rounded 12 dp, 1 dp border that darkens while focused. */
@Composable
private fun FieldFrame(
    focused: Boolean,
    colors: AuthColors,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(colors.fieldBackground, shape)
            .border(1.dp, if (focused) colors.fieldFocusedBorder else colors.fieldBorder, shape)
            .padding(start = 14.dp, end = if (trailing == null) 14.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { content() }
        if (trailing != null) trailing()
    }
}

@Composable
private fun Placeholder(state: TextFieldState, text: String, colors: AuthColors, font: FontFamily) {
    if (state.text.isEmpty()) {
        Text(
            text = text,
            color = colors.fieldPlaceholder,
            fontFamily = font,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 52 dp, rounded 12 dp; a spinner replaces the title while busy. */
@Composable
private fun PrimaryButton(text: String, busy: Boolean, colors: AuthColors, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.primaryButton)
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Kept while busy, only invisible, so the button keeps its size and its label.
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            color = if (busy) Color.Transparent else colors.primaryButtonText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = colors.primaryButtonText, strokeWidth = 2.dp)
    }
}

/** iOS's text buttons: centred, 14 sp medium, dimmed while disabled. */
@Composable
private fun LinkButton(text: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) color else color.copy(alpha = color.alpha * DISABLED_ALPHA),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OrSeparator(colors: AuthColors) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.separator))
        Text(AuthCopy.SEPARATOR.text(), color = colors.subtitle, fontSize = 13.sp)
        Box(Modifier.weight(1f).height(1.dp).background(colors.separator))
    }
}

/** "Continue with Google", in iOS's provider style: outlined, 52 dp, rounded 12 dp. */
@Composable
private fun GoogleButton(colors: AuthColors, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(colors.providerBackground)
            .border(1.dp, colors.providerBorder, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GoogleMark(colors.providerText)
        Spacer(Modifier.width(8.dp))
        Text(
            text = AuthCopy.GOOGLE_BUTTON.text(),
            color = colors.providerText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** iOS draws the SF Symbol `g.circle`: a G in a ring. Decorative; the button's text names it. */
@Composable
private fun GoogleMark(color: Color) {
    // Fixed to the ring's size, so a large font scale cannot push the letter out of it.
    val letterSize = with(LocalDensity.current) { 12.dp.toSp() }
    Box(
        Modifier
            .size(20.dp)
            .border(1.5.dp, color, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text("G", color = color, fontSize = letterSize, lineHeight = letterSize, fontWeight = FontWeight.Bold)
    }
}

private val GAP = 12.dp
private const val DISABLED_ALPHA = 0.4f
