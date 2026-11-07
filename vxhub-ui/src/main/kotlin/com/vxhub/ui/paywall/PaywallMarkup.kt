package com.vxhub.ui.paywall

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * The markup iOS's `VxLabel` and `VxButton` render in panel and app texts: `[b]bold[/b]`,
 * `[color=#FF0000]red[/color]`, `[color=rgb(255,0,0)]red[/color]` and `[url=https://…]link[/url]`.
 * Unknown or unmatched closing tags are dropped; a tag left open runs to the end of the text.
 */
internal object PaywallMarkup {
    /** iOS's `systemBlue`, the colour of links. */
    val LinkColor: Color = Color(0xFF007AFF)

    private val Tag = Regex("""\[(/?)(b|color|url)(?:=([^\]]*))?]""", RegexOption.IGNORE_CASE)
    private val Hex = Regex("""#([0-9a-fA-F]{6})""")
    private val Rgb = Regex("""rgb\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)""", RegexOption.IGNORE_CASE)
    private val LinkStyles = TextLinkStyles(style = SpanStyle(color = LinkColor, textDecoration = TextDecoration.Underline))

    /** iOS's `containsFormatting()`: plain texts skip the parser. */
    fun hasMarkup(text: String): Boolean =
        text.contains("[b]", ignoreCase = true) || text.contains("[color", ignoreCase = true) || text.contains("[url=", ignoreCase = true)

    /** [text] with its markup applied; [onLinkClick] receives a tapped `[url=…]`. */
    fun parse(text: String, onLinkClick: ((String) -> Unit)? = null): AnnotatedString {
        if (!hasMarkup(text)) return AnnotatedString(text)
        return buildAnnotatedString {
            // Open tags, innermost last, with the builder index that closes each.
            val open = ArrayList<Pair<String, Int>>()
            var cursor = 0
            for (match in Tag.findAll(text)) {
                append(text, cursor, match.range.first)
                cursor = match.range.last + 1
                val name = match.groupValues[2].lowercase()
                val isClosing = match.groupValues[1].isNotEmpty()
                if (isClosing) {
                    val at = open.indexOfLast { it.first == name }
                    if (at >= 0) {
                        pop(open[at].second)
                        while (open.size > at) open.removeAt(open.lastIndex)
                    }
                } else {
                    open += name to push(name, match.groupValues[3].trim(), onLinkClick)
                }
            }
            append(text, cursor, text.length)
        }
    }

    private fun AnnotatedString.Builder.push(name: String, value: String, onLinkClick: ((String) -> Unit)?): Int = when (name) {
        "b" -> pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        "color" -> pushStyle(SpanStyle(color = color(value) ?: Color.Unspecified))
        else -> if (value.isEmpty()) {
            pushStyle(SpanStyle())
        } else {
            val listener = onLinkClick?.let { handler -> LinkInteractionListener { handler(value) } }
            pushLink(LinkAnnotation.Url(value, LinkStyles, listener))
        }
    }

    private fun color(value: String): Color? {
        Hex.matchEntire(value)?.let { return Color(0xFF000000 or it.groupValues[1].toLong(16)) }
        val rgb = Rgb.matchEntire(value) ?: return null
        val (red, green, blue) = rgb.destructured
        return Color(red.toInt().coerceAtMost(255), green.toInt().coerceAtMost(255), blue.toInt().coerceAtMost(255))
    }
}

/** iOS's `applyValues`: fills `{{value_1}}`, `{{value_2}}`, … with [values] in order. */
internal fun String.withValues(vararg values: Any): String =
    values.foldIndexed(this) { index, text, value -> text.replace("{{value_${index + 1}}}", value.toString()) }
