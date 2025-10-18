package com.clone.EasyDelivery.Activity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.databinding.ActivityReturnDashBinding;
import com.clone.EasyDelivery.viewmodel.AuthViewModel;
import com.clone.EasyDelivery.viewmodel.DeliveryViewModel;
import com.clone.EasyDelivery.viewmodel.TripViewModel;
import com.clone.EasyDelivery.viewmodel.UserStateViewModel;
import androidx.lifecycle.ViewModelProvider;

import javax.inject.Inject;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.File;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Modern ReturnDash Activity using ViewBinding and ViewModels
 * Handles return/damaged item processing
 */
@AndroidEntryPoint
public class ReturnDash extends AppCompatActivity {

    private static final String TAG = "ReturnDash";
    
    // ViewBinding
    private ActivityReturnDashBinding binding;
    
    // ViewModels
    private DeliveryViewModel deliveryViewModel;
    private AuthViewModel authViewModel;
    private TripViewModel tripViewModel;
    private UserStateViewModel userStateViewModel;
    
    // Dependency injection
    @Inject
    com.clone.EasyDelivery.util.SharedPreferencesManager preferencesManager;
    
    // Activity reference
    public static ReturnDash activity;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize ViewBinding
        binding = ActivityReturnDashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Initialize ViewModels
        initializeViewModels();
        
        // Set activity reference
        activity = this;
        
        // Setup click listeners
        setupClickListeners();
        
        Log.i(TAG, "ReturnDash Activity initialized with modern architecture");
    }
    
    private void initializeViewModels() {
        deliveryViewModel = new ViewModelProvider(this).get(DeliveryViewModel.class);
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        tripViewModel = new ViewModelProvider(this).get(TripViewModel.class);
        userStateViewModel = new ViewModelProvider(this).get(UserStateViewModel.class);
        
        // Setup observers for ViewModels
        setupObservers();
        
        Log.i(TAG, "ViewModels initialized for return processing");
    }
    
    private void setupObservers() {
        // Observe trip state changes
        tripViewModel.getCurrentTripState().observe(this, tripState -> {
            if (tripState != null && tripState.isError()) {
                Log.e(TAG, "Trip state error: " + tripState.getErrorMessage());
            }
        });
        
        // Observe loading states
        tripViewModel.getIsLoading().observe(this, isLoading -> {
            // Update loading UI if needed
            Log.d(TAG, "Loading state: " + isLoading);
        });
    }
    
    private void setupClickListeners() {
        // Barcode scanner click handler
        binding.buttonBarcode.setOnClickListener(view -> handleBarcodeClick());
        
        // Next/Submit button click handler
        binding.btnNext.setOnClickListener(view -> handleSubmitClick());
    }
    
    private void handleBarcodeClick() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setPrompt("Scan Barcode");
        integrator.setBeepEnabled(true);
        integrator.initiateScan();
    }
    
    private void handleSubmitClick() {
        if (validateForm()) {
            ReturnItemData returnData = createReturnData();
            
            // Save via ViewModels only
            saveReturnData(returnData);
            
            // Clear form after successful save
            clearForm();
            
            // Navigate back to trip dashboard
            startActivity(new Intent(this, TripDash.class));
            finish();
        }
    }
    
    private void clearForm() {
        binding.etItem.setText("");
        binding.etQty.setText("");
        binding.etCustomer.setText("");
        binding.etComment.setText("");
        binding.etReference.setText("");
    }

    private ReturnItemData createReturnData() {
        return new ReturnItemData(
                binding.etItem.getText().toString().trim(),
                binding.etQty.getText().toString(),
                binding.etCustomer.getText().toString().trim(),
                binding.etComment.getText().toString().trim(),
                binding.etReference.getText().toString().trim(),
                new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US).format(new Date())
        );
    }

    private void saveReturnData(ReturnItemData returnData) {
        try {
            // Get current trip context from UserStateViewModel
            String currentTripId = userStateViewModel.getCurrentTripIdValue();
            String currentSessionId = authViewModel.getCurrentSessionId();
            
            // Save via TripViewModel (handles return/reverse logistics)
            tripViewModel.recordReturnItem(currentTripId, returnData);
            Log.i(TAG, "Return data saved via TripViewModel for trip: " + currentTripId);
            
            // Show success message
            Toast.makeText(this, "Return recorded successfully", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving return data: " + e.getMessage(), e);
            Toast.makeText(this, "Error saving return data: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    private boolean validateForm() {
        try {
            // Check item/parcel field
            if (binding.etItem.getText().toString().trim().isEmpty()) {
                showEnlargedToast("Enter Item/Parcel");
                return false;
            }
            
            // Check quantity field
            if (binding.etQty.getText().toString().trim().isEmpty()) {
                showEnlargedToast("Enter Quantity");
                return false;
            }
            
            // Check customer field
            if (binding.etCustomer.getText().toString().trim().isEmpty()) {
                showEnlargedToast("Enter Customer");
                return false;
            }
            
            // Check comment/reason field
            if (binding.etComment.getText().toString().trim().isEmpty()) {
                showEnlargedToast("Enter Comment/Reason");
                return false;
            }
            
            // Check reference field
            if (binding.etReference.getText().toString().trim().isEmpty()) {
                showEnlargedToast("Enter Reference");
                return false;
            }
            
            // All validations passed
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Validation error: " + e.getMessage(), e);
            return false;
        }
    }
    
    private void showEnlargedToast(String text) {
        SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
        biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
        Toast.makeText(this, biggerText, Toast.LENGTH_LONG).show();
    }

    /**
     * MODERN return item data structure - Clean and simple!
     */
    private static class ReturnItemData {
        public final String item;
        public final String quantity;
        public final String customer;
        public final String comment;
        public final String reference;
        public final String timestamp;

        public ReturnItemData(String item, String quantity, String customer, String comment, String reference, String timestamp) {
            this.item = item;
            this.quantity = quantity;
            this.customer = customer;
            this.comment = comment;
            this.reference = reference;
            this.timestamp = timestamp;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if (result != null) {

            if (result.getContents() == null) {

                Log.d("Barcode Scanner", "Cancelled scan");

            } else {
                binding.etItem.setText(result.getContents().trim());
                Log.d(TAG, "Barcode scanned: " + result.getContents());
            }
        }
    }
    
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(this, TripDash.class));
        finish();
    }
    
    // =================== LIFECYCLE METHODS ===================
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up ViewBinding
        if (binding != null) {
            binding = null;
        }
        
        // Clear static reference
        if (activity == this) {
            activity = null;
        }
        
        Log.i(TAG, "ReturnDash Activity destroyed and resources cleaned up");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "ReturnDash Activity resumed");
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "ReturnDash Activity paused");
    }
}



















