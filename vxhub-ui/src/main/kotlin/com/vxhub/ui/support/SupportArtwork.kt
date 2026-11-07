package com.vxhub.ui.support

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The support screens' artwork, redrawn from the iOS asset catalog (`support/…`) as vectors and
 * canvas drawing, so the module ships no bitmaps.
 */
internal object SupportArtwork {
    /** iOS's `new_chat_icon`: a square with a pencil. Tint it. */
    val NewChat: ImageVector by lazy {
        strokes(
            "NewChat",
            width = 2f,
            "M11,4H4a2,2 0,0 0,-2,2v14a2,2 0,0 0,2,2h14a2,2 0,0 0,2,-2v-7",
            "M18.5,2.5a2.121,2.121 0,0 1,3,3L12,15l-4,1 1,-4 9.5,-9.5z",
        )
    }

    /** The paper plane of iOS's `send_message_button_icon`, laid out to fill its 32 dp circle. Tint it. */
    val Send: ImageVector by lazy {
        strokes("Send", width = 1.6f, "M9.9,12.2L8,8.6H15.8L11.6,16Z", "M9.9,12.2L12.2,10.8")
    }

    private fun strokes(name: String, width: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = "VxSupport.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { path ->
                addPath(
                    pathData = PathParser().parsePathString(path).toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}

/** iOS's `empty_message_write_icon`: the new-chat glyph on a faint grey disc. */
@Composable
internal fun EmptyTicketsIcon(modifier: Modifier = Modifier) {
    Box(modifier.size(64.dp).background(Color(0x1F73737B), CircleShape), contentAlignment = Alignment.Center) {
        Icon(SupportArtwork.NewChat, contentDescription = null, tint = Color(0xFFF2F2F7), modifier = Modifier.size(20.dp))
    }
}

/** iOS's `empty_messages_help_icon`: a glowing violet orb, 156 dp like the original. */
@Composable
internal fun HelpOrb(modifier: Modifier = Modifier) {
    Canvas(modifier.size(156.dp)) {
        val unit = size.minDimension / 156f
        val glowRadius = 66f * unit
        val bodyRadius = 28f * unit
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0x40ED80E1),
                0.42f to Color(0x30ED80E1),
                0.45f to Color(0x2AED80E1),
                0.56f to Color(0x12E38EE3),
                0.65f to Color(0x0CEA95EA),
                0.8f to Color(0x06D5AAD5),
                1f to Color.Transparent,
                center = center,
                radius = glowRadius,
            ),
            radius = glowRadius,
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFFD4A8EC),
                0.45f to Color(0xFF493471),
                0.8f to Color(0xFF91439A),
                1f to Color(0xFFB45CC0),
                center = center + Offset(-0.15f * bodyRadius, -0.2f * bodyRadius),
                radius = bodyRadius * 1.15f,
            ),
            radius = bodyRadius,
        )
    }
}
