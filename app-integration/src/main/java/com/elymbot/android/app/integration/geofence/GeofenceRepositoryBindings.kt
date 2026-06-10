package com.elymbot.android.app.integration.geofence

import com.elymbot.android.feature.geofence.data.FeatureGeofenceRuleRepositoryPortAdapter
import com.elymbot.android.feature.geofence.domain.GeofenceRuleRepositoryPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMessageDeliveryPort
import com.elymbot.android.feature.plugin.domain.runtime.GeofenceActiveCapabilityFacade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object GeofenceRepositoryBindings {
    @Provides
    @Singleton
    fun provideGeofenceRuleRepositoryPort(
        adapter: FeatureGeofenceRuleRepositoryPortAdapter,
    ): GeofenceRuleRepositoryPort = adapter

    @Provides
    @Singleton
    fun provideGeofenceActiveCapabilityFacade(
        adapter: GeofenceActiveCapabilityFacadeAdapter,
    ): GeofenceActiveCapabilityFacade = adapter

    @Provides
    @Singleton
    fun provideGeofenceMessageDeliveryPort(
        adapter: GeofenceMessageDeliveryPortAdapter,
    ): GeofenceMessageDeliveryPort = adapter
}
