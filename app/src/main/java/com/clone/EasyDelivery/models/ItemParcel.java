package com.clone.EasyDelivery.models;

/**
 * Model for parcel items in delivery operations
 * Migrated from legacy Model package for consistency
 */
public class ItemParcel {

    private boolean isTextEmpty;
    private boolean completed;
    private String number, hint;
    private String docu, pic, sign, unit;
    private String parcels, time;
    private String driver, company, vehicle;

    public ItemParcel() {
    }

    public ItemParcel(String hint) {
        this.hint = hint;
    }

    // Getters and setters
    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getVehicle() {
        return vehicle;
    }

    public void setVehicle(String vehicle) {
        this.vehicle = vehicle;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getParcels() {
        return parcels;
    }

    public void setParcels(String parcels) {
        this.parcels = parcels;
    }

    public boolean isTextEmpty() {
        return isTextEmpty;
    }

    public void setTextEmpty(boolean textEmpty) {
        this.isTextEmpty = textEmpty;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getDocu() {
        return docu;
    }

    public void setDocu(String docu) {
        this.docu = docu;
    }

    public String getPic() {
        return pic;
    }

    public void setPic(String pic) {
        this.pic = pic;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}