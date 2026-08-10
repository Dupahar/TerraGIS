package com.terra.gis.api;

import java.util.ArrayList;
import java.util.List;

public final class RasterTileChunker {

    private RasterTileChunker() {
    }

    public static List<RasterTile> chunk(int rasterWidth, int rasterHeight, int tileSize) {
        if (rasterWidth <= 0 || rasterHeight <= 0) {
            throw new IllegalArgumentException("rasterWidth and rasterHeight must be positive");
        }
        if (tileSize <= 0) {
            throw new IllegalArgumentException("tileSize must be positive");
        }

        int cols = (int) Math.ceil(rasterWidth / (double) tileSize);
        int rows = (int) Math.ceil(rasterHeight / (double) tileSize);
        int total = cols * rows;

        List<RasterTile> tiles = new ArrayList<>(total);
        int index = 0;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = col * tileSize;
                int y = row * tileSize;
                int width = Math.min(tileSize, rasterWidth - x);
                int height = Math.min(tileSize, rasterHeight - y);
                tiles.add(new RasterTile(x, y, width, height, index++, total));
            }
        }
        return tiles;
    }
}
