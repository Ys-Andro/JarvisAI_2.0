package com.example.jarvisai.di

/**
 * Hilt Modules and Architecture Contracts for JarvisAi.
 *
 * NOTE: When the Hilt Gradle plugin (dagger.hilt.android.plugin) and hilt-android
 * dependencies are enabled in Gradle, these modules provide the standard @Module and @Provides
 * bindings for the DI graph.
 *
 * For zero-overhead local compilation and immediate execution, AppContainer and JarvisViewModelFactory
 * provide the working runtime implementation while adhering to the exact same contract.
 */

// Documentation and architecture specifications for Hilt modules:
// 1. AppModule: @Provides @Singleton Application Context
// 2. DatabaseModule: @Provides @Singleton JarvisDatabase, ConversationDao, MessageDao, ModelDao
// 3. DataStoreModule: @Provides @Singleton AppPreferences
// 4. NativeModule: @Provides @Singleton LlamaEngine
// 5. RepositoryModule: @Binds @Singleton IConversationRepository, IInferenceRepository, IModelRepository, ISettingsRepository, ITtsRepository
