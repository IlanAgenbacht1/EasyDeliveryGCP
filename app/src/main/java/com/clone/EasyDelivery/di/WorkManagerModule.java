package com.clone.EasyDelivery.di;

import androidx.work.WorkerFactory;

import com.clone.EasyDelivery.sync.HiltWorkerFactory;

import javax.inject.Singleton;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

/**
 * Hilt module for WorkManager configuration
 * Provides HiltWorkerFactory for dependency injection in Workers
 */
@Module
@InstallIn(SingletonComponent.class)
public abstract class WorkManagerModule {

    @Binds
    @Singleton
    abstract WorkerFactory bindWorkerFactory(HiltWorkerFactory factory);
}