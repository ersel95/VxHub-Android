package com.vxhub.ui.internal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VxMarkupTest {

    private fun AnnotatedString.styled(): List<Triple<String, FontWeight?, Color>> =
        spanStyles.map { Triple(text.substring(it.start, it.end), it.item.fontWeight, it.item.color) }

    @Test
    fun `text without an opening tag is left alone`() {
        val parsed = VxMarkup.parse("Unlimited [access] for {{value_1}} days[/b]")

        assertThat(parsed.text).isEqualTo("Unlimited [access] for {{value_1}} days[/b]")
        assertThat(parsed.spanStyles).isEmpty()
    }

    @Test
    fun `bold and colours become spans and the tags disappear`() {
        val parsed = VxMarkup.parse("Save [b]50%[/b] — [color=#FF0000]today[/color] [color=rgb(0, 128, 255)]only[/color]")

        assertThat(parsed.text).isEqualTo("Save 50% — today only")
        assertThat(parsed.styled()).containsExactly(
            Triple("50%", FontWeight.Bold, Color.Unspecified),
            Triple("today", null, Color(0xFFFF0000)),
            Triple("only", null, Color(0, 128, 255)),
        )
    }

    @Test
    fun `a closing tag ends only its own kind, as in HTML`() {
        val parsed = VxMarkup.parse("[b]a[color=#00FF00]b[/b]c[/color]")

        assertThat(parsed.text).isEqualTo("abc")
        assertThat(parsed.styled()).containsExactly(
            Triple("ab", FontWeight.Bold, Color.Unspecified),
            Triple("bc", null, Color(0xFF00FF00)),
        )
    }

    @Test
    fun `stray closing tags are dropped, as iOS's HTML rendering drops them`() {
        val parsed = VxMarkup.parse("[b]bold[/b][/b] plain[/color]")

        assertThat(parsed.text).isEqualTo("bold plain")
        assertThat(parsed.styled()).containsExactly(Triple("bold", FontWeight.Bold, Color.Unspecified))
    }

    @Test
    fun `tags iOS does not convert stay literal`() {
        assertThat(VxMarkup.parse("[b]x[/b] [i]y[/i]").text).isEqualTo("x [i]y[/i]")
        assertThat(VxMarkup.parse("[color=red]x[/color]").text).isEqualTo("[color=red]x")
        assertThat(VxMarkup.parse("[b=1]x [b]y[/b=1]").text).isEqualTo("[b=1]x y[/b=1]")
        assertThat(VxMarkup.parse("[url=]x[/url]").text).isEqualTo("[url=]x")
    }

    @Test
    fun `a tag left open runs to the end`() {
        val parsed = VxMarkup.parse("a[b]bc")

        assertThat(parsed.text).isEqualTo("abc")
        assertThat(parsed.styled()).containsExactly(Triple("bc", FontWeight.Bold, Color.Unspecified))
    }

    @Test
    fun `empty tags add no span`() {
        val parsed = VxMarkup.parse("a[b][/b]b")

        assertThat(parsed.text).isEqualTo("ab")
        assertThat(parsed.spanStyles).isEmpty()
    }

    @Test
    fun `tags are case-insensitive`() {
        assertThat(VxMarkup.parse("[B]x[/B]").styled()).containsExactly(Triple("x", FontWeight.Bold, Color.Unspecified))
    }

    @Test
    fun `urls become links that call back with the address`() {
        var opened: String? = null
        val parsed = VxMarkup.parse("Read the [url= https://example.com/terms ]terms[/url].") { opened = it }

        assertThat(parsed.text).isEqualTo("Read the terms.")
        val link = parsed.getLinkAnnotations(0, parsed.length).single()
        assertThat(parsed.text.substring(link.start, link.end)).isEqualTo("terms")
        val url = link.item as LinkAnnotation.Url
        assertThat(url.url).isEqualTo("https://example.com/terms")
        url.linkInteractionListener?.onClick(url)
        assertThat(opened).isEqualTo("https://example.com/terms")
    }

    @Test
    fun `without a handler a link opens through the platform`() {
        val parsed = VxMarkup.parse("[url=https://example.com]site[/url]")

        val url = parsed.getLinkAnnotations(0, parsed.length).single().item as LinkAnnotation.Url
        assertThat(url.linkInteractionListener).isNull()
    }

    @Test
    fun `colours parse as iOS reads them`() {
        assertThat(VxMarkup.parseColor("#4be162")).isEqualTo(Color(0xFF4BE162))
        assertThat(VxMarkup.parseColor(" rgb(1,2,3) ")).isEqualTo(Color(1, 2, 3))
        assertThat(VxMarkup.parseColor("rgb(300, 0, 0)")).isEqualTo(Color(255, 0, 0))
        assertThat(VxMarkup.parseColor("#FFF")).isNull()
        assertThat(VxMarkup.parseColor(null)).isNull()
    }

    @Test
    fun `values fill numbered placeholders`() {
        assertThat("Save {{value_1}}% on the {{value_2}} plan".withValues(40, "yearly")).isEqualTo("Save 40% on the yearly plan")
        assertThat("No placeholder".withValues(1)).isEqualTo("No placeholder")
    }
}
