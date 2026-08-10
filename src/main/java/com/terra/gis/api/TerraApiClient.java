package com.terra.gis.api;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terra.gis.proto.PingRequest;
import com.terra.gis.proto.PingResponse;
import com.terra.gis.proto.SegmentTileRequest;
import com.terra.gis.proto.SegmentTileResponse;
import com.terra.gis.proto.TerraApiServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

/**
 * Thin typed wrapper around gRPC stubs for TerraGIS API calls.
 */
public class TerraApiClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(TerraApiClient.class);

    private static final long PING_DEADLINE_SECONDS = 3;
    private static final long SEGMENT_DEADLINE_SECONDS = resolveSegmentDeadlineSeconds();

    private final ManagedChannel channel;
    private final TerraApiServiceGrpc.TerraApiServiceBlockingStub blockingStub;

    public TerraApiClient(String host, int port) {
        this(createChannel(host, port));
    }

    private static ManagedChannel createChannel(String host, int port) {
        try {
            return ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        } catch (RuntimeException ex) {
            throw new TerraApiException("Failed to create AI backend channel to " + host + ':' + port, ex);
        }
    }

    public TerraApiClient(ManagedChannel channel) {
        this.channel = channel;
        this.blockingStub = TerraApiServiceGrpc.newBlockingStub(channel);
    }

    public PingResult ping(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId cannot be null or blank");
        }

        PingRequest request = PingRequest.newBuilder().setClientId(clientId).build();
        try {
            PingResponse response = blockingStub
                    .withDeadlineAfter(PING_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .ping(request);
            return new PingResult(response.getMessage(), response.getServerTimeEpochMs());
        } catch (StatusRuntimeException ex) {
            log.warn("Ping failed with status code: {}", ex.getStatus().getCode());
            throw new TerraApiException("AI backend unavailable (" + ex.getStatus().getCode() + ")", ex);
        }
    }

    public SegmentMaskResult segmentTile(String modelName, com.terra.gis.api.RasterTile tile, int rasterWidth, int rasterHeight, String rasterPath) {
        if (tile == null) {
            throw new IllegalArgumentException("tile cannot be null");
        }
        if (rasterWidth <= 0 || rasterHeight <= 0) {
            throw new IllegalArgumentException("rasterWidth and rasterHeight must be positive");
        }

        String resolvedModel = (modelName == null || modelName.isBlank()) ? "default-segmentation" : modelName;
        SegmentTileRequest request = SegmentTileRequest.newBuilder()
                .setModelName(resolvedModel)
                .setRasterWidth(rasterWidth)
                .setRasterHeight(rasterHeight)
                .setTileX(tile.x())
                .setTileY(tile.y())
                .setTileWidth(tile.width())
                .setTileHeight(tile.height())
            .setRasterPath(rasterPath == null ? "" : rasterPath)
                .build();

        try {
            SegmentTileResponse response = blockingStub
                    .withDeadlineAfter(SEGMENT_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .segmentTile(request);
            int maskWidth = response.getMaskWidth();
            int maskHeight = response.getMaskHeight();
            if (maskWidth <= 0 || maskHeight <= 0) {
                throw new com.terra.gis.api.TerraApiException("AI backend returned invalid mask dimensions", null);
            }

            byte[] packedMask = response.getPackedMask().toByteArray();
            int expectedLength = maskWidth * maskHeight;
            if (packedMask.length != expectedLength) {
                throw new com.terra.gis.api.TerraApiException("AI backend returned invalid packed mask length", null);
            }

            boolean[] mask = new boolean[expectedLength];
            for (int i = 0; i < expectedLength; i++) {
                mask[i] = packedMask[i] != 0;
            }

            return new SegmentMaskResult(maskWidth, maskHeight, mask, response.getClassLabel(), response.getConfidence());
        } catch (StatusRuntimeException ex) {
            log.warn("SegmentTile failed with status code: {}", ex.getStatus().getCode());
            throw new com.terra.gis.api.TerraApiException("AI segmentation unavailable (" + ex.getStatus().getCode() + ")", ex);
        }
    }

    @Override
    public void close() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(3, TimeUnit.SECONDS)) {
                channel.shutdownNow();
                channel.awaitTermination(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }

    public record PingResult(String message, long serverTimeEpochMs) {
    }

    public record SegmentMaskResult(int maskWidth, int maskHeight, boolean[] mask, String classLabel, double confidence) {
    }

    private static long resolveSegmentDeadlineSeconds() {
        String fromProperty = System.getProperty("terragis.ai.segment.timeout.seconds", "").trim();
        if (!fromProperty.isBlank()) {
            try {
                long value = Long.parseLong(fromProperty);
                if (value > 0) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Fall through to environment variable/default.
            }
        }

        String fromEnv = System.getenv("TERRAGIS_SEGMENT_TIMEOUT_SECONDS");
        if (fromEnv != null && !fromEnv.isBlank()) {
            try {
                long value = Long.parseLong(fromEnv.trim());
                if (value > 0) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Fall through to default.
            }
        }

        return 30;
    }
}
