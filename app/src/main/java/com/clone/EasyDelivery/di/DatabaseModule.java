package com.clone.EasyDelivery.di;

import android.content.Context;

import androidx.room.Room;

import com.clone.EasyDelivery.data.DeliveryDatabase;
import com.clone.EasyDelivery.data.dao.DeliveryDao;
import com.clone.EasyDelivery.data.dao.DeviceDao;
import com.clone.EasyDelivery.data.dao.OfflineActionDao;
import com.clone.EasyDelivery.data.dao.SessionDao;
import com.clone.EasyDelivery.data.dao.TripDao;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

/**
 * Hilt module providing database-related dependencies
 * Manages Room database and DAO instances
 */
@Module
@InstallIn(SingletonComponent.class)
public class DatabaseModule {

    @Provides
    @Singleton
    public DeliveryDatabase provideDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(
                context.getApplicationContext(),
                DeliveryDatabase.class,
                "delivery_database"
            )
            .fallbackToDestructiveMigration() // For development - remove in production
            .build();
    }

    @Provides
    public DeliveryDao provideDeliveryDao(DeliveryDatabase database) {
        return database.deliveryDao();
    }

    @Provides
    public TripDao provideTripDao(DeliveryDatabase database) {
        return database.tripDao();
    }

    @Provides
    public DeviceDao provideDeviceDao(DeliveryDatabase database) {
        return database.deviceDao();
    }

    @Provides
    public SessionDao provideSessionDao(DeliveryDatabase database) {
        return database.sessionDao();
    }

    @Provides
    public OfflineActionDao provideOfflineActionDao(DeliveryDatabase database) {
        return database.offlineActionDao();
    }
}