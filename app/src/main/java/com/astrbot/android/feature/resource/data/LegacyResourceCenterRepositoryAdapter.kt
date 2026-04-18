package com.astrbot.android.feature.resource.data

import com.astrbot.android.data.ResourceCenterRepository
import com.astrbot.android.feature.resource.domain.ResourceCenterPort
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterKind
import kotlinx.coroutines.flow.StateFlow

class LegacyResourceCenterRepositoryAdapter : ResourceCenterPort {

    override val resources: StateFlow<List<ResourceCenterItem>>
        get() = ResourceCenterRepository.resources

    override val projections: StateFlow<List<ConfigResourceProjection>>
        get() = ResourceCenterRepository.projections

    override fun listResources(kind: ResourceCenterKind?): List<ResourceCenterItem> =
        ResourceCenterRepository.listResources(kind)

    override fun saveResource(resource: ResourceCenterItem): ResourceCenterItem =
        ResourceCenterRepository.saveResource(resource)

    override fun deleteResource(resourceId: String) =
        ResourceCenterRepository.deleteResource(resourceId)

    override fun setProjection(projection: ConfigResourceProjection): ConfigResourceProjection =
        ResourceCenterRepository.setProjection(projection)

    override fun projectionsForConfig(configId: String): List<ConfigResourceProjection> =
        ResourceCenterRepository.projectionsForConfig(configId)

    override fun compatibilitySnapshotForConfig(profile: ConfigProfile): ResourceCenterCompatibilitySnapshot =
        ResourceCenterRepository.compatibilitySnapshotForConfig(profile)
}
