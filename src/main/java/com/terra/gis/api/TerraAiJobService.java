package com.terra.gis.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Local fallback job service for importing TERRA.AI outputs when orchestrator RPC is unavailable.
 */
public final class TerraAiJobService {

    public JobRunResult runLocalJob() {
        Path outputDir = resolveOutputDirectory();
        if (outputDir != null && Files.isDirectory(outputDir)) {
            return new JobRunResult(true,
                    "Local TERRA.AI output directory detected at " + outputDir + " (" + Instant.now() + ")",
                    outputDir);
        }

        return new JobRunResult(false,
                "Local runner is not configured. Set TERRAGIS_TERRA_AI_OUTPUT_DIR to an existing output folder.",
                null);
    }

    public Deliverables discoverDeliverables(Path outputDir) {
        if (outputDir == null || !Files.isDirectory(outputDir)) {
            return new Deliverables(List.of(), List.of(), List.of());
        }

        List<Path> rasters = new ArrayList<>();
        List<Path> vectors = new ArrayList<>();
        List<Path> manifests = new ArrayList<>();

        try (var stream = Files.walk(outputDir, 4)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".tif") || name.endsWith(".tiff")) {
                    rasters.add(path);
                    return;
                }
                if (name.endsWith(".shp") || name.endsWith(".gpkg") || name.endsWith(".geojson") || name.endsWith(".json")) {
                    vectors.add(path);
                }
                if (name.endsWith(".json") && (name.contains("manifest") || name.contains("provenance"))) {
                    manifests.add(path);
                }
            });
        } catch (IOException ignored) {
            return new Deliverables(List.of(), List.of(), List.of());
        }

        return new Deliverables(List.copyOf(rasters), List.copyOf(vectors), List.copyOf(manifests));
    }

    private Path resolveOutputDirectory() {
        String fromEnv = System.getenv("TERRAGIS_TERRA_AI_OUTPUT_DIR");
        if (fromEnv != null && !fromEnv.isBlank()) {
            Path candidate = Path.of(fromEnv.trim());
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public record JobRunResult(boolean success, String message, Path outputDirectory) {
    }

    public record Deliverables(List<Path> rasters, List<Path> vectors, List<Path> manifests) {
    }
}
