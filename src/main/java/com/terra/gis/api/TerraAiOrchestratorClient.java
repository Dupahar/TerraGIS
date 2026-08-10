package com.terra.gis.api;

import com.terra.gis.terraai.proto.CancelResponse;
import com.terra.gis.terraai.proto.JobId;
import com.terra.gis.terraai.proto.JobStatusResponse;
import com.terra.gis.terraai.proto.JobSubmissionResponse;
import com.terra.gis.terraai.proto.PredictionRequest;
import com.terra.gis.terraai.proto.TerraAiOrchestratorGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * gRPC client for TerraAI orchestrator APIs.
 */
public final class TerraAiOrchestratorClient implements AutoCloseable {

    private final ManagedChannel channel;
    private final TerraAiOrchestratorGrpc.TerraAiOrchestratorBlockingStub blockingStub;

    public TerraAiOrchestratorClient(String host, int port) {
        String resolvedHost = (host == null || host.isBlank()) ? "localhost" : host.trim();
        int resolvedPort = port > 0 ? port : 50051;
        this.channel = ManagedChannelBuilder.forAddress(resolvedHost, resolvedPort)
                .usePlaintext()
                .build();
        this.blockingStub = TerraAiOrchestratorGrpc.newBlockingStub(channel);
    }

    public JobSubmission submitJob(
            String requestId,
            String aoiWkt,
            String inputRasterUri,
            String modelProfile,
            int targetResolutionM,
            Map<String, String> metadata) {
        PredictionRequest request = PredictionRequest.newBuilder()
                .setRequestId(requestId == null ? "" : requestId)
                .setAoiWkt(aoiWkt == null ? "" : aoiWkt)
                .setInputRasterUri(inputRasterUri == null ? "" : inputRasterUri)
                .setModelProfile(modelProfile == null ? "" : modelProfile)
                .setTargetResolutionM(targetResolutionM)
                .putAllMetadata(metadata == null ? Collections.emptyMap() : metadata)
                .build();

        JobSubmissionResponse response = blockingStub.submitJob(request);
        return new JobSubmission(response.getJobId(), response.getAcceptedAtUtc(), response.getStatus());
    }

    public JobStatus getJobStatus(String jobId) {
        JobStatusResponse response = blockingStub.getJobStatus(JobId.newBuilder()
                .setJobId(jobId == null ? "" : jobId)
                .build());

        List<Artifact> artifacts = response.getArtifactsList().stream()
                .map(a -> new Artifact(a.getName(), a.getUri(), a.getMediaType()))
                .toList();

        ErrorInfo error = null;
        if (response.hasError()) {
            var e = response.getError();
            error = new ErrorInfo(e.getCode(), e.getTitle(), e.getDetail(), e.getRetryable());
        }

        return new JobStatus(
                response.getJobId(),
                response.getStatus(),
                response.getPhase(),
                response.getPhaseIndex(),
                response.getPhaseTotal(),
                response.getProgressPercent(),
                response.getMessage(),
                response.getUpdatedAtUtc(),
                artifacts,
                error);
    }

    public CancelResult cancelJob(String jobId) {
        CancelResponse response = blockingStub.cancelJob(JobId.newBuilder()
                .setJobId(jobId == null ? "" : jobId)
                .build());
        return new CancelResult(response.getJobId(), response.getCancelled(), response.getMessage());
    }

    @Override
    public void close() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(2, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }

    public record JobSubmission(String jobId, String acceptedAtUtc, String status) {
    }

    public record Artifact(String name, String uri, String mediaType) {
    }

    public record ErrorInfo(String code, String title, String detail, boolean retryable) {
    }

    public record JobStatus(
            String jobId,
            String status,
            String phase,
            int phaseIndex,
            int phaseTotal,
            int progressPercent,
            String message,
            String updatedAtUtc,
            List<Artifact> artifacts,
            ErrorInfo error) {
    }

    public record CancelResult(String jobId, boolean cancelled, String message) {
    }
}
