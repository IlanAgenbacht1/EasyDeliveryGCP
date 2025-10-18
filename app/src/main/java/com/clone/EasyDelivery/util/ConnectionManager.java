package com.clone.EasyDelivery.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Modern ConnectionManager with dependency injection and repository integration
 * Handles network connectivity monitoring and status reporting
 */
@Singleton
public class ConnectionManager {
    
    private static final String TAG = "ConnectionManager";
    private static final String CONNECTIVITY_TEST_URL = "https://www.google.com";
    private static final int CONNECTIVITY_TIMEOUT_SECONDS = 10;
    
    public enum ConnectionType {
        NONE,
        WIFI,
        CELLULAR,
        ETHERNET,
        VPN,
        UNKNOWN
    }
    
    public static class NetworkStatus {
        public final boolean isConnected;
        public final ConnectionType connectionType;
        public final boolean isMetered;
        public final boolean hasInternet;
        
        public NetworkStatus(boolean isConnected, ConnectionType connectionType, 
                           boolean isMetered, boolean hasInternet) {
            this.isConnected = isConnected;
            this.connectionType = connectionType;
            this.isMetered = isMetered;
            this.hasInternet = hasInternet;
        }
        
        @NonNull
        @Override
        public String toString() {
            return "NetworkStatus{" +
                    "isConnected=" + isConnected +
                    ", connectionType=" + connectionType +
                    ", isMetered=" + isMetered +
                    ", hasInternet=" + hasInternet +
                    '}';
        }
    }
    
    private final Context context;
    private final ConnectivityManager connectivityManager;
    private final OkHttpClient httpClient;
    private final MutableLiveData<NetworkStatus> networkStatusLiveData;
    
    private ConnectivityManager.NetworkCallback networkCallback;
    
    @Inject
    public ConnectionManager(@ApplicationContext Context context) {
        this.context = context;
        this.connectivityManager = (ConnectivityManager) 
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(CONNECTIVITY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(CONNECTIVITY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(CONNECTIVITY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
        
        this.networkStatusLiveData = new MutableLiveData<>(getCurrentNetworkStatus());
        
        initializeNetworkCallback();
    }
    
    /**
     * Get observable network status
     */
    public LiveData<NetworkStatus> getNetworkStatusLiveData() {
        return networkStatusLiveData;
    }
    
    /**
     * Get current network status synchronously
     */
    @NonNull
    public NetworkStatus getCurrentNetworkStatus() {
        if (connectivityManager == null) {
            return new NetworkStatus(false, ConnectionType.NONE, false, false);
        }
        
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return new NetworkStatus(false, ConnectionType.NONE, false, false);
        }
        
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            return new NetworkStatus(false, ConnectionType.NONE, false, false);
        }
        
        boolean isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        ConnectionType connectionType = determineConnectionType(capabilities);
        boolean isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        boolean hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        
        return new NetworkStatus(isConnected, connectionType, isMetered, hasInternet);
    }
    
    /**
     * Check if device has internet connectivity
     */
    public boolean hasInternetConnection() {
        return getCurrentNetworkStatus().hasInternet;
    }
    
    /**
     * Check if connection is metered (cellular data with potential charges)
     */
    public boolean isConnectionMetered() {
        return getCurrentNetworkStatus().isMetered;
    }
    
    /**
     * Check if connected via WiFi
     */
    public boolean isConnectedViaWiFi() {
        return getCurrentNetworkStatus().connectionType == ConnectionType.WIFI;
    }
    
    /**
     * Check if connected via cellular
     */
    public boolean isConnectedViaCellular() {
        return getCurrentNetworkStatus().connectionType == ConnectionType.CELLULAR;
    }
    
    /**
     * Test actual internet connectivity by making HTTP request
     */
    public CompletableFuture<Boolean> testInternetConnectivity() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Request request = new Request.Builder()
                    .url(CONNECTIVITY_TEST_URL)
                    .head()
                    .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    boolean isSuccess = response.isSuccessful();
                    Log.d(TAG, "Internet connectivity test: " + (isSuccess ? "SUCCESS" : "FAILED") + 
                              " (status: " + response.code() + ")");
                    return isSuccess;
                }
            } catch (Exception e) {
                Log.w(TAG, "Internet connectivity test failed", e);
                return false;
            }
        });
    }
    
    /**
     * Get formatted connection status string
     */
    @NonNull
    public String getConnectionStatusDescription() {
        NetworkStatus status = getCurrentNetworkStatus();
        if (!status.isConnected) {
            return "No connection";
        }
        
        StringBuilder description = new StringBuilder();
        description.append("Connected via ").append(status.connectionType.name().toLowerCase());
        
        if (status.isMetered) {
            description.append(" (metered)");
        }
        
        if (!status.hasInternet) {
            description.append(" - No internet access");
        }
        
        return description.toString();
    }
    
    /**
     * Start monitoring network changes
     */
    public void startNetworkMonitoring() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
                
                connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
                Log.d(TAG, "Network monitoring started");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start network monitoring", e);
            }
        }
    }
    
    /**
     * Stop monitoring network changes
     */
    public void stopNetworkMonitoring() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
                Log.d(TAG, "Network monitoring stopped");
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop network monitoring", e);
            }
        }
    }
    
    // =================== PRIVATE HELPER METHODS ===================
    
    private void initializeNetworkCallback() {
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Log.d(TAG, "Network available: " + network);
                updateNetworkStatus();
            }
            
            @Override
            public void onLost(@NonNull Network network) {
                Log.d(TAG, "Network lost: " + network);
                updateNetworkStatus();
            }
            
            @Override
            public void onCapabilitiesChanged(@NonNull Network network, 
                                            @NonNull NetworkCapabilities networkCapabilities) {
                Log.d(TAG, "Network capabilities changed: " + network);
                updateNetworkStatus();
            }
            
            @Override
            public void onUnavailable() {
                Log.d(TAG, "Network unavailable");
                updateNetworkStatus();
            }
        };
    }
    
    private void updateNetworkStatus() {
        NetworkStatus newStatus = getCurrentNetworkStatus();
        NetworkStatus currentStatus = networkStatusLiveData.getValue();
        
        if (currentStatus == null || !networkStatusEquals(currentStatus, newStatus)) {
            Log.i(TAG, "Network status changed: " + newStatus);
            networkStatusLiveData.postValue(newStatus);
        }
    }
    
    private boolean networkStatusEquals(NetworkStatus status1, NetworkStatus status2) {
        return status1.isConnected == status2.isConnected
                && status1.connectionType == status2.connectionType
                && status1.isMetered == status2.isMetered
                && status1.hasInternet == status2.hasInternet;
    }
    
    private ConnectionType determineConnectionType(NetworkCapabilities capabilities) {
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return ConnectionType.WIFI;
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return ConnectionType.CELLULAR;
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return ConnectionType.ETHERNET;
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return ConnectionType.VPN;
        } else {
            return ConnectionType.UNKNOWN;
        }
    }
}