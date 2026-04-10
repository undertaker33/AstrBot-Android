package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPackageContractSnapshot
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

private const val V2_RUNTIME_PACKAGE = "com.astrbot.android.runtime.plugin"

internal fun pluginV2Class(simpleName: String): Class<*> {
    return Class.forName("$V2_RUNTIME_PACKAGE.$simpleName")
}

internal fun newPluginV2RuntimeSession(
    installRecord: PluginInstallRecord = samplePluginV2InstallRecord(),
    sessionInstanceId: String = "session-alpha",
): Any {
    val sessionClass = pluginV2Class("PluginV2RuntimeSession")
    val constructor = sessionClass.getConstructor(PluginInstallRecord::class.java, String::class.java)
    return try {
        constructor.newInstance(installRecord, sessionInstanceId)
    } catch (error: InvocationTargetException) {
        throw (error.cause ?: error)
    }
}

internal fun enumConstantNames(simpleName: String): List<String> {
    return pluginV2Class(simpleName)
        .enumConstants
        .map { constant -> (constant as Enum<*>).name }
}

internal fun firstEnumConstant(simpleName: String): Any {
    return pluginV2Class(simpleName).enumConstants.first()
}

internal fun enumConstant(simpleName: String, constantName: String): Any {
    return pluginV2Class(simpleName)
        .enumConstants
        .first { constant -> (constant as Enum<*>).name == constantName }
}

internal fun readProperty(instance: Any, propertyName: String): Any? {
    val getterName = "get" + propertyName.replaceFirstChar { character -> character.uppercase() }
    val getter = instance.javaClass.getMethod(getterName)
    return getter.invoke(instance)
}

internal fun invokeNoArg(instance: Any, methodName: String): Any? {
    return try {
        instance.javaClass.getMethod(methodName).invoke(instance)
    } catch (error: InvocationTargetException) {
        throw (error.cause ?: error)
    }
}

internal fun invokeMethod(instance: Any, methodName: String, vararg args: Any): Any? {
    return try {
        val method = instance.javaClass.methods.first { candidate ->
            candidate.name == methodName &&
                candidate.parameterTypes.size == args.size &&
                candidate.parameterTypes.zip(args).all { (parameterType, argument) ->
                    when {
                        parameterType.isPrimitive -> false
                        parameterType.isInstance(argument) -> true
                        parameterType == Map::class.java && argument is Map<*, *> -> true
                        else -> false
                    }
                }
        }
        method.invoke(instance, *args)
    } catch (error: InvocationTargetException) {
        throw (error.cause ?: error)
    }
}

internal fun newPluginV2CompiledRegistry(): Any {
    val compiledRegistryInterface = pluginV2Class("PluginV2CompiledRegistry")
    return Proxy.newProxyInstance(
        compiledRegistryInterface.classLoader,
        arrayOf(compiledRegistryInterface),
    ) { _, method, _ ->
        when (method.name) {
            "toString" -> "PluginV2CompiledRegistryStub"
            "hashCode" -> 0
            "equals" -> false
            else -> null
        }
    }
}

internal fun samplePluginV2InstallRecord(
    pluginId: String = "com.example.v2.demo",
    version: String = "1.0.0",
): PluginInstallRecord {
    val manifest = PluginManifest(
        pluginId = pluginId,
        version = version,
        protocolVersion = 2,
        author = "AstrBot",
        title = "Plugin V2 Demo",
        description = "V2 runtime session test plugin",
        permissions = listOf(
            PluginPermissionDeclaration(
                permissionId = "net.access",
                title = "Network access",
                description = "Allows outgoing requests",
                riskLevel = PluginRiskLevel.MEDIUM,
                required = true,
            ),
        ),
        minHostVersion = "0.3.0",
        sourceType = PluginSourceType.LOCAL_FILE,
        entrySummary = "V2 runtime bootstrap",
        riskLevel = PluginRiskLevel.LOW,
    )
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = manifest,
        source = PluginSource(
            sourceType = PluginSourceType.LOCAL_FILE,
            location = "/tmp/$pluginId-$version.zip",
            importedAt = 100L,
        ),
        packageContractSnapshot = PluginPackageContractSnapshot(
            protocolVersion = 2,
            runtime = PluginRuntimeDeclarationSnapshot(
                kind = "js_quickjs",
                bootstrap = "runtime/index.js",
                apiVersion = 1,
            ),
        ),
        permissionSnapshot = manifest.permissions,
        compatibilityState = PluginCompatibilityState.evaluated(
            protocolSupported = true,
            minHostVersionSatisfied = true,
            maxHostVersionSatisfied = true,
        ),
        enabled = true,
        installedAt = 100L,
        lastUpdatedAt = 100L,
        localPackagePath = "/tmp/$pluginId-$version.zip",
        extractedDir = "/tmp/$pluginId",
    )
}

internal fun samplePluginV2ManifestOnlyInstallRecord(
    pluginId: String = "com.example.v2.manifest-only",
    version: String = "1.0.0",
): PluginInstallRecord {
    val manifest = PluginManifest(
        pluginId = pluginId,
        version = version,
        protocolVersion = 2,
        author = "AstrBot",
        title = "Plugin V2 Manifest Only",
        description = "V2 runtime session test plugin without contract snapshot",
        permissions = listOf(
            PluginPermissionDeclaration(
                permissionId = "net.access",
                title = "Network access",
                description = "Allows outgoing requests",
                riskLevel = PluginRiskLevel.MEDIUM,
                required = true,
            ),
        ),
        minHostVersion = "0.3.0",
        sourceType = PluginSourceType.LOCAL_FILE,
        entrySummary = "V2 runtime bootstrap",
        riskLevel = PluginRiskLevel.LOW,
    )
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = manifest,
        source = PluginSource(
            sourceType = PluginSourceType.LOCAL_FILE,
            location = "/tmp/$pluginId-$version.zip",
            importedAt = 100L,
        ),
        packageContractSnapshot = null,
        permissionSnapshot = manifest.permissions,
        compatibilityState = PluginCompatibilityState.evaluated(
            protocolSupported = true,
            minHostVersionSatisfied = true,
            maxHostVersionSatisfied = true,
        ),
        enabled = true,
        installedAt = 100L,
        lastUpdatedAt = 100L,
        localPackagePath = "/tmp/$pluginId-$version.zip",
        extractedDir = "/tmp/$pluginId",
    )
}

internal fun PluginInstallRecord.copyWith(
    packageContractSnapshot: PluginPackageContractSnapshot? = this.packageContractSnapshot,
    compatibilityState: PluginCompatibilityState = this.compatibilityState,
    enabled: Boolean = this.enabled,
    extractedDir: String = this.extractedDir,
): PluginInstallRecord {
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = manifestSnapshot,
        source = source,
        packageContractSnapshot = packageContractSnapshot,
        permissionSnapshot = permissionSnapshot,
        compatibilityState = compatibilityState,
        uninstallPolicy = uninstallPolicy,
        enabled = enabled,
        failureState = failureState,
        catalogSourceId = catalogSourceId,
        installedPackageUrl = installedPackageUrl,
        lastCatalogCheckAtEpochMillis = lastCatalogCheckAtEpochMillis,
        installedAt = installedAt,
        lastUpdatedAt = lastUpdatedAt,
        localPackagePath = localPackagePath,
        extractedDir = extractedDir,
    )
}
