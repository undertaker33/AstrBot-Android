package com.astrbot.android.feature.resource.data

import com.astrbot.android.feature.resource.domain.ResourceCenterPort
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class FeatureResourceCenterPortAdapter @Inject constructor(
    private val repository: FeatureResourceCenterRepositoryStore,
) : ResourceCenterPort {
    override val resources: StateFlow<List<ResourceCenterItem>>
        get() = repository.resources

    override val projections: StateFlow<List<ConfigResourceProjection>>
        get() = repository.projections

    override fun listResources(kind: ResourceCenterKind?): List<ResourceCenterItem> =
        repository.listResources(kind)

    override fun saveResource(resource: ResourceCenterItem): ResourceCenterItem =
        repository.saveResource(resource)

    override fun deleteResource(resourceId: String) =
        repository.deleteResource(resourceId)

    override fun setProjection(projection: ConfigResourceProjection): ConfigResourceProjection =
        repository.setProjection(projection)

    override fun projectionsForConfig(configId: String): List<ConfigResourceProjection> =
        repository.projectionsForConfig(configId)

    override fun compatibilitySnapshotForConfig(profile: ConfigProfile): ResourceCenterCompatibilitySnapshot =
        repository.compatibilitySnapshotForConfig(profile)
}
