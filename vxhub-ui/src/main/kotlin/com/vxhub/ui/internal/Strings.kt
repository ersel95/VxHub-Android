package com.vxhub.ui.internal

import com.vxhub.VxHub

/**
 * Text for a VxHub screen: the panel's translation, else VxHub's bundled English, else [default].
 * Use the iOS SDK's keys (VxLocalizables.swift) so one panel translation serves both platforms.
 */
internal fun vxString(key: String, default: String? = null): String = VxHub.localize(key, default)

/** Fills iOS's `{xxx}` placeholder (`replaceKeyReplacing(toBeReplaced:)`). */
internal fun String.replacingPlaceholder(value: String): String = replace("{xxx}", value)

/** iOS's `applyValues`: fills `{{value_1}}`, `{{value_2}}`, … with [values] in order. */
internal fun String.withValues(vararg values: Any): String =
    values.foldIndexed(this) { index, text, value -> text.replace("{{value_${index + 1}}}", value.toString()) }
