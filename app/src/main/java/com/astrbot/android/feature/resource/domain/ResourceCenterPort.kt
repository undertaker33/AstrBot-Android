package com.astrbot.android.feature.resource.domain

import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterKind
import kotlinx.coroutines.flow.StateFlow

interface ResourceCenterPort {
    val resources: StateFlow<List<ResourceCenterItem>>
    val projections: StateFlow<List<ConfigResourceProjection>>
    fun listResources(kind: ResourceCenterKind? = null): List<ResourceCenterItem>
    fun saveResource(resource: ResourceCenterItem): ResourceCenterItem
    fun deleteResource(resourceId: String)
    fun setProjection(projection: ConfigResourceProjection): ConfigResourceProjection
    fun projectionsForConfig(configId: String): List<ConfigResourceProjection>
    fun compatibilitySnapshotForConfig(profile: ConfigProfile): ResourceCenterCompatibilitySnapshot
}
