package com.elymbot.android.feature.geofence.runtime

import com.elymbot.android.feature.geofence.domain.runtime.GeofenceCurrentLocationPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMapAvailabilityPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatusPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceActionExecutorPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceRegistrationPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceRuntimeReconciliationPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceTransitionEnqueuePort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class GeofenceRuntimeBindings {
    @Binds
    @Singleton
    abstract fun bindGeofenceActionExecutorPort(
        impl: DefaultGeofenceActionExecutor,
    ): GeofenceActionExecutorPort

    @Binds
    @Singleton
    abstract fun bindGeofencePermissionStatusPort(
        impl: AndroidGeofencePermissionStatusPort,
    ): GeofencePermissionStatusPort

    @Binds
    @Singleton
    abstract fun bindGeofenceMapAvailabilityPort(
        impl: AndroidGeofenceMapAvailabilityPort,
    ): GeofenceMapAvailabilityPort

    @Binds
    @Singleton
    abstract fun bindGeofenceCurrentLocationPort(
        impl: AndroidGeofenceCurrentLocationPort,
    ): GeofenceCurrentLocationPort

    @Binds
    @Singleton
    abstract fun bindGeofenceRegistrationBackend(
        impl: AndroidGeofenceRegistrationBackend,
    ): GeofenceRegistrationBackend

    @Binds
    @Singleton
    abstract fun bindGeofenceRegistrationPort(
        impl: HiltGeofenceRegistrationPort,
    ): GeofenceRegistrationPort

    @Binds
    @Singleton
    abstract fun bindGeofenceRuntimeReconciliationPort(
        impl: HiltGeofenceRuntimeReconciliationPort,
    ): GeofenceRuntimeReconciliationPort

    @Binds
    @Singleton
    abstract fun bindGeofenceTransitionEnqueuePort(
        impl: WorkManagerGeofenceTransitionEnqueuePort,
    ): GeofenceTransitionEnqueuePort
}
