package com.clone.EasyDelivery.Activity;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.RelativeLayout.LayoutParams;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.EasyDelivery.Activity.TripDash;
import com.clone.EasyDelivery.Adapter.PreviewAdapter;
import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.databinding.ActivityPreviewBinding;
import com.clone.EasyDelivery.util.ImageManager;
import com.clone.EasyDelivery.viewmodel.AuthViewModel;
import com.clone.EasyDelivery.viewmodel.DeliveryViewModel;
import com.clone.EasyDelivery.viewmodel.UserStateViewModel;
import com.clone.EasyDelivery.viewmodel.models.UiState;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.clone.EasyDelivery.viewmodel.UserStateViewModel;
import com.clone.EasyDelivery.viewmodel.models.UiState;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Modern Preview Activity - Complete delivery workflow implementation
 * Handles parcel validation, GPS capture, signature/photo collection, and delivery completion
 *
 * Uses Hilt for proper dependency injection into ViewModels
 */
@AndroidEntryPoint
public class Preview extends AppCompatActivity {

    private static final String TAG = "Preview";
    
    // ViewBinding
    private ActivityPreviewBinding binding;
    
    // ViewModels
    private DeliveryViewModel deliveryViewModel;
    private AuthViewModel authViewModel;
    private UserStateViewModel userStateViewModel;

    // Dependency injection - manually initialized
    private com.clone.EasyDelivery.util.SharedPreferencesManager preferencesManager;

    private ImageManager imageManager;
    
    // Adapters and data
    private PreviewAdapter adapter;
    private ArrayList<String> parcelId;
    private ArrayList<String> filePaths;
    
    // Data models
    private String strList;
    private String currentDate;
    private String currentDeliveryId;
    
    // File paths for attachments
    private String signatureFileName;
    private String imageFileName;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize ViewBinding
        binding = ActivityPreviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize dependencies manually (instead of @Inject)
        preferencesManager = new com.clone.EasyDelivery.util.SharedPreferencesManager(this);
        imageManager = new ImageManager(this, preferencesManager);

        // Get delivery ID from intent or UserStateViewModel
        currentDeliveryId = getIntent().getStringExtra("deliveryId");
        
        // Initialize ViewModels
        initializeViewModels();
        
        // Get delivery ID from UserStateViewModel if not provided in intent
        if (currentDeliveryId == null || currentDeliveryId.isEmpty()) {
            currentDeliveryId = userStateViewModel.getCurrentDocumentValue();
        }
        
        // Setup UI components
        setupRecyclerView();
        setupObservers();
        loadAndDisplayData();
        setupClickListeners();
        
        // Process signature and image files
        processMediaFiles();
        
        Log.i(TAG, "Preview Activity initialized with modern architecture");
    }
    
    // =================== INITIALIZATION METHODS ===================
    
    private void initializeViewModels() {
        deliveryViewModel = new ViewModelProvider(this).get(DeliveryViewModel.class);
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        userStateViewModel = new ViewModelProvider(this).get(UserStateViewModel.class);
        Log.i(TAG, "ViewModels initialized for offline-first delivery completion");
    }
    
    private void setupRecyclerView() {
        // Get validated parcels from UserStateViewModel
        List<String> validatedParcels = userStateViewModel.getValidatedParcelsValue();
        Collections.sort(validatedParcels);

        // Convert to ArrayList as required by PreviewAdapter
        ArrayList<String> parcelsList = new ArrayList<>(validatedParcels);
        adapter = new PreviewAdapter(this, parcelsList);
        
        binding.rvParcel.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        binding.rvParcel.setAdapter(adapter);

        // Observe changes to validated parcels - recreate adapter when data changes
        userStateViewModel.getValidatedParcels().observe(this, parcels -> {
            if (parcels != null) {
                List<String> sortedParcels = new ArrayList<>(parcels);
                Collections.sort(sortedParcels);
                // Recreate adapter with new data
                adapter = new PreviewAdapter(this, new ArrayList<>(sortedParcels));
                binding.rvParcel.setAdapter(adapter);
            }
        });
    }
    
    private void setupObservers() {
        // Observe delivery data changes
        deliveryViewModel.getCurrentDeliveryState().observe(this, deliveryState -> {
            if (deliveryState != null) {
                handleDeliveryStateChange(deliveryState);
            }
        });
        
        // Observe loading state
        deliveryViewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading != null) {
                handleLoadingStateChange(isLoading);
            }
        });
    }
    
    private void handleDeliveryStateChange(UiState<?> deliveryState) {
        if (deliveryState.isSuccess() && deliveryState.hasData()) {
            Log.i(TAG, "Delivery data loaded via ViewModel");
        } else if (deliveryState.isError()) {
            Log.w(TAG, "ViewModel delivery loading failed, using fallback: " + deliveryState.getErrorMessage());
        }
    }
    
    private void handleLoadingStateChange(boolean isLoading) {
        // Update loading UI if needed
        Log.d(TAG, "Loading state: " + isLoading);
    }
    
    private void loadAndDisplayData() {
        // Load delivery data from ViewModel first, fallback to legacy
        if (currentDeliveryId != null && !currentDeliveryId.isEmpty()) {
            deliveryViewModel.loadDeliveryById(currentDeliveryId);
        }
        
        // Load and display data using ViewBinding
        binding.tvCom.setText(getCompanyName());
        binding.tvDriver.setText(getDriverName());
        binding.tvVehicle.setText(getVehicleName());
        
        // Set current date and time
        binding.tvDate.setText(new SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(new Date()));
        binding.tvTime.setText(new SimpleDateFormat("HH:mm", Locale.US).format(new Date()) + " Hours");
        
        // Set delivery details from UserStateViewModel
        binding.tvDocu.setText(userStateViewModel.getCurrentDocumentValue());
        binding.tvComment.setText(userStateViewModel.getCommentValue());
        binding.tvParcel.setText(userStateViewModel.getCurrentParcelNo().getValue());
        
        Log.d(TAG, "Data loaded and displayed using ViewBinding");
    }
    
    private void setupClickListeners() {
        // Back button click handler
        binding.btnBack.setOnClickListener(view -> finish());
        
        // Confirm button click handler
        binding.btnConfirm.setOnClickListener(view -> handleConfirmDelivery());
        
        // Signature view click handler
        binding.rlSignView.setOnClickListener(view -> {
            String signaturePath = userStateViewModel.getFilePathsValue().signaturePath;
            userStateViewModel.setZoom(signaturePath);
            startActivity(new Intent(this, ZoomView.class));
        });
        
        // Image view click handler
        binding.rlViewParcel.setOnClickListener(view -> {
            String photoPath = userStateViewModel.getFilePathsValue().photoPath;
            userStateViewModel.setZoom(photoPath);
            startActivity(new Intent(this, ZoomView.class));
        });
    }
    
    private void processMediaFiles() {
        // Process signature file
        processSignatureFile();
        
        // Process image file
        processImageFile();
    }
    
    private void processSignatureFile() {
        String signaturePath = userStateViewModel.getFilePathsValue().signaturePath;
        if (signaturePath == null || signaturePath.trim().isEmpty()) {
            Log.e(TAG, "Signature path is null or empty");
            binding.tvSign.setText("No Signature Available");
            binding.rlSignView.setEnabled(false);
            return;
        }
        
        signatureFileName = signaturePath.substring(signaturePath.lastIndexOf('/') + 1).trim();
        
        // Validate and process signature file
        validateSignatureFile(signaturePath);
    }
    
    private void validateSignatureFile(String signaturePath) {
        try {
            Log.i(TAG, "Validating signature file for preview");
            
            // Check if signature file exists
            File signatureFile = new File(signaturePath);
            if (!signatureFile.exists()) {
                Log.e(TAG, "Signature file does not exist: " + signaturePath);
                binding.tvSign.setText("Signature Unavailable");
                binding.rlSignView.setEnabled(false);
                return;
            }
            
            // Signature file exists - enable preview
            binding.tvSign.setText("View Signature");
            binding.rlSignView.setEnabled(true);
            
            // Try modern secure validation first
            if (validateSignatureIntegrity(signaturePath)) {
                Log.i(TAG, "Signature integrity validated successfully");
            } else {
                Log.w(TAG, "Signature integrity check failed, but file exists - allowing preview");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error validating signature file: " + e.getMessage(), e);
            binding.tvSign.setText("Signature Error");
            binding.rlSignView.setEnabled(false);
            Toast.makeText(this, "Error validating signature file", Toast.LENGTH_SHORT).show();
        }
    }
    
    private boolean validateSignatureIntegrity(String signaturePath) {
        try {
            // Use modern signature validation methods
            // Note: Modern ImageManager doesn't expose signature packages directly
            // We just check if file exists and is readable
            File signatureFile = new File(signaturePath);
            boolean isValidSignature = signatureFile.exists() && signatureFile.canRead() && signatureFile.length() > 0;
                
            if (isValidSignature) {
                Log.i(TAG, "Signature file validated successfully");
                return true;
            } else {
                Log.w(TAG, "Signature file validation failed");
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Signature integrity validation failed: " + e.getMessage());
            return false;
        }
    }
    
    private void processImageFile() {
        String picPath = userStateViewModel.getFilePathsValue().photoPath;
        if (picPath == null || picPath.trim().isEmpty()) {
            Log.e(TAG, "Image path is null or empty");
            binding.tvPic.setText("No Image Available");
            binding.rlViewParcel.setEnabled(false);
            return;
        }
        
        imageFileName = picPath.substring(picPath.lastIndexOf('/') + 1).trim();
        binding.tvPic.setText("View Here");
        binding.rlViewParcel.setEnabled(true);
        
        Log.d(TAG, "Image file processed: " + imageFileName);
    }
    
    // =================== ACTION HANDLERS ===================
    
    private void handleConfirmDelivery() {
        try {
            Log.i(TAG, "Starting delivery confirmation process");
            
            // Prepare parcel data from UserStateViewModel
            List<String> validatedParcels = userStateViewModel.getValidatedParcelsValue();
            strList = TextUtils.join(", ", validatedParcels);
            strList = strList.replaceAll("\\s", " ");
            Log.d(TAG, "Validated parcels list: " + strList);
            
            // Save delivery completion through ViewModel and fallback
            saveDeliveryCompletion();
            
        } catch (Exception e) {
            Log.e(TAG, "Error confirming delivery: " + e.getMessage(), e);
            Toast.makeText(this, "Error confirming delivery: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void saveDeliveryCompletion() {
        try {
            // MODERN delivery completion via ViewModel
            if (currentDeliveryId != null && !currentDeliveryId.isEmpty()) {
                String deliveryNotes = createDeliveryNotes();
                String signaturePath = userStateViewModel.getFilePathsValue().signaturePath;
                String photoPath = userStateViewModel.getFilePathsValue().photoPath;

                deliveryViewModel.completeDelivery(currentDeliveryId, deliveryNotes, signaturePath, photoPath);
                Log.i(TAG, "Delivery completion saved via MODERN ViewModel");
            }

            // Update legacy database for compatibility
            updateDatabase();
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving delivery completion: " + e.getMessage(), e);
            // Continue with legacy database update as fallback
            updateDatabase();
        }
    }

    private String createDeliveryNotes() {
        StringBuilder notes = new StringBuilder();

        notes.append("Document: ").append(binding.tvDocu.getText().toString()).append("; ");
        notes.append("Parcels: ").append(strList).append("; ");
        notes.append("Driver: ").append(binding.tvDriver.getText().toString()).append("; ");
        notes.append("Vehicle: ").append(binding.tvVehicle.getText().toString()).append("; ");
        notes.append("Company: ").append(binding.tvCom.getText().toString()).append("; ");
        notes.append("Comment: ").append(userStateViewModel.getCommentValue()).append("; ");
        notes.append("Location: ").append(userStateViewModel.getFormattedGpsLocation());

        return notes.toString();
    }
    
    private void updateDatabase() {
        // Legacy database update method replaced with ViewModel-only approach
        // All database operations now handled through Repository pattern
        
        try {
            Log.i(TAG, "Completing delivery via ViewModel");
            
            // Create delivery completion data
            String deliveryNotes = createDeliveryNotes();
            String proofOfDeliveryPath = userStateViewModel.getFilePathsValue().signaturePath;
            
            // Complete delivery via ViewModel
            deliveryViewModel.markAsDelivered(currentDeliveryId, deliveryNotes, proofOfDeliveryPath);
            
            // Clear current delivery context
            userStateViewModel.setCurrentDocument("");
            userStateViewModel.setComment("");
            
            // Navigate back to DashHeader or TripDash
            navigateAfterCompletion();
            
        } catch (Exception e) {
            Log.e(TAG, "Error completing delivery: " + e.getMessage(), e);
            Toast.makeText(this, "Error completing delivery: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void navigateAfterCompletion() {
        // Check if all deliveries for this trip are completed via ViewModel
        String currentTripId = userStateViewModel.getCurrentTripIdValue();
        
        // Navigate based on completion status
        // For now, always navigate back to DashHeader
        // Trip completion logic will be handled by the Repository/ViewModel layer
        startActivity(new Intent(Preview.this, DashHeader.class));
        finishAffinity();
    }


    public class ViewDialog {

        public void showDialog(Activity activity) {

            final Dialog dialog = new Dialog(Preview.this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            dialog.setContentView(R.layout.confirm_dialog);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));

            Button save,exit;

            save = (Button) dialog.findViewById(R.id.save);
            exit = (Button) dialog.findViewById(R.id.exit);

            save.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    // Clear current document in UserStateViewModel instead of AppConstants
                    userStateViewModel.setCurrentDocument("");

                    startActivity(new Intent(Preview.this, DashHeader.class));

                    finishAffinity();
                }
            });

            exit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    finishAffinity();
                }
            });

            dialog.show();
            Window window = dialog.getWindow();
            window.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        }
    }


    private void email(){

        final Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE, Uri.parse("mailto:"));
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "ePOD Document Number: " + binding.tvDocu.getText().toString());

        shareIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{getEmailAddress()});
        shareIntent.putExtra(
                Intent.EXTRA_TEXT,
                Html.fromHtml(new StringBuilder()

                        .append("<p><b>"+"Dear Admin,"+"</b></p>")
                        .append("<p><b>" + "Please find the delivery details for document number: " + binding.tvDocu.getText().toString() + " below:" + "</b></p>")
                        .append("<p><b>" + "1. Company: " + binding.tvCom.getText().toString() + "</b></p>")
                        .append("<p><b>" + "2. Driver Name: " + binding.tvDriver.getText().toString() + "</b></p>")
                        .append("<p><b>" + "3. Delivery Vehicle: " + binding.tvVehicle.getText().toString() + "</b></p>")
                        .append("<p><b>" + "4. Date of Delivery: " + binding.tvDate.getText().toString() + "</b></p>")
                        .append("<p><b>" + "5. Time of Delivery: " + binding.tvTime.getText().toString() + "</b></p>")
                        .append("<p><b>" + "6. Document Number: " + binding.tvDocu.getText().toString() + "</b></p>")
                        .append("<p><b>" + "7. Number of Parcels: " + binding.tvParcel.getText().toString() + "</b></p>")
                        .append("<p><b>"+"8. Parcel Details: "+"</b></p>")
                        .append("<small><p>"+strList+"</p></small>")
                        .append("<p><b>" + "9. Customer Signature: " + "result" + "(See Attached File)" + "</b></p>")
                        .append("<p><b>" + "10. Parcel Photograph: " + "result1" + "(See Attached File)" + "</b></p>")

                    .append("<p><b>"+"Warm Regards, "+"</b></p>")
                    .append("<p><b>"+"EasyDelivery Team"+"</b></p>")

                    .toString())
        );

        filePaths = new ArrayList<>();

        // Use UserStateViewModel paths instead of AppConstants
        filePaths.add(userStateViewModel.getFilePathsValue().photoPath);
        filePaths.add(userStateViewModel.getFilePathsValue().signaturePath);
        ArrayList<Uri> uris = new ArrayList<Uri>();
        for (String file : filePaths)
        {

            File file1 =new File(file);
            uris.add( FileProvider.getUriForFile(Objects.requireNonNull(getApplicationContext()),
                    "com.clone.EasyDelivery" + ".provider", file1));
        }
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);


        try{
            startActivity(shareIntent.createChooser(shareIntent, "Send Email"));
        } catch (ActivityNotFoundException ex){
            Toast.makeText(Preview.this, ex.getMessage(), Toast.LENGTH_SHORT).show();
        }

    }

    // =================== CONFIGURATION METHODS ===================
    
    private String getCompanyName() {
        SharedPreferences shp = getSharedPreferences("COMPANY", MODE_PRIVATE);
        String company = shp.getString("company", "");
        Log.d(TAG, "Retrieved company: " + company);
        return company;
    }
    
    private String getDriverName() {
        SharedPreferences shp = getSharedPreferences("DRIVER", MODE_PRIVATE);
        String driver = shp.getString("driver", "");
        Log.d(TAG, "Retrieved driver: " + driver);
        return driver;
    }
    
    private String getEmailAddress() {
        SharedPreferences shp = getSharedPreferences("EMAIL", MODE_PRIVATE);
        String email = shp.getString("email", "");
        Log.d(TAG, "Retrieved email: " + email);
        return email;
    }
    
    private String getVehicleName() {
        SharedPreferences shp = getSharedPreferences("VEHICLE", MODE_PRIVATE);
        String vehicle = shp.getString("vehicle", "");
        Log.d(TAG, "Retrieved vehicle: " + vehicle);
        return vehicle;
    }
    
    // Trip completion checking is now handled by the ViewModel/Repository layer
    // No direct database access needed
    
    /**
     * Show trip completion confirmation dialog and navigate to trip dashboard
     */
    private void showTripCompletionDialog() {
        try {
            String currentTripId = userStateViewModel.getCurrentTripIdValue();
            final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this, R.style.AlertDialogStyle);
            
            builder.setTitle("🎉 Trip Completed!");
            builder.setMessage("Congratulations! You have successfully completed all deliveries for trip " + currentTripId + ".\n\nThe trip data is being synchronized with the cloud.");
            
            builder.setPositiveButton("Continue", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    // Navigate to trip dashboard
                    Log.i("TripCompletion", "User acknowledged trip completion - navigating to TripDash");
                    startActivity(new Intent(Preview.this, TripDash.class));
                    finishAffinity();
                }
            });
            
            // Prevent canceling - user must acknowledge
            builder.setCancelable(false);
            
            android.app.AlertDialog dialog = builder.create();
            dialog.show();
            
            Log.i("TripCompletion", "Displayed trip completion confirmation dialog");
            
        } catch (Exception e) {
            Log.e("TripCompletion", "Error showing completion dialog - falling back to direct navigation", e);
            // Fallback: Navigate directly if dialog fails
            startActivity(new Intent(Preview.this, TripDash.class));
            finishAffinity();
        }
    }
    
    // =================== LIFECYCLE METHODS ===================
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up ViewBinding
        if (binding != null) {
            binding = null;
        }
        
        Log.i(TAG, "Preview Activity destroyed and resources cleaned up");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "Preview Activity resumed");
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "Preview Activity paused");
    }
}
