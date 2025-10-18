package com.clone.EasyDelivery.util;

import android.content.Context;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

/**
 * Hilt module providing utility dependencies
 * Centralizes utility service provisioning for dependency injection
 */
@Module
@InstallIn(SingletonComponent.class)
public class UtilityModule {
    
    @Provides
    @Singleton
    public SharedPreferencesManager provideSharedPreferencesManager(@ApplicationContext Context context) {
        return new SharedPreferencesManager(context);
    }
    
    @Provides
    @Singleton
    public ImageManager provideImageManager(@ApplicationContext Context context,
                                          SharedPreferencesManager preferencesManager) {
        return new ImageManager(context, preferencesManager);
    }
    
    @Provides
    @Singleton
    public LocationManager provideLocationManager(@ApplicationContext Context context,
                                                SharedPreferencesManager preferencesManager) {
        return new LocationManager(context, preferencesManager);
    }
    
    @Provides
    @Singleton
    public ConnectionManager provideConnectionManager(@ApplicationContext Context context) {
        return new ConnectionManager(context);
    }
    
    @Provides
    @Singleton
    public FileManager provideFileManager(@ApplicationContext Context context,
                                        SharedPreferencesManager preferencesManager) {
        return new FileManager(context, preferencesManager);
    }
}