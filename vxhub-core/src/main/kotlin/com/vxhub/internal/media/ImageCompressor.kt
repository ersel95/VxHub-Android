package com.vxhub.internal.media

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** JPEG compression with a size budget — the Android port of iOS's `VxImageCompresser`. */
internal object ImageCompressor {
    private const val QUALITY_STEP = 10
    private const val MIN_QUALITY = 10

    fun compress(bitmap: Bitmap, maxDimension: Int, quality: Float, maxBytes: Int): ByteArray {
        val scaled = scale(bitmap, maxDimension)
        var jpegQuality = (quality * 100).roundToInt().coerceIn(MIN_QUALITY, 100)
        var bytes = encode(scaled, jpegQuality)
        while (bytes.size > maxBytes && jpegQuality > MIN_QUALITY) {
            jpegQuality = (jpegQuality - QUALITY_STEP).coerceAtLeast(MIN_QUALITY)
            bytes = encode(scaled, jpegQuality)
        }
        if (scaled !== bitmap) scaled.recycle()
        return bytes
    }

    private fun scale(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (maxDimension <= 0 || longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
}
