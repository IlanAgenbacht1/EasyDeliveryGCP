package com.clone.EasyDelivery.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

/**
 * Unified Delivery model for both API serialization and Room database
 * Consolidates DeliveryDto and DeliveryEntity to reduce duplication
 */
@Entity(
        tableName = "deliveries",
        indices = {
                @Index(value = "trip_id"),
                @Index(value = "status"),
                @Index(value = "order_number")
        }
)
public class Delivery {

    @PrimaryKey
    @ColumnInfo(name = "delivery_id")
    @SerializedName("deliveryId")
    private String deliveryId;

    @ColumnInfo(name = "trip_id")
    @SerializedName("tripId")
    private String tripId;

    @ColumnInfo(name = "order_number")
    @SerializedName("orderNumber")
    private String orderNumber;

    @ColumnInfo(name = "address")
    @SerializedName("address")
    private String address;

    // Customer info (flattened from CustomerDto)
    @ColumnInfo(name = "customer_name")
    @SerializedName("customerName")
    private String customerName;

    @ColumnInfo(name = "customer_contact")
    @SerializedName("customerContact")
    private String customerContact;

    @ColumnInfo(name = "customer_number")
    @SerializedName("customerNumber")
    private String customerNumber;

    @ColumnInfo(name = "customer_email")
    @SerializedName("customerEmail")
    private String customerEmail;

    @ColumnInfo(name = "quantity_expected")
    @SerializedName("quantityExpected")
    private int quantityExpected;

    @ColumnInfo(name = "quantity_delivered")
    @SerializedName("quantityDelivered")
    private Integer quantityDelivered;

    @ColumnInfo(name = "status")
    @SerializedName("status")
    private String status; // "pending", "in_transit", "delivered", "failed"

    // GPS coordinates (flattened from GpsLocationDto)
    @ColumnInfo(name = "latitude")
    @SerializedName("latitude")
    private Double latitude;

    @ColumnInfo(name = "longitude")
    @SerializedName("longitude")
    private Double longitude;

    @ColumnInfo(name = "captured_latitude")
    @SerializedName("capturedLatitude")
    private Double capturedLatitude;

    @ColumnInfo(name = "captured_longitude")
    @SerializedName("capturedLongitude")
    private Double capturedLongitude;

    // POD data (flattened from PodDataDto)
    @ColumnInfo(name = "signature_url")
    @SerializedName("signatureUrl")
    private String signatureUrl;

    @ColumnInfo(name = "photo_url")
    @SerializedName("photoUrl")
    private String photoUrl;

    @ColumnInfo(name = "comment")
    @SerializedName("comment")
    private String comment;

    @ColumnInfo(name = "recipient_name")
    @SerializedName("recipientName")
    private String recipientName;

    @ColumnInfo(name = "delivery_time")
    @SerializedName("deliveryTime")
    private Long deliveryTime;

    // Database-only fields (not serialized to API)
    @ColumnInfo(name = "signature_path")
    @Ignore // Don't serialize to API
    private String signaturePath;

    @ColumnInfo(name = "photo_path")
    @Ignore // Don't serialize to API
    private String photoPath;

    @ColumnInfo(name = "synced_at")
    @Ignore // Don't serialize to API
    private Long syncedAt;

    @ColumnInfo(name = "sync_status")
    @Ignore // Don't serialize to API
    private String syncStatus = "synced";

    @ColumnInfo(name = "files_uploaded")
    @Ignore // Don't serialize to API
    private boolean filesUploaded = false;

    @ColumnInfo(name = "created_at")
    @Ignore // Don't serialize to API
    private Long createdAt;

    @ColumnInfo(name = "updated_at")
    @Ignore // Don't serialize to API
    private Long updatedAt;

    // Constructors
    public Delivery() {
    }

    @Ignore
    public Delivery(String deliveryId, String tripId, String orderNumber, String address,
                    String customerName, int quantityExpected) {
        this.deliveryId = deliveryId;
        this.tripId = tripId;
        this.orderNumber = orderNumber;
        this.address = address;
        this.customerName = customerName;
        this.quantityExpected = quantityExpected;
        this.status = "pending";
        this.syncStatus = "synced";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    // Getters and setters (essential ones)
    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
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

    public String getSignatureUrl() {
        return signatureUrl;
    }

    public void setSignatureUrl(String signatureUrl) {
        this.signatureUrl = signatureUrl;
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

    // Database-only getters/setters
    public String getSignaturePath() {
        return signaturePath;
    }

    public void setSignaturePath(String signaturePath) {
        this.signaturePath = signaturePath;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
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

    public boolean isFilesUploaded() {
        return filesUploaded;
    }

    public void setFilesUploaded(boolean filesUploaded) {
        this.filesUploaded = filesUploaded;
    }

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

    // Business logic methods
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
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean needsSync() {
        return !"synced".equals(syncStatus);
    }

    public void markAsSynced() {
        this.syncStatus = "synced";
        this.syncedAt = System.currentTimeMillis();
    }

    public boolean isCompleted() {
        return "delivered".equals(status);
    }

    public boolean hasFiles() {
        return (signaturePath != null && !signaturePath.isEmpty()) ||
                (photoPath != null && !photoPath.isEmpty());
    }

    public String getCapturedGpsLocation() {
        if (capturedLatitude != null && capturedLongitude != null) {
            return "[" + capturedLatitude + "° N, " + capturedLongitude + "° E]";
        }
        return "";
    }
}