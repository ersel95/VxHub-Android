package com.vxhub.ui.internal

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.vxhub.VxHub

/**
 * The markup iOS's `VxLabel`, `VxButton` and `VxTextView` render in panel and app texts:
 * `[b]bold[/b]`, `[color=#FF0000]red[/color]`, `[color=rgb(255, 0, 0)]red[/color]` and
 * `[url=https://…]link[/url]`. Every screen parses it here.
 *
 * iOS converts the markup to HTML and renders that, so this follows HTML's rules:
 * - an opening tag iOS would not convert — an unknown tag, `[color=red]`, `[url=]`, `[b=1]` —
 *   stays literal text;
 * - a closing tag with nothing open to close is dropped, as HTML drops a stray end tag;
 * - a closing tag ends the innermost open tag of its kind only, so `[b]a[color=#0F0]b[/b]c[/color]`
 *   keeps `c` green;
 * - a tag left open runs to the end of the text.
 * Text without an opening tag skips the parser (iOS's `containsFormatting()`).
 */
internal object VxMarkup {
    /** `UIColor.systemBlue`, the colour iOS gives links. */
    val LinkColor: Color = Color(0xFF007AFF)

    private val Tag = Regex("""\[(/?)(b|color|url)(?:=([^\]]*))?]""", RegexOption.IGNORE_CASE)
    private val Hex = Regex("""#([0-9A-Fa-f]{6})""")
    private val Rgb = Regex("""rgb\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)""", RegexOption.IGNORE_CASE)
    private val LinkStyles = TextLinkStyles(SpanStyle(color = LinkColor, textDecoration = TextDecoration.Underline))
    private val Bold = SpanStyle(fontWeight = FontWeight.Bold)

    fun hasMarkup(text: String): Boolean =
        text.contains("[b]", ignoreCase = true) ||
            text.contains("[color", ignoreCase = true) ||
            text.contains("[url=", ignoreCase = true)

    /** [text] with its markup applied; [onLinkClick] receives a tapped `[url=…]`'s address. */
    fun parse(text: String, onLinkClick: ((String) -> Unit)? = null): AnnotatedString {
        if (!hasMarkup(text)) return AnnotatedString(text)
        val builder = AnnotatedString.Builder(text.length)
        val open = ArrayList<OpenTag>()
        var cursor = 0
        for (match in Tag.findAll(text)) {
            builder.append(text, cursor, match.range.first)
            cursor = match.range.last + 1
            val isClosing = match.groupValues[1].isNotEmpty()
            val name = match.groupValues[2].lowercase()
            val argument = match.groups[3]?.value
            when {
                isClosing && argument != null -> builder.append(match.value)
                isClosing -> {
                    val index = open.indexOfLast { it.name == name }
                    if (index >= 0) open.removeAt(index).close(builder)
                }
                else -> {
                    val tag = openTag(name, argument, builder.length, onLinkClick)
                    if (tag == null) builder.append(match.value) else open += tag
                }
            }
        }
        builder.append(text, cursor, text.length)
        open.forEach { it.close(builder) }
        return builder.toAnnotatedString()
    }

    /** `#RRGGBB` or `rgb(r, g, b)`, the two colour forms iOS reads. */
    fun parseColor(value: String?): Color? {
        val trimmed = value?.trim() ?: return null
        Hex.matchEntire(trimmed)?.let { return Color(0xFF000000 or it.groupValues[1].toLong(16)) }
        val rgb = Rgb.matchEntire(trimmed) ?: return null
        val (red, green, blue) = rgb.destructured
        return Color(red.toInt().coerceAtMost(255), green.toInt().coerceAtMost(255), blue.toInt().coerceAtMost(255))
    }

    private fun openTag(name: String, argument: String?, start: Int, onLinkClick: ((String) -> Unit)?): OpenTag? = when (name) {
        "b" -> if (argument == null) OpenTag(name, start) { from, to -> addStyle(Bold, from, to) } else null
        "color" -> parseColor(argument)?.let { color -> OpenTag(name, start) { from, to -> addStyle(SpanStyle(color = color), from, to) } }
        else -> argument?.trim()?.takeIf { it.isNotEmpty() }?.let { url ->
            val listener = onLinkClick?.let { handler -> LinkInteractionListener { handler(url) } }
            val link = LinkAnnotation.Url(url, LinkStyles, listener)
            OpenTag(name, start) { from, to -> addLink(link, from, to) }
        }
    }

    private class OpenTag(
        val name: String,
        private val start: Int,
        private val mark: AnnotatedString.Builder.(start: Int, end: Int) -> Unit,
    ) {
        fun close(builder: AnnotatedString.Builder) {
            if (builder.length > start) builder.mark(start, builder.length)
        }
    }
}

/** A [Text] showing [text]'s [VxMarkup]; links open in VxHub's web screen, as `VxLabel`'s do. */
@Composable
internal fun VxMarkupText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    val context = LocalContext.current
    val annotated = remember(text, context) { VxMarkup.parse(text) { url -> VxHub.presentWebUrl(context, url) } }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
