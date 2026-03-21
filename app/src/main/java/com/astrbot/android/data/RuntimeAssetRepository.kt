package com.astrbot.android.data

import android.content.Context
import com.astrbot.android.R
import com.astrbot.android.model.RuntimeAssetCatalogItem
import com.astrbot.android.model.RuntimeAssetEntryState
import com.astrbot.android.model.RuntimeAssetId
import com.astrbot.android.model.RuntimeAssetState
import com.astrbot.android.runtime.BridgeCommandRunner
import com.astrbot.android.runtime.ContainerRuntimeInstaller
import com.astrbot.android.runtime.RuntimeLogRepository
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RuntimeAssetRepository {
    private val assetCatalog = listOf(
        RuntimeAssetCatalogItem(
            id = RuntimeAssetId.TTS,
            titleRes = R.string.asset_tts_title,
            subtitleRes = R.string.asset_tts_list_subtitle,
            descriptionRes = R.string.asset_tts_desc,
        ),
        RuntimeAssetCatalogItem(
            id = RuntimeAssetId.ON_DEVICE_FRAMEWORK,
            titleRes = R.string.asset_on_device_framework_title,
            subtitleRes = R.string.asset_on_device_framework_list_subtitle,
            descriptionRes = R.string.asset_on_device_framework_desc,
        ),
        RuntimeAssetCatalogItem(
            id = RuntimeAssetId.ON_DEVICE_STT,
            titleRes = R.string.asset_on_device_stt_title,
            subtitleRes = R.string.asset_on_device_stt_list_subtitle,
            descriptionRes = R.string.asset_on_device_stt_desc,
        ),
        RuntimeAssetCatalogItem(
            id = RuntimeAssetId.ON_DEVICE_TTS,
            titleRes = R.string.asset_on_device_tts_title,
            subtitleRes = R.string.asset_on_device_tts_list_subtitle,
            descriptionRes = R.string.asset_on_device_tts_desc,
        ),
        RuntimeAssetCatalogItem(
            id = RuntimeAssetId.TTS_VOICE_ASSETS,
            titleRes = R.string.asset_tts_voice_assets_title,
            subtitleRes = R.string.asset_tts_voice_assets_list_subtitle,
            descriptionRes = R.string.asset_tts_voice_assets_desc,
            actionsEnabled = false,
        ),
    )

    private val _state = MutableStateFlow(RuntimeAssetState(assets = assetCatalog.map(::buildInitialEntry)))
    val state: StateFlow<RuntimeAssetState> = _state.asStateFlow()

    fun initialize(context: Context) {
        appContext = context.applicationContext
        SherpaOnnxAssetManager.clearDeprecatedTtsAssets(context.applicationContext)
        refresh(context.applicationContext)
    }

    fun getAssetOrNull(assetId: String): RuntimeAssetEntryState? {
        val id = RuntimeAssetId.fromValue(assetId) ?: return null
        return _state.value.assets.firstOrNull { it.catalog.id == id }
    }

    suspend fun downloadAsset(context: Context, assetId: String) {
        val id = RuntimeAssetId.fromValue(assetId) ?: return
        when (id) {
            RuntimeAssetId.TTS -> downloadTtsAssets(context.applicationContext)
            RuntimeAssetId.ON_DEVICE_FRAMEWORK -> downloadOnDeviceFramework(context.applicationContext)
            RuntimeAssetId.ON_DEVICE_STT -> downloadOnDeviceStt(context.applicationContext)
            RuntimeAssetId.ON_DEVICE_TTS,
            RuntimeAssetId.TTS_VOICE_ASSETS,
            -> Unit
        }
    }

    suspend fun clearAsset(context: Context, assetId: String) {
        val id = RuntimeAssetId.fromValue(assetId) ?: return
        when (id) {
            RuntimeAssetId.TTS -> clearTtsAssets(context.applicationContext)
            RuntimeAssetId.ON_DEVICE_FRAMEWORK -> clearOnDeviceFramework(context.applicationContext)
            RuntimeAssetId.ON_DEVICE_STT -> clearOnDeviceStt(context.applicationContext)
            RuntimeAssetId.ON_DEVICE_TTS,
            RuntimeAssetId.TTS_VOICE_ASSETS,
            -> Unit
        }
    }

    suspend fun downloadOnDeviceTtsModel(context: Context, modelId: String) {
        val appContext = context.applicationContext
        val normalized = modelId.trim().lowercase()
        when (normalized) {
            "kokoro" -> downloadKokoroAssets(appContext)
        }
    }

    suspend fun clearOnDeviceTtsModel(context: Context, modelId: String) {
        val appContext = context.applicationContext
        when (modelId.trim().lowercase()) {
            "kokoro" -> clearKokoroAssets(appContext)
        }
    }

    fun ttsAssetState(context: Context): SherpaOnnxAssetManager.TtsAssetState {
        return SherpaOnnxAssetManager.ttsState(context.applicationContext)
    }

    fun refresh(context: Context) {
        refresh(context.applicationContext, detailsOverrides = emptyMap())
    }

    private suspend fun downloadTtsAssets(context: Context) {
        updateAsset(
            RuntimeAssetId.TTS,
            installed = false,
            busy = true,
            lastAction = "Downloading",
            details = "Preparing TTS conversion assets. The first download can take several minutes.",
        )
        try {
            ContainerRuntimeInstaller.ensureInstalled(context)
            val scriptFile = File(context.filesDir, "runtime/scripts/prepare_tts_assets.sh")
            val command = buildString {
                append("/system/bin/sh ")
                append(scriptFile.absolutePath.shellQuote())
                append(' ')
                append(context.filesDir.absolutePath.shellQuote())
                append(' ')
                append(context.applicationInfo.nativeLibraryDir.shellQuote())
            }
            RuntimeLogRepository.append("Runtime assets download requested: TTS conversion")
            val result = BridgeCommandRunner.execute(command)
            if (result.exitCode == 0 && ttsMarkerFile(context).exists()) {
                val details = parseDownloadSummary(result.stdout)
                    ?: "TTS conversion assets downloaded."
                refresh(context, detailsOverrides = mapOf(RuntimeAssetId.TTS to details))
                RuntimeLogRepository.append("Runtime assets download finished: $details")
                updateAsset(RuntimeAssetId.TTS, lastAction = "Downloaded")
            } else {
                val message = result.stderr.ifBlank { result.stdout }.ifBlank { "Unknown error" }
                RuntimeLogRepository.append("Runtime assets download failed: $message")
                updateAsset(
                    RuntimeAssetId.TTS,
                    busy = false,
                    lastAction = "Download failed",
                    details = message,
                )
            }
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            RuntimeLogRepository.append("Runtime assets download exception: $message")
            updateAsset(
                RuntimeAssetId.TTS,
                busy = false,
                lastAction = "Download failed",
                details = message,
            )
        }
    }

    private suspend fun clearTtsAssets(context: Context) {
        updateAsset(
            RuntimeAssetId.TTS,
            installed = true,
            busy = true,
            lastAction = "Clearing",
            details = "Removing downloaded TTS conversion assets.",
        )
        try {
            ContainerRuntimeInstaller.ensureInstalled(context)
            val scriptFile = File(context.filesDir, "runtime/scripts/clear_tts_assets.sh")
            val command = buildString {
                append("/system/bin/sh ")
                append(scriptFile.absolutePath.shellQuote())
                append(' ')
                append(context.filesDir.absolutePath.shellQuote())
                append(' ')
                append(context.applicationInfo.nativeLibraryDir.shellQuote())
            }
            RuntimeLogRepository.append("Runtime assets clear requested: TTS conversion")
            val result = BridgeCommandRunner.execute(command)
            if (result.exitCode == 0) {
                refresh(context, detailsOverrides = mapOf(RuntimeAssetId.TTS to "TTS conversion assets removed."))
                RuntimeLogRepository.append("Runtime assets cleared: TTS conversion")
                updateAsset(RuntimeAssetId.TTS, lastAction = "Cleared")
            } else {
                val message = result.stderr.ifBlank { result.stdout }.ifBlank { "Unknown error" }
                RuntimeLogRepository.append("Runtime assets clear failed: $message")
                updateAsset(
                    RuntimeAssetId.TTS,
                    busy = false,
                    lastAction = "Clear failed",
                    details = message,
                )
            }
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            RuntimeLogRepository.append("Runtime assets clear exception: $message")
            updateAsset(
                RuntimeAssetId.TTS,
                busy = false,
                lastAction = "Clear failed",
                details = message,
            )
        }
    }

    private suspend fun downloadOnDeviceFramework(context: Context) {
        updateAsset(
            RuntimeAssetId.ON_DEVICE_FRAMEWORK,
            installed = false,
            busy = true,
            lastAction = "Downloading",
            details = "Activating the bundled Sherpa ONNX Android runtime.",
        )
        try {
            SherpaOnnxAssetManager.ensureFrameworkActivated(context)
            refresh(context)
            updateAsset(RuntimeAssetId.ON_DEVICE_FRAMEWORK, lastAction = "Downloaded")
        } catch (error: Exception) {
            updateAsset(
                RuntimeAssetId.ON_DEVICE_FRAMEWORK,
                busy = false,
                lastAction = "Download failed",
                details = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private suspend fun clearOnDeviceFramework(context: Context) {
        updateAsset(
            RuntimeAssetId.ON_DEVICE_FRAMEWORK,
            installed = true,
            busy = true,
            lastAction = "Clearing",
            details = "Removing the Sherpa ONNX activation marker and local model assets.",
        )
        try {
            SherpaOnnxAssetManager.clearFramework(context)
            refresh(context)
            updateAsset(RuntimeAssetId.ON_DEVICE_FRAMEWORK, lastAction = "Cleared")
        } catch (error: Exception) {
            updateAsset(
                RuntimeAssetId.ON_DEVICE_FRAMEWORK,
                busy = false,
                lastAction = "Clear failed",
                details = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private suspend fun downloadOnDeviceStt(context: Context) {
        updateAsset(
            RuntimeAssetId.ON_DEVICE_STT,
            installed = false,
            busy = true,
            lastAction = "Downloading",
            details = "Downloading Sherpa ONNX STT assets.",
        )
        try {
            SherpaOnnxAssetManager.downloadSttAssets(context)
            refresh(context)
            updateAsset(RuntimeAssetId.ON_DEVICE_STT, lastAction = "Downloaded")
        } catch (error: Exception) {
            updateAsset(
                RuntimeAssetId.ON_DEVICE_STT,
                busy = false,
                lastAction = "Download failed",
                details = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private suspend fun clearOnDeviceStt(context: Context) {
        updateAsset(
            RuntimeAssetId.ON_DEVICE_STT,
            installed = true,
            busy = true,
            lastAction = "Clearing",
            details = "Removing Sherpa ONNX STT assets.",
        )
        try {
            SherpaOnnxAssetManager.clearSttAssets(context)
            refresh(context)
            updateAsset(RuntimeAssetId.ON_DEVICE_STT, lastAction = "Cleared")
        } catch (error: Exception) {
            updateAsset(
                RuntimeAssetId.ON_DEVICE_STT,
                busy = false,
                lastAction = "Clear failed",
                details = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private suspend fun downloadKokoroAssets(context: Context) {
        updateAsset(
            RuntimeAssetId.ON_DEVICE_TTS,
            busy = true,
            lastAction = "Downloading kokoro",
            details = "Downloading kokoro local TTS assets.",
        )
        try {
            SherpaOnnxAssetManager.downloadKokoroAssets(context)
            check(SherpaOnnxAssetManager.ttsState(context).kokoro.installed) {
                "Kokoro assets are still missing after download."
            }
            refresh(context)
            updateAsset(RuntimeAssetId.ON_DEVICE_TTS, lastAction = "Downloaded kokoro")
        } catch (error: Exception) {
            runCatching { SherpaOnnxAssetManager.clearKokoroAssets(context) }
            updateAsset(
                RuntimeAssetId.ON_DEVICE_TTS,
                busy = false,
                lastAction = "Download failed",
                details = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private suspend fun clearKokoroAssets(context: Context) {
        updateAsset(
            RuntimeAssetId.ON_DEVICE_TTS,
            busy = true,
            lastAction = "Clearing kokoro",
            details = "Removing kokoro local TTS assets.",
        )
        try {
            SherpaOnnxAssetManager.clearKokoroAssets(context)
            refresh(context)
            updateAsset(RuntimeAssetId.ON_DEVICE_TTS, lastAction = "Cleared kokoro")
        } catch (error: Exception) {
            updateAsset(
                RuntimeAssetId.ON_DEVICE_TTS,
                busy = false,
                lastAction = "Clear failed",
                details = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private fun refresh(
        context: Context,
        detailsOverrides: Map<RuntimeAssetId, String>,
    ) {
        val previous = _state.value.assets.associateBy { it.catalog.id }
        val refreshed = assetCatalog.map { catalog ->
            val oldEntry = previous[catalog.id]
            val installed = isInstalled(context, catalog.id)
            RuntimeAssetEntryState(
                catalog = catalog,
                installed = installed,
                busy = false,
                lastAction = oldEntry?.lastAction.orEmpty(),
                details = detailsOverrides[catalog.id] ?: defaultDetails(context, catalog.id, installed),
            )
        }
        _state.value = RuntimeAssetState(assets = refreshed)
    }

    private fun isInstalled(context: Context, assetId: RuntimeAssetId): Boolean = when (assetId) {
        RuntimeAssetId.TTS -> ttsMarkerFile(context).exists()
        RuntimeAssetId.ON_DEVICE_FRAMEWORK -> SherpaOnnxAssetManager.frameworkState(context).installed
        RuntimeAssetId.ON_DEVICE_STT -> SherpaOnnxAssetManager.sttState(context).installed
        RuntimeAssetId.ON_DEVICE_TTS -> SherpaOnnxAssetManager.ttsState(context).kokoro.installed
        RuntimeAssetId.TTS_VOICE_ASSETS,
        -> false
    }

    private fun defaultDetails(
        context: Context?,
        assetId: RuntimeAssetId,
        installed: Boolean,
    ): String = when (assetId) {
        RuntimeAssetId.TTS -> if (installed) {
            "TTS conversion assets are ready."
        } else {
            "TTS conversion assets are not downloaded."
        }
        RuntimeAssetId.ON_DEVICE_FRAMEWORK -> context?.let {
            SherpaOnnxAssetManager.frameworkState(it).details
        } ?: "Activate the bundled Sherpa ONNX Android runtime before downloading local STT or TTS model assets."
        RuntimeAssetId.ON_DEVICE_STT -> context?.let {
            SherpaOnnxAssetManager.sttState(it).details
        } ?: "Offline Paraformer STT assets are not downloaded."
        RuntimeAssetId.ON_DEVICE_TTS -> context?.let {
            val state = SherpaOnnxAssetManager.ttsState(it)
            "Framework: ${state.framework.details} Kokoro: ${state.kokoro.details}"
        } ?: "Framework and kokoro asset status will appear after initialization."
        RuntimeAssetId.TTS_VOICE_ASSETS -> "Cloud TTS voice asset entry is ready. Reference audio import and clone management will be connected in this iteration."
    }

    private fun buildInitialEntry(catalog: RuntimeAssetCatalogItem): RuntimeAssetEntryState = RuntimeAssetEntryState(
        catalog = catalog,
        details = defaultDetails(appContext, catalog.id, installed = false),
    )

    private fun updateAsset(
        assetId: RuntimeAssetId,
        installed: Boolean? = null,
        busy: Boolean? = null,
        lastAction: String? = null,
        details: String? = null,
    ) {
        _state.value = RuntimeAssetState(
            assets = _state.value.assets.map { entry ->
                if (entry.catalog.id != assetId) {
                    entry
                } else {
                    entry.copy(
                        installed = installed ?: entry.installed,
                        busy = busy ?: entry.busy,
                        lastAction = lastAction ?: entry.lastAction,
                        details = details ?: entry.details,
                    )
                }
            },
        )
    }

    private fun ttsMarkerFile(context: Context): File {
        return File(context.filesDir, "runtime/rootfs/ubuntu/root/.astrbot-tts-assets-ready")
    }

    private fun String.shellQuote(): String {
        return "'" + replace("'", "'\"'\"'") + "'"
    }

    private fun parseDownloadSummary(stdout: String): String? {
        val summary = stdout.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.startsWith("ASTRBOT_TTS_ASSET_SUMMARY:") }
            ?.removePrefix("ASTRBOT_TTS_ASSET_SUMMARY:")
            ?.trim()
            .orEmpty()
        if (summary.isBlank()) return null
        return "TTS conversion assets downloaded. $summary"
    }

    private var appContext: Context? = null

}
