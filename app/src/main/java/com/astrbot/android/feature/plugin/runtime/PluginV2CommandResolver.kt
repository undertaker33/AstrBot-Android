package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.DiagnosticSeverity
import com.astrbot.android.model.plugin.PluginV2CompilerDiagnostic

data class PluginV2CommandResolverBuildResult(
    val resolver: PluginV2CommandResolver?,
    val diagnostics: List<PluginV2CompilerDiagnostic>,
)

data class PluginV2CommandResolution(
    val bucket: PluginV2CommandBucket,
    val matchedAlias: String,
    val remainingText: String,
    val commandPath: List<String>,
)

private data class PluginV2CommandResolverIndex(
    val pathIndexByKey: Map<String, String>,
    val bucketByCanonicalKey: Map<String, PluginV2CommandBucket>,
)

private data class PluginV2CommandResolverIndexBuildResult(
    val index: PluginV2CommandResolverIndex,
    val diagnostics: List<PluginV2CompilerDiagnostic>,
)

class PluginV2CommandResolver private constructor(
    private val index: PluginV2CommandResolverIndex,
) {
    constructor(
        registries: Collection<PluginV2HandlerRegistry> = emptyList(),
    ) : this(
        buildIndex(registries).let { buildResult ->
            val firstError = buildResult.diagnostics.firstOrNull { it.severity == DiagnosticSeverity.Error }
            require(firstError == null) {
                firstError?.message ?: "Plugin v2 command resolver build failed."
            }
            buildResult.index
        },
    )

    fun resolve(workingText: String): PluginV2CommandResolution? {
        val tokens = tokenize(workingText)
        if (tokens.isEmpty()) {
            return null
        }

        for (matchedTokenCount in tokens.size downTo 1) {
            val matchedTokens = tokens.take(matchedTokenCount)
            val matchedKey = matchedTokens.toCommandPathKey()
            val canonicalKey = index.pathIndexByKey[matchedKey] ?: continue
            val bucket = index.bucketByCanonicalKey[canonicalKey] ?: continue

            return PluginV2CommandResolution(
                bucket = bucket,
                matchedAlias = matchedTokens.toCommandPathText(),
                remainingText = tokens.drop(matchedTokenCount).toCommandPathText(),
                commandPath = bucket.commandPath,
            )
        }

        return null
    }

    companion object {
        fun build(registries: Collection<PluginV2HandlerRegistry>): PluginV2CommandResolverBuildResult {
            val buildResult = buildIndex(registries)
            val firstError = buildResult.diagnostics.firstOrNull { it.severity == DiagnosticSeverity.Error }
            return if (firstError == null) {
                PluginV2CommandResolverBuildResult(
                    resolver = PluginV2CommandResolver(buildResult.index),
                    diagnostics = buildResult.diagnostics,
                )
            } else {
                PluginV2CommandResolverBuildResult(
                    resolver = null,
                    diagnostics = buildResult.diagnostics,
                )
            }
        }
    }
}

private fun buildIndex(
    registries: Collection<PluginV2HandlerRegistry>,
): PluginV2CommandResolverIndexBuildResult {
    val mergeResult = mergeCommandRegistries(registries)
    val diagnostics = mergeResult.diagnostics.toMutableList()
    val commandRegistry = mergeResult.commandRegistry
    val pathIndexByKey = linkedMapOf<String, String>()
    val bucketByCanonicalKey = linkedMapOf<String, PluginV2CommandBucket>()

    commandRegistry?.commandBuckets.orEmpty().forEach { bucket ->
        pathIndexByKey[bucket.commandPathKey] = bucket.commandPathKey
        bucket.aliasPaths.forEach { aliasPath ->
            pathIndexByKey[aliasPath.toCommandPathKey()] = bucket.commandPathKey
        }
        bucketByCanonicalKey[bucket.commandPathKey] = bucket
    }

    return PluginV2CommandResolverIndexBuildResult(
        index = PluginV2CommandResolverIndex(
            pathIndexByKey = pathIndexByKey.toMap(),
            bucketByCanonicalKey = bucketByCanonicalKey.toMap(),
        ),
        diagnostics = diagnostics.toList(),
    )
}

private fun tokenize(workingText: String): List<String> {
    return workingText.trim()
        .split(Regex("\\s+"))
        .map(String::trim)
        .filter(String::isNotBlank)
}
