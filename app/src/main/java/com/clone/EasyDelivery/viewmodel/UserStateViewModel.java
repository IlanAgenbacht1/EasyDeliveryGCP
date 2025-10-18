package com.clone.EasyDelivery.viewmodel;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.clone.EasyDelivery.util.SharedPreferencesManager;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * UserStateViewModel - Shared state management across activities
 * Replaces static AppConstants fields with proper lifecycle-aware state
 */
@HiltViewModel
public class UserStateViewModel extends ViewModel {
    
    private static final String TAG = "UserStateViewModel";

    // Application context for resource access
    private final Context applicationContext;

    // SavedStateHandle for process death recovery
    private final SavedStateHandle savedStateHandle;
    private final SharedPreferencesManager preferencesManager;

    // =================== DELIVERY CONTEXT ===================
    private final MutableLiveData<String> currentDocumentLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> currentParcelNoLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> currentCompanyLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> currentTripIdLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> currentSessionIdLiveData = new MutableLiveData<>();

    // =================== FILE PATHS - CONSOLIDATED ===================
    // Consolidate multiple path LiveData objects into a single file paths object
    private final MutableLiveData<FilePaths> filePathsLiveData = new MutableLiveData<>(new FilePaths());
    private final MutableLiveData<String> commentLiveData = new MutableLiveData<>();

    // =================== LOCATION ===================
    private final MutableLiveData<Location> gpsLocationLiveData = new MutableLiveData<>();

    // =================== PARCEL VALIDATION ===================
    private final MutableLiveData<Boolean> parcelValidationEnabledLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> parcelInputLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> parcelPositionLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<String>> validatedParcelsLiveData = new MutableLiveData<>(new ArrayList<>());

    // =================== UI STATE ===================
    private final MutableLiveData<String> zoomLiveData = new MutableLiveData<>();

    /**
     * Consolidated file paths class to reduce LiveData complexity
     */
    public static class FilePaths {
        public String signaturePath;
        public String photoPath;
        public String imagePath; // Generic image path

        public FilePaths() {
        }

        public FilePaths(String signaturePath, String photoPath, String imagePath) {
            this.signaturePath = signaturePath;
            this.photoPath = photoPath;
            this.imagePath = imagePath;
        }

        public boolean hasSignature() {
            return signaturePath != null && !signaturePath.isEmpty();
        }

        public boolean hasPhoto() {
            return photoPath != null && !photoPath.isEmpty();
        }

        public boolean hasImage() {
            return imagePath != null && !imagePath.isEmpty();
        }

        public boolean hasAnyFiles() {
            return hasSignature() || hasPhoto() || hasImage();
        }
    }

    @Inject
    public UserStateViewModel(@ApplicationContext @NonNull Context applicationContext,
                              SavedStateHandle savedStateHandle,
                             SharedPreferencesManager preferencesManager) {
        this.applicationContext = applicationContext;
        this.savedStateHandle = savedStateHandle;
        this.preferencesManager = preferencesManager;
        
        // Initialize from saved state and preferences
        initializeFromSavedState();
        
        Log.i(TAG, "UserStateViewModel initialized with dependency injection");
    }

    public UserStateViewModel(@ApplicationContext @NonNull Context applicationContext, SavedStateHandle savedStateHandle) {
        this.applicationContext = applicationContext;
        this.savedStateHandle = savedStateHandle;
        this.preferencesManager = new SharedPreferencesManager(applicationContext);

        // Initialize from saved state and preferences
        initializeFromSavedState();

        Log.i(TAG, "UserStateViewModel initialized manually");
    }

    private void initializeFromSavedState() {
        // Initialize from SavedStateHandle first (survives process death)
        String currentDocument = savedStateHandle.get("current_document");
        if (currentDocument == null) {
            currentDocument = preferencesManager.getCurrentDocument();
        }
        currentDocumentLiveData.setValue(currentDocument != null ? currentDocument : "");

        String currentTripId = savedStateHandle.get("current_trip_id");
        if (currentTripId == null) {
            currentTripId = preferencesManager.getCurrentTripId();
        }
        currentTripIdLiveData.setValue(currentTripId != null ? currentTripId : "");

        String currentSessionId = savedStateHandle.get("current_session_id");
        if (currentSessionId == null) {
            currentSessionId = preferencesManager.getCurrentSessionId();
        }
        currentSessionIdLiveData.setValue(currentSessionId != null ? currentSessionId : "");
        
        // Initialize other fields from preferences
        currentParcelNoLiveData.setValue(preferencesManager.getCurrentParcelNo());
        currentCompanyLiveData.setValue(preferencesManager.getCurrentCompany());
        FilePaths filePaths = new FilePaths(
                preferencesManager.getSignaturePath(),
                preferencesManager.getPhotoPath(),
                preferencesManager.getImagePath()
        );
        filePathsLiveData.setValue(filePaths);
        commentLiveData.setValue(preferencesManager.getComment());
        gpsLocationLiveData.setValue(preferencesManager.getGpsLocation());
        parcelValidationEnabledLiveData.setValue(preferencesManager.isParcelValidationEnabled());
        parcelInputLiveData.setValue(preferencesManager.getParcelInput());
        parcelPositionLiveData.setValue(preferencesManager.getParcelPosition());
        zoomLiveData.setValue(preferencesManager.getZoom());
        
        // Initialize validated parcels list
        @SuppressWarnings("unchecked")
        ArrayList<String> validatedParcels = savedStateHandle.get("validated_parcels");
        if (validatedParcels == null) {
            validatedParcels = new ArrayList<>();
        }
        validatedParcelsLiveData.setValue(validatedParcels);
    }
    
    // =================== GETTERS FOR LIVEDATA ===================
    
    public LiveData<String> getCurrentDocument() {
        return currentDocumentLiveData;
    }
    
    public LiveData<String> getCurrentParcelNo() {
        return currentParcelNoLiveData;
    }
    
    public LiveData<String> getCurrentCompany() {
        return currentCompanyLiveData;
    }
    
    public LiveData<String> getCurrentTripId() {
        return currentTripIdLiveData;
    }
    
    public LiveData<String> getCurrentSessionId() {
        return currentSessionIdLiveData;
    }

    public LiveData<FilePaths> getFilePaths() {
        return filePathsLiveData;
    }
    
    public LiveData<String> getComment() {
        return commentLiveData;
    }
    
    public LiveData<Location> getGpsLocation() {
        return gpsLocationLiveData;
    }
    
    public LiveData<Boolean> getParcelValidationEnabled() {
        return parcelValidationEnabledLiveData;
    }
    
    public LiveData<String> getParcelInput() {
        return parcelInputLiveData;
    }
    
    public LiveData<Integer> getParcelPosition() {
        return parcelPositionLiveData;
    }
    
    public LiveData<List<String>> getValidatedParcels() {
        return validatedParcelsLiveData;
    }
    
    public LiveData<String> getZoom() {
        return zoomLiveData;
    }
    
    // =================== SETTERS WITH PERSISTENCE ===================
    
    public void setCurrentDocument(String document) {
        currentDocumentLiveData.setValue(document != null ? document : "");
        savedStateHandle.set("current_document", document);
        preferencesManager.setCurrentDocument(document != null ? document : "");
    }
    
    public void setCurrentParcelNo(String parcelNo) {
        currentParcelNoLiveData.setValue(parcelNo != null ? parcelNo : "");
        preferencesManager.setCurrentParcelNo(parcelNo != null ? parcelNo : "");
    }
    
    public void setCurrentCompany(String company) {
        currentCompanyLiveData.setValue(company != null ? company : "");
        preferencesManager.setCurrentCompany(company != null ? company : "");
    }
    
    public void setCurrentTripId(String tripId) {
        currentTripIdLiveData.setValue(tripId != null ? tripId : "");
        savedStateHandle.set("current_trip_id", tripId);
        preferencesManager.setCurrentTripId(tripId != null ? tripId : "");
    }
    
    public void setCurrentSessionId(String sessionId) {
        currentSessionIdLiveData.setValue(sessionId != null ? sessionId : "");
        savedStateHandle.set("current_session_id", sessionId);
        preferencesManager.setCurrentSessionId(sessionId != null ? sessionId : "");
    }

    public void setFilePaths(FilePaths paths) {
        filePathsLiveData.setValue(paths);
        preferencesManager.setSignaturePath(paths.signaturePath);
        preferencesManager.setPhotoPath(paths.photoPath);
        preferencesManager.setImagePath(paths.imagePath);
    }
    
    public void setComment(String comment) {
        commentLiveData.setValue(comment != null ? comment : "");
        preferencesManager.setComment(comment != null ? comment : "");
    }
    
    public void setGpsLocation(Location location) {
        gpsLocationLiveData.setValue(location);
        preferencesManager.setGpsLocation(location);
    }
    
    public void setParcelValidationEnabled(boolean enabled) {
        parcelValidationEnabledLiveData.setValue(enabled);
        preferencesManager.setParcelValidationEnabled(enabled);
    }
    
    public void setParcelInput(String input) {
        parcelInputLiveData.setValue(input != null ? input : "");
        preferencesManager.setParcelInput(input != null ? input : "");
    }
    
    public void setParcelPosition(int position) {
        parcelPositionLiveData.setValue(position);
        preferencesManager.setParcelPosition(position);
    }
    
    public void setValidatedParcels(List<String> parcels) {
        if (parcels == null) {
            parcels = new ArrayList<>();
        }
        validatedParcelsLiveData.setValue(parcels);
        savedStateHandle.set("validated_parcels", new ArrayList<>(parcels));
    }
    
    public void addValidatedParcel(String parcel) {
        List<String> currentList = validatedParcelsLiveData.getValue();
        if (currentList == null) {
            currentList = new ArrayList<>();
        } else {
            currentList = new ArrayList<>(currentList); // Create new list to trigger observers
        }
        
        if (!currentList.contains(parcel)) {
            currentList.add(parcel);
            setValidatedParcels(currentList);
        }
    }
    
    public void removeValidatedParcel(String parcel) {
        List<String> currentList = validatedParcelsLiveData.getValue();
        if (currentList != null && currentList.contains(parcel)) {
            currentList = new ArrayList<>(currentList); // Create new list to trigger observers
            currentList.remove(parcel);
            setValidatedParcels(currentList);
        }
    }
    
    public void setZoom(String zoom) {
        zoomLiveData.setValue(zoom != null ? zoom : "");
        preferencesManager.setZoom(zoom != null ? zoom : "");
    }
    
    // =================== HELPER METHODS ===================
    
    public void setCurrentDelivery(String documentId, String tripId, String sessionId) {
        setCurrentDocument(documentId);
        setCurrentTripId(tripId);
        setCurrentSessionId(sessionId);
        
        Log.i(TAG, "Current delivery context set - Doc: " + documentId + ", Trip: " + tripId + ", Session: " + sessionId);
    }
    
    public void clearCurrentContext() {
        setCurrentDocument("");
        setCurrentParcelNo("");
        setCurrentCompany("");
        setFilePaths(new FilePaths());
        setComment("");
        setGpsLocation(null);
        setParcelInput("");
        setParcelPosition(0);
        setValidatedParcels(new ArrayList<>());
        
        Log.i(TAG, "Current context cleared");
    }
    
    public boolean hasValidCurrentContext() {
        String document = currentDocumentLiveData.getValue();
        String tripId = currentTripIdLiveData.getValue();
        String sessionId = currentSessionIdLiveData.getValue();
        
        return document != null && !document.isEmpty() &&
               tripId != null && !tripId.isEmpty() &&
               sessionId != null && !sessionId.isEmpty();
    }
    
    public String getFormattedGpsLocation() {
        Location location = gpsLocationLiveData.getValue();
        if (location == null) {
            return "[0° N, 0° E]";
        }
        
        return "[" + location.getLatitude() + "° N, " + 
               location.getLongitude() + "° E]";
    }
    
    // =================== DIRECT VALUE ACCESS (for compatibility) ===================
    
    public String getCurrentDocumentValue() {
        String value = currentDocumentLiveData.getValue();
        return value != null ? value : "";
    }
    
    public String getCurrentTripIdValue() {
        String value = currentTripIdLiveData.getValue();
        return value != null ? value : "";
    }
    
    public String getCurrentSessionIdValue() {
        String value = currentSessionIdLiveData.getValue();
        return value != null ? value : "";
    }

    public FilePaths getFilePathsValue() {
        FilePaths value = filePathsLiveData.getValue();
        return value != null ? value : new FilePaths();
    }
    
    public String getCommentValue() {
        String value = commentLiveData.getValue();
        return value != null ? value : "";
    }
    
    public List<String> getValidatedParcelsValue() {
        List<String> value = validatedParcelsLiveData.getValue();
        return value != null ? value : new ArrayList<>();
    }
}