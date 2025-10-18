package com.clone.EasyDelivery.Api.services;

import com.clone.EasyDelivery.Api.models.request.CompleteDeliveryRequest;
import com.clone.EasyDelivery.Api.models.response.BaseApiResponse;
import com.clone.EasyDelivery.models.Delivery;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Retrofit service interface for delivery operations
 * Maps to DeliveriesController in DeliveryCloudApi
 */
public interface DeliveryService {
    
    /**
     * List deliveries for a specific trip
     */
    @GET("trips/{tripId}/deliveries")
    Call<DeliveriesResponse> getTripDeliveries(@Path("tripId") String tripId, 
                                              @Query("sessionId") String sessionId, 
                                              @Query("status") String status);
    
    /**
     * Get specific delivery details
     */
    @GET("trips/{tripId}/deliveries/{deliveryId}")
    Call<DeliveryResponse> getDelivery(@Path("tripId") String tripId, 
                                      @Path("deliveryId") String deliveryId, 
                                      @Query("sessionId") String sessionId);
    
    /**
     * Complete a delivery with POD data
     */
    @POST("trips/{tripId}/deliveries/{deliveryId}/complete")
    Call<BaseApiResponse> completeDelivery(@Path("tripId") String tripId, 
                                          @Path("deliveryId") String deliveryId, 
                                          @Body CompleteDeliveryRequest request);
    
    // Convenience aliases for backward compatibility
    
    /**
     * Alias for getTripDeliveries with simplified signature
     */
    @GET("trips/{tripId}/deliveries")
    Call<DeliveriesResponse> getDeliveriesForTrip(@Path("tripId") String tripId);
    
    /**
     * Get delivery by ID (assumes we can get tripId from context)
     */
    @GET("deliveries/{deliveryId}")
    Call<DeliveryResponse> getDeliveryById(@Path("deliveryId") String deliveryId);
    
    // Response classes
    public static class DeliveriesResponse extends BaseApiResponse {
        private String tripId;
        private String driverId;
        private String status;
        private List<Delivery> deliveries;
        private int count;
        
        // Getters and setters
        public String getTripId() { return tripId; }
        public void setTripId(String tripId) { this.tripId = tripId; }
        public String getDriverId() { return driverId; }
        public void setDriverId(String driverId) { this.driverId = driverId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public List<Delivery> getDeliveries() {
            return deliveries;
        }

        public void setDeliveries(List<Delivery> deliveries) {
            this.deliveries = deliveries;
        }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }
    
    public static class DeliveryResponse extends BaseApiResponse {
        private String tripId;
        private String deliveryId;
        private Delivery delivery;
        
        // Getters and setters
        public String getTripId() { return tripId; }
        public void setTripId(String tripId) { this.tripId = tripId; }
        public String getDeliveryId() { return deliveryId; }
        public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }

        public Delivery getDelivery() {
            return delivery;
        }

        public void setDelivery(Delivery delivery) {
            this.delivery = delivery;
        }
    }
}