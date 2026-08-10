package com.terra.gis.api;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AiRoundTripServicePipelineTest {

    @Test
    void runMockSegmentationPipeline_reportsProgressAndBuildsPolygons() {
        try (TerraApiClient client = new TerraApiClient("localhost", 65530)) {
            AiRoundTripService service = new AiRoundTripService(client);
            com.terra.gis.api.CancellationToken token = new com.terra.gis.api.CancellationToken();
            List<String> stages = new ArrayList<>();

            AiRoundTripService.PipelineResult result = service.runMockSegmentationPipeline(
                    600,
                    600,
                    256,
                    token,
                    (stage, completed, total, message) -> stages.add(stage + ":" + completed + "/" + total));

            Assertions.assertTrue(result.success());
            Assertions.assertFalse(result.cancelled());
            Assertions.assertEquals(result.totalTiles(), result.processedTiles());
            Assertions.assertTrue(result.polygonCount() > 0);
            Assertions.assertTrue(stages.get(stages.size() - 1).startsWith("completed:"));
        }
    }

    @Test
    void runMockSegmentationPipeline_respectsCancellation() {
        try (TerraApiClient client = new TerraApiClient("localhost", 65530)) {
            AiRoundTripService service = new AiRoundTripService(client);
            com.terra.gis.api.CancellationToken token = new com.terra.gis.api.CancellationToken();

            AiRoundTripService.PipelineResult result = service.runMockSegmentationPipeline(
                    1024,
                    1024,
                    256,
                    token,
                    (stage, completed, total, message) -> {
                        if (completed >= 1) {
                            token.cancel();
                        }
                    });

            Assertions.assertTrue(result.cancelled());
            Assertions.assertFalse(result.success());
            Assertions.assertTrue(result.processedTiles() >= 1);
            Assertions.assertTrue(result.processedTiles() < result.totalTiles());
        }
    }

    @Test
    void runSegmentationPipeline_fallsBackWhenBackendUnavailable() {
        try (TerraApiClient client = new TerraApiClient("localhost", 65530)) {
            AiRoundTripService service = new AiRoundTripService(client);

            AiRoundTripService.PipelineResult result = service.runSegmentationPipeline(
                    512,
                    512,
                    256,
                    "default-segmentation",
                    true,
                    null,
                    new com.terra.gis.api.CancellationToken(),
                    null);

            Assertions.assertTrue(result.success());
            Assertions.assertFalse(result.cancelled());
            Assertions.assertTrue(result.polygonCount() > 0);
            Assertions.assertFalse(result.polygons().isEmpty());
        }
    }
}
