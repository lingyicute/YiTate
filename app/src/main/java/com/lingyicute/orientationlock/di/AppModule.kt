package com.lingyicute.orientationlock.di

import android.app.NotificationManager
import android.content.Context
import com.lingyicute.orientationlock.data.OrientationRepository
import com.lingyicute.orientationlock.data.OrientationRepositoryImpl
import com.lingyicute.orientationlock.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePreferenceManager(
        @ApplicationContext context: Context
    ): PreferenceManager {
        return PreferenceManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideOrientationRepository(
        @ApplicationContext context: Context,
        preferenceManager: PreferenceManager
    ): OrientationRepository {
        return OrientationRepositoryImpl(context, preferenceManager)
    }

    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context
    ): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
} 