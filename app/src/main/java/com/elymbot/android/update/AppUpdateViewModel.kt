package com.elymbot.android.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elymbot.android.core.common.logging.RuntimeLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
internal class AppUpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
    private val buildInfo: AppUpdateBuildInfo,
    private val runtimeLogger: RuntimeLogger,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private val _installIntents = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val installIntents: SharedFlow<Intent> = _installIntents.asSharedFlow()

    private var checkedThisActivity = false

    fun checkForUpdateOnce() {
        if (checkedThisActivity) return
        checkedThisActivity = true
        viewModelScope.launch {
            runCatching {
                repository.findUpdateCandidate(
                    currentVersionName = buildInfo.versionName,
                    currentVersionCode = buildInfo.versionCode,
                    track = buildInfo.track,
                )
            }.onSuccess { candidate ->
                if (candidate != null) {
                    _uiState.value = AppUpdateUiState(
                        dialog = AppUpdateDialogState.Available(candidate),
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                runtimeLogger.append("App update check failed: ${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    fun updateNow(candidate: AppUpdateCandidate) {
        viewModelScope.launch {
            _uiState.value = AppUpdateUiState(
                dialog = AppUpdateDialogState.Downloading(
                    candidate = candidate,
                    progress = AppUpdateDownloadProgress(
                        downloadedBytes = 0L,
                        totalBytes = candidate.asset.sizeBytes,
                        bytesPerSecond = 0L,
                    ),
                ),
            )
            runCatching {
                repository.downloadUpdate(candidate) { progress ->
                    _uiState.value = AppUpdateUiState(
                        dialog = AppUpdateDialogState.Downloading(candidate, progress),
                    )
                }
            }.onSuccess { packageFile ->
                _uiState.value = AppUpdateUiState(
                    dialog = AppUpdateDialogState.ReadyToInstall(packageFile),
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                runtimeLogger.append("App update download failed: ${error.message ?: error.javaClass.simpleName}")
                _uiState.value = AppUpdateUiState(
                    dialog = AppUpdateDialogState.Failed(
                        candidate = candidate,
                        message = error.message ?: error.javaClass.simpleName,
                    ),
                )
            }
        }
    }

    fun snooze(candidate: AppUpdateCandidate) {
        viewModelScope.launch {
            runCatching {
                repository.snooze(candidate)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                runtimeLogger.append("App update snooze failed: ${error.message ?: error.javaClass.simpleName}")
            }
            _uiState.value = AppUpdateUiState()
        }
    }

    fun ignore(candidate: AppUpdateCandidate) {
        viewModelScope.launch {
            runCatching {
                repository.ignore(candidate)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                runtimeLogger.append("App update ignore failed: ${error.message ?: error.javaClass.simpleName}")
            }
            _uiState.value = AppUpdateUiState()
        }
    }

    fun install(packageFile: AppUpdateDownloadedPackage) {
        runCatching {
            repository.buildInstallIntent(packageFile.file)
        }.onSuccess { intent ->
            _installIntents.tryEmit(intent)
        }.onFailure { error ->
            runtimeLogger.append("App update installer intent failed: ${error.message ?: error.javaClass.simpleName}")
            _uiState.value = AppUpdateUiState(
                dialog = AppUpdateDialogState.Failed(
                    candidate = packageFile.candidate,
                    message = error.message ?: error.javaClass.simpleName,
                ),
            )
        }
    }
}
