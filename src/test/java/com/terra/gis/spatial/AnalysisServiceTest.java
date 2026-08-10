package com.terra.gis.spatial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.data.collection.CollectionFeatureSource;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

class AnalysisServiceTest {

    private final AnalysisService service = new AnalysisService();

    @Test
    void buffer_createsBufferedFeatures() throws Exception {
        SimpleFeatureSource source = createPointSource();

        SimpleFeatureSource result = service.buffer(source, 50.0);

        assertEquals(1, result.getFeatures().size());
        SimpleFeature feature = result.getFeatures().features().next();
        assertNotNull(feature.getDefaultGeometry());
        assertFalse(((org.locationtech.jts.geom.Geometry) feature.getDefaultGeometry()).isEmpty());
    }

    @Test
    void intersect_createsIntersectionFeatures() throws Exception {
        SimpleFeatureSource first = createPolygonSource("left", new double[] {0, 0, 10, 10});
        SimpleFeatureSource second = createPolygonSource("right", new double[] {5, 5, 15, 15});

        SimpleFeatureSource result = service.intersect(first, second);

        assertEquals(1, result.getFeatures().size());
        assertNotNull(result.getFeatures().features().next().getDefaultGeometry());
    }

    @Test
    void reproject_transformsCoordinates() throws Exception {
        SimpleFeatureSource source = createPointSource();

        SimpleFeatureSource result = service.reproject(source, "EPSG:3857");

        Point point = (Point) result.getFeatures().features().next().getDefaultGeometry();
        assertNotNull(point);
        assertTrue(Math.abs(point.getX()) > 1.0);
        assertTrue(Math.abs(point.getY()) > 1.0);
    }

    @Test
    void clip_returnsIntersectedFeaturesWithinBoundary() throws Exception {
        SimpleFeatureSource target = createPolygonSource("parcel", new double[] {0, 0, 10, 10});
        SimpleFeatureSource boundary = createPolygonSource("boundary", new double[] {2, 2, 8, 8});

        SimpleFeatureSource result = service.clip(target, boundary);

        assertEquals(1, result.getFeatures().size());
        assertNotNull(result.getFeatures().features().next().getDefaultGeometry());
    }

    @Test
    void dissolve_groupsByAttributeAndUnionsGeometry() throws Exception {
        SimpleFeatureSource source = createGroupedPolygonSource();

        SimpleFeatureSource grouped = service.dissolve(source, "group");
        SimpleFeatureSource dissolvedAll = service.dissolve(source, null);

        assertEquals(1, grouped.getFeatures().size());
        assertEquals(1, dissolvedAll.getFeatures().size());
    }

    @Test
    void invalidArgumentsAreRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.buffer(null, 10.0));
        assertThrows(IllegalArgumentException.class, () -> service.buffer(createPointSource(), 0.0));
        assertThrows(IllegalArgumentException.class, () -> service.reproject(createPointSource(), " "));
        assertThrows(IllegalArgumentException.class, () -> service.clip(null, createPointSource()));
        assertThrows(IllegalArgumentException.class, () -> service.dissolve(createPointSource(), "missing"));
    }

    private SimpleFeatureSource createPointSource() throws Exception {
        GeometryFactory geometryFactory = new GeometryFactory();
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("points");
        builder.setCRS(CRS.decode("EPSG:4326", true));
        builder.add("the_geom", Point.class);
        builder.add("name", String.class);
        SimpleFeatureType type = builder.buildFeatureType();

        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(type);
        featureBuilder.add(geometryFactory.createPoint(new Coordinate(77.5946, 12.9716)));
        featureBuilder.add("parcel-a");
        SimpleFeature feature = featureBuilder.buildFeature("point-1");

        return new CollectionFeatureSource(new ListFeatureCollection(type, List.of(feature)));
    }

    private SimpleFeatureSource createPolygonSource(String typeName, double[] bounds) throws Exception {
        GeometryFactory geometryFactory = new GeometryFactory();
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName(typeName);
        builder.setCRS(CRS.decode("EPSG:4326", true));
        builder.add("the_geom", Polygon.class);
        builder.add("name", String.class);
        SimpleFeatureType type = builder.buildFeatureType();

        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(type);
        featureBuilder.add(geometryFactory.createPolygon(new Coordinate[] {
                new Coordinate(bounds[0], bounds[1]),
                new Coordinate(bounds[2], bounds[1]),
                new Coordinate(bounds[2], bounds[3]),
                new Coordinate(bounds[0], bounds[3]),
                new Coordinate(bounds[0], bounds[1])
        }));
        featureBuilder.add(typeName);
        SimpleFeature feature = featureBuilder.buildFeature(typeName + "-1");

        return new CollectionFeatureSource(new ListFeatureCollection(type, List.of(feature)));
    }

    private SimpleFeatureSource createGroupedPolygonSource() throws Exception {
        GeometryFactory geometryFactory = new GeometryFactory();
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("grouped");
        builder.setCRS(CRS.decode("EPSG:4326", true));
        builder.add("the_geom", Polygon.class);
        builder.add("group", String.class);
        SimpleFeatureType type = builder.buildFeatureType();

        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(type);
        featureBuilder.add(geometryFactory.createPolygon(new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(2, 0),
                new Coordinate(2, 2),
                new Coordinate(0, 2),
                new Coordinate(0, 0)
        }));
        featureBuilder.add("A");
        SimpleFeature first = featureBuilder.buildFeature("group-1");
        featureBuilder.reset();

        featureBuilder.add(geometryFactory.createPolygon(new Coordinate[] {
                new Coordinate(1, 1),
                new Coordinate(3, 1),
                new Coordinate(3, 3),
                new Coordinate(1, 3),
                new Coordinate(1, 1)
        }));
        featureBuilder.add("A");
        SimpleFeature second = featureBuilder.buildFeature("group-2");

        return new CollectionFeatureSource(new ListFeatureCollection(type, List.of(first, second)));
    }
}