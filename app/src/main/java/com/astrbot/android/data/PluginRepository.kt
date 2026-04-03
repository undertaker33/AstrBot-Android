package com.astrbot.android.data

import android.content.Context
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.data.db.PluginInstallAggregate
import com.astrbot.android.data.db.PluginInstallAggregateDao
import com.astrbot.android.data.db.toInstallRecord
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

interface PluginInstallStore {
    fun findByPluginId(pluginId: String): PluginInstallRecord?

    fun upsert(record: PluginInstallRecord)
}

interface PluginDataRemover {
    fun removePluginData(record: PluginInstallRecord)
}

object NoOpPluginDataRemover : PluginDataRemover {
    override fun removePluginData(record: PluginInstallRecord) = Unit
}

data class PluginUninstallResult(
    val pluginId: String,
    val policy: PluginUninstallPolicy,
    val removedData: Boolean,
)

object PluginRepository : PluginInstallStore {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)

    private var pluginDao: PluginInstallAggregateDao? = null
    private var timeProvider: () -> Long = System::currentTimeMillis
    private var pluginDataRemover: PluginDataRemover = NoOpPluginDataRemover
    private val _records = MutableStateFlow<List<PluginInstallRecord>>(emptyList())

    val records: StateFlow<List<PluginInstallRecord>> = _records.asStateFlow()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val dao = AstrBotDatabase.get(context).pluginInstallAggregateDao()
        pluginDao = dao
        _records.value = runBlocking(Dispatchers.IO) {
            dao.listPluginInstallAggregates().map(PluginInstallAggregate::toInstallRecord)
        }
        repositoryScope.launch {
            dao.observePluginInstallAggregates().collect { aggregates ->
                _records.value = aggregates.map(PluginInstallAggregate::toInstallRecord)
            }
        }
    }

    override fun findByPluginId(pluginId: String): PluginInstallRecord? {
        requireInitialized()
        _records.value.firstOrNull { record -> record.pluginId == pluginId }?.let { return it }
        return runBlocking(Dispatchers.IO) {
            requireDao().getPluginInstallAggregate(pluginId)?.toInstallRecord()
        }?.also { persistedRecord ->
            _records.value = _records.value
                .filterNot { current -> current.pluginId == persistedRecord.pluginId }
                .plus(persistedRecord)
                .sortedByDescending { current -> current.lastUpdatedAt }
        }
    }

    override fun upsert(record: PluginInstallRecord) {
        requireInitialized()
        runBlocking(Dispatchers.IO) {
            requireDao().upsertRecord(record.toWriteModel())
        }
        _records.value = _records.value
            .filterNot { current -> current.pluginId == record.pluginId }
            .plus(record)
            .sortedByDescending { current -> current.lastUpdatedAt }
    }

    fun delete(pluginId: String) {
        requireInitialized()
        runBlocking(Dispatchers.IO) {
            requireDao().delete(pluginId)
        }
        _records.value = _records.value.filterNot { record -> record.pluginId == pluginId }
    }

    fun setEnabled(pluginId: String, enabled: Boolean): PluginInstallRecord {
        requireInitialized()
        val current = requireRecord(pluginId)
        if (enabled && current.compatibilityState.status == PluginCompatibilityStatus.INCOMPATIBLE) {
            val noteSuffix = current.compatibilityState.notes
                .takeIf { it.isNotBlank() }
                ?.let { " $it" }
                .orEmpty()
            throw IllegalStateException("Cannot enable plugin due to compatibility issues.$noteSuffix")
        }
        if (current.enabled == enabled) {
            return current
        }
        return persistUpdatedRecord(
            current = current,
            enabled = enabled,
            lastUpdatedAt = timeProvider(),
        )
    }

    fun updateUninstallPolicy(
        pluginId: String,
        policy: PluginUninstallPolicy,
    ): PluginInstallRecord {
        requireInitialized()
        val current = requireRecord(pluginId)
        if (current.uninstallPolicy == policy) {
            return current
        }
        return persistUpdatedRecord(
            current = current,
            uninstallPolicy = policy,
            lastUpdatedAt = timeProvider(),
        )
    }

    fun updateFailureState(
        pluginId: String,
        failureState: PluginFailureState,
    ): PluginInstallRecord {
        requireInitialized()
        val current = requireRecord(pluginId)
        if (current.failureState == failureState) {
            return current
        }
        return persistUpdatedRecord(
            current = current,
            failureState = failureState,
            lastUpdatedAt = timeProvider(),
        )
    }

    fun clearFailureState(pluginId: String): PluginInstallRecord {
        return updateFailureState(
            pluginId = pluginId,
            failureState = PluginFailureState.none(),
        )
    }

    fun uninstall(
        pluginId: String,
        policy: PluginUninstallPolicy,
    ): PluginUninstallResult {
        requireInitialized()
        val current = requireRecord(pluginId)
        if (policy == PluginUninstallPolicy.REMOVE_DATA) {
            pluginDataRemover.removePluginData(current)
        }
        delete(pluginId)
        return PluginUninstallResult(
            pluginId = pluginId,
            policy = policy,
            removedData = policy == PluginUninstallPolicy.REMOVE_DATA,
        )
    }

    private fun requireInitialized() {
        check(initialized.get() && pluginDao != null) {
            "PluginRepository.initialize(context) must be called before use."
        }
    }

    private fun requireRecord(pluginId: String): PluginInstallRecord {
        return findByPluginId(pluginId)
            ?: error("Plugin install record not found for pluginId=$pluginId")
    }

    private fun persistUpdatedRecord(
        current: PluginInstallRecord,
        enabled: Boolean = current.enabled,
        uninstallPolicy: PluginUninstallPolicy = current.uninstallPolicy,
        failureState: PluginFailureState = current.failureState,
        lastUpdatedAt: Long = current.lastUpdatedAt,
    ): PluginInstallRecord {
        val updated = PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = current.manifestSnapshot,
            source = current.source,
            permissionSnapshot = current.permissionSnapshot,
            compatibilityState = current.compatibilityState,
            uninstallPolicy = uninstallPolicy,
            enabled = enabled,
            failureState = failureState,
            installedAt = current.installedAt,
            lastUpdatedAt = lastUpdatedAt,
            localPackagePath = current.localPackagePath,
            extractedDir = current.extractedDir,
        )
        upsert(updated)
        return updated
    }

    private fun requireDao(): PluginInstallAggregateDao = pluginDao
        ?: error("PluginRepository.initialize(context) must be called before use.")
}
