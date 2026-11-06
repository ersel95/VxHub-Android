package com.vxhub.internal.device

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import androidx.core.os.ConfigurationCompat
import com.vxhub.internal.VxLog
import java.util.Locale

/**
 * Static facts about the device and the app, gathered once per process. The Android counterpart of
 * iOS's `VxDeviceConfig`.
 */
internal class DeviceConfig(
    val deviceType: String,
    val brand: String,
    val model: String,
    val osVersion: String,
    val resolution: String,
    val dpi: String,
    val countryCode: String,
    val carrierRegion: String,
    val deviceLanguage: String,
    val appVersion: String,
    val packageName: String,
    val installedApps: Map<String, Boolean>,
) {
    companion object {
        fun collect(context: Context): DeviceConfig {
            val resources = context.resources
            val metrics = resources.displayMetrics
            val locale = ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault()
            val isTablet = resources.configuration.smallestScreenWidthDp >= 600 ||
                (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
            return DeviceConfig(
                deviceType = if (isTablet) "tablet" else "phone",
                brand = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.ROOT) },
                // iOS strips whitespace from the model name; the panel groups on the same shape.
                model = Build.MODEL.filterNot { it.isWhitespace() }.take(100),
                osVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
                resolution = "${metrics.widthPixels}x${metrics.heightPixels}",
                dpi = metrics.densityDpi.toString(),
                countryCode = locale.country.takeIf { it.isNotBlank() } ?: "xx",
                carrierRegion = runCatching {
                    context.getSystemService<TelephonyManager>()?.networkCountryIso?.uppercase(Locale.ROOT)
                }.getOrNull().orEmpty(),
                deviceLanguage = locale.language.takeIf { it.isNotBlank() } ?: "en",
                appVersion = appVersionName(context),
                packageName = context.packageName,
                installedApps = installedApps(context.packageManager),
            )
        }

        private fun appVersionName(context: Context): String = runCatching {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            info.versionName
        }.getOrNull().orEmpty()

        /**
         * Same keys as iOS's `ThirdPartyApps`. Visibility of these packages is declared with
         * `<queries>` in the SDK manifest, which merges into the app.
         */
        private val THIRD_PARTY_APPS = mapOf(
            "fb" to listOf("com.facebook.katana"),
            "instagram" to listOf("com.instagram.android"),
            "snapchat" to listOf("com.snapchat.android"),
            "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            "whatsapp" to listOf("com.whatsapp"),
        )

        private fun installedApps(pm: PackageManager): Map<String, Boolean> = THIRD_PARTY_APPS.mapValues { (_, packages) ->
            packages.any { pkg ->
                try {
                    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(pkg, 0)
                    }
                    info.enabled
                } catch (_: PackageManager.NameNotFoundException) {
                    false
                } catch (e: RuntimeException) {
                    VxLog.d { "Installed-app check failed for $pkg: ${e.message}" }
                    false
                }
            }
        }
    }
}

/**
 * Compares dotted version names numerically ("1.10.0" > "1.9.3"). Non-numeric suffixes
 * ("2.1.0-beta") are ignored per component.
 */
internal fun compareVersions(a: String, b: String): Int {
    fun parts(v: String) = v.trim().split('.', '-', '_', ' ').map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val left = parts(a)
    val right = parts(b)
    for (i in 0 until maxOf(left.size, right.size)) {
        val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    return 0
}

/**
 * The Google Advertising ID, read reflectively so core does not force play-services-ads-identifier
 * on apps that do not collect it. Must run off the main thread. Empty when unavailable or when the
 * user opted out of ad personalization.
 */
internal fun readAdvertisingId(context: Context): String = try {
    val client = Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient")
    val info = client.getMethod("getAdvertisingIdInfo", Context::class.java).invoke(null, context)
    val limited = info.javaClass.getMethod("isLimitAdTrackingEnabled").invoke(info) as? Boolean ?: true
    if (limited) "" else (info.javaClass.getMethod("getId").invoke(info) as? String).orEmpty()
} catch (_: ClassNotFoundException) {
    VxLog.w("collectAdvertisingId is on but play-services-ads-identifier is not on the classpath")
    ""
} catch (e: Exception) {
    VxLog.d { "Advertising id unavailable: ${e.javaClass.simpleName}" }
    ""
}
