package com.clone.EasyDelivery.sync;

import android.content.Context;

import androidx.work.ListenableWorker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;


import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Custom WorkerFactory for Hilt dependency injection in Workers
 * Creates SyncWorker instances with proper dependency access
 */
@Singleton
public class HiltWorkerFactory extends WorkerFactory {

    @Inject
    public HiltWorkerFactory() {
        // Empty constructor - dependencies handled via static getInstance methods
    }

    @Override
    public ListenableWorker createWorker(
            Context appContext,
            String workerClassName,
            WorkerParameters workerParameters
    ) {
        if (workerClassName.equals(SyncWorker.class.getName())) {
            return new SyncWorker(appContext, workerParameters);
        }
        
        // Return null to delegate to the default factory
        return null;
    }
}