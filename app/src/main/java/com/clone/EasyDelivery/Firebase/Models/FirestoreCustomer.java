package com.clone.EasyDelivery.Firebase.Models;

import com.google.firebase.firestore.PropertyName;

import java.util.HashMap;
import java.util.Map;

/**
 * Firestore Customer model for delivery customer information
 */
public class FirestoreCustomer {
    
    @PropertyName("name")
    private String name = "";
    
    @PropertyName("contact")
    private String contact = "";
    
    @PropertyName("number")
    private String number = "";
    
    @PropertyName("email")
    private String email = "";
    
    // Default constructor required for Firestore
    public FirestoreCustomer() {}
    
    public FirestoreCustomer(String name, String contact, String number, String email) {
        this.name = name != null ? name : "";
        this.contact = contact != null ? contact : "";
        this.number = number != null ? number : "";
        this.email = email != null ? email : "";
    }
    
    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name != null ? name : ""; }
    
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact != null ? contact : ""; }
    
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number != null ? number : ""; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email != null ? email : ""; }
    
    /**
     * Convert to Map for Firestore operations
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("name", name);
        result.put("contact", contact);
        result.put("number", number);
        result.put("email", email);
        return result;
    }
    
    @Override
    public String toString() {
        return "FirestoreCustomer{" +
                "name='" + name + '\'' +
                ", contact='" + contact + '\'' +
                ", number='" + number + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}