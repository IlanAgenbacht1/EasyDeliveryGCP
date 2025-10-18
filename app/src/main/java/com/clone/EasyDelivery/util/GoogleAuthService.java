package com.clone.EasyDelivery.util;

import android.content.Context;
import android.util.Log;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.auth.oauth2.IdTokenCredentials;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing Google service account authentication
 * Handles token acquisition and refresh for API calls
 */
public class GoogleAuthService {

    private static final String TAG = "GoogleAuthService";
    private static final String SERVICE_ACCOUNT_FILE = "service-account-key.json";
    private static final String CLOUD_RUN_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final String CLOUD_RUN_URL = "https://delivery-cloud-api-833551875057.africa-south1.run.app";

    private final Context context;
    private GoogleCredentials credentials;

    public GoogleAuthService(Context context) {
        this.context = context.getApplicationContext();
        initializeCredentials();
    }

    /**
     * Initialize Google credentials from service account file
     */
    private void initializeCredentials() {
        try {
            InputStream serviceAccountStream = context.getAssets().open(SERVICE_ACCOUNT_FILE);
            credentials = GoogleCredentials.fromStream(serviceAccountStream)
                    .createScoped(Collections.singletonList(CLOUD_RUN_SCOPE));
            Log.i(TAG, "Google service account credentials initialized successfully");

        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize Google credentials", e);
            credentials = null;
        }
    }

    /**
     * Get valid access token (refresh if needed)
     */
    public CompletableFuture<String> getAccessToken() {
        return CompletableFuture.supplyAsync(() -> {
            if (credentials == null) {
                Log.w(TAG, "Credentials not initialized");
                return null;
            }

            try {
                // For Cloud Run, we need an identity token with the correct audience
                if (credentials instanceof IdTokenProvider) {
                    IdTokenProvider idTokenProvider = (IdTokenProvider) credentials;
                    IdTokenCredentials idTokenCredentials = IdTokenCredentials.newBuilder()
                            .setIdTokenProvider(idTokenProvider)
                            .setTargetAudience(CLOUD_RUN_URL)
                            .build();

                    idTokenCredentials.refreshIfExpired();
                    AccessToken idToken = idTokenCredentials.getAccessToken();

                    if (idToken != null) {
                        Log.d(TAG, "Identity token obtained successfully for Cloud Run");
                        return idToken.getTokenValue();
                    }
                }

                // Fallback to regular access token
                credentials.refreshIfExpired();
                AccessToken accessToken = credentials.getAccessToken();

                if (accessToken != null) {
                    Log.d(TAG, "Access token obtained successfully");
                    return accessToken.getTokenValue();
                } else {
                    Log.w(TAG, "Access token is null");
                    return null;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to get access token", e);
                return null;
            }
        });
    }

    /**
     * Check if service account is properly configured
     */
    public boolean isConfigured() {
        return credentials != null;
    }

    /**
     * Get service account email for logging
     */
    public String getServiceAccountEmail() {
        if (credentials instanceof com.google.auth.oauth2.ServiceAccountCredentials) {
            return ((com.google.auth.oauth2.ServiceAccountCredentials) credentials)
                    .getClientEmail();
        }
        return "unknown";
    }

    /**
     * Force token refresh (for testing)
     */
    public void forceRefresh() {
        try {
            if (credentials != null) {
                credentials.refresh();
                Log.d(TAG, "Forced token refresh completed");
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to refresh token", e);
        }
    }
}