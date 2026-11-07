package com.vxhub.ui.paywall

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PaywallMarkupTest {

    private fun AnnotatedString.styledText(predicate: (SpanStyle) -> Boolean): List<String> =
        spanStyles.filter { predicate(it.item) }.map { text.substring(it.start, it.end) }

    @Test
    fun `text without markup is left alone`() {
        val parsed = PaywallMarkup.parse("Unlimited [access] for {{value_1}} days")
        assertThat(parsed.text).isEqualTo("Unlimited [access] for {{value_1}} days")
        assertThat(parsed.spanStyles).isEmpty()
    }

    @Test
    fun `bold`() {
        val parsed = PaywallMarkup.parse("Go [b]Premium[/b] today")
        assertThat(parsed.text).isEqualTo("Go Premium today")
        assertThat(parsed.styledText { it.fontWeight == FontWeight.Bold }).containsExactly("Premium")
    }

    @Test
    fun `hex and rgb colours`() {
        val parsed = PaywallMarkup.parse("[color=#FF0000]red[/color] and [color=rgb(0, 128, 255)]blue[/color]")
        assertThat(parsed.text).isEqualTo("red and blue")
        assertThat(parsed.spanStyles.map { it.item.color }).containsExactly(Color(0xFFFF0000), Color(0, 128, 255)).inOrder()
        assertThat(parsed.styledText { it.color == Color(0, 128, 255) }).containsExactly("blue")
    }

    @Test
    fun `links open their url`() {
        var opened: String? = null
        val parsed = PaywallMarkup.parse("Read the [url=https://example.com/terms]terms[/url].") { opened = it }
        assertThat(parsed.text).isEqualTo("Read the terms.")
        val link = parsed.getLinkAnnotations(0, parsed.length).single()
        val url = link.item as LinkAnnotation.Url
        assertThat(url.url).isEqualTo("https://example.com/terms")
        assertThat(parsed.text.substring(link.start, link.end)).isEqualTo("terms")
        url.linkInteractionListener?.onClick(url)
        assertThat(opened).isEqualTo("https://example.com/terms")
    }

    @Test
    fun `nested, unclosed and stray tags`() {
        val parsed = PaywallMarkup.parse("[b]bold [color=#00FF00]green[/color][/b][/color] [b]open")
        assertThat(parsed.text).isEqualTo("bold green open")
        assertThat(parsed.styledText { it.fontWeight == FontWeight.Bold }).containsExactly("bold green", "open")
        assertThat(parsed.styledText { it.color == Color(0xFF00FF00) }).containsExactly("green")
    }

    @Test
    fun `values fill numbered placeholders`() {
        assertThat("Save {{value_1}}% on the {{value_2}} plan".withValues(40, "yearly")).isEqualTo("Save 40% on the yearly plan")
        assertThat("No placeholder".withValues(1)).isEqualTo("No placeholder")
    }
}
