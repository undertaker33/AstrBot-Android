package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.data.NoOpPluginDataRemover
import com.astrbot.android.data.db.PluginInstallAggregate
import com.astrbot.android.data.db.PluginInstallAggregateDao
import com.astrbot.android.data.db.PluginInstallRecordEntity
import com.astrbot.android.data.db.PluginInstallWriteModel
import com.astrbot.android.data.db.PluginManifestPermissionEntity
import com.astrbot.android.data.db.PluginManifestSnapshotEntity
import com.astrbot.android.data.db.PluginPackageContractSnapshotEntity
import com.astrbot.android.data.db.PluginPermissionSnapshotEntity
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.model.chat.MessageSessionRef
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.NoOp
import com.astrbot.android.model.plugin.PluginBotSummary
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginConfigSummary
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginExecutionResult
import com.astrbot.android.model.plugin.PluginHostAction
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginInstallState
import com.astrbot.android.model.plugin.PluginInstallStatus
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginMessageSummary
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.model.plugin.PluginTriggerMetadata
import com.astrbot.android.model.plugin.PluginTriggerSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

internal class TestClock(
    var now: Long = 1_000L,
) {
    fun advanceBy(deltaMillis: Long) {
        now += deltaMillis
    }
}

internal fun runtimePlugin(
    pluginId: String,
    version: String = "1.0.0",
    installStatus: PluginInstallStatus = PluginInstallStatus.INSTALLED,
    enabled: Boolean = true,
    compatibilityStatus: PluginCompatibilityStatus = PluginCompatibilityStatus.COMPATIBLE,
    supportedTriggers: Set<PluginTriggerSource> = setOf(PluginTriggerSource.OnCommand),
    handler: (PluginExecutionContext) -> PluginExecutionResult = { NoOp("noop") },
): PluginRuntimePlugin {
    val manifest = PluginManifest(
        pluginId = pluginId,
        version = version,
        protocolVersion = 1,
        author = "AstrBot",
        title = pluginId,
        description = "runtime test plugin",
        permissions = listOf(
            PluginPermissionDeclaration(
                permissionId = "send_message",
                title = "Send message",
                description = "Allows sending a host message",
            ),
        ),
        minHostVersion = "0.3.0",
        sourceType = PluginSourceType.LOCAL_FILE,
        entrySummary = "runtime entry",
        riskLevel = PluginRiskLevel.LOW,
    )
    val installState = PluginInstallState(
        status = installStatus,
        installedVersion = version,
        source = PluginSource(
            sourceType = PluginSourceType.LOCAL_FILE,
            location = "/plugins/$pluginId.zip",
            importedAt = 100L,
        ),
        manifestSnapshot = manifest,
        permissionSnapshot = manifest.permissions,
        compatibilityState = compatibilityStateFor(compatibilityStatus),
        enabled = enabled,
        lastInstalledAt = 100L,
        lastUpdatedAt = 100L,
        localPackagePath = "/plugins/$pluginId.zip",
        extractedDir = "/plugins/$pluginId",
    )
    return PluginRuntimePlugin(
        pluginId = pluginId,
        pluginVersion = version,
        installState = installState,
        supportedTriggers = supportedTriggers,
        handler = object : PluginRuntimeHandler {
            override fun execute(context: PluginExecutionContext): PluginExecutionResult {
                return handler(context)
            }
        },
    )
}

internal fun executionContextFor(
    plugin: PluginRuntimePlugin,
    trigger: PluginTriggerSource = PluginTriggerSource.OnCommand,
): PluginExecutionContext {
    return PluginExecutionContext(
        trigger = trigger,
        pluginId = plugin.pluginId,
        pluginVersion = plugin.pluginVersion,
        sessionRef = MessageSessionRef(
            platformId = "qq",
            messageType = MessageType.GroupMessage,
            originSessionId = "group-42",
        ),
        message = PluginMessageSummary(
            messageId = "msg-1",
            contentPreview = "/plugin run",
            senderId = "user-1",
            messageType = "command",
            attachmentCount = 0,
            timestamp = 123L,
        ),
        bot = PluginBotSummary(
            botId = "bot-1",
            displayName = "AstrBot",
            platformId = "qq",
        ),
        config = PluginConfigSummary(
            providerId = "provider-1",
            modelId = "gpt-test",
            personaId = "assistant",
        ),
        hostActionWhitelist = listOf(PluginHostAction.SendMessage),
        triggerMetadata = PluginTriggerMetadata(
            eventId = "evt-1",
            command = "/plugin",
        ),
    )
}

private fun compatibilityStateFor(status: PluginCompatibilityStatus): PluginCompatibilityState {
    return when (status) {
        PluginCompatibilityStatus.COMPATIBLE -> PluginCompatibilityState.evaluated(
            protocolSupported = true,
            minHostVersionSatisfied = true,
            maxHostVersionSatisfied = true,
        )

        PluginCompatibilityStatus.INCOMPATIBLE -> PluginCompatibilityState.evaluated(
            protocolSupported = false,
            minHostVersionSatisfied = true,
            maxHostVersionSatisfied = true,
            notes = "incompatible",
        )

        PluginCompatibilityStatus.UNKNOWN -> PluginCompatibilityState.unknown()
    }
}

internal fun installPluginRepositoryForTest(
    records: List<PluginInstallRecord> = emptyList(),
    initialized: Boolean,
    now: Long = 0L,
): PluginInstallAggregateDao {
    val dao = RuntimePluginInstallAggregateDao()
    records.forEach(dao::seed)

    val repositoryClass = PluginRepository::class.java
    repositoryClass.getDeclaredField("pluginDao").apply {
        isAccessible = true
        set(PluginRepository, dao)
    }

    @Suppress("UNCHECKED_CAST")
    val recordsField = repositoryClass.getDeclaredField("_records").apply {
        isAccessible = true
    }.get(PluginRepository) as MutableStateFlow<List<PluginInstallRecord>>
    recordsField.value = records

    val initializedField = repositoryClass.getDeclaredField("initialized").apply {
        isAccessible = true
    }.get(PluginRepository) as AtomicBoolean
    initializedField.set(initialized)

    repositoryClass.getDeclaredField("timeProvider").apply {
        isAccessible = true
        set(PluginRepository, { now })
    }
    repositoryClass.getDeclaredField("pluginDataRemover").apply {
        isAccessible = true
        set(PluginRepository, NoOpPluginDataRemover)
    }

    return dao
}

internal class RuntimePluginInstallAggregateDao : PluginInstallAggregateDao() {
    private val aggregates = linkedMapOf<String, PluginInstallAggregate>()
    private val state = MutableStateFlow<List<PluginInstallAggregate>>(emptyList())

    fun seed(record: PluginInstallRecord) {
        val writeModel = record.toWriteModel()
        aggregates[record.pluginId] = PluginInstallAggregate(
            record = writeModel.record,
            manifestSnapshots = listOf(writeModel.manifestSnapshot),
            packageContractSnapshots = listOfNotNull(writeModel.packageContractSnapshot),
            manifestPermissions = writeModel.manifestPermissions,
            permissionSnapshots = writeModel.permissionSnapshots,
        )
        publish()
    }

    override fun observePluginInstallAggregates(): Flow<List<PluginInstallAggregate>> = state

    override suspend fun listPluginInstallAggregates(): List<PluginInstallAggregate> = state.value

    override fun observePluginInstallAggregate(pluginId: String): Flow<PluginInstallAggregate?> {
        return state.map { aggregates ->
            aggregates.firstOrNull { aggregate -> aggregate.record.pluginId == pluginId }
        }
    }

    override suspend fun getPluginInstallAggregate(pluginId: String): PluginInstallAggregate? = aggregates[pluginId]

    override suspend fun upsertRecord(writeModel: PluginInstallWriteModel) {
        aggregates[writeModel.record.pluginId] = PluginInstallAggregate(
            record = writeModel.record,
            manifestSnapshots = listOf(writeModel.manifestSnapshot),
            packageContractSnapshots = listOfNotNull(writeModel.packageContractSnapshot),
            manifestPermissions = writeModel.manifestPermissions,
            permissionSnapshots = writeModel.permissionSnapshots,
        )
        publish()
    }

    override suspend fun upsertRecords(entities: List<PluginInstallRecordEntity>) = Unit

    override suspend fun upsertManifestSnapshots(entities: List<PluginManifestSnapshotEntity>) = Unit

    override suspend fun upsertPackageContractSnapshots(entities: List<PluginPackageContractSnapshotEntity>) = Unit

    override suspend fun upsertManifestPermissions(entities: List<PluginManifestPermissionEntity>) = Unit

    override suspend fun upsertPermissionSnapshots(entities: List<PluginPermissionSnapshotEntity>) = Unit

    override suspend fun deleteManifestPermissions(pluginId: String) = Unit

    override suspend fun deletePackageContractSnapshots(pluginId: String) = Unit

    override suspend fun deletePermissionSnapshots(pluginId: String) = Unit

    override suspend fun delete(pluginId: String) {
        aggregates.remove(pluginId)
        publish()
    }

    override suspend fun count(): Int = aggregates.size

    private fun publish() {
        state.value = aggregates.values.sortedWith(
            compareByDescending<PluginInstallAggregate> { aggregate -> aggregate.record.lastUpdatedAt }
                .thenBy { aggregate -> aggregate.record.pluginId },
        )
    }
}

internal fun samplePluginInstallRecord(
    pluginId: String = "com.example.demo",
    version: String = "1.0.0",
    lastUpdatedAt: Long = 0L,
    failureState: PluginFailureState = PluginFailureState.none(),
): PluginInstallRecord {
    val manifest = PluginManifest(
        pluginId = pluginId,
        version = version,
        protocolVersion = 1,
        author = "AstrBot",
        title = "Demo Plugin",
        description = "Example plugin",
        permissions = listOf(
            PluginPermissionDeclaration(
                permissionId = "net.access",
                title = "Network access",
                description = "Allows outgoing requests",
                riskLevel = PluginRiskLevel.MEDIUM,
                required = true,
            ),
        ),
        minHostVersion = "0.3.0",
        maxHostVersion = "",
        sourceType = PluginSourceType.LOCAL_FILE,
        entrySummary = "Example entry",
        riskLevel = PluginRiskLevel.LOW,
    )
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = manifest,
        source = PluginSource(
            sourceType = PluginSourceType.LOCAL_FILE,
            location = "/tmp/$version.zip",
            importedAt = lastUpdatedAt,
        ),
        permissionSnapshot = manifest.permissions,
        compatibilityState = PluginCompatibilityState.evaluated(
            protocolSupported = true,
            minHostVersionSatisfied = true,
            maxHostVersionSatisfied = true,
        ),
        uninstallPolicy = PluginUninstallPolicy.default(),
        failureState = failureState,
        enabled = true,
        installedAt = lastUpdatedAt,
        lastUpdatedAt = lastUpdatedAt,
        localPackagePath = "/tmp/$version.zip",
        extractedDir = "/tmp/$version",
    )
}
