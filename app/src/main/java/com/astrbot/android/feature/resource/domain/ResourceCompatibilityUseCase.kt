package com.astrbot.android.feature.resource.domain

import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import javax.inject.Inject

class ResourceCompatibilityUseCase @Inject constructor(
    private val resourceCenterPort: ResourceCenterPort,
) {
    fun snapshotForConfig(profile: ConfigProfile): ResourceCenterCompatibilitySnapshot =
        resourceCenterPort.compatibilitySnapshotForConfig(profile)
}
