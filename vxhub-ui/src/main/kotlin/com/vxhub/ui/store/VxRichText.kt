// Shared by the promo offer and the store; belongs in com.vxhub.ui.internal (see ScreenSupport.kt).
package com.vxhub.ui.store

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.vxhub.VxHub

/**
 * VxLabel's markup as an [AnnotatedString]: `[b]…[/b]`, `[color=#RRGGBB]…[/color]`,
 * `[color=rgb(r, g, b)]…[/color]` and `[url=…]…[/url]`. Panel translations and the store's texts
 * may use it on iOS. Anything else — an unknown tag, a closing tag with nothing open — stays
 * literal text; a tag left open runs to the end, as in the HTML iOS renders it through.
 */
internal fun vxRichText(text: String, onLinkClick: ((String) -> Unit)? = null): AnnotatedString {
    if ('[' !in text) return AnnotatedString(text)
    val builder = AnnotatedString.Builder(text.length)
    val open = ArrayList<OpenTag>()
    var cursor = 0
    for (match in TAG_PATTERN.findAll(text)) {
        builder.append(text.substring(cursor, match.range.first))
        cursor = match.range.last + 1
        val isClosing = match.groupValues[1] == "/"
        val name = match.groupValues[2].lowercase()
        val argument = match.groups[3]?.value
        if (isClosing) {
            val index = open.indexOfLast { it.name == name }
            if (argument != null || index < 0) {
                builder.append(match.value)
            } else {
                open.removeAt(index).close(builder, builder.length)
            }
        } else {
            val tag = openTag(name, argument, builder.length, onLinkClick)
            if (tag == null) builder.append(match.value) else open += tag
        }
    }
    builder.append(text.substring(cursor))
    open.forEach { it.close(builder, builder.length) }
    return builder.toAnnotatedString()
}

/** A [Text] showing [vxRichText]; links open in VxHub's web screen, as VxLabel's do. */
@Composable
internal fun RichText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val context = LocalContext.current
    val annotated = remember(text, context) { vxRichText(text) { url -> VxHub.presentWebUrl(context, url) } }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

private class OpenTag(
    val name: String,
    val start: Int,
    private val style: AnnotatedString.Builder.(start: Int, end: Int) -> Unit,
) {
    fun close(builder: AnnotatedString.Builder, end: Int) {
        if (end > start) builder.style(start, end)
    }
}

private fun openTag(name: String, argument: String?, start: Int, onLinkClick: ((String) -> Unit)?): OpenTag? = when (name) {
    "b" -> if (argument == null) OpenTag(name, start) { from, to -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), from, to) } else null
    "color" -> parseColor(argument)?.let { color -> OpenTag(name, start) { from, to -> addStyle(SpanStyle(color = color), from, to) } }
    "url" -> argument?.trim()?.takeIf { it.isNotEmpty() }?.let { url -> linkTag(url, start, onLinkClick) }
    else -> null
}

private fun linkTag(url: String, start: Int, onLinkClick: ((String) -> Unit)?): OpenTag {
    val styles = TextLinkStyles(SpanStyle(color = LINK_COLOR, textDecoration = TextDecoration.Underline))
    return if (onLinkClick != null) {
        val link = LinkAnnotation.Clickable(url, styles) { onLinkClick(url) }
        OpenTag("url", start) { from, to -> addLink(link, from, to) }
    } else {
        val link = LinkAnnotation.Url(url, styles)
        OpenTag("url", start) { from, to -> addLink(link, from, to) }
    }
}

/** `#RRGGBB` or `rgb(r, g, b)`, as VxLabel reads them. */
internal fun parseColor(value: String?): Color? {
    val trimmed = value?.trim() ?: return null
    HEX_COLOR.matchEntire(trimmed)?.let { return Color(0xFF000000 or it.groupValues[1].toLong(16)) }
    RGB_COLOR.matchEntire(trimmed)?.let { match ->
        val (r, g, b) = match.destructured
        return Color(r.toInt().coerceAtMost(255), g.toInt().coerceAtMost(255), b.toInt().coerceAtMost(255))
    }
    return null
}

private val TAG_PATTERN = Regex("""\[(/?)(b|color|url)(?:=([^\]]*))?]""", RegexOption.IGNORE_CASE)
private val HEX_COLOR = Regex("""#([0-9A-Fa-f]{6})""")
private val RGB_COLOR = Regex("""rgb\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)""", RegexOption.IGNORE_CASE)

/** `UIColor.systemBlue`, the colour VxLabel gives links. */
private val LINK_COLOR = Color(0xFF007AFF)
