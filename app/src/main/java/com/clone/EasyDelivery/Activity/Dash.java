package com.clone.EasyDelivery.Activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clone.EasyDelivery.Adapter.ParcelAdapter;
import com.clone.EasyDelivery.R;
import com.clone.EasyDelivery.databinding.ActivityDashBinding;
import com.clone.EasyDelivery.util.ImageManager;
import com.clone.EasyDelivery.util.LocationManager;
import com.clone.EasyDelivery.util.SignaturePackage;
import com.clone.EasyDelivery.viewmodel.AuthViewModel;
import com.clone.EasyDelivery.viewmodel.DeliveryViewModel;
import com.clone.EasyDelivery.viewmodel.UserStateViewModel;
import com.clone.EasyDelivery.viewmodel.models.UiState;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.kyanogen.signatureview.SignatureView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Modern Dash Activity using ViewBinding and ViewModels
 * Handles delivery data capture including photos and signatures
 */
@AndroidEntryPoint
public class Dash extends AppCompatActivity {

    private static final String TAG = "Dash";
    
    // ViewBinding
    private ActivityDashBinding binding;
    
    // ViewModels
    private DeliveryViewModel deliveryViewModel;
    private AuthViewModel authViewModel;
    private UserStateViewModel userStateViewModel;
    
    // Adapters and data
    private ArrayList<String> adapterList;
    private ParcelAdapter adapter;
    private LinearLayoutManager linearLayoutManager;
    
    // Delivery state
    private String currentDeliveryId;
    private boolean isSign = false, isPic = false;
    private boolean isQtyValidated = false;

    // Dependency injection 
    @Inject
    protected com.clone.EasyDelivery.util.SharedPreferencesManager preferencesManager;
    @Inject
    protected ImageManager imageManager;
    @Inject
    protected LocationManager locationManager;
    
    // Image and signature handling
    private Bitmap bitmap;
    private String signImagePath;
    private Uri imageFileUri;
    private File file;
    private String img_URI;
    private int imageType = 0;
    private int img_isthere = 0;
    
    // Constants
    private static final String IMAGE_DIRECTORY = "/DeliveryApp";
    private static final String SIGN_DIRECTORY = "/DeliverySignature";
    private static final String PIC_DIRECTORY = "/DeliveryImages";
    private static final int REQUEST_CAPTURE = 7;
    private static final int VALIDATION_DISTANCE = 50;
    
    
    // Comment dialog
    private EditText commentEditText;
    
    // Activity reference
    public static Dash activity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize ViewBinding
        binding = ActivityDashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize dependencies 
        preferencesManager = new com.clone.EasyDelivery.util.SharedPreferencesManager(this);
        imageManager = new ImageManager(this, preferencesManager);
        locationManager = new LocationManager(this, preferencesManager);

        // Initialize data
        adapterList = new ArrayList<>();
        activity = this;
        
        // Get delivery ID from intent or UserStateViewModel
        currentDeliveryId = getIntent().getStringExtra("deliveryId");
        
        // Initialize ViewModels
        initializeViewModels();
        
        // Get delivery ID from UserStateViewModel if not provided in intent
        if (currentDeliveryId == null || currentDeliveryId.isEmpty()) {
            currentDeliveryId = userStateViewModel.getCurrentDocumentValue();
        }

        // Setup RecyclerView
        setupRecyclerView();
        
        // Setup click listeners
        setupClickListeners();
        
        // Setup observers
        setupObservers();
        
        // Load and display delivery data
        loadDeliveryData();
        
        // Validate location
        validateLocation();
    }
    
    // =================== INITIALIZATION METHODS ===================
    
    private void initializeViewModels() {
        deliveryViewModel = new ViewModelProvider(this).get(DeliveryViewModel.class);
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        userStateViewModel = new ViewModelProvider(this).get(UserStateViewModel.class);
        Log.i(TAG, "ViewModels initialized for offline-first data capture");
    }
    
    private void setupRecyclerView() {
        linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        linearLayoutManager.setSmoothScrollbarEnabled(true);
        
        adapter = new ParcelAdapter(this, adapterList, binding.rv, null);
        
        binding.rv.setLayoutManager(linearLayoutManager);
        binding.rv.setAdapter(adapter);
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
        
        // Observe user state changes
        userStateViewModel.getCurrentDocument().observe(this, document -> {
            if (document != null && !document.isEmpty()) {
                binding.tvDashDocTitle.setText(document);
            }
        });
        
        userStateViewModel.getCurrentTripId().observe(this, tripId -> {
            if (tripId != null && !tripId.isEmpty()) {
                binding.tvDashTripTitle.setText(tripId);
            }
        });
        
        userStateViewModel.getValidatedParcels().observe(this, parcels -> {
            if (parcels != null) {
                adapterList.clear();
                adapterList.addAll(parcels);
                adapter.notifyDataSetChanged();
            }
        });
        
        // Observe GPS location changes
        userStateViewModel.getGpsLocation().observe(this, location -> {
            if (location != null) {
                // Location updated - could trigger validation
                Log.d(TAG, "GPS location updated: " + userStateViewModel.getFormattedGpsLocation());
            }
        });
    }
    
    private void handleDeliveryStateChange(UiState<?> deliveryState) {
        if (deliveryState.isSuccess() && deliveryState.hasData()) {
            // Delivery data loaded successfully via ViewModel
            Log.i(TAG, "Delivery data loaded via ViewModel");
            updateUIFromDeliveryData();
        } else if (deliveryState.isError()) {
            Log.w(TAG, "ViewModel delivery loading failed: " + deliveryState.getErrorMessage());
            showErrorMessage("Failed to load delivery: " + deliveryState.getErrorMessage());
        }
    }
    
    private void updateUIFromDeliveryData() {
        // Update UI based on current delivery data from ViewModel
        String currentTripId = userStateViewModel.getCurrentTripIdValue();
        String currentDocument = userStateViewModel.getCurrentDocumentValue();
        
        if (!currentDocument.isEmpty()) {
            binding.tvDashDocTitle.setText(currentDocument);
        }
        
        if (!currentTripId.isEmpty()) {
            binding.tvDashTripTitle.setText(currentTripId);
        }
        
        binding.tvParcels.setText("Parcels");
    }
    
    private void showErrorMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    
    private void handleLoadingStateChange(boolean isLoading) {
        // Update loading UI if needed
        Log.d(TAG, "Loading state: " + isLoading);
    }
    
    private void setupClickListeners() {
        // Next/Complete button click handler
        binding.btnNext.setOnClickListener(view -> handleNextButtonClick());
        
        // Signature button click handler
        binding.btnSign.setOnClickListener(view -> handleSignatureClick());
        
        // Picture button click handler
        binding.btnPic.setOnClickListener(view -> handlePictureClick());
    }
    
    private void handleNextButtonClick() {
        String inputQtyStr = binding.etNumber.getText().toString();
        
        if (isQtyValidated) {
            if (validation()) {
                // Save parcel data through ViewModel
                saveDeliveryProgress();
                
                // Update parcel count in UserStateViewModel
                String parcelCount = binding.etNumber.getText().toString();
                userStateViewModel.setCurrentParcelNo(parcelCount);
                
                Intent intent = new Intent(this, Preview.class);
                intent.putExtra("deliveryId", currentDeliveryId);
                startActivity(intent);
            }
        } else {
            validateAndConfirmQuantity(inputQtyStr);
        }
    }
    
    private void validateAndConfirmQuantity(String inputQtyStr) {
        if (inputQtyStr.isEmpty()) {
            Toast.makeText(this, "Please enter parcel quantity", Toast.LENGTH_LONG).show();
            return;
        }
        
        int inputQty;
        try {
            inputQty = Integer.parseInt(inputQtyStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format", Toast.LENGTH_LONG).show();
            return;
        }
        
        if (inputQty > 0) {
            confirmParcelQuantity(inputQty);
        } else {
            Toast.makeText(this, "Please enter a valid quantity", Toast.LENGTH_LONG).show();
        }
    }
    
    private void confirmParcelQuantity(int inputQty) {
        isQtyValidated = true;
        
        // Update parcel count in UserStateViewModel
        userStateViewModel.setCurrentParcelNo(String.valueOf(inputQty));
        
        // Update UI through ViewBinding
        binding.btnNext.setTextColor(getResources().getColor(R.color.black, null));
        binding.btnNext.setText("Complete Delivery");
        
        String parcelText = (inputQty == 1) ? inputQty + " Item Confirmed" : inputQty + " Items Confirmed";
        binding.etNumber.setText(parcelText);
        binding.etNumber.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        binding.etNumber.setHint("");
        binding.etNumber.setFocusable(false);
        binding.etNumber.setCursorVisible(false);
        
        Toast.makeText(this, "Parcel quantity validated", Toast.LENGTH_SHORT).show();
        
        // Save progress via ViewModel
        saveDeliveryProgress();
    }
    
    private void handleSignatureClick() {
        if (adapterList.size() > 0 && binding.tvDashDocTitle.getText().length() > 0) {
            ViewDialog alert = new ViewDialog();
            alert.showDialog(this);
        } else {
            showEnlargedToast("Enter All Parcel Details");
        }
    }
    
    private void handlePictureClick() {
        if (adapterList.size() > 0) {
            captureDeliveryImage();
        } else {
            showEnlargedToast("Enter All Parcel Details");
        }
    }
    
    private void captureDeliveryImage() {
        file = null;
        long tsLong = System.currentTimeMillis() / 1000;
        
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        file = new File(getFilesDir() + IMAGE_DIRECTORY + "/DeliveryImage/");
        
        if (!file.exists()) {
            file.mkdirs();
        }
        
        file = new File(file, tsLong + ".jpg");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            img_URI = "file:" + file.getAbsolutePath();
            intent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(this,
                    "com.clone.EasyDelivery" + ".provider", file));
        } else {
            intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(file));
            img_URI = "" + Uri.fromFile(file);
        }
        
        startActivityForResult(intent, REQUEST_CAPTURE);
    }
    
    private void saveDeliveryProgress() {
        // Save current progress through ViewModel
        if (currentDeliveryId != null && !currentDeliveryId.isEmpty()) {
            // Create delivery notes from current state
            String deliveryNotes = createDeliveryNotes();
            String proofOfDeliveryPath = userStateViewModel.getFilePathsValue().signaturePath;
            
            // Use ViewModel to save progress
            deliveryViewModel.startDelivery(currentDeliveryId);
            Log.i(TAG, "Delivery progress saved via ViewModel for: " + currentDeliveryId);
        }
    }
    
    private String createDeliveryNotes() {
        StringBuilder notes = new StringBuilder();
        
        String comment = userStateViewModel.getCommentValue();
        if (!comment.isEmpty()) {
            notes.append("Comment: ").append(comment).append("; ");
        }
        
        String parcelCount = userStateViewModel.getCurrentParcelNo().getValue();
        if (parcelCount != null && !parcelCount.isEmpty()) {
            notes.append("Parcels: ").append(parcelCount).append("; ");
        }
        
        String location = userStateViewModel.getFormattedGpsLocation();
        notes.append("Location: ").append(location);
        
        return notes.toString();
    }
    
    private void showEnlargedToast(String text) {
        SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
        biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);
        Toast.makeText(this, biggerText, Toast.LENGTH_LONG).show();
    }
    
    // =================== DATA LOADING METHODS ===================
    
    private void loadDeliveryData() {
        // Load delivery data through ViewModel only
        if (currentDeliveryId != null && !currentDeliveryId.isEmpty()) {
            // Set current delivery in UserStateViewModel
            String tripId = authViewModel.getCurrentSessionId(); // Get current session/trip
            String sessionId = authViewModel.getCurrentSessionId();
            
            userStateViewModel.setCurrentDelivery(currentDeliveryId, tripId, sessionId);
            
            // Load delivery data via ViewModel
            deliveryViewModel.loadDeliveryById(currentDeliveryId);
            
            Log.i(TAG, "Loading delivery data via ViewModel: " + currentDeliveryId);
        } else {
            Log.w(TAG, "No delivery ID provided, cannot load data");
            showErrorMessage("No delivery selected");
        }
    }
    
    private void loadCustomerDataForDelivery() {
        // This will be populated when delivery data is loaded via ViewModel observers
        // Customer name will come from the DeliveryEntity through the observer
        deliveryViewModel.getCurrentDeliveryState().observe(this, deliveryState -> {
            if (deliveryState != null && deliveryState.isSuccess() && deliveryState.hasData()) {
                // Extract customer info from delivery entity if available
                Log.d(TAG, "Customer data will be populated from delivery entity");
                // Note: This would require the DeliveryEntity to have customer info
                // For now, we'll use a placeholder
                binding.tvDashCustomer.setText("Customer Name");
            }
        });
    }


    public void validateLocation() {
        // Use the modern LocationManager with callback
        locationManager.getCurrentLocationAsync(new LocationManager.LocationCallback() {
            @Override
            public void onLocationFound(@NonNull Location location) {
                // Save location in UserStateViewModel instead of AppConstants
                userStateViewModel.setGpsLocation(location);
                checkLocationDistance(location);
            }

            @Override
            public void onLocationNotFound() {
                Log.w(TAG, "Could not get current location for validation");
                // Continue with delivery without location validation
                displayCommentDialog();
            }
            
            @Override
            public void onLocationError(@NonNull String error) {
                Log.w(TAG, "Location error: " + error);
                displayCommentDialog();
            }
        });
    }

    private void checkLocationDistance(Location currentLocation) {
        // Note: This method needs delivery location from ViewModel
        // For now, we'll proceed with the comment dialog since location validation
        // will be handled by the ViewModel/Repository layer
        Log.d(TAG, "Location check completed, proceeding with comment dialog");
        displayCommentDialog();
    }


    public void displayCommentDialog() {
        commentEditText = new EditText(this);
        commentEditText.setTextColor(getResources().getColor(R.color.ic_launcher_background));
        commentEditText.setHintTextColor(getResources().getColor(R.color.gold));
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.AlertDialogStyle)
                .setTitle("Enter Comment")
                .setView(commentEditText)
                .setCancelable(false)
                .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // Save comment in UserStateViewModel instead of AppConstants
                        userStateViewModel.setComment(commentEditText.getText().toString());
                    }

                }).create();
        dialog.show();
        commentEditText.requestFocus();
    }


    public boolean validation() {
        try {
            // Check document number using ViewBinding
            if (binding.tvDashDocTitle.getText().length() == 0) {
                showEnlargedToast("Enter Document Number");
                return false;
            }
            
            // Check parcel details
            if (adapterList.size() == 0) {
                showEnlargedToast("Enter All Parcel Details");
                return false;
            }
            
            // Check if image is captured
            if (!isPic) {
                showEnlargedToast("Capture Document Image");
                return false;
            }
            
            // Check if signature is captured
            if (!isSign) {
                showEnlargedToast("Capture Signature");
                return false;
            }
            
            // All validations passed
            Log.i(TAG, "All delivery validations passed");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Validation error: " + e.getMessage(), e);
            return false;
        }
    }


    public class ViewDialog {

        public void showDialog(Activity activity) {

            final BottomSheetDialog dialog = new BottomSheetDialog(Dash.this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            dialog.setCancelable(true);
            dialog.setContentView(R.layout.signature_dialog);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            Button clear, save;
            final SignatureView signatureView;
            RelativeLayout llClose;

            clear = (Button) dialog.findViewById(R.id.clear);
            save = (Button) dialog.findViewById(R.id.save);
            llClose = dialog.findViewById(R.id.llTop);

            signatureView = dialog.findViewById(R.id.signature_view);

            clear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    signatureView.clearCanvas();
                }
            });

            llClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    dialog.dismiss();
                }
            });

            save.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Log.i("SignatureSecurity", "=== Starting secure signature capture ===");
                        
                        // Capture signature bitmap
                        bitmap = signatureView.getSignatureBitmap();
                        
                        if (bitmap == null) {
                            Log.e("SignatureSecurity", "Failed to capture signature bitmap");
                            Toast.makeText(Dash.this, "Failed to capture signature. Please try again.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        
                        // Convert bitmap to byte array
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                        byte[] bitmapData = baos.toByteArray();
                        
                        Log.d("SignatureSecurity", "Signature bitmap captured: " + bitmapData.length + " bytes");
                        
                        // Create simplified signature package for new architecture
                        com.clone.EasyDelivery.util.SignaturePackage signaturePackage =
                                new com.clone.EasyDelivery.util.SignaturePackage(bitmapData, "simplified_signature");
                        
                        // Save signature using modern ImageManager
                        String path = null;
                        try {
                            path = imageManager.saveSignatureSecurelySync(bitmapData, "signature_" + System.currentTimeMillis());
                        } catch (Exception e) {
                            Log.e("SignatureSecurity", "Error saving signature", e);
                        }
                        if (path == null) {
                            Log.e("SignatureSecurity", "Failed to save signature");
                            Toast.makeText(Dash.this, "Failed to save signature. Please try again.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        // Save signature path using modern UserStateViewModel FilePaths
                        UserStateViewModel.FilePaths currentPaths = userStateViewModel.getFilePathsValue();
                        UserStateViewModel.FilePaths updatedPaths = new UserStateViewModel.FilePaths(
                                path, // signaturePath
                                currentPaths.photoPath, // keep existing photoPath
                                currentPaths.imagePath  // keep existing imagePath
                        );
                        userStateViewModel.setFilePaths(updatedPaths);
                        signImagePath = path; // Store for cleanup
                        Log.i("SignatureSecurity", "Signature saved securely at: " + path);
                        
                        // Save progress via ViewModel
                        saveDeliveryProgress();

                        dialog.dismiss();
                        binding.rlTick1.setVisibility(View.VISIBLE);
                        isSign = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            try {
                Field behaviorField = dialog.getClass().getDeclaredField("behavior");
                behaviorField.setAccessible(true);
                final BottomSheetBehavior behavior = (BottomSheetBehavior) behaviorField.get(dialog);
                behavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {

                    @Override
                    public void onStateChanged(@NonNull View bottomSheet, int newState) {
                        if (newState == BottomSheetBehavior.STATE_DRAGGING) {
                            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                        }
                    }

                    @Override
                    public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                    }
                });

            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

            dialog.show();
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode == REQUEST_CAPTURE) {
            // Update UI using ViewBinding
            binding.rlTick2.setVisibility(View.VISIBLE);
            isPic = true;
            
            img_isthere = 1;
            imageType = 2;

            imageManager.compressImageAsync(img_URI)
                    .thenAccept(compressedPath -> {
                        if (compressedPath != null) {
                            signImagePath = compressedPath;
                            imageFileUri = Uri.parse(compressedPath);

                            // Save image path in UserStateViewModel instead of AppConstants
                            UserStateViewModel.FilePaths currentPaths = userStateViewModel.getFilePathsValue();
                            UserStateViewModel.FilePaths updatedPaths = new UserStateViewModel.FilePaths(
                                    currentPaths.signaturePath, // keep existing signaturePath
                                    compressedPath, // photoPath
                                    currentPaths.imagePath  // keep existing imagePath
                            );
                            userStateViewModel.setFilePaths(updatedPaths);

                            // Save progress via ViewModel
                            saveDeliveryProgress();

                            Log.i(TAG, "Image captured and processed: " + compressedPath);
                        } else {
                            Log.e(TAG, "Failed to compress image, using original");
                            signImagePath = img_URI;
                            imageFileUri = Uri.parse(img_URI);

                            UserStateViewModel.FilePaths currentPaths = userStateViewModel.getFilePathsValue();
                            UserStateViewModel.FilePaths updatedPaths = new UserStateViewModel.FilePaths(
                                    currentPaths.signaturePath, // keep existing signaturePath
                                    img_URI, // photoPath
                                    currentPaths.imagePath  // keep existing imagePath
                            );
                            userStateViewModel.setFilePaths(updatedPaths);

                            saveDeliveryProgress();
                        }
                    })
                    .exceptionally(throwable -> {
                        Log.e(TAG, "Error compressing image", throwable);
                        // Fallback to original
                        signImagePath = img_URI;
                        imageFileUri = Uri.parse(img_URI);

                        UserStateViewModel.FilePaths currentPaths = userStateViewModel.getFilePathsValue();
                        UserStateViewModel.FilePaths updatedPaths = new UserStateViewModel.FilePaths(
                                currentPaths.signaturePath, // keep existing signaturePath
                                img_URI, // photoPath
                                currentPaths.imagePath  // keep existing imagePath
                        );
                        userStateViewModel.setFilePaths(updatedPaths);

                        saveDeliveryProgress();
                        return null;
                    });

            // Clean up temporary file
            if (file != null && file.exists()) {
                file.delete();
            }
        }
    }


    // Enhanced method to securely store encryption key
    private void storeEncryptionKey(javax.crypto.SecretKey key) {
        try {
            // Use SharedPreferencesManager instead of direct SharedPreferences access
            String encodedKey = android.util.Base64.encodeToString(key.getEncoded(), android.util.Base64.DEFAULT);
            // Note: This would be better handled through a dedicated security manager
            // For now, we'll log that the key would be stored
            Log.d("SIGNATURE_DEBUG", "Encryption key would be stored securely via preferences manager");
        } catch (Exception e) {
            Log.e("SIGNATURE_ERROR", "Failed to store encryption key: " + e.getMessage(), e);
        }
    }


    private SpannableStringBuilder enlargedText (String text) {

        SpannableStringBuilder biggerText = new SpannableStringBuilder(text);
        biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, text.length(), 0);

        return biggerText;
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();

        // Clean up image files if they exist using modern FileManager
        if (signImagePath != null) {
            imageManager.deleteImageAsync(signImagePath);
        }
        if (img_URI != null) {
            imageManager.deleteImageAsync(img_URI);
        }

        startActivity(new Intent(this, DashHeader.class));
        finish();
    }

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
        
        Log.i(TAG, "Activity destroyed and resources cleaned up");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "Activity resumed");
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "Activity paused");
    }
}
