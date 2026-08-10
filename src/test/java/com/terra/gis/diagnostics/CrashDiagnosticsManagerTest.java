package com.terra.gis.diagnostics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class CrashDiagnosticsManagerTest {

    @Test
    void writesIncidentFileForUnhandledException() throws Exception {
        Path tempDir = Files.createTempDirectory("terragis-diagnostics-test-");
        System.setProperty("terragis.diagnostics.dir", tempDir.toString());

        try {
            CrashDiagnosticsManager.install("test-version");

            Thread t = new Thread(() -> {
                throw new RuntimeException("intentional-test-crash");
            }, "diagnostics-test-thread");
            t.start();
            t.join();

            boolean found = false;
            for (int i = 0; i < 20; i++) {
                try (Stream<Path> stream = Files.list(tempDir)) {
                    found = stream.anyMatch(path -> path.getFileName().toString().startsWith("incident-")
                            && path.getFileName().toString().endsWith(".json"));
                }
                if (found) {
                    break;
                }
                Thread.sleep(100);
            }

            assertTrue(found, "Expected an incident JSON file after uncaught exception");
        } finally {
            System.clearProperty("terragis.diagnostics.dir");
        }
    }
}
