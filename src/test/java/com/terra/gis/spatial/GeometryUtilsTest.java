package com.terra.gis.spatial;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

class GeometryUtilsTest {

    private final GeometryUtils utils = new GeometryUtils();
    private final GeometryFactory factory = utils.getFactory();

    @Test
    void buffer_increasesAreaForPolygon() {
        Polygon polygon = square(0, 0, 10);

        Geometry buffered = utils.buffer(polygon, 1.0);

        assertNotNull(buffered);
        assertTrue(buffered.getArea() > polygon.getArea());
    }

    @Test
    void intersect_returnsNonEmptyForOverlappingPolygons() {
        Polygon a = square(0, 0, 10);
        Polygon b = square(5, 5, 10);

        Geometry intersection = utils.intersect(a, b);

        assertNotNull(intersection);
        assertFalse(intersection.isEmpty());
        assertTrue(intersection.getArea() > 0.0);
    }

    @Test
    void isValid_returnsFalseForSelfIntersectingPolygon() {
        Coordinate[] shell = new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(10, 10),
                new Coordinate(0, 10),
                new Coordinate(10, 0),
                new Coordinate(0, 0)
        };
        LinearRing ring = factory.createLinearRing(shell);
        Polygon invalid = factory.createPolygon(ring, null);

        assertFalse(utils.isValid(invalid));
    }

    @Test
    void buffer_throwsForNullGeometry() {
        assertThrows(IllegalArgumentException.class, () -> utils.buffer(null, 1.0));
    }

    @Test
    void intersect_throwsForNullGeometry() {
        Polygon a = square(0, 0, 10);
        assertThrows(IllegalArgumentException.class, () -> utils.intersect(a, null));
    }

    private Polygon square(double x, double y, double size) {
        Coordinate[] shell = new Coordinate[] {
                new Coordinate(x, y),
                new Coordinate(x + size, y),
                new Coordinate(x + size, y + size),
                new Coordinate(x, y + size),
                new Coordinate(x, y)
        };
        return factory.createPolygon(shell);
    }
}
