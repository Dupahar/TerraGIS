package com.terra.gis.licensing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);
    private static final int DEFAULT_GRACE_DAYS = 7;

    public LicenseEvaluation evaluateNow() {
        if (isDeveloperLicenseBypassEnabled()) {
            return LicenseEvaluation.active("Developer mode: license token check bypassed.");
        }

        Path licensePath = resolveLicensePath();
        if (!Files.exists(licensePath)) {
            return LicenseEvaluation.grace("No beta license token found. Running in grace mode.");
        }

        String json;
        try {
            json = Files.readString(licensePath, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("Failed to read license token file: {}", licensePath, ex);
            return LicenseEvaluation.grace("License token cannot be read. Running in grace mode.");
        }

        String status = findString(json, "status").orElse("active").toLowerCase(Locale.ROOT);
        int graceDays = findInt(json, "graceDays").orElse(DEFAULT_GRACE_DAYS);
        Instant expiresAt = findInstant(json, "expiresAt").orElse(null);

        if ("revoked".equals(status) || "suspended".equals(status)) {
            return LicenseEvaluation.readOnly("Beta license is " + status + ". App is in read-only mode.");
        }

        if (expiresAt == null) {
            return LicenseEvaluation.active("License active");
        }

        Instant now = Instant.now();
        if (!now.isAfter(expiresAt)) {
            return LicenseEvaluation.active("License active");
        }

        Instant graceUntil = expiresAt.plus(Duration.ofDays(Math.max(0, graceDays)));
        if (!now.isAfter(graceUntil)) {
            return LicenseEvaluation.grace("License expired. Running in grace mode until " + graceUntil + ".");
        }

        return LicenseEvaluation.readOnly("License expired and grace period ended. App is in read-only mode.");
    }

    private boolean isDeveloperLicenseBypassEnabled() {
        String value = firstNonBlank(
                System.getProperty("terragis.license.devBypass"),
                System.getenv("TERRAGIS_LICENSE_DEV_BYPASS"));

        if (value == null) {
            return false;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized)
                || "true".equals(normalized)
                || "yes".equals(normalized)
                || "on".equals(normalized);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Path resolveLicensePath() {
        String override = System.getProperty("terragis.license.file", "").trim();
        if (!override.isBlank()) {
            return Path.of(override);
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "TerraGIS", "license", "license-token.json");
        }

        return Path.of(System.getProperty("user.home"), ".terragis", "license", "license-token.json");
    }

    private java.util.Optional<String> findString(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return java.util.Optional.ofNullable(matcher.group(1));
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<Integer> findInt(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return java.util.Optional.of(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                return java.util.Optional.empty();
            }
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<Instant> findInstant(String json, String key) {
        java.util.Optional<String> raw = findString(json, key);
        if (raw.isEmpty() || raw.get().isBlank()) {
            return java.util.Optional.empty();
        }

        try {
            return java.util.Optional.of(Instant.parse(raw.get().trim()));
        } catch (Exception ex) {
            log.warn("Invalid instant value for key {}: {}", key, raw.get());
            return java.util.Optional.empty();
        }
    }
}
