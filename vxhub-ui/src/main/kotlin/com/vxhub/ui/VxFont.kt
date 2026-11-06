package com.vxhub.ui

import androidx.compose.ui.text.font.FontFamily

/**
 * Typeface for VxHub's screens — iOS's `VxFont`.
 *
 * iOS's `.custom("Manrope")` resolves `Manrope-Bold`, `Manrope-Regular`… by name; on Android pass a
 * [FontFamily] that declares those weights (`FontFamily(Font(R.font.manrope_bold, FontWeight.Bold), …)`).
 */
public sealed interface VxFont {
    /** The platform typeface (Roboto, or the OEM's). */
    public data object Default : VxFont

    /**
     * iOS's SF Rounded. Android has no system rounded face, so this is the platform typeface;
     * pass [Custom] with a rounded family (e.g. Nunito) for the same look.
     */
    public data object Rounded : VxFont

    public data class Custom(val family: FontFamily) : VxFont
}

/** The Compose family for this font. */
public val VxFont.fontFamily: FontFamily
    get() = when (this) {
        VxFont.Default, VxFont.Rounded -> FontFamily.Default
        is VxFont.Custom -> family
    }
