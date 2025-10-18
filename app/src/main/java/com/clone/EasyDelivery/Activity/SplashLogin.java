package com.clone.EasyDelivery.Activity;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.databinding.ActivitySplashBinding;
import com.clone.EasyDelivery.viewmodel.AuthViewModel;
import com.clone.EasyDelivery.viewmodel.models.AuthState;
import com.clone.EasyDelivery.viewmodel.models.UiState;
import com.clone.EasyDelivery.models.Driver;
import com.clone.EasyDelivery.models.Vehicle;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Clean, Modern SplashLogin with Dynamic UI Flow
 */
@AndroidEntryPoint
public class SplashLogin extends AppCompatActivity {

    private static final String TAG = "SplashLogin";
    private static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 10000;
    
    private ActivitySplashBinding binding;
    private AuthViewModel authViewModel;
    
    // State
    private boolean splashCompleted = false;
    private List<Driver> availableDrivers = new ArrayList<>();
    private List<Vehicle> availableVehicles = new ArrayList<>();
    private Driver selectedDriver = null;
    private Vehicle selectedVehicle = null;
    private boolean isLoading = false;

    // Caching
    private boolean driversLoaded = false;
    private boolean vehiclesLoaded = false;
    private long lastDriversLoadTime = 0;
    private long lastVehiclesLoadTime = 0;
    private static final long CACHE_EXPIRY_TIME = 5 * 60 * 1000; // 5 minutes

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        if (!checkAndRequestPermissions()) {
            initializeApp();
        }
    }
    
    private void initializeApp() {
        // Hide all UI elements initially
        hideAllUIElements();

        // Setup observers
        setupObservers();

        // Start splash animation
        startSplashAnimation();

        // Initialize caching
        initializeCache();
    }

    /**
     * Hide all UI elements to prevent flashing
     */
    private void hideAllUIElements() {
        binding.rlMain.setVisibility(View.GONE);
        binding.btnLogin.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.GONE);
        binding.llCom.setVisibility(View.GONE);
        binding.llDocu.setVisibility(View.GONE);
        binding.llDriver.setVisibility(View.GONE);
        binding.llPin.setVisibility(View.GONE);
        binding.llVehicle.setVisibility(View.GONE);

        if (binding.tvSplashStatus != null) {
            binding.tvSplashStatus.setVisibility(View.GONE);
        }
    }

    /**
     * Start splash animation and auth check
     */
    private void startSplashAnimation() {
        binding.ivSplashLogo.setVisibility(View.VISIBLE);

        Animation fadeIn = new AlphaAnimation(0, 1);
        fadeIn.setInterpolator(new DecelerateInterpolator());
        fadeIn.setDuration(1000);
        fadeIn.setStartOffset(500);

        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up);
        slideUp.setStartOffset(1950);
        slideUp.setDuration(250);
        slideUp.setFillAfter(true);

        slideUp.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                splashCompleted = true;
                checkAuthenticationState();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        AnimationSet animationSet = new AnimationSet(false);
        animationSet.addAnimation(fadeIn);
        animationSet.addAnimation(slideUp);
        animationSet.setFillAfter(true);

        binding.ivSplashLogo.startAnimation(animationSet);
    }
    
    /**
     * Check authentication state intelligently with caching
     */
    private void checkAuthenticationState() {
        AuthState cachedState = getCachedAuthState();

        if (cachedState == AuthState.DEVICE_REGISTERED) {
            // Device is known registered, skip API check
            Log.i(TAG, "Using cached auth state, proceeding to driver selection");
            handleAuthState(AuthState.DEVICE_REGISTERED);
        } else {
            // Need API check - show status only if needed
            if (binding.tvSplashStatus != null) {
                showAuthCheckingStatus();
            }

            authViewModel.getAuthState().observe(this, authState -> {
                if (authState != null) {
                    hideAuthCheckingStatus();

                    // Cache successful registration
                    if (authState == AuthState.DEVICE_REGISTERED) {
                        cacheAuthState(authState);
                    }

                    handleAuthState(authState);
                }
            });
        }
    }
    
    /**
     * Handle auth state and show appropriate UI
     */
    private void handleAuthState(AuthState authState) {
        switch (authState) {
            case NOT_AUTHENTICATED:
                navigateToAuthActivity();
                break;

            case DEVICE_REGISTERED:
                showDriverSelection();
                break;

            case AUTHENTICATED:
                showVehicleSelection();
                break;

            case SESSION_ACTIVE:
                navigateToTripDash();
                break;

            case AUTHENTICATION_FAILED:
                showDriverSelection(); // Restart flow
                break;
        }
    }
    
    /**
     * Show driver selection UI dynamically
     */
    private void showDriverSelection() {
        Log.d(TAG, "Showing driver selection");

        // Show main form
        showMainForm();

        // Show only driver dropdown
        showFieldAnimated(binding.llDriver);
        updateButton("Select driver", false);

        // Check if we have cached drivers to show immediately
        if (!availableDrivers.isEmpty()) {
            Log.d(TAG, "Using cached drivers (" + availableDrivers.size() + " drivers)");
            ArrayAdapter<Driver> adapter = new ArrayAdapter<>(this, R.layout.dropdown_item_driver, availableDrivers);
            binding.etDriver.setAdapter(adapter);
        }

        // Load fresh data if cache is expired or empty
        if (!driversLoaded || System.currentTimeMillis() - lastDriversLoadTime > CACHE_EXPIRY_TIME) {
            Log.d(TAG, "Loading fresh drivers from API");
            binding.progressBar.setVisibility(View.VISIBLE);
            authViewModel.loadAvailableDrivers();
        }
    }
    
    /**
     * Show PIN input dynamically based on driver selection
     */
    private void showPinInput() {
        Log.d(TAG, "Showing PIN input for driver: " + selectedDriver.getName());

        // Show PIN field with animation after a brief delay to ensure driver field is stable
        binding.llPin.postDelayed(() -> {
            showFieldAnimated(binding.llPin);

            if (selectedDriver.getHasPinSetup()) {
                // Existing driver - PIN authentication
                binding.etPin.setHint("Enter your 4-digit PIN");
                binding.etPin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                updateButton("Continue", false);
                Toast.makeText(this, "Welcome back " + selectedDriver.getName() + "!", Toast.LENGTH_SHORT).show();
            } else {
                // New driver - PIN setup
                binding.etPin.setHint("Create your 4-digit PIN");
                binding.etPin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                updateButton("Create PIN", false);
                Toast.makeText(this, "Welcome " + selectedDriver.getName() + "! Please create a secure 4-digit PIN.", Toast.LENGTH_LONG).show();
            }

            // Clear any previous PIN input
            binding.etPin.setText("");
            binding.etPin.requestFocus();
        }, 50); // Very brief delay for smooth appearance
    }

    /**
     * Show vehicle selection after successful PIN
     */
    private void showVehicleSelection() {
        Log.d(TAG, "Showing vehicle selection");

        // First, hide driver and PIN fields immediately
        binding.llDriver.setVisibility(View.GONE);
        binding.llPin.setVisibility(View.GONE);

        // Clear any text from previous fields to prevent flash
        binding.etDriver.setText("");
        binding.etPin.setText("");

        // Show vehicle dropdown with a slight delay for smooth transition
        binding.llVehicle.postDelayed(() -> {
            showFieldAnimated(binding.llVehicle);
            updateButton("Start session", false);

            // Check if we have cached vehicles to show immediately
            if (!availableVehicles.isEmpty()) {
                Log.d(TAG, "Using cached vehicles (" + availableVehicles.size() + " vehicles)");
                ArrayAdapter<Vehicle> adapter = new ArrayAdapter<>(this, R.layout.dropdown_item_vehicle, availableVehicles);
                binding.etVehicle.setAdapter(adapter);
            }

            // Load fresh data if cache is expired or empty
            if (!vehiclesLoaded || System.currentTimeMillis() - lastVehiclesLoadTime > CACHE_EXPIRY_TIME) {
                Log.d(TAG, "Loading fresh vehicles from API");
                binding.progressBar.setVisibility(View.VISIBLE);
                authViewModel.loadAvailableVehicles();
            }
        }, 100); // Small delay for smooth transition
    }

    /**
     * Setup observers for dynamic UI updates
     */
    private void setupObservers() {
        // Driver selection listener
        binding.etDriver.setOnItemClickListener((parent, view, position, id) -> {
            selectedDriver = availableDrivers.get(position);
            showPinInput();
        });

        // Vehicle selection listener
        binding.etVehicle.setOnItemClickListener((parent, view, position, id) -> {
            selectedVehicle = availableVehicles.get(position);
            updateButton("Start session", true);
        });

        // PIN input listener
        binding.etPin.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 4) {
                    String buttonText = selectedDriver != null && !selectedDriver.getHasPinSetup() ? "Create PIN" : "Continue";
                    updateButton(buttonText, true);
                } else {
                    String buttonText = selectedDriver != null && !selectedDriver.getHasPinSetup() ? "Create PIN" : "Continue";
                    updateButton(buttonText, false);
                }
            }
        });

        // Button click
        binding.btnLogin.setOnClickListener(v -> handleButtonClick());

        // ViewModel observers
        authViewModel.getAvailableDriversState().observe(this, this::handleDriversResult);
        authViewModel.getAvailableVehiclesState().observe(this, this::handleVehiclesResult);
        authViewModel.getDriverAuthenticationState().observe(this, this::handleAuthResult);
        authViewModel.getSessionState().observe(this, this::handleSessionResult);

        // Loading state observer
        authViewModel.getIsLoading().observe(this, this::handleLoadingState);
    }

    private void handleLoadingState(Boolean isLoading) {
        if (isLoading == null) return;

        this.isLoading = isLoading;

        // Only show global loading for button actions, not for data fetching
        // Data fetching loading is handled specifically in each method
    }

    /**
     * Show loading state on button
     */
    private void showButtonLoading() {
        binding.btnLogin.setEnabled(false);
        binding.btnLogin.setText("Loading...");
        binding.btnLogin.setAlpha(0.7f);
        binding.progressBar.setVisibility(View.VISIBLE);
    }

    /**
     * Hide loading state and restore button
     */
    private void hideButtonLoading() {
        binding.progressBar.setVisibility(View.GONE);

        // Restore appropriate button text based on current state
        if (binding.llPin.getVisibility() == View.VISIBLE && selectedDriver != null) {
            String buttonText = !selectedDriver.getHasPinSetup() ? "Create PIN" : "Continue";
            updateButton(buttonText, binding.etPin.getText().toString().length() == 4);
        } else if (binding.llVehicle.getVisibility() == View.VISIBLE) {
            updateButton("Start session", selectedVehicle != null);
        } else {
            updateButton("Select driver", false);
        }
    }

    /**
     * Handle button click based on current state
     */
    private void handleButtonClick() {
        if (selectedDriver == null || isLoading) return;

        if (binding.llPin.getVisibility() == View.VISIBLE) {
            // PIN authentication/setup
            String pin = binding.etPin.getText().toString().trim();
            if (pin.length() == 4) {
                // Show loading state
                showButtonLoading();

                if (selectedDriver.getHasPinSetup()) {
                    Log.d(TAG, "Authenticating driver: " + selectedDriver.getName());
                    authViewModel.authenticateDriver(selectedDriver.getDriverId(), pin);
                } else {
                    Log.d(TAG, "Setting up PIN for new driver: " + selectedDriver.getName());
                    authViewModel.setupDriverPin(selectedDriver.getDriverId(), pin);
                }
            }
        } else if (selectedVehicle != null) {
            // Start session
            showButtonLoading();
            authViewModel.startSession(selectedVehicle.getVehicleId());
        }
    }

    /**
     * Handle API results
     */
    private void handleDriversResult(UiState<List<Driver>> state) {
        if (state != null && state.isSuccess()) {
            availableDrivers = state.getData();
            driversLoaded = true;
            lastDriversLoadTime = System.currentTimeMillis();

            ArrayAdapter<Driver> adapter = new ArrayAdapter<>(this, R.layout.dropdown_item_driver, availableDrivers);
            binding.etDriver.setAdapter(adapter);
            binding.progressBar.setVisibility(View.GONE);

            // Save to persistent cache
            saveCachedDrivers(availableDrivers);
        }
    }

    private void handleVehiclesResult(UiState<List<Vehicle>> state) {
        if (state != null && state.isSuccess()) {
            availableVehicles = state.getData();
            vehiclesLoaded = true;
            lastVehiclesLoadTime = System.currentTimeMillis();

            ArrayAdapter<Vehicle> adapter = new ArrayAdapter<>(this, R.layout.dropdown_item_vehicle, availableVehicles);
            binding.etVehicle.setAdapter(adapter);
            binding.progressBar.setVisibility(View.GONE);

            // Save to persistent cache
            saveCachedVehicles(availableVehicles);
        }
    }

    private void handleAuthResult(UiState<String> state) {
        if (state != null && state.isSuccess()) {
            // Check if this was PIN setup or authentication
            boolean wasPinSetup = selectedDriver != null && !selectedDriver.getHasPinSetup();

            if (wasPinSetup) {
                Toast.makeText(this, "PIN created successfully! " + selectedDriver.getName() + " is now set up.", Toast.LENGTH_LONG).show();
                // Update driver's PIN setup status locally for this session
                selectedDriver.setHasPinSetup(true);
            } else {
                Toast.makeText(this, "Authentication successful!", Toast.LENGTH_SHORT).show();
            }

            showVehicleSelection();
            hideButtonLoading();
        } else if (state != null && state.isError()) {
            String errorMessage = "Authentication failed";
            boolean wasPinSetup = selectedDriver != null && !selectedDriver.getHasPinSetup();

            if (wasPinSetup) {
                errorMessage = "PIN creation failed. Please try again.";
            }

            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
            binding.etPin.setText("");
            binding.etPin.requestFocus();
            hideButtonLoading();
        }
    }

    private void handleSessionResult(UiState<?> state) {
        if (state != null && state.isSuccess()) {
            Toast.makeText(this, "Session started!", Toast.LENGTH_SHORT).show();
            navigateToTripDash();
            hideButtonLoading();
        }
    }

    // =================== UI HELPERS ===================

    private void showMainForm() {
        if (binding.rlMain.getVisibility() != View.VISIBLE) {
            binding.rlMain.setVisibility(View.VISIBLE);
            binding.btnLogin.setVisibility(View.VISIBLE);

            binding.rlMain.setAlpha(0f);
            binding.rlMain.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator(1.2f))
                    .start();
        }
    }

    private void showFieldAnimated(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            view.setVisibility(View.VISIBLE);
            view.setAlpha(0f);
            view.setTranslationY(15f);
            view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(250)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .start();
        }
    }

    private void hideFieldAnimated(View view) {
        if (view.getVisibility() == View.VISIBLE) {
            view.animate()
                    .alpha(0f)
                    .translationY(-10f)
                    .setDuration(150)
                    .withEndAction(() -> {
                        view.setVisibility(View.GONE);
                        view.setTranslationY(0f);
                    })
                    .start();
        }
    }

    private void updateButton(String text, boolean enabled) {
        binding.btnLogin.setText(text);
        binding.btnLogin.setEnabled(enabled);
        binding.btnLogin.setAlpha(enabled ? 1f : 0.5f);
    }

    private void showAuthCheckingStatus() {
        if (binding.tvSplashStatus != null) {
            binding.tvSplashStatus.setText("Checking authentication...");
            binding.tvSplashStatus.setVisibility(View.VISIBLE);
            binding.tvSplashStatus.setAlpha(0f);
            binding.tvSplashStatus.animate().alpha(0.8f).setDuration(300).start();
        }
    }

    private void hideAuthCheckingStatus() {
        if (binding.tvSplashStatus != null) {
            binding.tvSplashStatus.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> binding.tvSplashStatus.setVisibility(View.GONE))
                    .start();
        }
    }

    // =================== AUTH STATE CACHING ===================

    private AuthState getCachedAuthState() {
        SharedPreferences prefs = getSharedPreferences("AuthCache", MODE_PRIVATE);
        String state = prefs.getString("authState", "");
        try {
            return state.isEmpty() ? null : AuthState.valueOf(state);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void cacheAuthState(AuthState authState) {
        SharedPreferences.Editor editor = getSharedPreferences("AuthCache", MODE_PRIVATE).edit();
        editor.putString("authState", authState.name());
        editor.apply();
    }

    // =================== NAVIGATION ===================

    private void navigateToAuthActivity() {
        Intent intent = new Intent(this, AuthActivity.class);
        startActivity(intent);
        finish();
    }

    private void navigateToTripDash() {
        Intent intent = new Intent(this, TripDash.class);
        startActivity(intent);
        finish();
    }

    // =================== PERMISSIONS ===================

    private boolean checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

        if (Build.VERSION.SDK_INT < 34) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), REQUEST_ID_MULTIPLE_PERMISSIONS);
            return true;
        }

        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ID_MULTIPLE_PERMISSIONS) {
            initializeApp();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    // =================== CACHING METHODS ===================

    private void initializeCache() {
        // Load cached drivers and vehicles from SharedPreferences
        loadCachedDrivers();
        loadCachedVehicles();
    }

    private void saveCachedDrivers(List<Driver> drivers) {
        SharedPreferences prefs = getSharedPreferences("DriversCache", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // Simple JSON serialization - in production, consider using Gson
        StringBuilder driversJson = new StringBuilder("[");
        for (int i = 0; i < drivers.size(); i++) {
            Driver driver = drivers.get(i);
            driversJson.append("{")
                    .append("\"driverId\":\"").append(driver.getDriverId()).append("\",")
                    .append("\"name\":\"").append(driver.getName()).append("\",")
                    .append("\"isActive\":").append(driver.isActive()).append(",")
                    .append("\"hasPinSetup\":").append(driver.getHasPinSetup())
                    .append("}");
            if (i < drivers.size() - 1) driversJson.append(",");
        }
        driversJson.append("]");

        editor.putString("drivers", driversJson.toString());
        editor.putLong("driversTimestamp", System.currentTimeMillis());
        editor.apply();

        Log.d(TAG, "Cached " + drivers.size() + " drivers");
    }

    private void loadCachedDrivers() {
        SharedPreferences prefs = getSharedPreferences("DriversCache", MODE_PRIVATE);
        String driversJson = prefs.getString("drivers", "");
        long timestamp = prefs.getLong("driversTimestamp", 0);

        // Check if cache is still valid
        if (!driversJson.isEmpty() && System.currentTimeMillis() - timestamp < CACHE_EXPIRY_TIME) {
            // Parse simple JSON format
            availableDrivers = parseCachedDrivers(driversJson);
            if (!availableDrivers.isEmpty()) {
                driversLoaded = true;
                lastDriversLoadTime = timestamp;
                Log.d(TAG, "Loaded " + availableDrivers.size() + " cached drivers");
            }
        }
    }

    private List<Driver> parseCachedDrivers(String json) {
        List<Driver> drivers = new ArrayList<>();
        // Simple JSON parsing - in production, use proper JSON library
        try {
            json = json.trim();
            if (json.startsWith("[") && json.endsWith("]")) {
                json = json.substring(1, json.length() - 1); // Remove brackets
                String[] driverEntries = json.split("},\\{");

                for (String entry : driverEntries) {
                    entry = entry.replace("{", "").replace("}", "");
                    String[] fields = entry.split(",");

                    String driverId = "", name = "";
                    boolean isActive = true, hasPinSetup = false;

                    for (String field : fields) {
                        String[] keyValue = field.split(":");
                        if (keyValue.length == 2) {
                            String key = keyValue[0].replace("\"", "").trim();
                            String value = keyValue[1].replace("\"", "").trim();

                            switch (key) {
                                case "driverId":
                                    driverId = value;
                                    break;
                                case "name":
                                    name = value;
                                    break;
                                case "isActive":
                                    isActive = Boolean.parseBoolean(value);
                                    break;
                                case "hasPinSetup":
                                    hasPinSetup = Boolean.parseBoolean(value);
                                    break;
                            }
                        }
                    }

                    if (!driverId.isEmpty() && !name.isEmpty()) {
                        drivers.add(new Driver(driverId, name, isActive, hasPinSetup, System.currentTimeMillis()));
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing cached drivers", e);
        }
        return drivers;
    }

    private void saveCachedVehicles(List<Vehicle> vehicles) {
        SharedPreferences prefs = getSharedPreferences("VehiclesCache", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // Simple JSON serialization
        StringBuilder vehiclesJson = new StringBuilder("[");
        for (int i = 0; i < vehicles.size(); i++) {
            Vehicle vehicle = vehicles.get(i);
            vehiclesJson.append("{")
                    .append("\"vehicleId\":\"").append(vehicle.getVehicleId()).append("\",")
                    .append("\"registrationNumber\":\"").append(vehicle.getRegistrationNumber()).append("\"")
                    .append("}");
            if (i < vehicles.size() - 1) vehiclesJson.append(",");
        }
        vehiclesJson.append("]");

        editor.putString("vehicles", vehiclesJson.toString());
        editor.putLong("vehiclesTimestamp", System.currentTimeMillis());
        editor.apply();

        Log.d(TAG, "Cached " + vehicles.size() + " vehicles");
    }

    private void loadCachedVehicles() {
        SharedPreferences prefs = getSharedPreferences("VehiclesCache", MODE_PRIVATE);
        String vehiclesJson = prefs.getString("vehicles", "");
        long timestamp = prefs.getLong("vehiclesTimestamp", 0);

        // Check if cache is still valid
        if (!vehiclesJson.isEmpty() && System.currentTimeMillis() - timestamp < CACHE_EXPIRY_TIME) {
            // Parse simple JSON format
            availableVehicles = parseCachedVehicles(vehiclesJson);
            if (!availableVehicles.isEmpty()) {
                vehiclesLoaded = true;
                lastVehiclesLoadTime = timestamp;
                Log.d(TAG, "Loaded " + availableVehicles.size() + " cached vehicles");
            }
        }
    }

    private List<Vehicle> parseCachedVehicles(String json) {
        List<Vehicle> vehicles = new ArrayList<>();
        // Simple JSON parsing
        try {
            json = json.trim();
            if (json.startsWith("[") && json.endsWith("]")) {
                json = json.substring(1, json.length() - 1); // Remove brackets
                String[] vehicleEntries = json.split("},\\{");

                for (String entry : vehicleEntries) {
                    entry = entry.replace("{", "").replace("}", "");
                    String[] fields = entry.split(",");

                    String vehicleId = "", registrationNumber = "";

                    for (String field : fields) {
                        String[] keyValue = field.split(":");
                        if (keyValue.length == 2) {
                            String key = keyValue[0].replace("\"", "").trim();
                            String value = keyValue[1].replace("\"", "").trim();

                            switch (key) {
                                case "vehicleId":
                                    vehicleId = value;
                                    break;
                                case "registrationNumber":
                                    registrationNumber = value;
                                    break;
                            }
                        }
                    }

                    if (!vehicleId.isEmpty() && !registrationNumber.isEmpty()) {
                        Vehicle vehicle = new Vehicle();
                        vehicle.setVehicleId(vehicleId);
                        vehicle.setRegistrationNumber(registrationNumber);
                        vehicle.setCreatedAtFromTimestamp(System.currentTimeMillis());
                        vehicles.add(vehicle);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing cached vehicles", e);
        }
        return vehicles;
    }
}
