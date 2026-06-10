package com.elymbot.android.ui.config.geofence

import androidx.lifecycle.ViewModel
import com.elymbot.android.feature.geofence.domain.GeofenceRuleRepositoryPort
import com.elymbot.android.feature.geofence.domain.model.ConfigGeofenceBinding
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

@HiltViewModel
class ConfigGeofenceBindingViewModel @Inject constructor(
    private val repository: GeofenceRuleRepositoryPort,
    private val controller: ConfigGeofenceBindingController,
) : ViewModel() {
    val rules: StateFlow<List<GeofenceRule>> = repository.rules
    val bindings: StateFlow<List<ConfigGeofenceBinding>> = repository.bindings

    fun buildPresentation(
        configId: String,
        rules: List<GeofenceRule>,
        bindings: List<ConfigGeofenceBinding>,
    ): ConfigGeofenceBindingPresentation =
        controller.buildPresentation(configId = configId, rules = rules, bindings = bindings)

    suspend fun saveDraft(
        configId: String,
        draft: ConfigGeofenceBindingDraft,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        controller.saveDraft(configId = configId, draft = draft)
    }
}
