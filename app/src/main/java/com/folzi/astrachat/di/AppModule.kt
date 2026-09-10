package com.folzi.astrachat.di

import android.content.Context
import androidx.room.Room
import com.folzi.astrachat.core.*
import com.folzi.astrachat.data.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AstraDatabase = Room.databaseBuilder(context, AstraDatabase::class.java, "astra.db")
        .addMigrations(AstraDatabase.MIGRATION_1_2, AstraDatabase.MIGRATION_2_3, AstraDatabase.MIGRATION_3_4).build()
    @Provides @Singleton
    fun gateway(): ChatGateway = ProviderClient()
    @Provides @Singleton
    fun mcpClient(): McpClient = McpClient()
}
