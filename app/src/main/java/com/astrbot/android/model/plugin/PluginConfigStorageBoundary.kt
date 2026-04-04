package com.astrbot.android.model.plugin

data class PluginConfigStoreSnapshot(
    val coreValues: Map<String, PluginStaticConfigValue> = emptyMap(),
    val extensionValues: Map<String, PluginStaticConfigValue> = emptyMap(),
)

data class PluginConfigStorageBoundary(
    val coreFieldKeys: Set<String>,
    val extensionFieldKeys: Set<String> = emptySet(),
    val coreDefaults: Map<String, PluginStaticConfigValue> = emptyMap(),
) {
    init {
        val overlappingKeys = coreFieldKeys.intersect(extensionFieldKeys)
        require(overlappingKeys.isEmpty()) {
            "Extension config keys overlap core schema keys: ${overlappingKeys.sorted().joinToString(", ")}"
        }
        require(coreDefaults.keys.all { it in coreFieldKeys }) {
            val unknownDefaultKeys = coreDefaults.keys.filterNot { it in coreFieldKeys }.sorted()
            "Core defaults contain undeclared keys: ${unknownDefaultKeys.joinToString(", ")}"
        }
    }

    fun createSnapshot(
        coreValues: Map<String, PluginStaticConfigValue> = emptyMap(),
        extensionValues: Map<String, PluginStaticConfigValue> = emptyMap(),
    ): PluginConfigStoreSnapshot {
        val unknownCoreKeys = coreValues.keys.filterNot { it in coreFieldKeys }.sorted()
        require(unknownCoreKeys.isEmpty()) {
            "Core config contains undeclared keys: ${unknownCoreKeys.joinToString(", ")}"
        }
        val unknownExtensionKeys = extensionValues.keys.filterNot { it in extensionFieldKeys }.sorted()
        require(unknownExtensionKeys.isEmpty()) {
            "Extension config contains undeclared keys: ${unknownExtensionKeys.joinToString(", ")}"
        }
        val overlappingKeys = coreValues.keys.intersect(extensionValues.keys)
        require(overlappingKeys.isEmpty()) {
            "Config snapshot contains duplicated keys across core and extension: ${overlappingKeys.sorted().joinToString(", ")}"
        }
        return PluginConfigStoreSnapshot(
            coreValues = coreValues.toMap(),
            extensionValues = extensionValues.toMap(),
        )
    }
}

fun PluginStaticConfigSchema.toStorageBoundary(
    extensionFieldKeys: Set<String> = emptySet(),
): PluginConfigStorageBoundary {
    return PluginConfigStorageBoundary(
        coreFieldKeys = fields.map(PluginStaticConfigField::fieldKey).toSet(),
        extensionFieldKeys = extensionFieldKeys,
        coreDefaults = fields.mapNotNull { field ->
            field.defaultValue?.let { defaultValue -> field.fieldKey to defaultValue }
        }.toMap(),
    )
}
