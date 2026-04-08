package com.astrbot.android.runtime.plugin.samples

import com.astrbot.android.MainDispatcherRule
import com.astrbot.android.data.PluginRepository
import com.astrbot.android.data.plugin.PluginStoragePaths
import com.astrbot.android.di.PluginViewModelDependencies
import com.astrbot.android.model.plugin.ExternalPluginExecutionContractJson
import com.astrbot.android.model.plugin.ExternalPluginMediaSourceResolver
import com.astrbot.android.model.plugin.ExternalPluginRuntimeKind
import com.astrbot.android.model.plugin.MediaResult
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginExecutionResult
import com.astrbot.android.model.plugin.PluginHostAction
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginInstallIntentResult
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginMediaItem
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginSettingsSection
import com.astrbot.android.model.plugin.PluginHostWorkspaceSnapshot
import com.astrbot.android.model.plugin.SettingsUiRequest
import com.astrbot.android.model.plugin.TextInputSettingField
import com.astrbot.android.model.plugin.NoOp
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.runtime.plugin.ExternalPluginBridgeRuntime
import com.astrbot.android.runtime.plugin.ExternalPluginRuntimeBinder
import com.astrbot.android.runtime.plugin.ExternalPluginScriptExecutionRequest
import com.astrbot.android.runtime.plugin.ExternalPluginScriptExecutor
import com.astrbot.android.runtime.plugin.PluginExecutionEngine
import com.astrbot.android.runtime.plugin.PluginFailureGuard
import com.astrbot.android.runtime.plugin.PluginInstaller
import com.astrbot.android.runtime.plugin.PluginPackageValidator
import com.astrbot.android.runtime.plugin.PluginRuntimeDispatcher
import com.astrbot.android.runtime.plugin.PluginRuntimePlugin
import com.astrbot.android.runtime.plugin.PluginRuntimeRegistry
import com.astrbot.android.runtime.plugin.RemotePluginPackageDownloader
import com.astrbot.android.runtime.plugin.runtimePlugin
import com.astrbot.android.runtime.plugin.samplePluginInstallRecord
import com.astrbot.android.ui.viewmodel.PluginSchemaUiState
import com.astrbot.android.ui.viewmodel.PluginViewModel
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemeManagerSampleRuntimeAndAdminPathTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @org.junit.After
    fun tearDown() {
        PluginRuntimeRegistry.reset()
    }

    @Test
    fun sample_assets_can_drive_a_runtime_main_path_via_registered_handler() = runTest(dispatcher) {
        val tempDir = Files.createTempDirectory("meme-manager-sample-runtime").toFile()
        try {
            resetPluginRepositoryForSampleTest(initialized = true)
            val installed = installSample(tempDir = tempDir, version = "1.0.0")
            val extractedDir = File(installed.extractedDir)

            PluginRuntimeRegistry.registerProvider {
                listOf(
                    PluginRuntimePlugin(
                        pluginId = SAMPLE_PLUGIN_ID,
                        pluginVersion = installed.installedVersion,
                        installState = installed.toInstallStateForRuntime(),
                        supportedTriggers = setOf(PluginTriggerSource.OnCommand),
                        handler = object : com.astrbot.android.runtime.plugin.PluginRuntimeHandler {
                            override fun execute(context: PluginExecutionContext): PluginExecutionResult {
                                val item = resolveSampleMemeItem(
                                    extractedDir = extractedDir,
                                    context = context,
                                ) ?: return NoOp("No packaged meme matched the command")
                                return MediaResult(items = listOf(item))
                            }
                        },
                    ),
                )
            }

            val failureGuard = PluginFailureGuard()
            val engine = PluginExecutionEngine(
                dispatcher = PluginRuntimeDispatcher(failureGuard),
                failureGuard = failureGuard,
            )
            val plugin = PluginRuntimeRegistry.plugins().single { it.pluginId == SAMPLE_PLUGIN_ID }
            val batch = engine.executeBatch(
                trigger = PluginTriggerSource.OnCommand,
                plugins = listOf(plugin),
                contextFactory = ::sampleExecutionContext,
            )

            assertEquals(1, batch.outcomes.size)
            assertTrue(batch.outcomes.single().succeeded)
            val result = batch.outcomes.single().result as MediaResult
            assertEquals(1, result.items.size)
            val item = result.items.single()
            assertTrue(item.source.endsWith("resources\\memes\\angry\\9D03FF21BB828C2AF9CCC7FCCB1E25B3.jpg") || item.source.endsWith("resources/memes/angry/9D03FF21BB828C2AF9CCC7FCCB1E25B3.jpg"))
            assertTrue("Resolved media file should exist: ${item.source}", File(item.source).exists())
            assertEquals("image/jpeg", item.mimeType)
            assertTrue(item.altText.contains("angry"))
        } finally {
            resetPluginRepositoryForSampleTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun sample_assets_can_drive_a_basic_admin_management_path_via_plugin_entry_click_schema() = runTest(dispatcher) {
        val tempDir = Files.createTempDirectory("meme-manager-sample-admin").toFile()
        try {
            resetPluginRepositoryForSampleTest(initialized = true)
            val installed = installSample(tempDir = tempDir, version = "1.0.0")
            val extractedDir = File(installed.extractedDir)

            PluginRuntimeRegistry.registerProvider {
                listOf(
                    PluginRuntimePlugin(
                        pluginId = SAMPLE_PLUGIN_ID,
                        pluginVersion = installed.installedVersion,
                        installState = installed.toInstallStateForRuntime(),
                        supportedTriggers = setOf(PluginTriggerSource.OnPluginEntryClick),
                        handler = object : com.astrbot.android.runtime.plugin.PluginRuntimeHandler {
                            override fun execute(context: PluginExecutionContext): PluginExecutionResult {
                                // Minimal "admin" schema; reads a resource marker to prove fixture wiring.
                                val marker = File(extractedDir, "resources/admin/seed.txt")
                                val seed = marker.readText(Charsets.UTF_8).trim().ifBlank { "missing" }
                                return SettingsUiRequest(
                                    schema = PluginSettingsSchema(
                                        title = "Meme Manager",
                                        sections = listOf(
                                            PluginSettingsSection(
                                                sectionId = "admin",
                                                title = "Admin",
                                                fields = listOf(
                                                    TextInputSettingField(
                                                        fieldId = "defaultCategory",
                                                        label = "Default category",
                                                        placeholder = "funny",
                                                        defaultValue = seed,
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                )
                            }
                        },
                    ),
                )
            }

            val deps = MinimalPluginViewModelDeps(
                records = listOf(installed),
            )
            val viewModel = PluginViewModel(deps)
            advanceUntilIdle()

            viewModel.selectPlugin(SAMPLE_PLUGIN_ID)
            advanceUntilIdle()

            val state = viewModel.uiState.value.schemaUiState
            assertTrue(state is PluginSchemaUiState.Settings)
            val schema = (state as PluginSchemaUiState.Settings).schema
            assertEquals("Meme Manager", schema.title)
            assertEquals("admin", schema.sections.single().sectionId)
        } finally {
            resetPluginRepositoryForSampleTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun sample_external_bridge_can_execute_media_and_admin_paths_via_quickjs_contract() = runTest(dispatcher) {
        val tempDir = Files.createTempDirectory("meme-manager-sample-external").toFile()
        try {
            resetPluginRepositoryForSampleTest(initialized = true)
            val installed = installSample(tempDir = tempDir, version = "1.1.0")
            val binding = ExternalPluginRuntimeBinder().bind(installed)
            assertTrue(File(installed.extractedDir, "runtime/index.js").exists())
            assertFalse(File(installed.extractedDir, "runtime/entry.py").exists())
            val contract = ExternalPluginExecutionContractJson.decodeContract(
                JSONObject(File(installed.extractedDir, "android-execution.json").readText(Charsets.UTF_8)),
            )
            assertEquals(ExternalPluginRuntimeKind.JsQuickJs, contract.entryPoint.runtimeKind)
            assertEquals("runtime/index.js", contract.entryPoint.path)
            assertEquals("handleEvent", contract.entryPoint.entrySymbol)
            val scriptRequests = mutableListOf<ExternalPluginScriptExecutionRequest>()
            val runtime = ExternalPluginBridgeRuntime(
                scriptExecutor = object : ExternalPluginScriptExecutor {
                    override fun execute(request: ExternalPluginScriptExecutionRequest): String {
                        scriptRequests += request
                        val script = File(request.scriptAbsolutePath).readText(Charsets.UTF_8)
                        assertTrue(script.contains("function handleEvent"))
                        assertTrue(script.contains("plugin://package/resources/memes/"))
                        assertTrue(script.contains("plugin://workspace/imports/"))
                        assertTrue(script.contains("open_host_page"))
                        return when (scriptRequests.size) {
                            1 -> """{"resultType":"media","items":[{"source":"plugin://package/resources/memes/angry/9D03FF21BB828C2AF9CCC7FCCB1E25B3.jpg","mimeType":"image/jpeg","altText":"angry meme"}]}"""
                            2 -> """{"resultType":"settings_ui","schema":{"title":"Meme Manager","sections":[{"sectionId":"admin","title":"Admin","fields":[{"fieldType":"text_input","fieldId":"defaultCategory","label":"Default category","defaultValue":"angry"}]}]}}"""
                            else -> error("Unexpected QuickJS script request #${scriptRequests.size}")
                        }
                    }
                },
            )

            val mediaResult = runtime.execute(
                binding = binding,
                context = sampleExecutionContext(
                    runtimePlugin(
                        pluginId = installed.pluginId,
                        version = installed.installedVersion,
                        supportedTriggers = setOf(PluginTriggerSource.OnCommand),
                    ),
                ),
            )
            assertTrue(mediaResult is MediaResult)
            val mediaItem = (mediaResult as MediaResult).items.single()
            assertTrue(mediaItem.source.startsWith("plugin://package/resources/memes/angry/"))
            val resolvedMedia = ExternalPluginMediaSourceResolver.resolve(
                item = mediaItem,
                extractedDir = installed.extractedDir,
            )
            assertTrue(File(resolvedMedia.resolvedSource).exists())

            val adminResult = runtime.execute(
                binding = binding,
                context = sampleEntryClickContext(installed.pluginId, installed.installedVersion),
            )
            assertTrue(adminResult is SettingsUiRequest)
            val schema = (adminResult as SettingsUiRequest).schema
            assertEquals("Meme Manager", schema.title)
            assertEquals("admin", schema.sections.single().sectionId)
            assertEquals(2, scriptRequests.size)
            assertTrue(scriptRequests.all { request ->
                request.scriptAbsolutePath.endsWith("runtime\\index.js") ||
                    request.scriptAbsolutePath.endsWith("runtime/index.js")
            })
            assertTrue(scriptRequests.all { request -> request.entrySymbol == "handleEvent" })
        } finally {
            resetPluginRepositoryForSampleTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }

    private suspend fun installSample(tempDir: File, version: String): PluginInstallRecord {
        val zip = SampleAssetPaths.packageZip(version)
        assertTrue("Missing sample zip: ${zip.absolutePath}", zip.exists())
        val installer = PluginInstaller(
            validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 1),
            storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
            installStore = PluginRepository,
            remotePackageDownloader = RemotePluginPackageDownloader { _, destinationFile, _ ->
                zip.copyTo(destinationFile, overwrite = true)
            },
            clock = { 2000L },
        )
        return installer.install(
            PluginInstallIntent.catalogVersion(
                pluginId = SAMPLE_PLUGIN_ID,
                version = version,
                packageUrl = "https://samples.astrbot.local/catalog/packages/meme-manager-$version.zip",
                catalogSourceId = "sample",
            ),
        )
    }
}

private const val SAMPLE_PLUGIN_ID = "com.astrbot.samples.meme_manager"

private fun sampleExecutionContext(plugin: PluginRuntimePlugin): PluginExecutionContext {
    return PluginExecutionContext(
        trigger = PluginTriggerSource.OnCommand,
        pluginId = plugin.pluginId,
        pluginVersion = plugin.pluginVersion,
        sessionRef = com.astrbot.android.model.chat.MessageSessionRef(
            platformId = "host",
            messageType = com.astrbot.android.model.chat.MessageType.OtherMessage,
            originSessionId = "sample",
        ),
        message = com.astrbot.android.model.plugin.PluginMessageSummary(
            messageId = "msg-1",
            contentPreview = "/meme angry",
            messageType = "command",
        ),
        bot = com.astrbot.android.model.plugin.PluginBotSummary(
            botId = "host",
            displayName = "AstrBot",
            platformId = "host",
        ),
        config = com.astrbot.android.model.plugin.PluginConfigSummary(),
        permissionSnapshot = emptyList(),
        hostActionWhitelist = listOf(PluginHostAction.SendMessage),
        triggerMetadata = com.astrbot.android.model.plugin.PluginTriggerMetadata(command = "/meme"),
    )
}

private fun sampleEntryClickContext(
    pluginId: String,
    version: String,
): PluginExecutionContext {
    return sampleExecutionContext(
        PluginRuntimePlugin(
            pluginId = pluginId,
            pluginVersion = version,
            installState = samplePluginInstallRecord(
                pluginId = pluginId,
                version = version,
                lastUpdatedAt = 100L,
            ).toInstallStateForRuntime(),
            supportedTriggers = setOf(PluginTriggerSource.OnPluginEntryClick),
            handler = object : com.astrbot.android.runtime.plugin.PluginRuntimeHandler {
                override fun execute(context: PluginExecutionContext): PluginExecutionResult = NoOp("unused")
            },
        ),
    ).copy(
        trigger = PluginTriggerSource.OnPluginEntryClick,
        message = com.astrbot.android.model.plugin.PluginMessageSummary(
            messageId = "entry-click",
            contentPreview = "",
            messageType = "entry",
        ),
        triggerMetadata = com.astrbot.android.model.plugin.PluginTriggerMetadata(
            entryPoint = "plugin-detail",
        ),
    )
}

private fun resolveSampleMemeItem(
    extractedDir: File,
    context: PluginExecutionContext,
): PluginMediaItem? {
    val indexFile = File(extractedDir, "resources/memes/index.json")
    if (!indexFile.exists()) {
        return null
    }

    val index = JSONObject(indexFile.readText(Charsets.UTF_8))
    val command = context.triggerMetadata.command.ifBlank {
        context.message.contentPreview.substringBefore(' ')
    }
    val commandArgument = context.message.contentPreview
        .removePrefix(command)
        .trim()
        .substringBefore(' ')
        .trim()

    val matchedTrigger = (0 until index.optJSONArray("triggers").orEmpty().length())
        .map { triggerIndex -> index.getJSONArray("triggers").getJSONObject(triggerIndex) }
        .firstOrNull { trigger -> trigger.optString("keyword") == command }
        ?: return null

    val categoryId = when {
        commandArgument.isNotBlank() && matchedTrigger.optBoolean("matchArgumentAsCategory", false) -> commandArgument
        matchedTrigger.has("category") -> matchedTrigger.getString("category")
        matchedTrigger.has("defaultCategory") -> matchedTrigger.getString("defaultCategory")
        else -> return null
    }

    val categories = index.optJSONArray("categories") ?: return null
    val matchedCategory = (0 until categories.length())
        .map { categoryIndex -> categories.getJSONObject(categoryIndex) }
        .firstOrNull { category -> category.optString("id") == categoryId }
        ?: return null

    val item = matchedCategory.optJSONArray("items")
        ?.takeIf { it.length() > 0 }
        ?.getJSONObject(0)
        ?: return null
    val relativePath = item.getString("file")
    val sourceFile = File(extractedDir, relativePath)
    return PluginMediaItem(
        source = sourceFile.absolutePath,
        mimeType = item.optString("mimeType"),
        altText = item.optString("altText", "${matchedCategory.optString("id")} meme"),
    )
}

private fun org.json.JSONArray?.orEmpty(): org.json.JSONArray {
    return this ?: org.json.JSONArray()
}

private class MinimalPluginViewModelDeps(
    records: List<PluginInstallRecord>,
) : PluginViewModelDependencies {
    private val recordsFlow = MutableStateFlow(records)
    private val sourcesFlow = MutableStateFlow<List<PluginRepositorySource>>(emptyList())
    private val catalogEntriesFlow = MutableStateFlow<List<PluginCatalogEntryRecord>>(emptyList())

    override val records: StateFlow<List<PluginInstallRecord>> = recordsFlow
    override val repositorySources: StateFlow<List<PluginRepositorySource>> = sourcesFlow
    override val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>> = catalogEntriesFlow

    override suspend fun handleInstallIntent(
        intent: PluginInstallIntent,
        onDownloadProgress: (com.astrbot.android.model.plugin.PluginDownloadProgress) -> Unit,
    ): PluginInstallIntentResult {
        error("Not needed for this sample test: $intent")
    }

    override suspend fun installFromLocalPackageUri(uri: String): PluginInstallIntentResult {
        return PluginInstallIntentResult.Ignored
    }

    override suspend fun ensureOfficialMarketCatalogSubscribed(): com.astrbot.android.model.plugin.PluginCatalogSyncState {
        return com.astrbot.android.model.plugin.PluginCatalogSyncState(
            sourceId = "official-market",
            lastSyncAtEpochMillis = 0L,
            lastSyncStatus = com.astrbot.android.model.plugin.PluginCatalogSyncStatus.SUCCESS,
        )
    }

    override suspend fun refreshMarketCatalog(): List<com.astrbot.android.model.plugin.PluginCatalogSyncState> {
        return emptyList()
    }

    override fun getUpdateAvailability(pluginId: String) = null

    override suspend fun upgradePlugin(update: com.astrbot.android.model.plugin.PluginUpdateAvailability): PluginInstallRecord {
        error("Not needed for this sample test")
    }

    override fun getPluginStaticConfigSchema(pluginId: String): com.astrbot.android.model.plugin.PluginStaticConfigSchema? {
        return null
    }

    override fun resolvePluginConfigSnapshot(
        pluginId: String,
        boundary: com.astrbot.android.model.plugin.PluginConfigStorageBoundary,
    ): com.astrbot.android.model.plugin.PluginConfigStoreSnapshot {
        return boundary.createSnapshot()
    }

    override fun savePluginCoreConfig(
        pluginId: String,
        boundary: com.astrbot.android.model.plugin.PluginConfigStorageBoundary,
        coreValues: Map<String, com.astrbot.android.model.plugin.PluginStaticConfigValue>,
    ): com.astrbot.android.model.plugin.PluginConfigStoreSnapshot {
        return boundary.createSnapshot(coreValues = coreValues)
    }

    override fun savePluginExtensionConfig(
        pluginId: String,
        boundary: com.astrbot.android.model.plugin.PluginConfigStorageBoundary,
        extensionValues: Map<String, com.astrbot.android.model.plugin.PluginStaticConfigValue>,
    ): com.astrbot.android.model.plugin.PluginConfigStoreSnapshot {
        return boundary.createSnapshot(extensionValues = extensionValues)
    }

    override fun resolvePluginWorkspaceSnapshot(pluginId: String): PluginHostWorkspaceSnapshot {
        return PluginHostWorkspaceSnapshot()
    }

    override suspend fun importPluginWorkspaceFile(
        pluginId: String,
        uri: String,
    ): PluginHostWorkspaceSnapshot {
        return PluginHostWorkspaceSnapshot()
    }

    override fun deletePluginWorkspaceFile(
        pluginId: String,
        relativePath: String,
    ): PluginHostWorkspaceSnapshot {
        return PluginHostWorkspaceSnapshot()
    }

    override fun clearPluginFailureState(pluginId: String): PluginInstallRecord {
        val current = recordsFlow.value.first { it.pluginId == pluginId }
        val updated = PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = current.manifestSnapshot,
            source = current.source,
            permissionSnapshot = current.permissionSnapshot,
            compatibilityState = current.compatibilityState,
            uninstallPolicy = current.uninstallPolicy,
            enabled = current.enabled,
            failureState = com.astrbot.android.model.plugin.PluginFailureState.none(),
            catalogSourceId = current.catalogSourceId,
            installedPackageUrl = current.installedPackageUrl,
            lastCatalogCheckAtEpochMillis = current.lastCatalogCheckAtEpochMillis,
            installedAt = current.installedAt,
            lastUpdatedAt = current.lastUpdatedAt,
            localPackagePath = current.localPackagePath,
            extractedDir = current.extractedDir,
        )
        recordsFlow.value = recordsFlow.value.map { if (it.pluginId == pluginId) updated else it }
        return updated
    }

    override fun setPluginEnabled(pluginId: String, enabled: Boolean): PluginInstallRecord {
        val current = recordsFlow.value.first { it.pluginId == pluginId }
        val updated = PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = current.manifestSnapshot,
            source = current.source,
            permissionSnapshot = current.permissionSnapshot,
            compatibilityState = current.compatibilityState,
            uninstallPolicy = current.uninstallPolicy,
            enabled = enabled,
            failureState = current.failureState,
            catalogSourceId = current.catalogSourceId,
            installedPackageUrl = current.installedPackageUrl,
            lastCatalogCheckAtEpochMillis = current.lastCatalogCheckAtEpochMillis,
            installedAt = current.installedAt,
            lastUpdatedAt = current.lastUpdatedAt,
            localPackagePath = current.localPackagePath,
            extractedDir = current.extractedDir,
        )
        recordsFlow.value = recordsFlow.value.map { if (it.pluginId == pluginId) updated else it }
        return updated
    }

    override fun uninstallPlugin(pluginId: String, policy: com.astrbot.android.model.plugin.PluginUninstallPolicy): com.astrbot.android.data.PluginUninstallResult {
        error("Not needed for this sample test")
    }
}

private fun PluginInstallRecord.toInstallStateForRuntime(): com.astrbot.android.model.plugin.PluginInstallState {
    return com.astrbot.android.model.plugin.PluginInstallState(
        status = com.astrbot.android.model.plugin.PluginInstallStatus.INSTALLED,
        installedVersion = installedVersion,
        source = source,
        manifestSnapshot = manifestSnapshot,
        permissionSnapshot = permissionSnapshot,
        compatibilityState = compatibilityState,
        // Runtime main path requires an activated plugin; installer defaults to disabled,
        // so tests explicitly opt-in without changing production defaults.
        enabled = true,
        lastInstalledAt = installedAt,
        lastUpdatedAt = lastUpdatedAt,
        localPackagePath = localPackagePath,
        extractedDir = extractedDir,
    )
}

