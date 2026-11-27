package com.ryccoatika.contactmanager.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.perf.FirebasePerformance
import com.ryccoatika.contactmanager.data.analytics.FirebaseAnalyticsImpl
import com.ryccoatika.contactmanager.data.analytics.FirebasePerformanceTracer
import com.ryccoatika.contactmanager.domain.analytics.Analytics
import com.ryccoatika.contactmanager.domain.analytics.NoOpAnalytics
import com.ryccoatika.contactmanager.domain.analytics.NoOpPerformanceTracer
import com.ryccoatika.contactmanager.domain.analytics.PerformanceTracer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {
    /** Real Firebase sink only when google-services.json is present (FirebaseApp initialised); else no-op. */
    @Provides
    @Singleton
    fun provideAnalytics(
        @ApplicationContext context: Context,
    ): Analytics =
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            FirebaseAnalyticsImpl(FirebaseAnalytics.getInstance(context))
        } else {
            NoOpAnalytics
        }

    /** Same guard as [provideAnalytics]: Firebase Performance only when FirebaseApp is initialised. */
    @Provides
    @Singleton
    fun providePerformanceTracer(
        @ApplicationContext context: Context,
    ): PerformanceTracer =
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            FirebasePerformanceTracer(FirebasePerformance.getInstance())
        } else {
            NoOpPerformanceTracer
        }
}
