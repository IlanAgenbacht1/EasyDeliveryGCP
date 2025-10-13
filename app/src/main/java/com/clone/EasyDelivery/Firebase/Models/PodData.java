package com.clone.EasyDelivery.Firebase.Models;

import com.google.gson.annotations.SerializedName;

/**
 * POD (Proof of Delivery) data model
 * This matches the JSON structure stored in Firestore delivery documents
 */
public class PodData {
    
    @SerializedName("signatureUrl")
    private String signatureUrl = "";
    
    @SerializedName("capturedGpsLocation") 
    private String capturedGpsLocation = "[0° N, 0° E]";
    
    @SerializedName("photoUrl")
    private String photoUrl = "";
    
    @SerializedName("comment")
    private String comment = "";
    
    @SerializedName("recipientName")
    private String recipientName = "";
    
    @SerializedName("quantity")
    private String quantity = "";
    
    // Default constructor
    public PodData() {}
    
    public PodData(String signatureUrl, String photoUrl, String comment, String recipientName, String quantity, String capturedGpsLocation) {
        this.signatureUrl = signatureUrl != null ? signatureUrl : "";
        this.photoUrl = photoUrl != null ? photoUrl : "";
        this.comment = comment != null ? comment : "";
        this.recipientName = recipientName != null ? recipientName : "";
        this.quantity = quantity != null ? quantity : "";
        this.capturedGpsLocation = capturedGpsLocation != null ? capturedGpsLocation : "[0° N, 0° E]";
    }
    
    // Getters and setters
    public String getSignatureUrl() { return signatureUrl; }
    public void setSignatureUrl(String signatureUrl) { this.signatureUrl = signatureUrl != null ? signatureUrl : ""; }
    
    public String getCapturedGpsLocation() { return capturedGpsLocation; }
    public void setCapturedGpsLocation(String capturedGpsLocation) { this.capturedGpsLocation = capturedGpsLocation != null ? capturedGpsLocation : "[0° N, 0° E]"; }
    
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl != null ? photoUrl : ""; }
    
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment != null ? comment : ""; }
    
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName != null ? recipientName : ""; }
    
    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity != null ? quantity : ""; }
    
    /**
     * Check if POD data has been completed (has signature or photo)
     */
    public boolean isCompleted() {
        return (!signatureUrl.isEmpty() || !photoUrl.isEmpty());
    }
    
    /**
     * Set GPS location from Android Location object
     */
    public void setCapturedLocation(android.location.Location location) {
        if (location != null) {
            String gpsLoc = String.format("[%f° %s, %f° %s]", 
                Math.abs(location.getLatitude()),
                location.getLatitude() >= 0 ? "N" : "S",
                Math.abs(location.getLongitude()),
                location.getLongitude() >= 0 ? "E" : "W");
            this.capturedGpsLocation = gpsLoc;
        }
    }
    
    @Override
    public String toString() {
        return "PodData{" +
                "signatureUrl='" + signatureUrl + '\'' +
                ", photoUrl='" + photoUrl + '\'' +
                ", comment='" + comment + '\'' +
                ", recipientName='" + recipientName + '\'' +
                ", quantity='" + quantity + '\'' +
                ", capturedGpsLocation='" + capturedGpsLocation + '\'' +
                '}';
    }
}