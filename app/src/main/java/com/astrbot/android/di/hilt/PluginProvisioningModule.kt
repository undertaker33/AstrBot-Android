@file:Suppress("DEPRECATION")

package com.astrbot.android.di.hilt

import android.content.Context
import com.astrbot.android.feature.plugin.data.FeaturePluginRepository
import com.astrbot.android.feature.plugin.data.PluginInstallStore
import com.astrbot.android.feature.plugin.data.PluginStoragePaths
import com.astrbot.android.feature.plugin.data.catalog.PluginCatalogSyncStore
import com.astrbot.android.feature.plugin.runtime.DownloadManagerRemotePluginPackageDownloader
import com.astrbot.android.feature.plugin.runtime.PluginPackageValidator
import com.astrbot.android.feature.plugin.runtime.RemotePluginPackageDownloader
import com.astrbot.android.feature.plugin.runtime.catalog.PluginCatalogFetcher
import com.astrbot.android.feature.plugin.runtime.catalog.UrlConnectionPluginCatalogFetcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class PluginHostVersion

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class SupportedPluginProtocolVersion

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PluginProvisioningModule {

    @Binds
    @Singleton
    abstract fun bindPluginCatalogFetcher(
        fetcher: UrlConnectionPluginCatalogFetcher,
    ): PluginCatalogFetcher

    @Binds
    @Singleton
    abstract fun bindRemotePluginPackageDownloader(
        downloader: DownloadManagerRemotePluginPackageDownloader,
    ): RemotePluginPackageDownloader

    companion object {
        @Provides
        @Singleton
        fun providePluginInstallStore(): PluginInstallStore = FeaturePluginRepository

        @Provides
        @Singleton
        fun providePluginCatalogSyncStore(): PluginCatalogSyncStore = FeaturePluginRepository

        @Provides
        @Singleton
        fun providePluginStoragePaths(
            @ApplicationContext appContext: Context,
        ): PluginStoragePaths = PluginStoragePaths.fromFilesDir(appContext.filesDir)

        @Provides
        @Singleton
        @PluginHostVersion
        fun providePluginHostVersion(
            @ApplicationContext appContext: Context,
        ): String {
            return runCatching {
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
            }.getOrNull().orEmpty().ifBlank { "0.0.0" }
        }

        @Provides
        @Singleton
        @SupportedPluginProtocolVersion
        fun provideSupportedPluginProtocolVersion(): Int = FeaturePluginRepository.SUPPORTED_PROTOCOL_VERSION

        @Provides
        @Singleton
        fun providePluginPackageValidator(
            @PluginHostVersion hostVersion: String,
            @SupportedPluginProtocolVersion supportedProtocolVersion: Int,
        ): PluginPackageValidator {
            return PluginPackageValidator(
                hostVersion = hostVersion,
                supportedProtocolVersion = supportedProtocolVersion,
            )
        }
    }
}
