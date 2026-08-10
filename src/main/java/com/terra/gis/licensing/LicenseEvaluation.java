package com.terra.gis.licensing;

public record LicenseEvaluation(LicenseMode mode, String message) {

    public static LicenseEvaluation active(String message) {
        return new LicenseEvaluation(LicenseMode.ACTIVE, message);
    }

    public static LicenseEvaluation grace(String message) {
        return new LicenseEvaluation(LicenseMode.GRACE, message);
    }

    public static LicenseEvaluation readOnly(String message) {
        return new LicenseEvaluation(LicenseMode.READ_ONLY, message);
    }
}
