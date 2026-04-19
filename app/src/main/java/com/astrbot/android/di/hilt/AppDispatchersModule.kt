package com.astrbot.android.di.hilt

import com.astrbot.android.core.common.logging.RuntimeLogRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
internal object AppDispatchersModule {

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideApplicationExceptionHandler(): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            RuntimeLogRepository.append(
                "App scope uncaught exception: ${throwable.message ?: throwable.javaClass.simpleName}",
            )
        }
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        exceptionHandler: CoroutineExceptionHandler,
    ): CoroutineScope {
        return CoroutineScope(SupervisorJob() + defaultDispatcher + exceptionHandler)
    }
}
