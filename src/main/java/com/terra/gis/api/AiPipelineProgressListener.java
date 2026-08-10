package com.terra.gis.api;

@FunctionalInterface
public interface AiPipelineProgressListener {

    void onProgress(String stage, int completed, int total, String message);
}
