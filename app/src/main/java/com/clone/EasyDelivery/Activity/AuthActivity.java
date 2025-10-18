package com.clone.EasyDelivery.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.viewmodel.AuthViewModel;
import com.clone.EasyDelivery.viewmodel.models.UiState;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Simplified Authentication Activity for device enrollment and registration only
 * Handles the enrollment key validation and device registration process
 *
 * Uses Hilt for proper dependency injection into ViewModels
 */
@AndroidEntryPoint
public class AuthActivity extends AppCompatActivity {
    
    private static final String TAG = "AuthActivity";
    
    // ViewModels
    private AuthViewModel authViewModel;
    
    // UI Components
    private EditText etEnrollmentKey;
    private Button btnValidateKey;
    private Button btnRegisterDevice;
    private ProgressBar progressBar;
    private TextView tvStatus;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        
        // Initialize ViewModel
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        
        // Initialize UI
        initializeViews();
        setupListeners();
        setupObservers();

        Log.i(TAG, "AuthActivity initialized for device enrollment");
    }
    
    private void initializeViews() {
        // Input fields
        etEnrollmentKey = findViewById(R.id.et_enrollment_key);
        
        // Buttons
        btnValidateKey = findViewById(R.id.btn_validate_key);
        btnRegisterDevice = findViewById(R.id.btn_register_device);
        
        // Status displays
        progressBar = findViewById(R.id.progress_bar);
        tvStatus =findViewById(R.id.tv_status);
        
        // Initialize UI state
        updateUIForEnrollmentStep();
    }
    
    private void setupListeners() {
        // Enrollment key validation on text change
        etEnrollmentKey.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Reset validation state when user types
                btnRegisterDevice.setVisibility(View.GONE);
                btnValidateKey.setEnabled(s.length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Validate enrollment key
        btnValidateKey.setOnClickListener(v -> {
            String enrollmentKey = etEnrollmentKey.getText().toString().trim();
            if (!enrollmentKey.isEmpty()) {
                authViewModel.validateEnrollmentKey(enrollmentKey);
            } else {
                showError("Please enter an enrollment key");
            }
        });

        // Register device
        btnRegisterDevice.setOnClickListener(v -> {
            String enrollmentKey = etEnrollmentKey.getText().toString().trim();
            if (!enrollmentKey.isEmpty()) {
                authViewModel.registerDevice(enrollmentKey);
            } else {
                showError("Please enter an enrollment key");
            }
        });
    }
    
    private void setupObservers() {
        // Observe loading state
        authViewModel.getIsLoading().observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnValidateKey.setEnabled(!isLoading && !etEnrollmentKey.getText().toString().trim().isEmpty());
            btnRegisterDevice.setEnabled(!isLoading);
            etEnrollmentKey.setEnabled(!isLoading);
        });
        
        // Observe enrollment key validation
        authViewModel.getEnrollmentKeyValidationState().observe(this, state -> {
            handleEnrollmentValidation(state);
        });
        
        // Observe device registration
        authViewModel.getDeviceRegistrationState().observe(this, state -> {
            handleDeviceRegistration(state);
        });

        // Observe error messages
        authViewModel.getErrorMessage().observe(this, errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                showError(errorMessage);
            }
        });
    }

    private void handleEnrollmentValidation(UiState<String> state) {
        if (state == null) return;

        if (state.isLoading()) {
            tvStatus.setText("Validating enrollment key...");
            tvStatus.setTextColor(getResources().getColor(android.R.color.white));
        } else if (state.isSuccess()) {
            // Display the success message from the ViewModel (includes registration progress)
            tvStatus.setText("✓ " + state.getData());
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_light));

            // Hide validate button since process is automated now
            btnValidateKey.setVisibility(View.GONE);
            btnRegisterDevice.setVisibility(View.GONE);

        } else if (state.isError()) {
            tvStatus.setText("✗ " + state.getErrorMessage());
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));

            // Show validate button again for retry
            btnValidateKey.setVisibility(View.VISIBLE);
            btnRegisterDevice.setVisibility(View.GONE);
            btnValidateKey.setEnabled(true);

            // Focus back to input field
            etEnrollmentKey.requestFocus();
        }
    }

    private void handleDeviceRegistration(UiState<String> state) {
        if (state == null) return;

        if (state.isLoading()) {
            tvStatus.setText("Registering device...");
            tvStatus.setTextColor(getResources().getColor(android.R.color.white));
        } else if (state.isSuccess()) {
            tvStatus.setText("✓ " + state.getData());
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_light));

            // Show success message and navigate to SplashLogin after delay
            Toast.makeText(this, "Device enrolled successfully! Redirecting to driver login...", Toast.LENGTH_LONG).show();

            // Navigate to SplashLogin after a short delay
            etEnrollmentKey.postDelayed(() -> {
                navigateToSplashLogin();
            }, 2000);

        } else if (state.isError()) {
            tvStatus.setText("✗ Registration failed: " + state.getErrorMessage());
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));

            // Show validate button again for retry
            btnValidateKey.setVisibility(View.VISIBLE);
            btnRegisterDevice.setVisibility(View.GONE);
            btnValidateKey.setEnabled(true);
        }
    }

    private void updateUIForEnrollmentStep() {
        //tvStatus.setText("Enter your enrollment key to register this device.");
        tvStatus.setTextColor(getResources().getColor(android.R.color.white));

        btnValidateKey.setVisibility(View.VISIBLE);
        btnRegisterDevice.setVisibility(View.GONE);
        btnValidateKey.setEnabled(false);
    }

    private void showError(String message) {
        Log.w(TAG, "Error: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void navigateToSplashLogin() {
        Log.i(TAG, "Device enrollment complete, navigating to SplashLogin");
        Intent intent = new Intent(this, SplashLogin.class);
        // Clear the back stack since enrollment is complete
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    @Override
    public void onBackPressed() {
        // Allow back navigation during enrollment (user might want to exit the app)
        super.onBackPressed();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Reset states when leaving activity
        if (authViewModel != null) {
            authViewModel.resetEnrollmentState();
            authViewModel.resetRegistrationState();
        }
    }
}