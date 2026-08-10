package com.terra.gis.spatial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

class CrsUtilsTest {

    private final CrsUtils utils = new CrsUtils();

    @Test
    void parseCrs_returnsCrsForValidEpsgCode() throws Exception {
        CoordinateReferenceSystem crs = utils.parseCRS("EPSG:4326");

        assertNotNull(crs);
        assertTrue(crs.getName().toString().toUpperCase().contains("WGS"));
    }

    @Test
    void parseCrs_throwsForNullAndEmpty() {
        assertThrows(IllegalArgumentException.class, () -> utils.parseCRS(null));
        assertThrows(IllegalArgumentException.class, () -> utils.parseCRS("  "));
    }

    @Test
    void reproject_transformsPointBetweenCommonCrs() throws Exception {
        CoordinateReferenceSystem source = utils.parseCRS("EPSG:4326");
        CoordinateReferenceSystem target = utils.parseCRS("EPSG:3857");

        GeometryFactory factory = new GeometryFactory();
        Point input = factory.createPoint(new Coordinate(77.5946, 12.9716));

        Geometry output = utils.reproject(input, source, target);

        assertNotNull(output);
        assertEquals("Point", output.getGeometryType());
        assertTrue(Math.abs(output.getCoordinate().x) > 1000.0);
        assertTrue(Math.abs(output.getCoordinate().y) > 1000.0);
    }

    @Test
    void reproject_throwsForNullParameters() throws Exception {
        CoordinateReferenceSystem crs = utils.parseCRS("EPSG:4326");
        Point point = new GeometryFactory().createPoint(new Coordinate(0, 0));

        assertThrows(IllegalArgumentException.class, () -> utils.reproject(null, crs, crs));
        assertThrows(IllegalArgumentException.class, () -> utils.reproject(point, null, crs));
        assertThrows(IllegalArgumentException.class, () -> utils.reproject(point, crs, null));
    }
}
