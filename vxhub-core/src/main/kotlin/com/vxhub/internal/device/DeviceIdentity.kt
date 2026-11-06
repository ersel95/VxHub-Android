package com.vxhub.internal.device

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.vxhub.internal.storage.VxPreferences
import java.util.UUID

/**
 * The id VxHub sends as `X-Hub-Device-Id`.
 *
 * iOS keeps a generated id in the Keychain so it survives reinstalls, which is what stops a
 * reinstall from minting a fresh device (and a fresh register coin gift, and a lifted ban). The
 * Android equivalent is `ANDROID_ID`: stable across reinstalls for the same signing key, user and
 * device, and different on every other device — so it is also immune to backup-restore cloning.
 * Only when it is unavailable does VxHub fall back to a random id kept in no-backup storage.
 */
internal class DeviceIdentity(private val context: Context, private val prefs: VxPreferences) {

    @Volatile
    private var cached: String? = null

    val deviceId: String
        get() = cached ?: resolve().also { cached = it }

    @SuppressLint("HardwareIds")
    private fun resolve(): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        if (androidId != null && androidId.isNotBlank() && androidId !in KNOWN_BAD_IDS) return androidId

        prefs[VxPreferences.Keys.DEVICE_ID_FALLBACK]?.let { return it }
        val generated = UUID.randomUUID().toString().replace("-", "")
        prefs.editAsync { it[VxPreferences.Keys.DEVICE_ID_FALLBACK] = generated }
        return generated
    }

    private companion object {
        // The id shared by a batch of Android 2.2 devices and by some emulator images.
        val KNOWN_BAD_IDS = setOf("9774d56d682e549c", "0000000000000000")
    }
}
