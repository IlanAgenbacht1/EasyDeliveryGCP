package com.clone.EasyDelivery.repository.utils;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.clone.EasyDelivery.Api.utils.ApiResponseHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Generic class that provides offline-first data loading strategy
 * 
 * This class implements the offline-first pattern:
 * 1. Load data from local database immediately
 * 2. Check if network fetch is needed 
 * 3. Fetch from network if needed and save to database
 * 4. Return updated data from database (single source of truth)
 *
 * @param <ResultType> Type for the Resource data (what UI observes)
 * @param <RequestType> Type for the API response
 */
public abstract class NetworkBoundResource<ResultType, RequestType> {
    
    private final ExecutorService executor;
    private final MediatorLiveData<Resource<ResultType>> result = new MediatorLiveData<>();

    @MainThread
    protected NetworkBoundResource(ExecutorService executor) {
        this.executor = executor;
        
        // Start with loading state
        result.setValue(Resource.loading(null));
        
        // Load from database first
        LiveData<ResultType> dbSource = loadFromDb();
        result.addSource(dbSource, data -> {
            result.removeSource(dbSource);
            
            if (shouldFetch(data)) {
                fetchFromNetwork(dbSource);
            } else {
                result.addSource(dbSource, newData -> result.setValue(Resource.success(newData)));
            }
        });
    }

    private void fetchFromNetwork(LiveData<ResultType> dbSource) {
        LiveData<ResultType> reloadDbSource = loadFromDb();
        result.addSource(dbSource, newData -> result.setValue(Resource.loading(newData)));
        
        executor.execute(() -> {
            createCall().thenAccept(apiResult -> {
                result.removeSource(dbSource);
                
                if (apiResult.isSuccess()) {
                    executor.execute(() -> {
                        saveCallResult(processResponse(apiResult));
                        
                        // Reload from database to get updated data
                        result.addSource(reloadDbSource, newData -> 
                            result.setValue(Resource.success(newData)));
                    });
                } else {
                    onFetchFailed();
                    result.addSource(reloadDbSource, newData ->
                        result.setValue(Resource.error(apiResult.getErrorMessage(), newData)));
                }
            }).exceptionally(throwable -> {
                onFetchFailed();
                result.addSource(reloadDbSource, newData ->
                    result.setValue(Resource.error(throwable.getMessage(), newData)));
                return null;
            });
        });
    }

    protected void onFetchFailed() {
        // Override to handle fetch failures (e.g., analytics, logging)
    }

    public LiveData<Resource<ResultType>> asLiveData() {
        return result;
    }

    @WorkerThread
    protected RequestType processResponse(ApiResponseHandler.ApiResult<RequestType> response) {
        return response.getData();
    }

    @WorkerThread
    protected abstract void saveCallResult(@NonNull RequestType item);

    @MainThread
    protected abstract boolean shouldFetch(ResultType data);

    @NonNull
    @MainThread
    protected abstract LiveData<ResultType> loadFromDb();

    @NonNull
    @MainThread
    protected abstract CompletableFuture<ApiResponseHandler.ApiResult<RequestType>> createCall();
}