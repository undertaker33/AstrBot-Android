package com.astrbot.android.feature.plugin.data.catalog

import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.core.common.logging.RuntimeLogRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCatalogJsonTest {
    @Test
    fun decode_repository_source_contract_reads_nested_entries_versions_and_permissions() {
        RuntimeLogRepository.clear()
        val json = """
            {
              "sourceId": "official",
              "title": "Official Repository",
              "catalogUrl": "https://repo.example.com/catalogs/stable/index.json",
              "updatedAt": 1712123456789,
              "plugins": [
                {
                  "pluginId": "com.example.weather",
                  "title": "Weather",
                  "author": "AstrBot",
                  "repositoryUrl": "https://github.com/example/weather-plugin",
                  "description": "Shows current weather.",
                  "entrySummary": "Weather commands",
                  "scenarios": ["forecast", "alerts"],
                  "versions": [
                    {
                      "version": "1.4.0",
                      "packageUrl": "../packages/weather-1.4.0.zip",
                      "publishedAt": 1712123400000,
                      "protocolVersion": 2,
                      "minHostVersion": "0.3.0",
                      "maxHostVersion": "0.4.0",
                      "permissions": [
                        {
                          "permissionId": "network.http",
                          "title": "Network",
                          "description": "Fetches weather APIs",
                          "riskLevel": "MEDIUM",
                          "required": true
                        }
                      ],
                      "changelog": "Adds severe weather alerts."
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val source = PluginCatalogJson.decodeRepositorySource(json)
        val entry = source.plugins.single()
        val version = entry.versions.single()

        assertEquals("official", source.sourceId)
        assertEquals("Official Repository", source.title)
        assertEquals("https://repo.example.com/catalogs/stable/index.json", source.catalogUrl)
        assertEquals(1712123456789L, source.updatedAt)
        assertEquals("com.example.weather", entry.pluginId)
        assertEquals("https://github.com/example/weather-plugin", entry.repositoryUrl)
        assertEquals(listOf("forecast", "alerts"), entry.scenarios)
        assertEquals("1.4.0", version.version)
        assertEquals("https://repo.example.com/catalogs/packages/weather-1.4.0.zip", version.resolvePackageUrl(source.catalogUrl))
        assertEquals(PluginRiskLevel.MEDIUM, version.permissions.single().riskLevel)
        assertTrue(version.permissions.single().required)
        assertEquals("Adds severe weather alerts.", version.changelog)
        assertTrue(
            RuntimeLogRepository.logs.value.any {
                it.contains("Plugin market parse success") &&
                    it.contains("sourceId=official") &&
                    it.contains("plugins=1") &&
                    it.contains("versions=1")
            },
        )
    }
}
