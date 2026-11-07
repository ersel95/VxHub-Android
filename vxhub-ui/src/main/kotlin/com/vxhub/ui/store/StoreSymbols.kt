package com.vxhub.ui.store

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Stand-ins for the SF Symbols the iOS store uses by default, as Material path data (the module
 * ships no icon library). Drawn tinted, like the symbols.
 */
internal object StoreSymbols {
    /** `bag.fill`. */
    val Bag: ImageVector by lazy {
        symbol(
            "Bag",
            "M18,6h-2c0,-2.21 -1.79,-4 -4,-4S8,3.79 8,6H6c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V8" +
                "c0,-1.1 -0.9,-2 -2,-2zM10,10c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1V8h2v2zM12,4c1.1,0 2,0.9 2,2h-4c0,-1.1 0.9,-2 2,-2z" +
                "M16,10c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1V8h2v2z",
        )
    }

    /** `dollarsign.circle.fill`. */
    val Coin: ImageVector by lazy {
        symbol(
            "Coin",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13.41,18.09v1.91h-2.67v-1.93c-1.71,-0.36 " +
                "-3.16,-1.46 -3.27,-3.4h1.96c0.1,1.05 0.82,1.87 2.65,1.87 1.96,0 2.4,-0.98 2.4,-1.59 0,-0.83 -0.44,-1.61 " +
                "-2.67,-2.14 -2.48,-0.6 -4.18,-1.62 -4.18,-3.67 0,-1.72 1.39,-2.84 3.11,-3.21V4h2.67v1.95c1.86,0.45 2.79,1.86 " +
                "2.85,3.39H14.3c-0.05,-1.11 -0.64,-1.87 -2.22,-1.87 -1.5,0 -2.4,0.68 -2.4,1.64 0,0.84 0.65,1.39 2.67,1.91s4.18," +
                "1.39 4.18,3.91c-0.01,1.83 -1.38,2.83 -3.12,3.16z",
        )
    }

    /** `star.fill`. */
    val Star: ImageVector by lazy {
        symbol("Star", "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z")
    }

    private fun symbol(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name = "VxStore.$name", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.Black))
            .build()
}
