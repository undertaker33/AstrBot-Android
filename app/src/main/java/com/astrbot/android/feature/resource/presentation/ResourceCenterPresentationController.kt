package com.astrbot.android.feature.resource.presentation

import com.astrbot.android.feature.resource.domain.ResourceCenterPort
import com.astrbot.android.feature.resource.domain.ResourceCompatibilityUseCase
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterKind

class ResourceCenterPresentationController(
    private val port: ResourceCenterPort,
    private val compatibilityUseCase: ResourceCompatibilityUseCase,
) {
    fun listResources(kind: ResourceCenterKind? = null): List<ResourceCenterItem> =
        port.listResources(kind)

    fun saveResource(resource: ResourceCenterItem): ResourceCenterItem =
        port.saveResource(resource)

    fun deleteResource(resourceId: String) =
        port.deleteResource(resourceId)

    fun setProjection(projection: ConfigResourceProjection): ConfigResourceProjection =
        port.setProjection(projection)

    fun projectionsForConfig(configId: String): List<ConfigResourceProjection> =
        port.projectionsForConfig(configId)

    fun compatibilitySnapshotForConfig(profile: ConfigProfile): ResourceCenterCompatibilitySnapshot =
        compatibilityUseCase.snapshotForConfig(profile)
}
