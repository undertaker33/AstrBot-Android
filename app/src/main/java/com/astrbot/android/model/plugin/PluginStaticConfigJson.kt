package com.astrbot.android.model.plugin

import org.json.JSONArray
import org.json.JSONObject

object PluginStaticConfigJson {
    fun encodeSchema(schema: PluginStaticConfigSchema): JSONObject {
        return JSONObject().apply {
            schema.fields.forEach { field ->
                put(field.fieldKey, encodeField(field))
            }
        }
    }

    fun decodeSchema(json: JSONObject): PluginStaticConfigSchema {
        val fields = buildList {
            val iterator = json.keys()
            while (iterator.hasNext()) {
                val fieldKey = iterator.next()
                val fieldJson = json.optJSONObject(fieldKey)
                    ?: throw IllegalArgumentException("schema.$fieldKey must be an object")
                add(decodeField(fieldKey, fieldJson, "schema.$fieldKey"))
            }
        }
        return PluginStaticConfigSchema(fields = fields)
    }

    private fun encodeField(field: PluginStaticConfigField): JSONObject {
        return JSONObject().apply {
            put("type", field.fieldType.wireValue)
            if (field.description.isNotBlank()) {
                put("description", field.description)
            }
            if (field.hint.isNotBlank()) {
                put("hint", field.hint)
            }
            if (field.obviousHint) {
                put("obvious_hint", true)
            }
            if (field.defaultValue != null) {
                put("default", encodeDefaultValue(field.defaultValue))
            }
            if (field.invisible) {
                put("invisible", true)
            }
            if (field.options.isNotEmpty()) {
                put(
                    "options",
                    JSONArray().apply {
                        field.options.forEach { option ->
                            put(
                                when (option) {
                                    is PluginStaticConfigOption.Plain -> option.value
                                    is PluginStaticConfigOption.Labeled -> JSONObject().apply {
                                        put("value", option.value)
                                        put("label", option.label)
                                    }
                                },
                            )
                        }
                    },
                )
            }
            field.specialType?.let { put("_special", it.wireValue) }
            if (field.section.isNotBlank()) {
                put("section", field.section)
            }
        }
    }

    private fun decodeField(
        fieldKey: String,
        json: JSONObject,
        path: String,
    ): PluginStaticConfigField {
        val fieldType = decodeFieldType(readRequiredString(json, "type", "$path.type"), "$path.type")
        return PluginStaticConfigField(
            fieldKey = fieldKey,
            fieldType = fieldType,
            description = json.optString("description"),
            hint = json.optString("hint"),
            obviousHint = json.optBoolean("obvious_hint", false),
            defaultValue = decodeDefaultValue(json, fieldType, "$path.default"),
            invisible = json.optBoolean("invisible", false),
            options = decodeOptions(readOptionalArray(json, "options"), "$path.options"),
            specialType = decodeSpecialType(readOptionalString(json, "_special"), "$path._special"),
            section = json.optString("section"),
        )
    }

    private fun decodeFieldType(value: String, path: String): PluginStaticConfigFieldType {
        return PluginStaticConfigFieldType.fromWireValue(value)
            ?: throw IllegalArgumentException("$path has unsupported value")
    }

    private fun decodeSpecialType(value: String?, path: String): PluginStaticConfigSpecialType? {
        if (value == null) return null
        return PluginStaticConfigSpecialType.fromWireValue(value)
            ?: throw IllegalArgumentException("$path has unsupported value")
    }

    private fun encodeDefaultValue(value: PluginStaticConfigValue): Any {
        return when (value) {
            is PluginStaticConfigValue.StringValue -> value.value
            is PluginStaticConfigValue.IntValue -> value.value
            is PluginStaticConfigValue.FloatValue -> value.value
            is PluginStaticConfigValue.BoolValue -> value.value
        }
    }

    private fun decodeDefaultValue(
        json: JSONObject,
        fieldType: PluginStaticConfigFieldType,
        path: String,
    ): PluginStaticConfigValue? {
        if (!json.has("default")) return null
        val value = json.get("default")
        return when (fieldType) {
            PluginStaticConfigFieldType.StringField,
            PluginStaticConfigFieldType.TextField,
            -> {
                if (value !is String) {
                    throw IllegalArgumentException("$path must be a string")
                }
                PluginStaticConfigValue.StringValue(value)
            }

            PluginStaticConfigFieldType.IntField -> {
                val intValue = when (value) {
                    is Int -> value
                    is Long -> value.toInt()
                    else -> throw IllegalArgumentException("$path must be an integer")
                }
                PluginStaticConfigValue.IntValue(intValue)
            }

            PluginStaticConfigFieldType.FloatField -> {
                val doubleValue = when (value) {
                    is Float -> value.toDouble()
                    is Double -> value
                    is Int -> value.toDouble()
                    is Long -> value.toDouble()
                    else -> throw IllegalArgumentException("$path must be a float")
                }
                PluginStaticConfigValue.FloatValue(doubleValue)
            }

            PluginStaticConfigFieldType.BoolField -> {
                if (value !is Boolean) {
                    throw IllegalArgumentException("$path must be a boolean")
                }
                PluginStaticConfigValue.BoolValue(value)
            }
        }
    }

    private fun decodeOptions(array: JSONArray?, path: String): List<PluginStaticConfigOption> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.get(index)
                add(
                    when (item) {
                        is String -> PluginStaticConfigOption.Plain(item)
                        is JSONObject -> PluginStaticConfigOption.Labeled(
                            value = readRequiredString(item, "value", "$path[$index].value"),
                            label = readRequiredString(item, "label", "$path[$index].label"),
                        )

                        else -> throw IllegalArgumentException("$path[$index] has unsupported value")
                    },
                )
            }
        }
    }

    private fun readRequiredString(json: JSONObject, key: String, path: String): String {
        val value = json.optString(key)
        if (value.isBlank()) {
            throw IllegalArgumentException("$path is required")
        }
        return value
    }

    private fun readOptionalArray(json: JSONObject, key: String): JSONArray? {
        return json.optJSONArray(key)
    }

    private fun readOptionalString(json: JSONObject, key: String): String? {
        if (!json.has(key)) return null
        val value = json.optString(key)
        return value.takeIf { it.isNotBlank() }
    }
}
