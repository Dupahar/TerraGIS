package com.terra.gis.api;

public record AiMaskResult(
        RasterTile tile,
        int maskWidth,
        int maskHeight,
        boolean[] mask,
        String classLabel,
        double confidence) {

    public AiMaskResult {
        if (tile == null) {
            throw new IllegalArgumentException("tile cannot be null");
        }
        if (maskWidth <= 0 || maskHeight <= 0) {
            throw new IllegalArgumentException("maskWidth and maskHeight must be positive");
        }
        if (mask == null) {
            throw new IllegalArgumentException("mask cannot be null");
        }
        if (mask.length != maskWidth * maskHeight) {
            throw new IllegalArgumentException("mask length does not match mask dimensions");
        }
    }
}
