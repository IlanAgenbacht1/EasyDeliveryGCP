package com.clone.EasyDelivery.Activity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.clone.EasyDelivery.Adapter.HeaderAdapter;
import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.databinding.ActivityDashHeaderBinding;
import com.clone.EasyDelivery.viewmodel.AuthViewModel;
import com.clone.EasyDelivery.viewmodel.DeliveryViewModel;
import com.clone.EasyDelivery.viewmodel.UserStateViewModel;
import com.clone.EasyDelivery.viewmodel.models.UiState;
import com.clone.EasyDelivery.data.entities.DeliveryEntity;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Modern DashHeader Activity using ViewModels for offline-first delivery management
 * Displays deliveries list and handles navigation to detailed delivery views
 *
 * Uses Hilt for proper dependency injection into ViewModels
 */
@AndroidEntryPoint
public class DashHeader extends AppCompatActivity {

    private static final String TAG = "DashHeader";
    
    // ViewBinding
    private ActivityDashHeaderBinding binding;
    
    // Adapters and data
    private HeaderAdapter adapter;
    private List<DeliveryEntity> deliveryList;
    
    // ViewModels
    private DeliveryViewModel deliveryViewModel;
    private AuthViewModel authViewModel;
    private UserStateViewModel userStateViewModel;

    // Dependency injection - manually initialized
    private com.clone.EasyDelivery.util.SharedPreferencesManager preferencesManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize ViewBinding
        binding = ActivityDashHeaderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize dependencies manually (instead of @Inject)
        preferencesManager = new com.clone.EasyDelivery.util.SharedPreferencesManager(this);

        // Setup RecyclerView
        binding.rvDocument.setLayoutManager(new LinearLayoutManager(this));

        // Initialize data
        deliveryList = new ArrayList<>();
        
        // Initialize ViewModels
        initializeViewModels();
        
        // Setup adapter with modern click handling
        setupModernAdapter();
        
        binding.rvDocument.setAdapter(adapter);
        
        // Setup observers and load deliveries
        setupObservers();
        loadDeliveriesUsingViewModel();
        
        Log.i(TAG, "✅ INSTANT: Trip " + getCurrentTripId() + " ready with offline-first architecture");
    }

    private void setupModernAdapter() {
        adapter = new HeaderAdapter(deliveryList, delivery -> {
            Log.i(TAG, "Delivery selected: " + delivery.getDeliveryId());
            
            // Set current delivery in ViewModels
            deliveryViewModel.selectDelivery(delivery.getDeliveryId());
            
            // Update UserStateViewModel with current delivery context
            String tripId = userStateViewModel.getCurrentTripIdValue();
            String sessionId = authViewModel.getCurrentSessionId();
            userStateViewModel.setCurrentDelivery(delivery.getDeliveryId(), tripId, sessionId);
            
            // Navigate to Dash activity
            Intent intent = new Intent(DashHeader.this, Dash.class);
            intent.putExtra("deliveryId", delivery.getDeliveryId());
            startActivity(intent);
            finish();
        });
        // Pass UserStateViewModel to adapter for proper state management
        adapter.setUserStateViewModel(userStateViewModel);
    }
    
    // =================== VIEWMODEL SETUP ===================
    
    private void initializeViewModels() {
        deliveryViewModel = new ViewModelProvider(this).get(DeliveryViewModel.class);
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        userStateViewModel = new ViewModelProvider(this).get(UserStateViewModel.class);
        Log.i(TAG, "ViewModels initialized for offline-first architecture");
    }
    
    private void setupObservers() {
        // Observe delivery data changes
        deliveryViewModel.getDeliveriesState().observe(this, deliveriesState -> {
            if (deliveriesState != null) {
                handleDeliveryStateChange(deliveriesState);
            }
        });
        
        // Observe authentication state
        authViewModel.getAuthState().observe(this, authState -> {
            if (authState != null) {
                Log.d(TAG, "Auth state: " + authState);
                // Handle auth state changes if needed
            }
        });
        
        // Observe loading state
        deliveryViewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading != null) {
                handleLoadingStateChange(isLoading);
            }
        });
    }

    private void handleDeliveryStateChange(UiState<List<DeliveryEntity>> deliveriesState) {
        if (deliveriesState.isSuccess() && deliveriesState.hasData()) {
            // DIRECT modern entity usage - NO CONVERSION GARBAGE!
            List<DeliveryEntity> modernDeliveries = deliveriesState.getData();

            Log.i(TAG, "Loaded " + modernDeliveries.size() + " deliveries via MODERN ViewModel");

            // Update MODERN adapter directly
            deliveryList.clear();
            deliveryList.addAll(modernDeliveries);
            adapter.notifyDataSetChanged();
            
            // Hide loading indicator
            showLoadingState(false);
            
        } else if (deliveriesState.isError()) {
            Log.e(TAG, "Error loading deliveries: " + deliveriesState.getErrorMessage());
            
            showLoadingState(false);
            showErrorMessage("Failed to load deliveries: " + deliveriesState.getErrorMessage());
            
        } else if (deliveriesState.isLoading()) {
            Log.d(TAG, "Loading deliveries...");
            showLoadingState(true);
        }
    }
    
    private void showErrorMessage(String message) {
        // Show error message to user - could be a Toast or update UI
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
    }
    
    private void handleLoadingStateChange(boolean isLoading) {
        showLoadingState(isLoading);
    }
    
    private void showLoadingState(boolean isLoading) {
        // If your layout has a loading indicator, show/hide it here
        Log.d(TAG, "Loading state: " + isLoading);
    }
    
    private void loadDeliveriesUsingViewModel() {
        String currentTripId = getCurrentTripId();
        
        if (currentTripId != null && !currentTripId.trim().isEmpty()) {
            Log.i(TAG, "Loading deliveries via ViewModel for trip: " + currentTripId);
            deliveryViewModel.loadDeliveriesForTrip(currentTripId, false);
        } else {
            Log.w(TAG, "No current trip ID - falling back to legacy loading");
        }
    }
    
    private String getCurrentTripId() {
        // Get trip ID from UserStateViewModel (primary source)
        String tripId = userStateViewModel.getCurrentTripIdValue();
        
        if (tripId != null && !tripId.isEmpty()) {
            return tripId;
        }
        
        // Try to get from DeliveryViewModel as secondary source
        String vmTripId = deliveryViewModel.getCurrentTripId();
        if (vmTripId != null && !vmTripId.isEmpty()) {
            // Update UserStateViewModel with the found trip ID
            userStateViewModel.setCurrentTripId(vmTripId);
            return vmTripId;
        }
        
        // Try to get from AuthViewModel session
        String sessionId = authViewModel.getCurrentSessionId();
        if (sessionId != null && !sessionId.isEmpty()) {
            // In some cases, session ID might be the trip ID
            userStateViewModel.setCurrentTripId(sessionId);
            return sessionId;
        }
        
        Log.w(TAG, "No trip ID found in any ViewModel");
        return null;
    }
    
    // Legacy database loading method removed - now fully MVVM
    // All data loading handled through ViewModels and Repository pattern
    

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        //super.onBackPressed();

        AlertDialog alertDialog = new AlertDialog.Builder(DashHeader.this, R.style.AlertDialogStyle).create();

        alertDialog.setTitle("Trip Selection");

        alertDialog.setMessage("Return to trip selection screen?");

        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "No",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                        dialog.dismiss();
                    }
                });

        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Yes",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        // Handle trip cleanup with ViewModels only
                        String currentTripId = getCurrentTripId();
                        
                        // Clear current delivery and trip context in ViewModels
                        deliveryViewModel.clearCurrentDelivery();
                        userStateViewModel.clearCurrentContext();
                        
                        Log.i(TAG, "User backed out - cleared trip context: " + currentTripId);
                        
                        // Navigate back to trip selection
                        Intent intent = new Intent(DashHeader.this, TripDash.class);
                        startActivity(intent);
                        finish();
                    }
                });

        alertDialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up ViewBinding
        if (binding != null) {
            binding = null;
        }
        
        Log.d(TAG, "DashHeader destroyed, resources cleaned up");
    }
}
