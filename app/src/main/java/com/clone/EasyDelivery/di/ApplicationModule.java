package com.clone.EasyDelivery.di;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

/**
 * Hilt module providing general application-level dependencies
 * Includes ExecutorService, SharedPreferences, and other utilities
 */
@Module
@InstallIn(SingletonComponent.class)
public class ApplicationModule {

    /**
     * Provides a shared ExecutorService for background operations
     * Used by repositories and other components for async operations
     */
    @Provides
    @Singleton
    public ExecutorService provideExecutorService() {
        return Executors.newFixedThreadPool(4); // 4 threads for background operations
    }

    /**
     * Provides SharedPreferences for app-wide settings storage
     * Uses default SharedPreferences instance
     */
    @Provides
    @Singleton
    public SharedPreferences provideSharedPreferences(@ApplicationContext Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Provides application Context
     * Available as a dependency throughout the app
     */
    @Provides
    @Singleton
    public Context provideApplicationContext(@ApplicationContext Context context) {
        return context.getApplicationContext();
    }
}