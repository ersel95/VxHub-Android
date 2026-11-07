package com.vxhub.ui.store

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VxRichTextTest {

    private fun AnnotatedString.styled(): List<Triple<String, FontWeight?, Color>> =
        spanStyles.map { Triple(text.substring(it.start, it.end), it.item.fontWeight, it.item.color) }

    @Test
    fun `plain text passes through`() {
        val text = vxRichText("Save 50% on the yearly plan")

        assertThat(text.text).isEqualTo("Save 50% on the yearly plan")
        assertThat(text.spanStyles).isEmpty()
    }

    @Test
    fun `bold and colours become spans and the tags disappear`() {
        val text = vxRichText("Save [b]50%[/b] — [color=#FF0000]today[/color] [color=rgb(0, 128, 255)]only[/color]")

        assertThat(text.text).isEqualTo("Save 50% — today only")
        assertThat(text.styled()).containsExactly(
            Triple("50%", FontWeight.Bold, Color.Unspecified),
            Triple("today", null, Color(0xFFFF0000)),
            Triple("only", null, Color(0, 128, 255)),
        )
    }

    @Test
    fun `tags nest and may close out of order`() {
        val text = vxRichText("[b]a[color=#00FF00]b[/b]c[/color]")

        assertThat(text.text).isEqualTo("abc")
        assertThat(text.styled()).containsExactly(
            Triple("ab", FontWeight.Bold, Color.Unspecified),
            Triple("bc", null, Color(0xFF00FF00)),
        )
    }

    @Test
    fun `unknown, malformed and unmatched tags stay literal`() {
        assertThat(vxRichText("[i]x[/i]").text).isEqualTo("[i]x[/i]")
        assertThat(vxRichText("[color=red]x[/color]").text).isEqualTo("[color=red]x[/color]")
        assertThat(vxRichText("x[/b]").text).isEqualTo("x[/b]")
        assertThat(vxRichText("[b=1]x").text).isEqualTo("[b=1]x")
        assertThat(vxRichText("[url=]x[/url]").text).isEqualTo("[url=]x[/url]")
    }

    @Test
    fun `a tag left open runs to the end`() {
        val text = vxRichText("a[b]bc")

        assertThat(text.text).isEqualTo("abc")
        assertThat(text.styled()).containsExactly(Triple("bc", FontWeight.Bold, Color.Unspecified))
    }

    @Test
    fun `tags are case-insensitive`() {
        assertThat(vxRichText("[B]x[/B]").styled()).containsExactly(Triple("x", FontWeight.Bold, Color.Unspecified))
    }

    @Test
    fun `urls become links that call back with the address`() {
        var opened: String? = null
        val text = vxRichText("Read the [url=https://example.com/terms]terms[/url].") { opened = it }

        assertThat(text.text).isEqualTo("Read the terms.")
        val link = text.getLinkAnnotations(0, text.length).single()
        assertThat(text.text.substring(link.start, link.end)).isEqualTo("terms")
        val clickable = link.item as LinkAnnotation.Clickable
        assertThat(clickable.tag).isEqualTo("https://example.com/terms")
        clickable.linkInteractionListener?.onClick(clickable)
        assertThat(opened).isEqualTo("https://example.com/terms")
    }

    @Test
    fun `colours parse as VxLabel reads them`() {
        assertThat(parseColor("#4be162")).isEqualTo(Color(0xFF4BE162))
        assertThat(parseColor(" rgb(1,2,3) ")).isEqualTo(Color(1, 2, 3))
        assertThat(parseColor("#FFF")).isNull()
        assertThat(parseColor(null)).isNull()
    }
}
