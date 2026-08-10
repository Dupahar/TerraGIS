package com.terra.gis.diagnostics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;

class DiagnosticsBundleServiceTest {

    @Test
    void createBundle_writesManifestAndDiagnosticEntries() throws Exception {
        Path diagnosticsDir = Files.createTempDirectory("terragis-diagnostics-bundle-");
        Path incident = diagnosticsDir.resolve("incident-20260414-000001-test.json");
        Path hsErr = diagnosticsDir.resolve("hs_err_pid4242.log");
        Files.writeString(incident, "{\"incident\":true}", StandardCharsets.UTF_8);
        Files.writeString(hsErr, "jvm crash sample", StandardCharsets.UTF_8);

        Path logsDir = Path.of("logs");
        Files.createDirectories(logsDir);
        Path tempLog = logsDir.resolve("terragis-test-diagnosticsbundle.log");
        Files.writeString(tempLog, "test log line", StandardCharsets.UTF_8);

        System.setProperty("terragis.diagnostics.dir", diagnosticsDir.toString());

        try {
            DiagnosticsBundleService service = new DiagnosticsBundleService();
            DiagnosticsBundleService.BundleResult result = service.createBundle("1.0.0-beta.6", "session-123");

            assertTrue(Files.exists(result.bundlePath()), "Expected diagnostics bundle zip to be created");
            assertTrue(result.fileCount() >= 2, "Expected collected diagnostics files");

            Set<String> entries = readZipEntries(result.bundlePath());
            assertTrue(entries.contains("manifest.json"), "Expected manifest entry");
            assertTrue(entries.stream().anyMatch(name -> name.endsWith("incident-20260414-000001-test.json")), "Expected incident entry in zip");
            assertTrue(entries.stream().anyMatch(name -> name.endsWith("hs_err_pid4242.log")), "Expected hs_err entry in zip");
            assertTrue(entries.stream().anyMatch(name -> name.endsWith("terragis-test-diagnosticsbundle.log")), "Expected log entry in zip");
        } finally {
            System.clearProperty("terragis.diagnostics.dir");
            try {
                Files.deleteIfExists(tempLog);
            } catch (IOException ignored) {
                // Cleanup best-effort only.
            }
        }
    }

    @Test
    void resolveDiagnosticsDirectory_prefersSystemPropertyOverride() throws Exception {
        Path diagnosticsDir = Files.createTempDirectory("terragis-diagnostics-dir-");
        System.setProperty("terragis.diagnostics.dir", diagnosticsDir.toString());

        try {
            DiagnosticsBundleService service = new DiagnosticsBundleService();
            Method resolver = DiagnosticsBundleService.class.getDeclaredMethod("resolveDiagnosticsDirectory");
            resolver.setAccessible(true);

            Path resolved = (Path) resolver.invoke(service);
            assertTrue(diagnosticsDir.equals(resolved), "Expected diagnostics dir override to be respected");
        } finally {
            System.clearProperty("terragis.diagnostics.dir");
        }
    }

    private Set<String> readZipEntries(Path zipPath) throws IOException {
        Set<String> names = new HashSet<>();
        try (ZipFile zipFile = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                names.add(enumeration.nextElement().getName());
            }
        }
        return names;
    }
}
