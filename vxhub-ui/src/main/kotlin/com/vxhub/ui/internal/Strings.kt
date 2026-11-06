package com.vxhub.ui.internal

import com.vxhub.VxHub

/**
 * Text for a VxHub screen: the panel's translation, else VxHub's bundled English, else [default].
 * Use the iOS SDK's keys (VxLocalizables.swift) so one panel translation serves both platforms.
 */
internal fun vxString(key: String, default: String? = null): String = VxHub.localize(key, default)

/** Fills iOS's `{xxx}` placeholder (`replaceKeyReplacing(toBeReplaced:)`). */
internal fun String.replacingPlaceholder(value: String): String = replace("{xxx}", value)
