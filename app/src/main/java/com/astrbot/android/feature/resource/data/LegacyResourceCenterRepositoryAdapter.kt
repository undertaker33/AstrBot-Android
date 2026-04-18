package com.astrbot.android.feature.resource.data

import com.astrbot.android.feature.resource.data.FeatureResourceCenterRepository
import com.astrbot.android.feature.resource.domain.ResourceCenterPort
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterKind
import kotlinx.coroutines.flow.StateFlow

class LegacyResourceCenterRepositoryAdapter : ResourceCenterPort {

    override val resources: StateFlow<List<ResourceCenterItem>>
        get() = FeatureResourceCenterRepository.resources

    override val projections: StateFlow<List<ConfigResourceProjection>>
        get() = FeatureResourceCenterRepository.projections

    override fun listResources(kind: ResourceCenterKind?): List<ResourceCenterItem> =
        FeatureResourceCenterRepository.listResources(kind)

    override fun saveResource(resource: ResourceCenterItem): ResourceCenterItem =
        FeatureResourceCenterRepository.saveResource(resource)

    override fun deleteResource(resourceId: String) =
        FeatureResourceCenterRepository.deleteResource(resourceId)

    override fun setProjection(projection: ConfigResourceProjection): ConfigResourceProjection =
        FeatureResourceCenterRepository.setProjection(projection)

    override fun projectionsForConfig(configId: String): List<ConfigResourceProjection> =
        FeatureResourceCenterRepository.projectionsForConfig(configId)

    override fun compatibilitySnapshotForConfig(profile: ConfigProfile): ResourceCenterCompatibilitySnapshot =
        FeatureResourceCenterRepository.compatibilitySnapshotForConfig(profile)
}



