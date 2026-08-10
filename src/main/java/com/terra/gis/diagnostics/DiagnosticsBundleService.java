package com.terra.gis.diagnostics;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DiagnosticsBundleService {

    private static final DateTimeFormatter TS_FILE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    public record BundleResult(Path bundlePath, int fileCount) {
    }

    public BundleResult createBundle(String appVersion, String sessionId) throws IOException {
        Path diagnosticsDir = resolveDiagnosticsDirectory();
        Files.createDirectories(diagnosticsDir);

        Path bundlesDir = diagnosticsDir.resolve("bundles");
        Files.createDirectories(bundlesDir);

        String fileName = "diagnostics-bundle-" + TS_FILE.format(Instant.now()) + ".zip";
        Path bundlePath = bundlesDir.resolve(fileName);

        List<Path> files = collectFiles(diagnosticsDir);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(bundlePath), StandardCharsets.UTF_8)) {
            String manifest = buildManifest(appVersion, sessionId, files.size());
            writeStringEntry(zos, "manifest.json", manifest);

            for (Path file : files) {
                String category = categorize(file);
                writeFileEntry(zos, category + "/" + file.getFileName(), file);
            }
        }

        return new BundleResult(bundlePath, files.size());
    }

    private List<Path> collectFiles(Path diagnosticsDir) throws IOException {
        List<Path> files = new ArrayList<>();

        Path logsDir = Path.of("logs");
        if (Files.isDirectory(logsDir)) {
            try (DirectoryStream<Path> logStream = Files.newDirectoryStream(logsDir, "terragis*.log")) {
                for (Path p : logStream) {
                    if (Files.isRegularFile(p)) {
                        files.add(p);
                    }
                }
            }
        }

        try (DirectoryStream<Path> incidentStream = Files.newDirectoryStream(diagnosticsDir, "incident-*.json")) {
            for (Path p : incidentStream) {
                if (Files.isRegularFile(p)) {
                    files.add(p);
                }
            }
        }

        try (DirectoryStream<Path> hsErrStream = Files.newDirectoryStream(diagnosticsDir, "hs_err_pid*.log")) {
            for (Path p : hsErrStream) {
                if (Files.isRegularFile(p)) {
                    files.add(p);
                }
            }
        }

        return files;
    }

    private void writeStringEntry(ZipOutputStream zos, String entryName, String content) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        zos.write(bytes);
        zos.closeEntry();
    }

    private void writeFileEntry(ZipOutputStream zos, String entryName, Path source) throws IOException {
        ZipEntry entry = new ZipEntry(entryName.replace('\\', '/'));
        zos.putNextEntry(entry);

        try (InputStream in = Files.newInputStream(source)) {
            in.transferTo(zos);
        }

        zos.closeEntry();
    }

    private String categorize(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.startsWith("incident-") && name.endsWith(".json")) {
            return "incidents";
        }
        if (name.startsWith("hs_err_pid") && name.endsWith(".log")) {
            return "jvm-crash";
        }
        if (name.endsWith(".log")) {
            return "logs";
        }
        return "misc";
    }

    private String buildManifest(String appVersion, String sessionId, int fileCount) {
        String version = appVersion == null || appVersion.isBlank() ? "unknown" : appVersion;
        String sid = sessionId == null || sessionId.isBlank() ? "unknown" : sessionId;

        return "{\n"
                + "  \"createdAtUtc\": \"" + Instant.now() + "\",\n"
                + "  \"appVersion\": \"" + escape(version) + "\",\n"
                + "  \"sessionId\": \"" + escape(sid) + "\",\n"
                + "  \"fileCount\": " + fileCount + "\n"
                + "}\n";
    }

    private Path resolveDiagnosticsDirectory() {
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

    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
