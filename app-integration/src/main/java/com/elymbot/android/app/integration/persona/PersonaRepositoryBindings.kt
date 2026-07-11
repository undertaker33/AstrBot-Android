package com.elymbot.android.app.integration.persona

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import com.elymbot.android.feature.persona.data.AndroidPersonaCoverSourceImporter
import com.elymbot.android.feature.persona.data.AndroidPersonaPresentationPreferences
import com.elymbot.android.feature.persona.data.FeaturePersonaCoverMetadataStore
import com.elymbot.android.feature.persona.data.FeaturePersonaRepositoryPortAdapter
import com.elymbot.android.feature.persona.data.PersonaCoverAssetManager
import com.elymbot.android.feature.persona.data.PersonaCoverMetadataStore
import com.elymbot.android.feature.persona.data.PersonaCoverSourceImporter
import com.elymbot.android.feature.persona.data.PersonaCoverStoragePaths
import com.elymbot.android.feature.persona.data.PersonaPresentationStorage
import com.elymbot.android.feature.persona.domain.PersonaCoverAssetPort
import com.elymbot.android.feature.persona.domain.PersonaRepositoryPort
import com.elymbot.android.feature.persona.domain.PersonaPresentationPreferencesPort
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object PersonaRepositoryBindings {
    @Provides
    @Singleton
    fun providePersonaRepositoryPort(
        adapter: FeaturePersonaRepositoryPortAdapter,
    ): PersonaRepositoryPort = adapter

    @Provides @Singleton fun providePersonaCoverStoragePaths(@ApplicationContext context: Context) =
        PersonaCoverStoragePaths(File(context.filesDir, "assets/persona-covers"))

    @Provides fun provideContentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver
    @Provides @Singleton @PersonaPresentationStorage fun providePersonaPresentationPreferencesStorage(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("persona_presentation", Context.MODE_PRIVATE)
    @Provides @Singleton fun bindCoverPort(
        paths: PersonaCoverStoragePaths,
        importer: PersonaCoverSourceImporter,
        store: PersonaCoverMetadataStore,
    ): PersonaCoverAssetPort = PersonaCoverAssetManager(paths, importer, store)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PersonaCoverBindings {
    @Binds abstract fun bindCoverImporter(importer: AndroidPersonaCoverSourceImporter): PersonaCoverSourceImporter
    @Binds abstract fun bindCoverStore(store: FeaturePersonaCoverMetadataStore): PersonaCoverMetadataStore
    @Binds abstract fun bindPresentationPreferences(implementation: AndroidPersonaPresentationPreferences): PersonaPresentationPreferencesPort
}
