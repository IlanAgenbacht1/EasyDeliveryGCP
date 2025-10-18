package com.clone.EasyDelivery.util;

/**
 * Simple signature package class to replace deprecated SecurityManager.SignaturePackage
 * This provides basic structure for signature data without complex encryption
 * <p>
 * Migrated from legacy Utility package for consistency
 */
public class SignaturePackage {
    public byte[] encryptedData;
    public String integrityData;

    public SignaturePackage(byte[] encryptedData, String integrityData) {
        this.encryptedData = encryptedData;
        this.integrityData = integrityData;
    }

    @Override
    public String toString() {
        return "SignaturePackage{" +
                "encryptedData=" + (encryptedData != null ? encryptedData.length + " bytes" : "null") +
                ", integrityData='" + (integrityData != null ? integrityData.length() + " chars" : "null") + '\'' +
                '}';
    }
}