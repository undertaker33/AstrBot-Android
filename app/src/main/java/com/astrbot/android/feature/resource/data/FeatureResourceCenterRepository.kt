package com.astrbot.android.feature.resource.data

import kotlinx.coroutines.flow.collect

import android.content.Context
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.data.db.resource.ResourceCenterDao
import com.astrbot.android.data.db.resource.ResourceCenterItemEntity
import com.astrbot.android.data.db.resource.toEntity
import com.astrbot.android.data.db.resource.toModel
import com.astrbot.android.data.db.toProfile
import com.astrbot.android.data.db.resource.ConfigResourceProjectionEntity
import com.astrbot.android.feature.resource.data.ResourceCenterCompatibility
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterKind
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Deprecated("Use ResourceCenterPort from feature/resource/domain. Direct access will be removed.")
object FeatureResourceCenterRepository {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)

    private var resourceCenterDao: ResourceCenterDao = ResourceCenterDaoPlaceholder.instance

    private val _resources = MutableStateFlow<List<ResourceCenterItem>>(emptyList())
    private val _projections = MutableStateFlow<List<ConfigResourceProjection>>(emptyList())

    val resources: StateFlow<List<ResourceCenterItem>> = _resources.asStateFlow()
    val projections: StateFlow<List<ConfigResourceProjection>> = _projections.asStateFlow()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val database = AstrBotDatabase.get(context)
        resourceCenterDao = database.resourceCenterDao()

        runBlocking(Dispatchers.IO) {
            seedFromLegacyConfigTablesIfNeeded(database)
            refreshFromStorage()
        }

        repositoryScope.launch {
            combine(
                resourceCenterDao.observeResources(),
                resourceCenterDao.observeProjections(),
            ) { resourceEntities, projectionEntities ->
                resourceEntities.map { it.toModel() } to projectionEntities.map { it.toModel() }
            }.collect { (resourceModels, projectionModels) ->
                _resources.value = resourceModels
                _projections.value = projectionModels
            }
        }
    }

    fun listResources(kind: ResourceCenterKind? = null): List<ResourceCenterItem> {
        if (!initialized.get()) {
            return _resources.value.filter { resource -> kind == null || resource.kind == kind }
        }
        return runBlocking(Dispatchers.IO) {
            val entities = if (kind == null) {
                resourceCenterDao.listResources()
            } else {
                resourceCenterDao.listResources(kind.name)
            }
            entities.map { it.toModel() }
        }
    }

    fun saveResource(resource: ResourceCenterItem): ResourceCenterItem {
        val normalized = resource.copy(
            resourceId = resource.resourceId.trim(),
            name = resource.name.trim(),
        )
        require(normalized.resourceId.isNotBlank()) { "resourceId must not be blank" }
        require(normalized.name.isNotBlank()) { "name must not be blank" }

        if (initialized.get()) {
            runBlocking(Dispatchers.IO) {
                resourceCenterDao.upsertResource(normalized.toEntity())
                refreshFromStorage()
            }
        } else {
            _resources.value = (_resources.value.filterNot { it.resourceId == normalized.resourceId } + normalized)
                .sortedWith(compareBy<ResourceCenterItem> { it.kind.name }.thenBy { it.name })
        }
        return normalized
    }

    fun deleteResource(resourceId: String) {
        if (initialized.get()) {
            runBlocking(Dispatchers.IO) {
                resourceCenterDao.deleteResource(resourceId)
                refreshFromStorage()
            }
        } else {
            _resources.value = _resources.value.filterNot { it.resourceId == resourceId }
            _projections.value = _projections.value.filterNot { it.resourceId == resourceId }
        }
    }

    fun setProjection(projection: ConfigResourceProjection): ConfigResourceProjection {
        val normalized = projection.copy(
            configId = projection.configId.trim(),
            resourceId = projection.resourceId.trim(),
        )
        require(normalized.configId.isNotBlank()) { "configId must not be blank" }
        require(normalized.resourceId.isNotBlank()) { "resourceId must not be blank" }

        if (initialized.get()) {
            runBlocking(Dispatchers.IO) {
                resourceCenterDao.upsertProjection(normalized.toEntity())
                refreshFromStorage()
            }
        } else {
            _projections.value = (
                _projections.value.filterNot {
                    it.configId == normalized.configId &&
                        it.kind == normalized.kind &&
                        it.resourceId == normalized.resourceId
                } + normalized
                ).sortedWith(compareBy<ConfigResourceProjection> { it.configId }.thenBy { it.kind.name }.thenBy { it.sortIndex })
        }
        return normalized
    }

    fun projectionsForConfig(configId: String): List<ConfigResourceProjection> {
        if (!initialized.get()) {
            return _projections.value
                .filter { it.configId == configId }
                .sortedWith(compareBy<ConfigResourceProjection> { it.kind.name }.thenBy { it.sortIndex })
        }
        return runBlocking(Dispatchers.IO) {
            resourceCenterDao.projectionsForConfig(configId).map { it.toModel() }
        }
    }

    fun projectionsForConfig(profile: ConfigProfile): List<ConfigResourceProjection> {
        val stored = projectionsForConfig(profile.id)
        return stored.ifEmpty {
            ResourceCenterCompatibility.projectionsFromConfigProfile(profile).projections
        }
    }

    fun compatibilitySnapshotForConfig(profile: ConfigProfile): ResourceCenterCompatibilitySnapshot {
        val storedProjections = projectionsForConfig(profile.id)
        if (storedProjections.isNotEmpty()) {
            val resourceIds = storedProjections.map { it.resourceId }.toSet()
            return ResourceCenterCompatibilitySnapshot(
                resources = _resources.value.filter { it.resourceId in resourceIds },
                projections = storedProjections,
            )
        }
        return ResourceCenterCompatibility.projectionsFromConfigProfile(profile)
    }

    private suspend fun seedFromLegacyConfigTablesIfNeeded(database: AstrBotDatabase) {
        if (resourceCenterDao.countResources() > 0 || resourceCenterDao.countProjections() > 0) return
        val snapshots = database.configAggregateDao()
            .listConfigAggregates()
            .map { aggregate -> ResourceCenterCompatibility.projectionsFromConfigProfile(aggregate.toProfile()) }
        val resourcesToSeed = snapshots.flatMap { it.resources }.distinctBy { it.resourceId }
        val projectionsToSeed = snapshots.flatMap { it.projections }
        if (resourcesToSeed.isNotEmpty()) {
            resourceCenterDao.upsertResources(resourcesToSeed.map { it.toEntity() })
        }
        if (projectionsToSeed.isNotEmpty()) {
            resourceCenterDao.upsertProjections(projectionsToSeed.map { it.toEntity() })
        }
    }

    private suspend fun refreshFromStorage() {
        _resources.value = resourceCenterDao.listResources().map { it.toModel() }
        _projections.value = resourceCenterDao.listProjections().map { it.toModel() }
    }
}

private object ResourceCenterDaoPlaceholder {
    val instance: PlaceholderDao = PlaceholderDao()

    class PlaceholderDao : ResourceCenterDao {
        override fun observeResources(): Flow<List<ResourceCenterItemEntity>> = flowOf(emptyList())
        override fun observeProjections(): Flow<List<ConfigResourceProjectionEntity>> = flowOf(emptyList())
        override suspend fun listResources(): List<ResourceCenterItemEntity> = emptyList()
        override suspend fun listResources(kind: String): List<ResourceCenterItemEntity> = emptyList()
        override suspend fun listProjections(): List<ConfigResourceProjectionEntity> = emptyList()
        override suspend fun projectionsForConfig(configId: String): List<ConfigResourceProjectionEntity> = emptyList()
        override suspend fun countResources(): Int = 0
        override suspend fun countProjections(): Int = 0
        override suspend fun upsertResource(entity: ResourceCenterItemEntity) = Unit
        override suspend fun upsertResources(entities: List<ResourceCenterItemEntity>) = Unit
        override suspend fun deleteResource(resourceId: String) = Unit
        override suspend fun upsertProjection(entity: ConfigResourceProjectionEntity) = Unit
        override suspend fun upsertProjections(entities: List<ConfigResourceProjectionEntity>) = Unit
    }
}

