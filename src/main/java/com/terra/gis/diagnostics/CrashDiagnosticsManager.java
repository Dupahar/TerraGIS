package com.terra.gis.diagnostics;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CrashDiagnosticsManager {

    private static final Logger log = LoggerFactory.getLogger(CrashDiagnosticsManager.class);
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final DateTimeFormatter TS_FILE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private static volatile String sessionId;
    private static volatile String appVersion;
    private static volatile Path diagnosticsDirectory;

    private CrashDiagnosticsManager() {
    }

    public static void install(String version) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }

        appVersion = version == null || version.isBlank() ? "unknown" : version;
        sessionId = UUID.randomUUID().toString();
        diagnosticsDirectory = resolveDiagnosticsDirectory();

        MDC.put("sessionId", sessionId);
        System.setProperty("terragis.session.id", sessionId);

        createDirectoryIfNeeded(diagnosticsDirectory);

        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.UncaughtExceptionHandler handler = (thread, throwable) -> {
            handleUncaughtException(thread, throwable);
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        };

        Thread.setDefaultUncaughtExceptionHandler(handler);
        Thread.currentThread().setUncaughtExceptionHandler(handler);

        try {
            Platform.runLater(() -> {
                Thread fxThread = Thread.currentThread();
                MDC.put("sessionId", sessionId);
                fxThread.setUncaughtExceptionHandler(handler);
            });
        } catch (IllegalStateException fxNotInitialized) {
            log.debug("JavaFX platform not initialized yet; FX-thread exception handler will be set after startup");
        }

        log.info("Crash diagnostics initialized: sessionId={}, diagnosticsDir={}", sessionId, diagnosticsDirectory.toAbsolutePath());
    }

    public static String getSessionId() {
        return sessionId;
    }

    public static Path getDiagnosticsDirectory() {
        return diagnosticsDirectory;
    }

    private static void handleUncaughtException(Thread thread, Throwable throwable) {
        String incidentId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String fileName = "incident-" + TS_FILE.format(now) + "-" + incidentId.substring(0, 8) + ".json";
        Path incidentFile = diagnosticsDirectory.resolve(fileName);

        String payload = buildIncidentPayload(incidentId, now, thread, throwable);
        try {
            Files.writeString(incidentFile, payload, StandardCharsets.UTF_8);
        } catch (Exception writeError) {
            log.error("Failed writing crash incident file {}", incidentFile, writeError);
        }

        log.error("Unhandled exception captured: incidentId={}, thread={}, incidentFile={}",
                incidentId,
                thread != null ? thread.getName() : "unknown",
                incidentFile.toAbsolutePath(),
                throwable);
    }

    private static String buildIncidentPayload(String incidentId, Instant timestamp, Thread thread, Throwable throwable) {
        String exceptionClass = throwable != null ? throwable.getClass().getName() : "unknown";
        String message = throwable != null ? throwable.getMessage() : "null";
        String stack = throwable != null ? stackTrace(throwable) : "";

        return "{\n"
                + "  \"incidentId\": \"" + escape(incidentId) + "\",\n"
                + "  \"timestampUtc\": \"" + escape(timestamp.toString()) + "\",\n"
                + "  \"appVersion\": \"" + escape(appVersion) + "\",\n"
                + "  \"sessionId\": \"" + escape(sessionId) + "\",\n"
                + "  \"threadName\": \"" + escape(thread != null ? thread.getName() : "unknown") + "\",\n"
                + "  \"threadId\": " + (thread != null ? thread.threadId() : -1) + ",\n"
                + "  \"exceptionClass\": \"" + escape(exceptionClass) + "\",\n"
                + "  \"message\": \"" + escape(message) + "\",\n"
                + "  \"stackTrace\": \"" + escape(stack) + "\"\n"
                + "}\n";
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    private static void createDirectoryIfNeeded(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create diagnostics directory: " + directory, e);
        }
    }

    private static Path resolveDiagnosticsDirectory() {
        String override = System.getProperty("terragis.diagnostics.dir", "").trim();
        if (!override.isBlank()) {
            return Path.of(override);
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "TerraGIS", "diagnostics");
        }
        return Path.of(System.getProperty("user.home"), ".terragis", "diagnostics");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
