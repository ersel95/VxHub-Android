package com.vxhub.internal

import android.util.Log
import com.vxhub.VxLogLevel

/**
 * The SDK's only logging path. Everything goes to Logcat under the `VxHub` tag, filtered by
 * [VxHubConfig.logLevel][com.vxhub.VxHubConfig.logLevel].
 */
internal object VxLog {
    private const val TAG = "VxHub"

    @Volatile
    var level: VxLogLevel = VxLogLevel.INFO

    fun isEnabled(level: VxLogLevel): Boolean =
        this.level != VxLogLevel.NONE && level.ordinal >= this.level.ordinal

    fun v(message: () -> String) {
        if (isEnabled(VxLogLevel.VERBOSE)) Log.v(TAG, message())
    }

    fun d(message: () -> String) {
        if (isEnabled(VxLogLevel.DEBUG)) Log.d(TAG, message())
    }

    fun i(message: String) {
        if (isEnabled(VxLogLevel.INFO)) Log.i(TAG, message)
    }

    fun w(message: String, error: Throwable? = null) {
        if (isEnabled(VxLogLevel.WARNING)) Log.w(TAG, message, error)
    }

    fun e(message: String, error: Throwable? = null) {
        if (isEnabled(VxLogLevel.ERROR)) Log.e(TAG, message, error)
    }
}
