package com.astrbot.android.model.plugin

enum class PluginStaticConfigFieldType(
    val wireValue: String,
) {
    StringField("string"),
    TextField("text"),
    IntField("int"),
    FloatField("float"),
    BoolField("bool");

    companion object {
        fun fromWireValue(value: String): PluginStaticConfigFieldType? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}

enum class PluginStaticConfigSpecialType(
    val wireValue: String,
) {
    SelectProvider("select_provider"),
    SelectProviderTts("select_provider_tts"),
    SelectProviderStt("select_provider_stt"),
    SelectPersona("select_persona");

    companion object {
        fun fromWireValue(value: String): PluginStaticConfigSpecialType? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}

sealed interface PluginStaticConfigValue {
    data class StringValue(val value: String) : PluginStaticConfigValue

    data class IntValue(val value: Int) : PluginStaticConfigValue

    data class FloatValue(val value: Double) : PluginStaticConfigValue

    data class BoolValue(val value: Boolean) : PluginStaticConfigValue
}

sealed interface PluginStaticConfigOption {
    val value: String
    val label: String

    data class Plain(
        override val value: String,
    ) : PluginStaticConfigOption {
        override val label: String = value
    }

    data class Labeled(
        override val value: String,
        override val label: String,
    ) : PluginStaticConfigOption
}

data class PluginStaticConfigField(
    val fieldKey: String,
    val fieldType: PluginStaticConfigFieldType,
    val description: String = "",
    val hint: String = "",
    val obviousHint: Boolean = false,
    val defaultValue: PluginStaticConfigValue? = null,
    val invisible: Boolean = false,
    val options: List<PluginStaticConfigOption> = emptyList(),
    val specialType: PluginStaticConfigSpecialType? = null,
    val section: String = "",
)

data class PluginStaticConfigSchema(
    val fields: List<PluginStaticConfigField> = emptyList(),
) {
    init {
        val duplicateFieldKey = fields
            .groupingBy(PluginStaticConfigField::fieldKey)
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicateFieldKey == null) {
            "Duplicate static config field key: $duplicateFieldKey"
        }
    }
}
