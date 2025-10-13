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
import com.clone.EasyDelivery.Utility.AppConstant;
import com.clone.EasyDelivery.Utility.ScheduleHelper;
// SyncConstant merged into AppConstant
import com.clone.EasyDelivery.Utility.UnifiedTripManager;
import com.clone.EasyDelivery.Firebase.Models.FirestoreTrip;
import com.google.firebase.auth.FirebaseAuth;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import com.clone.EasyDelivery.databinding.ActivityTripDashBinding;

import java.util.ArrayList;

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



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_dash);

        binding = ActivityTripDashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        AppConstant.loadCompletedTripsFromStorage(this);
        UnifiedTripManager.getInstance(this);

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

                AppConstant.TRIPID = tripName;

                startTrip(tripName);
            }
        });

        recyclerView.setAdapter(adapter);

        //checkConnectivity();

        textHandler = new Handler(Looper.getMainLooper());
        loop();
    }


    public void loop() {
        tripUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                // Use modern Firestore async API
                UnifiedTripManager tripManager = UnifiedTripManager.getInstance(TripDash.this);
                
                tripManager.getAvailableTrips()
                    .thenAccept(firestoreTrips -> {
                        // Convert FirestoreTrips to trip IDs for UI
                        ArrayList<String> newTrips = firestoreTrips.stream()
                            .map(FirestoreTrip::getTripId)
                            .collect(Collectors.toCollection(ArrayList::new));
                        
                        textHandler.post(() -> {
                            try {
                                // 🔧 STABILITY: Only update UI if trips actually changed
                                boolean tripsChanged = !newTrips.equals(lastKnownTrips);
                                
                                if (tripsChanged) {
                                    Log.i("TripDash", "🔄 FIRESTORE_UI: Trip list changed from " + lastKnownTrips.size() + " to " + newTrips.size() + " trips");
                                    Log.d("TripDash", "🔄 FIRESTORE_UI: Previous: " + lastKnownTrips + ", New: " + newTrips);
                                    
                                    adapter.updateTrips(newTrips);
                                    lastKnownTrips = new ArrayList<>(newTrips);
                                    stableUpdateCount = 0;
                                } else {
                                    stableUpdateCount++;
                                    Log.v("TripDash", "🔄 FIRESTORE_UI: No changes detected (stable for " + (stableUpdateCount * 5) + "s)");
                                }

                                if (!newTrips.isEmpty() && !layoutAnimated) {
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
                                } else if (newTrips.isEmpty()) {
                                    checkFirebaseConnectivity();
                                }
                                
                            } catch (Exception e) {
                                Log.e("TripDash", "Error updating UI in trip loop", e);
                            }
                            
                            // 🔧 ADAPTIVE POLLING: Use longer intervals when trips are stable
                            long nextPollInterval;
                            if (stableUpdateCount > 10) {
                                // After 50 seconds of stability, poll every 15 seconds
                                nextPollInterval = 15000;
                            } else if (stableUpdateCount > 3) {
                                // After 15 seconds of stability, poll every 8 seconds  
                                nextPollInterval = 8000;
                            } else {
                                // Recent changes or first few updates, poll every 5 seconds
                                nextPollInterval = 5000;
                            }
                            
                            textHandler.postDelayed(this, nextPollInterval);
                        });
                    })
                    .exceptionally(throwable -> {
                        Log.e("TripDash", "Error fetching trips from Firestore", throwable);
                        textHandler.post(() -> {
                            checkFirebaseConnectivity();
                            textHandler.postDelayed(this, 10000); // Retry in 10 seconds
                        });
                        return null;
                    });
            }
        };
        // Start the first run
        textHandler.post(tripUpdateRunnable);
    }


    private void checkFirebaseConnectivity() {
        UnifiedTripManager tripManager = UnifiedTripManager.getInstance(TripDash.this);
        String syncStatus = tripManager.getSyncStatus();
        
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            title.setText("NOT AUTHENTICATED - PLEASE LOGIN");
        } else if (syncStatus.contains("not available")) {
            title.setText("FIREBASE UNAVAILABLE - NO TRIPS");
        } else {
            title.setText("NO TRIPS AVAILABLE");
        }
    }


    public void startTrip(String trip) {

        AppConstant.STARTED_TRIP = AppConstant.TRIPID;
        ScheduleHelper.getSchedule(this, AppConstant.TRIPID);
        textHandler.removeCallbacksAndMessages(null);
        
        new Thread(() -> {
            try {
                UnifiedTripManager tripManager = UnifiedTripManager.getInstance(TripDash.this);
                tripManager.claimAndStartTrip(AppConstant.TRIPID);
            } catch (Exception e) {
                Log.e("TripDash", "Error in unified trip operations", e);
            }
        }).start();

        startActivity(new Intent(TripDash.this, DashHeader.class));
        finish();
    }


    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        //super.onBackPressed();

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

                            startActivity(new Intent(TripDash.this, SplashLogin.class));
                            finish();
                        }
                    });

            alertDialog.show();
        }
    }


    private void shrinkFab() {
        binding.transparentBg.startAnimation(toBottomBgAnim);
        //binding.mainFabBtn.startAnimation(rotateAntiClockWiseFabAnim);
        //binding.galleryFabBtn.startAnimation(toBottomFabAnim);
        //binding.shareFabBtn.startAnimation(toBottomFabAnim);
        //binding.sendFabBtn.startAnimation(toBottomFabAnim);
        //binding.galleryTv.startAnimation(toBottomFabAnim);
        binding.itemReturn.startAnimation(toBottomFabAnim);
        //binding.sendTv.startAnimation(toBottomFabAnim);
        isExpanded = false;
    }

    private void expandFab() {
        binding.transparentBg.startAnimation(fromBottomBgAnim);
        //binding.mainFabBtn.startAnimation(rotateClockWiseFabAnim);
       // binding.galleryFabBtn.startAnimation(fromBottomFabAnim);
        //binding.shareFabBtn.startAnimation(fromBottomFabAnim);
        //binding.sendFabBtn.startAnimation(fromBottomFabAnim);
        //binding.galleryTv.startAnimation(fromBottomFabAnim);
        binding.itemReturn.startAnimation(fromBottomFabAnim);
        //binding.sendTv.startAnimation(fromBottomFabAnim);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();

        textHandler.removeCallbacksAndMessages(null);
    }
    
    // Device ID functionality moved to UnifiedTripManager
}
