package com.clone.EasyDelivery.data.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity for delivery data with enhanced sync capabilities
 */
@Entity(
    tableName = "deliveries",
    foreignKeys = {
        @ForeignKey(
            entity = TripEntity.class,
            parentColumns = "trip_id",
            childColumns = "trip_id",
            onDelete = ForeignKey.CASCADE
        )
    },
    indices = {
        @Index(value = "trip_id"),
        @Index(value = "status"),
        @Index(value = "order_number"),
        @Index(value = "sync_status")
    }
)
public class DeliveryEntity {
    
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "delivery_id")
    private String deliveryId;
    
    @ColumnInfo(name = "trip_id")
    private String tripId;
    
    @ColumnInfo(name = "order_number")
    private String orderNumber;
    
    @ColumnInfo(name = "customer_name")
    private String customerName;
    
    @ColumnInfo(name = "customer_contact")
    private String customerContact;
    
    @ColumnInfo(name = "customer_number")
    private String customerNumber;
    
    @ColumnInfo(name = "customer_email")
    private String customerEmail;
    
    @ColumnInfo(name = "address")
    private String address;
    
    @ColumnInfo(name = "quantity_expected")
    private int quantityExpected;
    
    @ColumnInfo(name = "quantity_delivered")
    private Integer quantityDelivered;
    
    @ColumnInfo(name = "status") // "pending", "in_transit", "delivered", "failed"
    private String status;
    
    // GPS coordinates
    @ColumnInfo(name = "latitude")
    private Double latitude;
    
    @ColumnInfo(name = "longitude")
    private Double longitude;
    
    @ColumnInfo(name = "captured_latitude")
    private Double capturedLatitude;
    
    @ColumnInfo(name = "captured_longitude")
    private Double capturedLongitude;
    
    // POD data
    @ColumnInfo(name = "signature_path")
    private String signaturePath;
    
    @ColumnInfo(name = "signature_url")
    private String signatureUrl;
    
    @ColumnInfo(name = "photo_path")
    private String photoPath;
    
    @ColumnInfo(name = "photo_url")
    private String photoUrl;
    
    @ColumnInfo(name = "comment")
    private String comment;
    
    @ColumnInfo(name = "recipient_name")
    private String recipientName;
    
    @ColumnInfo(name = "delivery_time")
    private Long deliveryTime;
    
    // Sync metadata
    @ColumnInfo(name = "synced_at")
    private Long syncedAt;
    
    @ColumnInfo(name = "sync_status")
    private String syncStatus;
    
    @ColumnInfo(name = "server_version")
    private String serverVersion;
    
    @ColumnInfo(name = "files_uploaded")
    private boolean filesUploaded;
    
    @ColumnInfo(name = "created_at")
    private Long createdAt;
    
    @ColumnInfo(name = "updated_at")
    private Long updatedAt;
    
    @ColumnInfo(name = "estimated_delivery_time")
    private Long estimatedDeliveryTime;
    
    @ColumnInfo(name = "actual_delivery_time")
    private Long actualDeliveryTime;
    
    @ColumnInfo(name = "is_deleted")
    private boolean isDeleted;
    
    @ColumnInfo(name = "delivery_instructions")
    private String deliveryInstructions;
    
    public DeliveryEntity() {}
    
    @Ignore
    public DeliveryEntity(String deliveryId, String tripId, String orderNumber,
                         String customerName, String address, int quantityExpected) {
        this.deliveryId = deliveryId;
        this.tripId = tripId;
        this.orderNumber = orderNumber;
        this.customerName = customerName;
        this.address = address;
        this.quantityExpected = quantityExpected;
        this.status = "pending";
        this.filesUploaded = false;
        this.syncStatus = "synced";
        this.syncedAt = System.currentTimeMillis();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.isDeleted = false;
    }
    
    // Getters and setters
    public String getDeliveryId() {
        return deliveryId;
    }
    
    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getId() {
        return deliveryId;
    }

    public void setId(String id) {
        this.deliveryId = id;
    }

    public String getTripId() {
        return tripId;
    }
    
    public void setTripId(String tripId) {
        this.tripId = tripId;
    }
    
    public String getOrderNumber() {
        return orderNumber;
    }
    
    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }
    
    public String getCustomerName() {
        return customerName;
    }
    
    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }
    
    public String getCustomerContact() {
        return customerContact;
    }
    
    public void setCustomerContact(String customerContact) {
        this.customerContact = customerContact;
    }
    
    public String getCustomerNumber() {
        return customerNumber;
    }
    
    public void setCustomerNumber(String customerNumber) {
        this.customerNumber = customerNumber;
    }
    
    public String getCustomerEmail() {
        return customerEmail;
    }
    
    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public int getQuantityExpected() {
        return quantityExpected;
    }
    
    public void setQuantityExpected(int quantityExpected) {
        this.quantityExpected = quantityExpected;
    }
    
    public Integer getQuantityDelivered() {
        return quantityDelivered;
    }
    
    public void setQuantityDelivered(Integer quantityDelivered) {
        this.quantityDelivered = quantityDelivered;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }
    
    public Double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
    
    public Double getCapturedLatitude() {
        return capturedLatitude;
    }
    
    public void setCapturedLatitude(Double capturedLatitude) {
        this.capturedLatitude = capturedLatitude;
    }
    
    public Double getCapturedLongitude() {
        return capturedLongitude;
    }
    
    public void setCapturedLongitude(Double capturedLongitude) {
        this.capturedLongitude = capturedLongitude;
    }
    
    public String getSignaturePath() {
        return signaturePath;
    }
    
    public void setSignaturePath(String signaturePath) {
        this.signaturePath = signaturePath;
    }
    
    public String getSignatureUrl() {
        return signatureUrl;
    }
    
    public void setSignatureUrl(String signatureUrl) {
        this.signatureUrl = signatureUrl;
    }
    
    public String getPhotoPath() {
        return photoPath;
    }
    
    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }
    
    public String getPhotoUrl() {
        return photoUrl;
    }
    
    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }
    
    public String getComment() {
        return comment;
    }
    
    public void setComment(String comment) {
        this.comment = comment;
    }
    
    public String getRecipientName() {
        return recipientName;
    }
    
    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }
    
    public Long getDeliveryTime() {
        return deliveryTime;
    }
    
    public void setDeliveryTime(Long deliveryTime) {
        this.deliveryTime = deliveryTime;
    }

    public Long getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(Long syncedAt) {
        this.syncedAt = syncedAt;
    }
    
    public String getSyncStatus() {
        return syncStatus;
    }
    
    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }
    
    public String getServerVersion() {
        return serverVersion;
    }
    
    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }
    
    public boolean isFilesUploaded() {
        return filesUploaded;
    }
    
    public void setFilesUploaded(boolean filesUploaded) {
        this.filesUploaded = filesUploaded;
    }
    
    // Helper methods
    public void completeDelivery(int quantityDelivered, String recipientName, String comment,
                                Double capturedLat, Double capturedLon) {
        this.status = "delivered";
        this.quantityDelivered = quantityDelivered;
        this.recipientName = recipientName;
        this.comment = comment;
        this.capturedLatitude = capturedLat;
        this.capturedLongitude = capturedLon;
        this.deliveryTime = System.currentTimeMillis();
        this.syncStatus = "pending";
    }
    
    public boolean needsSync() {
        return !"synced".equals(syncStatus);
    }
    
    public void markAsSynced() {
        this.syncStatus = "synced";
        this.syncedAt = System.currentTimeMillis();
    }
    
    public void markFilesUploaded() {
        this.filesUploaded = true;
        this.syncStatus = "pending"; // Need to sync the URLs
    }
    
    public boolean hasFiles() {
        return (signaturePath != null && !signaturePath.isEmpty()) || 
               (photoPath != null && !photoPath.isEmpty());
    }
    
    public boolean hasUploadedFiles() {
        return (signatureUrl != null && !signatureUrl.isEmpty()) || 
               (photoUrl != null && !photoUrl.isEmpty());
    }
    
    public String getCapturedGpsLocation() {
        if (capturedLatitude != null && capturedLongitude != null) {
            return "[" + capturedLatitude + "° N, " + capturedLongitude + "° E]";
        }
        return "";
    }
    
    public boolean isCompleted() {
        return "delivered".equals(status);
    }
    
    public boolean needsFileUpload() {
        return hasFiles() && !filesUploaded;
    }
    
    // Additional getters and setters for missing methods

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public Long getEstimatedDeliveryTime() { return estimatedDeliveryTime; }
    public void setEstimatedDeliveryTime(Long estimatedDeliveryTime) { this.estimatedDeliveryTime = estimatedDeliveryTime; }
    
    public Long getActualDeliveryTime() { return actualDeliveryTime; }
    public void setActualDeliveryTime(Long actualDeliveryTime) { this.actualDeliveryTime = actualDeliveryTime; }
    
    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { this.isDeleted = deleted; }
    
    public String getDeliveryInstructions() { return deliveryInstructions; }
    public void setDeliveryInstructions(String deliveryInstructions) { this.deliveryInstructions = deliveryInstructions; }
    
    // Convenience alias methods for backward compatibility
    
    public String getDeliveryAddress() { return address; }
    public String getCustomerPhone() { return customerContact; }
    public void setProofOfDeliveryPath(String path) { this.photoPath = path; }

    public String getProofOfDeliveryPath() {
        return photoPath;
    }

    public Integer getParcelCount() {
        return quantityDelivered;
    }

    public void setParcelCount(Integer parcelCount) {
        this.quantityDelivered = parcelCount;
    }

    public String getDeliveryNotes() {
        return comment;
    }

    public void setDeliveryNotes(String deliveryNotes) {
        this.comment = deliveryNotes;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerContact = customerPhone;
    }

    public String getDocument() {
        return orderNumber;
    }

    public void setDocument(String document) {
        this.orderNumber = document;
    }

}
