package com.clone.EasyDelivery.di;

import android.content.Context;

import com.clone.EasyDelivery.BuildConfig;
import com.clone.EasyDelivery.util.ConnectionManager;
import com.clone.EasyDelivery.Api.services.AuthService;
import com.clone.EasyDelivery.Api.services.DeliveryService;
import com.clone.EasyDelivery.Api.services.TripService;
import com.clone.EasyDelivery.util.GoogleAuthService;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Hilt module providing network-related dependencies
 * Replaces manual RetrofitApiClient instantiation with proper DI
 */
@Module
@InstallIn(SingletonComponent.class)
public class NetworkModule {

    private static final String TAG = "NetworkModule";

    @Provides
    @Singleton
    public OkHttpClient provideOkHttpClient(@ApplicationContext Context context) {
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder()
            .connectTimeout(BuildConfig.API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(BuildConfig.API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(BuildConfig.API_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Add interceptors in optimal order:
        // 1. Network check (fail fast if no connection)
        ConnectionManager connectionManager = new ConnectionManager(context);
        httpClientBuilder.addInterceptor(new NetworkConnectionInterceptor(connectionManager));

        // 2. Authentication (add auth headers)
        httpClientBuilder.addInterceptor(new AuthenticationInterceptor(context));

        // 3. Common headers (add standard headers)
        httpClientBuilder.addInterceptor(new CommonHeadersInterceptor());

        // 4. Logging (last, so we see final request/response)
        if (BuildConfig.API_DEBUG) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            httpClientBuilder.addInterceptor(loggingInterceptor);
        }

        return httpClientBuilder.build();
    }

    @Provides
    @Singleton
    public Gson provideGson() {
        return new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .registerTypeAdapter(Date.class, new JsonDeserializer<Date>() {
                    @Override
                    public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                        String dateString = json.getAsString();

                        // Handle invalid/default dates from backend
                        if (dateString == null || dateString.isEmpty() ||
                                dateString.startsWith("0001-01-01") || dateString.equals("null")) {
                            return new Date(); // Use current date for invalid dates
                        }

                        // Try multiple date formats commonly used by APIs
                        String[] dateFormats = {
                                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",     // ISO8601 with milliseconds and Z
                                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",      // ISO8601 with milliseconds and timezone
                                "yyyy-MM-dd'T'HH:mm:ssZ",          // ISO8601 without milliseconds
                                "yyyy-MM-dd'T'HH:mm:ss",           // ISO8601 without timezone (backend issue)
                                "yyyy-MM-dd HH:mm:ss",             // Simple datetime format
                                "yyyy-MM-dd"                       // Date only
                        };

                        for (String format : dateFormats) {
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                                return sdf.parse(dateString);
                            } catch (ParseException e) {
                                // Try next format
                            }
                        }

                        // If all formats fail, return current date
                        return new Date();
                    }
                })
                .create();
    }

    @Provides
    @Singleton
    public Retrofit provideRetrofit(OkHttpClient okHttpClient, Gson gson) {
        return new Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
            .build();
    }

    @Provides
    @Singleton
    public AuthService provideAuthService(Retrofit retrofit) {
        return retrofit.create(AuthService.class);
    }

    @Provides
    @Singleton
    public TripService provideTripService(Retrofit retrofit) {
        return retrofit.create(TripService.class);
    }

    @Provides
    @Singleton
    public DeliveryService provideDeliveryService(Retrofit retrofit) {
        return retrofit.create(DeliveryService.class);
    }

    /**
     * Interceptor to add authentication headers to requests
     */
    private static class AuthenticationInterceptor implements Interceptor {
        private final Context context;
        private final GoogleAuthService authService;

        AuthenticationInterceptor(Context context) {
            this.context = context.getApplicationContext();
            this.authService = new GoogleAuthService(context);
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();

            // Skip authentication for health check endpoints
            String url = originalRequest.url().toString();
            if (url.contains("/health") || url.contains("/ping")) {
                return chain.proceed(originalRequest);
            }

            try {
                // Get access token synchronously for the request
                String accessToken = authService.getAccessToken().get();

                if (accessToken != null && !accessToken.isEmpty()) {
                    Request.Builder requestBuilder = originalRequest.newBuilder();
                    requestBuilder.addHeader("Authorization", "Bearer " + accessToken);
                    return chain.proceed(requestBuilder.build());
                } else {
                    return chain.proceed(originalRequest);
                }
            } catch (Exception e) {
                return chain.proceed(originalRequest);
            }
        }
    }

    /**
     * Interceptor to check network connection before making requests
     */
    private static class NetworkConnectionInterceptor implements Interceptor {
        private final ConnectionManager connectionManager;

        NetworkConnectionInterceptor(ConnectionManager connectionManager) {
            this.connectionManager = connectionManager;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            if (!connectionManager.hasInternetConnection()) {
                throw new IOException("No internet connection available");
            }
            return chain.proceed(chain.request());
        }
    }

    /**
     * Interceptor to add common headers to all requests
     */
    private static class CommonHeadersInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request original = chain.request();
            Request.Builder requestBuilder = original.newBuilder()
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "EasyDelivery-Android/" + BuildConfig.VERSION_NAME);

            Request request = requestBuilder.build();
            return chain.proceed(request);
        }
    }
}