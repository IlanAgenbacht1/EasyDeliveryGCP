package com.clone.EasyDelivery.Api.models.request;

/**
 * Request model for driver PIN setup
 */
public class SetupPinRequest {
    private String driverId;
    private String pin;

    public SetupPinRequest() {
    }

    public SetupPinRequest(String driverId, String pin) {
        this.driverId = driverId;
        this.pin = pin;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }
}