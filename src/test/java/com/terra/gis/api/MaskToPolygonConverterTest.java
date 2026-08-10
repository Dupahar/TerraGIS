package com.terra.gis.api;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Polygon;

class MaskToPolygonConverterTest {

    @Test
    void convert_mapsTrueCellsToPolygonsWithTileOffset() {
        RasterTile tile = new RasterTile(100, 200, 2, 2, 0, 1);
        boolean[] mask = new boolean[] {
                true, false,
                false, true
        };
        AiMaskResult result = new AiMaskResult(tile, 2, 2, mask, "building", 0.9d);

        List<Polygon> polygons = MaskToPolygonConverter.convert(result);

        Assertions.assertEquals(2, polygons.size());
        Assertions.assertEquals(100.0d, polygons.get(0).getCoordinate().x, 0.0001d);
        Assertions.assertEquals(200.0d, polygons.get(0).getCoordinate().y, 0.0001d);
        Assertions.assertEquals(101.0d, polygons.get(1).getCoordinate().x, 0.0001d);
        Assertions.assertEquals(201.0d, polygons.get(1).getCoordinate().y, 0.0001d);
    }
}
