package com.clone.EasyDelivery.Api.services;

import com.clone.EasyDelivery.Api.models.request.ClaimTripRequest;
import com.clone.EasyDelivery.Api.models.response.BaseApiResponse;
import com.clone.EasyDelivery.models.Trip;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface TripService {
    
    @GET("trips")
    Call<TripsResponse> getAvailableTrips(@Query("clientId") String clientId, @Query("status") String status);
    
    @GET("trips/my")
    Call<TripsResponse> getMyTrips(@Query("sessionId") String sessionId);
    
    @GET("trips/{tripId}")
    Call<TripResponse> getTrip(@Path("tripId") String tripId, @Query("sessionId") String sessionId);
    
    @POST("trips/{tripId}/claim")
    Call<BaseApiResponse> claimTrip(@Path("tripId") String tripId, @Body ClaimTripRequest request);
    
    @POST("trips/{tripId}/start")
    Call<BaseApiResponse> startTrip(@Path("tripId") String tripId, @Body ClaimTripRequest request);
    
    @POST("trips/{tripId}/complete")
    Call<BaseApiResponse> completeTrip(@Path("tripId") String tripId, @Body ClaimTripRequest request);
    
    public static class TripsResponse extends BaseApiResponse {
        private String clientId;
        private String status;
        private List<Trip> trips;
        private int count;
        
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public List<Trip> getTrips() {
            return trips;
        }

        public void setTrips(List<Trip> trips) {
            this.trips = trips;
        }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }
    
    public static class TripResponse extends BaseApiResponse {
        private Trip trip;

        public Trip getTrip() {
            return trip;
        }

        public void setTrip(Trip trip) {
            this.trip = trip;
        }
    }
}