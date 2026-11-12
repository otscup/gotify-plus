package com.gotify.client.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.gotify.client.data.api.GotifyWebSocketManager
import com.gotify.client.data.repository.*
import com.gotify.client.service.up.UnifiedPushForwarder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create()

    @Provides
    @Singleton
    fun provideWebSocketManager(
        gson: Gson,
        unifiedPushForwarder: UnifiedPushForwarder
    ): GotifyWebSocketManager =
        GotifyWebSocketManager(gson, unifiedPushForwarder)

    @Provides
    @Singleton
    fun provideServerManager(
        webSocketManager: GotifyWebSocketManager
    ): ServerManager = ServerManager(webSocketManager)
}
