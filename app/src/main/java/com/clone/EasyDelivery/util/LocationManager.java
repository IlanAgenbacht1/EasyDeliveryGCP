package com.clone.EasyDelivery.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Modern LocationManager with dependency injection and async operations
 * Handles GPS location retrieval and distance calculations
 */
@Singleton
public class LocationManager {
    
    private static final String TAG = "LocationManager";
    private static final long LOCATION_TIMEOUT_MS = 30000; // 30 seconds
    private static final float MIN_DISTANCE_FOR_UPDATES = 10f; // 10 meters
    private static final long MIN_TIME_BETWEEN_UPDATES = 5000; // 5 seconds
    
    public interface LocationCallback {
        void onLocationFound(@NonNull Location location);
        void onLocationNotFound();
        void onLocationError(@NonNull String error);
    }
    
    private final Context context;
    private final SharedPreferencesManager preferencesManager;
    private android.location.LocationManager systemLocationManager;

    @Inject
    public LocationManager(Context context, SharedPreferencesManager preferencesManager) {
        this.context = context;
        this.preferencesManager = preferencesManager;
        this.systemLocationManager = (android.location.LocationManager) 
            context.getSystemService(Context.LOCATION_SERVICE);
    }
    
    /**
     * Get current location asynchronously with proper error handling
     */
    public CompletableFuture<Location> getCurrentLocationAsync() {
        CompletableFuture<Location> future = new CompletableFuture<>();
        
        if (!hasLocationPermissions()) {
            future.completeExceptionally(new SecurityException("Location permissions not granted"));
            return future;
        }
        
        if (systemLocationManager == null) {
            future.completeExceptionally(new RuntimeException("Location service not available"));
            return future;
        }
        
        getCurrentLocationInternal(new LocationCallback() {
            @Override
            public void onLocationFound(@NonNull Location location) {
                // Save location in preferences
                preferencesManager.setGpsLocation(location);
                future.complete(location);
            }
            
            @Override
            public void onLocationNotFound() {
                future.completeExceptionally(new RuntimeException("Location not found"));
            }
            
            @Override
            public void onLocationError(@NonNull String error) {
                future.completeExceptionally(new RuntimeException(error));
            }
        });
        
        // Set timeout
        new Thread(() -> {
            try {
                Thread.sleep(LOCATION_TIMEOUT_MS);
                if (!future.isDone()) {
                    future.completeExceptionally(new RuntimeException("Location request timed out"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        return future;
    }
    
    /**
     * Get current location with callback (for legacy compatibility)
     */
    public void getCurrentLocationAsync(@NonNull LocationCallback callback) {
        if (!hasLocationPermissions()) {
            callback.onLocationError("Location permissions not granted");
            return;
        }
        
        if (systemLocationManager == null) {
            callback.onLocationError("Location service not available");
            return;
        }
        
        getCurrentLocationInternal(callback);
    }
    
    /**
     * Calculate distance between two locations using Haversine formula
     */
    public static double calculateHaversineDistance(@NonNull Location location1, 
                                                   @NonNull Location location2) {
        final int EARTH_RADIUS_METERS = 6371000;
        
        double latDistance = Math.toRadians(location1.getLatitude() - location2.getLatitude());
        double lonDistance = Math.toRadians(location1.getLongitude() - location2.getLongitude());
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(location2.getLatitude())) 
                * Math.cos(Math.toRadians(location1.getLatitude()))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_METERS * c;
    }
    
    /**
     * Check if current location is within specified distance of target
     */
    public CompletableFuture<Boolean> isCurrentLocationWithinDistance(@NonNull Location targetLocation,
                                                                      double maxDistanceMeters) {
        return getCurrentLocationAsync()
            .thenApply(currentLocation -> {
                double distance = calculateHaversineDistance(currentLocation, targetLocation);
                Log.d(TAG, "Distance to target: " + distance + " meters (max: " + maxDistanceMeters + ")");
                return distance <= maxDistanceMeters;
            })
            .exceptionally(throwable -> {
                Log.w(TAG, "Could not determine distance - location unavailable", throwable);
                return false; // Assume not within distance if location unavailable
            });
    }
    
    /**
     * Check if two locations are within distance (static utility method)
     */
    public static boolean isWithinDistance(@NonNull Location location1, @NonNull Location location2, double maxDistanceMeters) {
        if (location1 == null || location2 == null) {
            Log.w(TAG, "Null location provided to isWithinDistance");
            return false;
        }

        double distance = calculateHaversineDistance(location1, location2);
        Log.d(TAG, "Distance between locations: " + distance + " meters (max: " + maxDistanceMeters + ")");
        return distance <= maxDistanceMeters;
    }
    
    /**
     * Get last known location from preferences
     */
    @Nullable
    public Location getLastKnownLocation() {
        return preferencesManager.getGpsLocation();
    }
    
    /**
     * Get formatted location string
     */
    @NonNull
    public String getFormattedLocation(@Nullable Location location) {
        if (location == null) {
            return "[Location unavailable]";
        }
        
        return String.format("[%.6f° N, %.6f° E]", 
                           location.getLatitude(), 
                           location.getLongitude());
    }
    
    // =================== PRIVATE HELPER METHODS ===================
    
    private void getCurrentLocationInternal(@NonNull LocationCallback callback) {
        try {
            // Try to get last known location first
            Location lastKnown = getBestLastKnownLocation();
            if (lastKnown != null && isLocationRecentEnough(lastKnown)) {
                Log.i(TAG, "Using recent cached location: " + getFormattedLocation(lastKnown));
                callback.onLocationFound(lastKnown);
                return;
            }
            
            // Request fresh location update
            requestLocationUpdate(callback);
            
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when accessing location", e);
            callback.onLocationError("Location permission denied");
        } catch (Exception e) {
            Log.e(TAG, "Error getting location", e);
            callback.onLocationError("Location service error: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("MissingPermission")
    private void requestLocationUpdate(@NonNull LocationCallback callback) {
        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                Log.i(TAG, "Fresh location received: " + getFormattedLocation(location));
                systemLocationManager.removeUpdates(this);
                callback.onLocationFound(location);
            }
            
            @Override
            public void onProviderEnabled(@NonNull String provider) {
                Log.d(TAG, "Location provider enabled: " + provider);
            }
            
            @Override
            public void onProviderDisabled(@NonNull String provider) {
                Log.w(TAG, "Location provider disabled: " + provider);
            }
            
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                Log.d(TAG, "Location provider status changed: " + provider + ", status: " + status);
            }
        };
        
        // Try network provider first (usually faster)
        if (systemLocationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
            systemLocationManager.requestLocationUpdates(
                android.location.LocationManager.NETWORK_PROVIDER,
                MIN_TIME_BETWEEN_UPDATES,
                MIN_DISTANCE_FOR_UPDATES,
                locationListener,
                Looper.getMainLooper()
            );
            Log.d(TAG, "Requested location updates from network provider");
        } 
        // Fallback to GPS provider
        else if (systemLocationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
            systemLocationManager.requestLocationUpdates(
                android.location.LocationManager.GPS_PROVIDER,
                MIN_TIME_BETWEEN_UPDATES,
                MIN_DISTANCE_FOR_UPDATES,
                locationListener,
                Looper.getMainLooper()
            );
            Log.d(TAG, "Requested location updates from GPS provider");
        } else {
            callback.onLocationNotFound();
        }
    }
    
    @SuppressWarnings("MissingPermission")
    @Nullable
    private Location getBestLastKnownLocation() {
        Location networkLocation = null;
        Location gpsLocation = null;
        
        if (systemLocationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
            networkLocation = systemLocationManager.getLastKnownLocation(
                android.location.LocationManager.NETWORK_PROVIDER);
        }
        
        if (systemLocationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
            gpsLocation = systemLocationManager.getLastKnownLocation(
                android.location.LocationManager.GPS_PROVIDER);
        }
        
        // Prefer more recent location
        if (networkLocation != null && gpsLocation != null) {
            return networkLocation.getTime() > gpsLocation.getTime() ? networkLocation : gpsLocation;
        } else if (networkLocation != null) {
            return networkLocation;
        } else {
            return gpsLocation;
        }
    }
    
    private boolean isLocationRecentEnough(@NonNull Location location) {
        long locationAge = System.currentTimeMillis() - location.getTime();
        return locationAge < TimeUnit.MINUTES.toMillis(5); // Consider 5 minutes as recent enough
    }
    
    private boolean hasLocationPermissions() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                   == PackageManager.PERMISSION_GRANTED
               || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) 
                   == PackageManager.PERMISSION_GRANTED;
    }
}