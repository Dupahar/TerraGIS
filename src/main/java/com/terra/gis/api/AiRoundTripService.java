package com.terra.gis.api;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skeleton service for AI request/response round trips.
 */
public class AiRoundTripService {

    private static final Logger log = LoggerFactory.getLogger(AiRoundTripService.class);
    private static final int MAX_PIPELINE_TILES = 4096;
    private static final int MAX_OUTPUT_POLYGONS = 50_000;
    private static final int MIN_COMPONENT_PIXELS = 6;
    private static final double MIN_COMPONENT_FILL_RATIO = 0.08d;

    private final TerraApiClient apiClient;

    public AiRoundTripService(TerraApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public RoundTripResult runHandshake(String clientId) {
        TerraApiClient.PingResult ping = apiClient.ping(clientId);
        return new RoundTripResult(true, ping.message(), ping.serverTimeEpochMs());
    }

    /**
     * Runs a local mock AI segmentation round-trip pipeline.
     *
     * <p>The method intentionally avoids network calls so the desktop app can validate tile and
     * conversion logic without depending on a running backend service.</p>
     */
    public PipelineResult runMockSegmentationPipeline(
            int rasterWidth,
            int rasterHeight,
            int tileSize,
            com.terra.gis.api.CancellationToken cancellationToken,
            com.terra.gis.api.AiPipelineProgressListener progressListener) {
        return runSegmentationPipeline(
                rasterWidth,
                rasterHeight,
                tileSize,
                "default-segmentation",
                false,
                null,
                cancellationToken,
                progressListener);
    }

    /**
     * Runs segmentation with backend-first behavior and local fallback.
     * Pass {@code null} for {@code rasterPath} when the raster file is not needed by the backend.
     */
    public PipelineResult runSegmentationPipeline(
            int rasterWidth,
            int rasterHeight,
            int tileSize,
            String modelName,
            boolean preferBackend,
            String rasterPath,
            com.terra.gis.api.CancellationToken cancellationToken,
            com.terra.gis.api.AiPipelineProgressListener progressListener) {
        List<com.terra.gis.api.RasterTile> tiles = com.terra.gis.api.RasterTileChunker.chunk(rasterWidth, rasterHeight, tileSize);
        if (tiles.size() > MAX_PIPELINE_TILES) {
            throw new IllegalArgumentException("Raster is too large for segmentation at current tile size. Increase tile size or crop area.");
        }
        List<Polygon> polygons = new ArrayList<>();

        boolean backendAvailable = preferBackend;
        int processedTiles = 0;
        for (com.terra.gis.api.RasterTile tile : tiles) {
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                notifyProgress(progressListener, "cancelled", processedTiles, tiles.size(), "Pipeline cancelled by user");
                return new PipelineResult(false, "Cancelled", processedTiles, tiles.size(), polygons.size(), true, List.copyOf(polygons));
            }

            com.terra.gis.api.AiMaskResult result = null;
            if (backendAvailable) {
                try {
                    TerraApiClient.SegmentMaskResult backendResult = apiClient.segmentTile(modelName, tile, rasterWidth, rasterHeight, rasterPath);
                    result = new com.terra.gis.api.AiMaskResult(
                            tile,
                            backendResult.maskWidth(),
                            backendResult.maskHeight(),
                            backendResult.mask(),
                            backendResult.classLabel(),
                            backendResult.confidence());
                } catch (com.terra.gis.api.TerraApiException ex) {
                    backendAvailable = false;
                    log.warn("Backend segmentation unavailable, falling back to local mask generation: {}", ex.getMessage());
                }
            }

            if (result == null) {
                boolean[] mask = createMockMask(tile.width(), tile.height());
                result = new com.terra.gis.api.AiMaskResult(tile, tile.width(), tile.height(), mask, "mock-feature", 0.92d);
            }

            int remainingBudget = MAX_OUTPUT_POLYGONS - polygons.size();
            if (remainingBudget <= 0) {
                notifyProgress(progressListener, "completed", processedTiles, tiles.size(), "Reached overlay polygon limit");
                return new PipelineResult(true, "Completed with polygon limit", processedTiles, tiles.size(), polygons.size(), false, List.copyOf(polygons));
            }

                polygons.addAll(com.terra.gis.api.MaskToPolygonConverter.convert(
                    result,
                    remainingBudget,
                    MIN_COMPONENT_PIXELS,
                    MIN_COMPONENT_FILL_RATIO));
            processedTiles++;
            notifyProgress(progressListener, "processing", processedTiles, tiles.size(), "Processed tile " + processedTiles + "/" + tiles.size());
        }

        notifyProgress(progressListener, "completed", processedTiles, tiles.size(), "Pipeline completed");
        return new PipelineResult(true, "Completed", processedTiles, tiles.size(), polygons.size(), false, List.copyOf(polygons));
    }

    private void notifyProgress(
            com.terra.gis.api.AiPipelineProgressListener progressListener,
            String stage,
            int completed,
            int total,
            String message) {
        if (progressListener != null) {
            progressListener.onProgress(stage, completed, total, message);
        }
    }

    private boolean[] createMockMask(int width, int height) {
        boolean[] mask = new boolean[width * height];
        int cx = width / 2;
        int cy = height / 2;
        int radius = Math.max(2, Math.min(width, height) / 10);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int dx = x - cx;
                int dy = y - cy;
                if ((dx * dx + dy * dy) <= radius * radius) {
                    mask[y * width + x] = true;
                }
            }
        }
        return mask;
    }

    public record RoundTripResult(boolean success, String message, long serverTimeEpochMs) {
    }

    public record PipelineResult(
            boolean success,
            String message,
            int processedTiles,
            int totalTiles,
            int polygonCount,
            boolean cancelled,
            List<Polygon> polygons) {
    }
}
