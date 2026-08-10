package com.terra.gis.api;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RasterTileChunkerTest {

    @Test
    void chunk_createsExpectedTileCountAndEdgeDimensions() {
        List<RasterTile> tiles = RasterTileChunker.chunk(1025, 700, 512);

        Assertions.assertEquals(6, tiles.size());

        RasterTile lastTile = tiles.get(tiles.size() - 1);
        Assertions.assertEquals(1024, lastTile.x());
        Assertions.assertEquals(512, lastTile.y());
        Assertions.assertEquals(1, lastTile.width());
        Assertions.assertEquals(188, lastTile.height());
        Assertions.assertEquals(6, lastTile.total());
    }

    @Test
    void chunk_throwsForInvalidInput() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> RasterTileChunker.chunk(0, 10, 5));
        Assertions.assertThrows(IllegalArgumentException.class, () -> RasterTileChunker.chunk(10, 0, 5));
        Assertions.assertThrows(IllegalArgumentException.class, () -> RasterTileChunker.chunk(10, 10, 0));
    }
}
