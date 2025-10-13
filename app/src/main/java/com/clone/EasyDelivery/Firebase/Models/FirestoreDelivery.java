package com.clone.EasyDelivery.Firebase.Models;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.PropertyName;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.HashMap;
import java.util.Map;

/**
 * Firestore Delivery model matching Cloud API schema
 * Document path: /trips/{tripId}/deliveries/{deliveryId}
 * Delivery ID is typically the SHINUMBER from Sage
 */
public class FirestoreDelivery {
    
    @DocumentId
    private String id;
    
    @PropertyName("address")
    private String address = "";
    
    @PropertyName("customer")
    private FirestoreCustomer customer = new FirestoreCustomer();
    
    @PropertyName("gpsLocation")
    private String gpsLocation = ""; // Format: "[lat, lng]"
    
    @PropertyName("orderNumber")
    private String orderNumber = "";
    
    @PropertyName("quantityExpected")
    private String quantityExpected = "";
    
    @PropertyName("status")
    private String status = "pending"; // pending, completed
    
    @PropertyName("podData")
    private String podData = "{\"signatureUrl\": \"\", \"capturedGpsLocation\": \"[0° N, 0° E]\", \"photoUrl\": \"\", \"comment\": \"\", \"recipientName\": \"\", \"quantity\": \"\"}";
    
    // Default constructor required for Firestore
    public FirestoreDelivery() {}
    
    public FirestoreDelivery(String orderNumber, String address, FirestoreCustomer customer, String gpsLocation, String quantityExpected) {
        this.orderNumber = orderNumber;
        this.address = address;
        this.customer = customer != null ? customer : new FirestoreCustomer();
        this.gpsLocation = gpsLocation;
        this.quantityExpected = quantityExpected;
        this.status = "pending";
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address != null ? address : ""; }
    
    public FirestoreCustomer getCustomer() { return customer; }
    public void setCustomer(FirestoreCustomer customer) { this.customer = customer != null ? customer : new FirestoreCustomer(); }
    
    public String getGpsLocation() { return gpsLocation; }
    public void setGpsLocation(String gpsLocation) { this.gpsLocation = gpsLocation != null ? gpsLocation : ""; }
    
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber != null ? orderNumber : ""; }
    
    public String getQuantityExpected() { return quantityExpected; }
    public void setQuantityExpected(String quantityExpected) { this.quantityExpected = quantityExpected != null ? quantityExpected : ""; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status != null ? status : "pending"; }
    
    public String getPodData() { return podData; }
    public void setPodData(String podData) { this.podData = podData != null ? podData : "{}"; }
    
    /**
     * Check if delivery is completed
     */
    public boolean isCompleted() {
        return "completed".equals(status);
    }
    
    /**
     * Check if delivery is pending
     */
    public boolean isPending() {
        return "pending".equals(status);
    }
    
    /**
     * Parse POD data as PodData object
     */
    public PodData getParsedPodData() {
        try {
            Gson gson = new Gson();
            return gson.fromJson(podData, PodData.class);
        } catch (JsonSyntaxException e) {
            return new PodData(); // Return empty POD data if parsing fails
        }
    }
    
    /**
     * Set POD data from PodData object
     */
    public void setParsedPodData(PodData podDataObj) {
        Gson gson = new Gson();
        this.podData = gson.toJson(podDataObj);
    }
    
    /**
     * Convert to Map for Firestore batch operations
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("address", address);
        result.put("customer", customer != null ? customer.toMap() : new HashMap<>());
        result.put("gpsLocation", gpsLocation);
        result.put("orderNumber", orderNumber);
        result.put("quantityExpected", quantityExpected);
        result.put("status", status);
        result.put("podData", podData);
        return result;
    }
    
    /**
     * Convert from legacy Delivery model
     */
    public static FirestoreDelivery fromLegacyDelivery(com.clone.EasyDelivery.Model.Delivery legacy) {
        FirestoreDelivery firestore = new FirestoreDelivery();
        
        firestore.setId(legacy.getDocument());
        firestore.setOrderNumber(legacy.getOrderNumber() != null ? legacy.getOrderNumber() : "");
        firestore.setAddress(legacy.getAddress() != null ? legacy.getAddress() : "");
        firestore.setQuantityExpected(String.valueOf(legacy.getNumberOfParcels()));
        firestore.setStatus(legacy.completed() ? "completed" : "pending");
        
        // Convert customer info
        FirestoreCustomer customer = new FirestoreCustomer();
        customer.setName(legacy.getCustomerName() != null ? legacy.getCustomerName() : "");
        customer.setContact(legacy.getContactName() != null ? legacy.getContactName() : "");
        customer.setNumber(legacy.getContactNumber() != null ? legacy.getContactNumber() : "");
        firestore.setCustomer(customer);
        
        // Convert GPS location
        if (legacy.getLocation() != null) {
            String gpsLoc = String.format("[%f° %s, %f° %s]", 
                Math.abs(legacy.getLocation().getLatitude()),
                legacy.getLocation().getLatitude() >= 0 ? "N" : "S",
                Math.abs(legacy.getLocation().getLongitude()),
                legacy.getLocation().getLongitude() >= 0 ? "E" : "W");
            firestore.setGpsLocation(gpsLoc);
        }
        
        // Convert POD data if completed
        if (legacy.completed()) {
            PodData pod = new PodData();
            pod.setPhotoUrl(legacy.getImagePath() != null ? legacy.getImagePath() : "");
            pod.setSignatureUrl(legacy.getSignPath() != null ? legacy.getSignPath() : "");
            pod.setComment(legacy.getComment() != null ? legacy.getComment() : "");
            pod.setQuantity(String.valueOf(legacy.getNumberOfParcels()));
            firestore.setParsedPodData(pod);
        }
        
        return firestore;
    }
    
    @Override
    public String toString() {
        return "FirestoreDelivery{" +
                "id='" + id + '\'' +
                ", orderNumber='" + orderNumber + '\'' +
                ", address='" + address + '\'' +
                ", status='" + status + '\'' +
                ", customer=" + customer +
                '}';
    }
}