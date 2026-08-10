package com.terra.gis.licensing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class LicenseServiceTest {

    @Test
    void expiredLicenseBeyondGraceBecomesReadOnly() throws Exception {
        Path temp = Files.createTempFile("terragis-license-", ".json");
        Instant expired = Instant.now().minusSeconds(10L * 24 * 3600);
        String json = "{\n"
                + "  \"status\": \"active\",\n"
                + "  \"expiresAt\": \"" + expired + "\",\n"
                + "  \"graceDays\": 7\n"
                + "}\n";
        Files.writeString(temp, json, StandardCharsets.UTF_8);

        System.setProperty("terragis.license.file", temp.toString());
        try {
            LicenseService service = new LicenseService();
            LicenseEvaluation evaluation = service.evaluateNow();
            assertEquals(LicenseMode.READ_ONLY, evaluation.mode());
        } finally {
            System.clearProperty("terragis.license.file");
        }
    }

    @Test
    void revokedLicenseIsReadOnlyImmediately() throws Exception {
        Path temp = Files.createTempFile("terragis-license-", ".json");
        String json = "{\n"
                + "  \"status\": \"revoked\",\n"
                + "  \"expiresAt\": \"" + Instant.now().plusSeconds(3600) + "\",\n"
                + "  \"graceDays\": 7\n"
                + "}\n";
        Files.writeString(temp, json, StandardCharsets.UTF_8);

        System.setProperty("terragis.license.file", temp.toString());
        try {
            LicenseService service = new LicenseService();
            LicenseEvaluation evaluation = service.evaluateNow();
            assertEquals(LicenseMode.READ_ONLY, evaluation.mode());
        } finally {
            System.clearProperty("terragis.license.file");
        }
    }
}
