package com.archive.core.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * JSON 序列化绑定（Gson）。
 */
@Module
@InstallIn(SingletonComponent::class)
object GsonModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}
