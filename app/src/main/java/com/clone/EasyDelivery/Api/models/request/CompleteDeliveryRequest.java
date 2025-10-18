package com.clone.EasyDelivery.Api.models.request;

/**
 * Request model for completing a delivery
 */
public class CompleteDeliveryRequest {
    private String sessionId;
    private String signatureUrl;
    private String photoUrl;
    private String capturedGpsLocation;
    private String comment;
    private String recipientName;
    private String quantity;
    
    public CompleteDeliveryRequest() {}
    
    public CompleteDeliveryRequest(String sessionId, String signatureUrl, String photoUrl, 
                                 String capturedGpsLocation, String comment, 
                                 String recipientName, String quantity) {
        this.sessionId = sessionId;
        this.signatureUrl = signatureUrl;
        this.photoUrl = photoUrl;
        this.capturedGpsLocation = capturedGpsLocation;
        this.comment = comment;
        this.recipientName = recipientName;
        this.quantity = quantity;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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
    
    public String getCapturedGpsLocation() {
        return capturedGpsLocation;
    }
    
    public void setCapturedGpsLocation(String capturedGpsLocation) {
        this.capturedGpsLocation = capturedGpsLocation;
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
    
    public String getQuantity() {
        return quantity;
    }
    
    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }
}