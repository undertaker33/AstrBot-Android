package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
import com.astrbot.android.model.plugin.PluginTriggerSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface PluginRuntimeLogBus {
    val records: StateFlow<List<PluginRuntimeLogRecord>>

    fun publish(record: PluginRuntimeLogRecord)

    fun snapshot(
        limit: Int = 100,
        pluginId: String? = null,
        trigger: PluginTriggerSource? = null,
        category: PluginRuntimeLogCategory? = null,
    ): List<PluginRuntimeLogRecord>

    fun clear()

    fun clearPlugin(pluginId: String)
}

object NoOpPluginRuntimeLogBus : PluginRuntimeLogBus {
    private val emptyRecords = MutableStateFlow<List<PluginRuntimeLogRecord>>(emptyList())

    override val records: StateFlow<List<PluginRuntimeLogRecord>> = emptyRecords.asStateFlow()

    override fun publish(record: PluginRuntimeLogRecord) = Unit

    override fun snapshot(
        limit: Int,
        pluginId: String?,
        trigger: PluginTriggerSource?,
        category: PluginRuntimeLogCategory?,
    ): List<PluginRuntimeLogRecord> = emptyList()

    override fun clear() = Unit

    override fun clearPlugin(pluginId: String) = Unit
}

class InMemoryPluginRuntimeLogBus(
    private val capacity: Int = 200,
    private val clock: () -> Long = System::currentTimeMillis,
) : PluginRuntimeLogBus {
    private val buffer = ArrayDeque<PluginRuntimeLogRecord>()
    private val state = MutableStateFlow<List<PluginRuntimeLogRecord>>(emptyList())

    init {
        require(capacity > 0) { "capacity must be greater than zero." }
    }

    override val records: StateFlow<List<PluginRuntimeLogRecord>> = state.asStateFlow()

    override fun publish(record: PluginRuntimeLogRecord) {
        synchronized(buffer) {
            PluginRuntimeLogCleanupRepository.maybeAutoClear(
                pluginId = record.pluginId,
                now = clock(),
            ) {
                removePluginLocked(record.pluginId)
            }
            if (buffer.size == capacity) {
                buffer.removeFirst()
            }
            buffer.addLast(record)
            state.value = buffer.reversed()
        }
    }

    override fun snapshot(
        limit: Int,
        pluginId: String?,
        trigger: PluginTriggerSource?,
        category: PluginRuntimeLogCategory?,
    ): List<PluginRuntimeLogRecord> {
        require(limit > 0) { "limit must be greater than zero." }
        return synchronized(buffer) {
            buffer.asReversed()
                .asSequence()
                .filter { record -> pluginId == null || record.pluginId == pluginId }
                .filter { record -> trigger == null || record.trigger == trigger }
                .filter { record -> category == null || record.category == category }
                .take(limit)
                .toList()
        }
    }

    override fun clear() {
        synchronized(buffer) {
            buffer.clear()
            state.value = emptyList()
        }
    }

    override fun clearPlugin(pluginId: String) {
        synchronized(buffer) {
            removePluginLocked(pluginId)
            state.value = buffer.reversed()
        }
    }

    private fun removePluginLocked(pluginId: String) {
        val retained = buffer.filterNot { record -> record.pluginId == pluginId }
        buffer.clear()
        buffer.addAll(retained)
    }
}

object PluginRuntimeLogBusProvider {
    @Volatile
    private var busOverrideForTests: PluginRuntimeLogBus? = null

    private val sharedBus: PluginRuntimeLogBus by lazy {
        InMemoryPluginRuntimeLogBus()
    }

    fun bus(): PluginRuntimeLogBus = busOverrideForTests ?: sharedBus

    internal fun setBusOverrideForTests(bus: PluginRuntimeLogBus?) {
        busOverrideForTests = bus
    }
}

internal fun PluginRuntimeLogBus.publishBootstrapRecord(
    pluginId: String,
    pluginVersion: String,
    occurredAtEpochMillis: Long,
    level: PluginRuntimeLogLevel,
    code: String,
    message: String,
    metadata: Map<String, String> = emptyMap(),
) {
    publish(
        PluginRuntimeLogRecord(
            occurredAtEpochMillis = occurredAtEpochMillis,
            pluginId = pluginId,
            pluginVersion = pluginVersion,
            category = PluginRuntimeLogCategory.WorkspaceApi,
            level = level,
            code = code,
            message = message,
            metadata = metadata,
        ),
    )
}
