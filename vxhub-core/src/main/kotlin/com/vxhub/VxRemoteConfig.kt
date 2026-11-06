package com.vxhub

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The panel's remote config for this device, as delivered with the last registration.
 *
 * The backend sends most values as strings ("true", "3", a JSON document), so the typed getters
 * parse them: [getBoolean] accepts `true`/`false`/`1`/`0`, the numeric getters accept numbers and
 * numeric strings, and [getJson] accepts an object/array or a string holding one.
 */
public class VxRemoteConfig internal constructor(private val values: JsonObject) {

    public val keys: Set<String> get() = values.keys

    public val isEmpty: Boolean get() = values.isEmpty()

    public operator fun contains(key: String): Boolean = values.containsKey(key)

    /** The value as text; objects and arrays come back as their JSON. */
    public fun getString(key: String): String? = when (val v = values[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> v.contentOrNull
        else -> v.toString()
    }

    public fun getString(key: String, default: String): String = getString(key) ?: default

    public fun getBoolean(key: String, default: Boolean = false): Boolean {
        val primitive = values[key] as? JsonPrimitive ?: return default
        primitive.booleanOrNull?.let { return it }
        return when (primitive.contentOrNull?.trim()?.lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> default
        }
    }

    public fun getLong(key: String, default: Long = 0L): Long {
        val primitive = values[key] as? JsonPrimitive ?: return default
        return primitive.longOrNull
            ?: primitive.contentOrNull?.trim()?.toLongOrNull()
            ?: primitive.doubleOrNull?.toLong()
            ?: default
    }

    public fun getInt(key: String, default: Int = 0): Int = getLong(key, default.toLong()).toInt()

    public fun getDouble(key: String, default: Double = 0.0): Double {
        val primitive = values[key] as? JsonPrimitive ?: return default
        return primitive.doubleOrNull ?: primitive.contentOrNull?.trim()?.toDoubleOrNull() ?: default
    }

    /**
     * The value as JSON text, for parsing with the app's own serializer. A string that holds a
     * JSON document is returned as that document.
     */
    public fun getJson(key: String): String? = when (val v = values[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> v.contentOrNull?.trim()?.takeIf { it.startsWith("{") || it.startsWith("[") }
        else -> v.toString()
    }

    /** Every value converted to plain Kotlin types: String, Boolean, Long, Double, List, Map or null. */
    public fun asMap(): Map<String, Any?> = values.mapValues { (_, v) -> v.toPlain() }

    override fun toString(): String = "VxRemoteConfig($values)"

    internal companion object {
        val EMPTY: VxRemoteConfig = VxRemoteConfig(JsonObject(emptyMap()))
    }
}

internal fun JsonElement.toPlain(): Any? = when (this) {
    JsonNull -> null
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        else -> doubleOrNull ?: content
    }
    is JsonArray -> map { it.toPlain() }
    is JsonObject -> mapValues { (_, v) -> v.toPlain() }
}
