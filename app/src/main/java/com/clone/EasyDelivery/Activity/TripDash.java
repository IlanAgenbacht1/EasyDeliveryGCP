package com.clone.EasyDelivery.Activity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.EasyDelivery.Adapter.TripAdapter;
import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.databinding.ActivityTripDashBinding;
import com.clone.EasyDelivery.sync.models.SyncStatus;
import com.clone.EasyDelivery.viewmodel.AuthViewModel;
import com.clone.EasyDelivery.viewmodel.TripViewModel;
import com.clone.EasyDelivery.viewmodel.UserStateViewModel;
import com.clone.EasyDelivery.viewmodel.models.UiState;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import com.clone.EasyDelivery.databinding.ActivityTripDashBinding;

import java.util.ArrayList;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Modern TripDash Activity using ViewModels and offline-first architecture
 * Handles trip selection and management with real-time sync
 *
 * Uses Hilt for proper dependency injection into ViewModels
 */
@AndroidEntryPoint
public class TripDash extends AppCompatActivity {

    TextView title;
    RecyclerView recyclerView;
    public static TripAdapter adapter;
    ProgressBar loadingIcon;
    ImageView logo;
    ConstraintLayout layout;
    boolean layoutAnimated;

    private @NonNull ActivityTripDashBinding binding;
    private boolean isExpanded = false;
    private Handler textHandler;
    private Runnable tripUpdateRunnable;
    private Animation fromBottomFabAnim;
    private Animation toBottomFabAnim;
    private Animation fromBottomBgAnim;
    private Animation toBottomBgAnim;
    
    // 🔧 STABILITY: Track trip list stability to reduce unnecessary updates
    private ArrayList<String> lastKnownTrips = new ArrayList<>();
    private int stableUpdateCount = 0;
    
    // State
    private static final String TAG = "TripDash";
    
    // ViewModels for offline-first architecture
    private TripViewModel tripViewModel;
    private AuthViewModel authViewModel;
    private UserStateViewModel userStateViewModel;

    // Dependency injection - manually initialized
    private com.clone.EasyDelivery.util.SharedPreferencesManager preferencesManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_dash);

        binding = ActivityTripDashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize dependencies manually (instead of @Inject)
        preferencesManager = new com.clone.EasyDelivery.util.SharedPreferencesManager(this);

        // Initialize ViewModels for offline-first data access
        initializeViewModels();

        fromBottomFabAnim = AnimationUtils.loadAnimation(this, R.anim.from_bottom_fab);
        toBottomFabAnim = AnimationUtils.loadAnimation(this, R.anim.to_bottom_fab);
        fromBottomBgAnim = AnimationUtils.loadAnimation(this, R.anim.from_bottom_anim);
        toBottomBgAnim = AnimationUtils.loadAnimation(this, R.anim.to_bottom_anim);

        binding.mainFabBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isExpanded) {
                    shrinkFab();
                } else {
                    expandFab();
                }
            }
        });

        binding.fabReturn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                startActivity(new Intent(TripDash.this, ReturnDash.class));

                finish();
            }
        });

        recyclerView = findViewById(R.id.rv_trip);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        
        recyclerView.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());
        recyclerView.getItemAnimator().setChangeDuration(300);
        recyclerView.getItemAnimator().setMoveDuration(300);
        recyclerView.getItemAnimator().setAddDuration(400);
        recyclerView.getItemAnimator().setRemoveDuration(300);

        layout = findViewById(R.id.trip_dash_main);

        title = findViewById(R.id.tv_tripSelection);

        loadingIcon = findViewById(R.id.progressBarTrip);

        logo = findViewById(R.id.iv_logoTrip);

        layoutAnimated = false;

        adapter = new TripAdapter(this, new TripAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(String tripName) {

                recyclerView.setFocusable(false);
                recyclerView.setEnabled(false);
                
                logo.setVisibility(View.INVISIBLE);
                loadingIcon.setVisibility(View.VISIBLE);
                title.setText("STARTING TRIP " + tripName + "...");

                startTrip(tripName);
            }
        });

        recyclerView.setAdapter(adapter);

        textHandler = new Handler(Looper.getMainLooper());
        
        // NEW: Setup LiveData observers for offline-first data
        setupObservers();
        
        // UPDATED: Start with ViewModel-based approach, fallback to legacy loop
        loadTripsUsingViewModel();
    }


    /**
     * Legacy method kept for fallback - now triggers ViewModel refresh instead
     */
    public void loop() {
        Log.i(TAG, "Legacy loop called - redirecting to ViewModel refresh");
        
        String driverId = authViewModel.getCurrentDriverId();
        if (driverId != null && !driverId.isEmpty()) {
            // Force refresh from network
            tripViewModel.loadTripsForDriver(driverId, true);
        } else {
            Log.w(TAG, "No authenticated driver found");
            title.setText("NOT AUTHENTICATED - PLEASE LOGIN");
        }
    }


    private void checkApiConnectivity() {
        // Check authentication status through ViewModel
        if (authViewModel.getCurrentSessionId() == null) {
            title.setText("NOT AUTHENTICATED - PLEASE LOGIN");
        } else {
            // Check if trips are available using available method
            if (tripViewModel.hasTripsData()) {
                title.setText("TRIPS AVAILABLE");
            } else {
                title.setText("NO TRIPS AVAILABLE");
                // Try to refresh trips
                String driverId = authViewModel.getCurrentDriverId();
                if (driverId != null) {
                    tripViewModel.loadTripsForDriver(driverId, true);
                }
            }
        }
    }


    public void startTrip(String tripId) {
        Log.i(TAG, "Starting trip: " + tripId);
        
        textHandler.removeCallbacksAndMessages(null);
        
        // Update UserStateViewModel with selected trip
        String sessionId = authViewModel.getCurrentSessionId();
        userStateViewModel.setCurrentTripId(tripId);
        userStateViewModel.setCurrentSessionId(sessionId);
        
        // Use TripViewModel for offline-first trip start
        tripViewModel.startTrip(tripId);
        
        // Update UI for immediate feedback
        logo.setVisibility(View.INVISIBLE);
        loadingIcon.setVisibility(View.VISIBLE);
        title.setText("STARTING TRIP " + tripId + "...");
        recyclerView.setFocusable(false);
        recyclerView.setEnabled(false);
    }


    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {

        if (isExpanded) {

            shrinkFab();

        } else {

            AlertDialog alertDialog = new AlertDialog.Builder(TripDash.this, R.style.AlertDialogStyle).create();

            alertDialog.setTitle("Login");

            alertDialog.setMessage("Return to login screen?");

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
                            // Clear all user state when logging out
                            userStateViewModel.clearCurrentContext();
                            authViewModel.logout();
                            
                            startActivity(new Intent(TripDash.this, AuthActivity.class));
                            finish();
                        }
                    });

            alertDialog.show();
        }
    }


    private void shrinkFab() {
        binding.transparentBg.startAnimation(toBottomBgAnim);
        binding.itemReturn.startAnimation(toBottomFabAnim);
        isExpanded = false;
    }

    private void expandFab() {
        binding.transparentBg.startAnimation(fromBottomBgAnim);
        binding.itemReturn.startAnimation(fromBottomFabAnim);
        isExpanded = true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (isExpanded) {
                Rect outRect = new Rect();
                binding.fabConstraint.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    shrinkFab();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    // =================== VIEWMODEL SETUP ===================
    
    private void initializeViewModels() {
        // Initialize ViewModels with Hilt injection
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        tripViewModel = new ViewModelProvider(this).get(TripViewModel.class);
        userStateViewModel = new ViewModelProvider(this).get(UserStateViewModel.class);
        
        Log.i(TAG, "ViewModels initialized for offline-first architecture");
    }
    
    private void setupObservers() {
        // Observe trip data changes
        tripViewModel.getTripIdsForAdapter().observe(this, tripIds -> {
            if (tripIds != null) {
                textHandler.post(() -> {
                        try {
                            // Use existing adapter update logic
                            boolean tripsChanged = !tripIds.equals(lastKnownTrips);
                            
                            if (tripsChanged) {
                                Log.i(TAG, "🔄 VIEWMODEL: Trip list changed from " + lastKnownTrips.size() + " to " + tripIds.size() + " trips");
                                
                                adapter.updateTrips(tripIds);
                                lastKnownTrips = new ArrayList<>(tripIds);
                                stableUpdateCount = 0;
                                
                                // Existing animation logic
                                if (!tripIds.isEmpty() && !layoutAnimated) {
                                    showTripsAvailable();
                                } else if (tripIds.isEmpty()) {
                                    checkConnectivityStatus();
                                }
                            } else {
                                stableUpdateCount++;
                                Log.v(TAG, "🔄 VIEWMODEL: No changes detected (stable for " + (stableUpdateCount * 5) + "s)");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating UI from ViewModel", e);
                        }
                });
            }
        });
        
        // Observe trip operation results
        tripViewModel.getCurrentTripState().observe(this, tripState -> {
            if (tripState != null) {
                textHandler.post(() -> {
                    if (tripState.isSuccess()) {
                        // Trip started successfully - navigate to DashHeader
                        Log.i(TAG, "Trip started successfully via ViewModel");
                        
                        // Update UserStateViewModel with current trip context
                        String startedTripId = tripViewModel.getCurrentTripId();
                        String sessionId = authViewModel.getCurrentSessionId();
                        userStateViewModel.setCurrentTripId(startedTripId);
                        userStateViewModel.setCurrentSessionId(sessionId);
                        
                        startActivity(new Intent(TripDash.this, DashHeader.class));
                        finish();
                    } else if (tripState.isError()) {
                        // Reset UI state on error
                        logo.setVisibility(View.VISIBLE);
                        loadingIcon.setVisibility(View.INVISIBLE);
                        title.setText("ERROR: " + tripState.getErrorMessage());
                        recyclerView.setEnabled(true);
                        recyclerView.setFocusable(true);
                    } else if (tripState.isLoading()) {
                        // Handle loading state
                        logo.setVisibility(View.INVISIBLE);
                        loadingIcon.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
        
        // Observe sync status for offline indicators
        tripViewModel.getSyncStatus().observe(this, syncStatus -> {
            if (syncStatus != null) {
                Log.d(TAG, "Sync status: " + syncStatus);
                
                // Update UI based on sync status
                switch (syncStatus) {
                    case SYNCING:
                        // Could add sync indicator to UI
                        Log.d(TAG, "Syncing trips data...");
                        break;
                    case SUCCESS:
                        Log.d(TAG, "Trip data synchronized");
                        break;
                    case ERROR:
                        Log.w(TAG, "Sync error - using cached data");
                        break;
                    case IDLE:
                        Log.d(TAG, "Sync idle");
                        break;
                    default:
                        break;
                }
            }
        });
    }
    
    private void loadTripsUsingViewModel() {
        // Get current driver ID and load trips
        String driverId = authViewModel.getCurrentDriverId();
        if (driverId != null && !driverId.isEmpty()) {
            Log.i(TAG, "Loading trips via ViewModel for driver: " + driverId);
            tripViewModel.loadTripsForDriver(driverId, false);
        } else {
            Log.w(TAG, "No authenticated driver found");
            title.setText("NOT AUTHENTICATED - PLEASE LOGIN");
            
            // Navigate back to auth after a delay
            textHandler.postDelayed(() -> {
                Intent intent = new Intent(TripDash.this, AuthActivity.class);
                startActivity(intent);
                finish();
            }, 3000);
        }
    }
    
    private void showTripsAvailable() {
        loadingIcon.setVisibility(View.INVISIBLE);
        title.setVisibility(View.INVISIBLE);

        Animation fadeIn = new AlphaAnimation(0, 1);
        fadeIn.setInterpolator(new DecelerateInterpolator());
        fadeIn.setDuration(1000);
        fadeIn.setStartOffset(250);

        title.startAnimation(fadeIn);
        title.setText("SELECT TRIP");
        title.setVisibility(View.VISIBLE);
        layoutAnimated = true;
    }
    
    private void checkConnectivityStatus() {
        // Use existing connectivity check but with new messaging
        String errorMsg = getCurrentErrorMessage();
        if (errorMsg != null) {
            title.setText(errorMsg);
        } else {
            checkApiConnectivity();
        }
    }
    
    private String getCurrentErrorMessage() {
        return tripViewModel.getCurrentErrorMessage();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up handlers to prevent memory leaks
        if (textHandler != null) {
            textHandler.removeCallbacksAndMessages(null);
        }
        
        // ViewModels are automatically cleaned up by Architecture Components
        Log.d(TAG, "TripDash destroyed, resources cleaned up");
    }
}
