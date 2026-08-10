package com.terra.gis.spatial;

import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.api.feature.type.GeometryDescriptor;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.data.collection.CollectionFeatureSource;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.union.UnaryUnionOp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lightweight geoprocessing service for Analysis toolbox v1 operations.
 */
public class AnalysisService {

    private final GeometryUtils geometryUtils = new GeometryUtils();
    private final CrsUtils crsUtils = new CrsUtils();

    public SimpleFeatureSource buffer(SimpleFeatureSource source, double distance) throws Exception {
        if (source == null) {
            throw new IllegalArgumentException("Source layer cannot be null");
        }
        if (!Double.isFinite(distance) || distance == 0.0d) {
            throw new IllegalArgumentException("Buffer distance must be a non-zero finite number");
        }

        SimpleFeatureType outputType = createOutputType(source.getSchema(), source.getSchema().getCoordinateReferenceSystem(), "buffer_result");
        List<SimpleFeature> outFeatures = new ArrayList<>();
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(outputType);

        try (var it = source.getFeatures().features()) {
            int id = 0;
            while (it.hasNext()) {
                SimpleFeature in = it.next();
                Geometry geometry = (Geometry) in.getDefaultGeometry();
                if (geometry == null) {
                    continue;
                }

                Geometry buffered = geometryUtils.buffer(geometry, distance);
                copyAttributesWithGeometry(in, outputType, buffered, builder);
                outFeatures.add(builder.buildFeature("buffer-" + (id++)));
                builder.reset();
            }
        }

        SimpleFeatureCollection collection = new ListFeatureCollection(outputType, outFeatures);
        return new CollectionFeatureSource(collection);
    }

    public SimpleFeatureSource intersect(SimpleFeatureSource first, SimpleFeatureSource second) throws Exception {
        if (first == null || second == null) {
            throw new IllegalArgumentException("Both input layers are required");
        }

        CoordinateReferenceSystem firstCrs = first.getSchema().getCoordinateReferenceSystem();
        CoordinateReferenceSystem secondCrs = second.getSchema().getCoordinateReferenceSystem();

        SimpleFeatureType outputType = createOutputType(first.getSchema(), firstCrs, "intersection_result");
        List<SimpleFeature> outFeatures = new ArrayList<>();
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(outputType);

        List<Geometry> rightGeometries = new ArrayList<>();
        try (var it = second.getFeatures().features()) {
            while (it.hasNext()) {
                SimpleFeature f = it.next();
                Geometry g = (Geometry) f.getDefaultGeometry();
                if (g == null) {
                    continue;
                }
                if (firstCrs != null && secondCrs != null && !firstCrs.equals(secondCrs)) {
                    g = crsUtils.reproject(g, secondCrs, firstCrs);
                }
                rightGeometries.add(g);
            }
        }

        try (var leftIt = first.getFeatures().features()) {
            int id = 0;
            while (leftIt.hasNext()) {
                SimpleFeature left = leftIt.next();
                Geometry leftGeometry = (Geometry) left.getDefaultGeometry();
                if (leftGeometry == null) {
                    continue;
                }

                for (Geometry rightGeometry : rightGeometries) {
                    if (!leftGeometry.getEnvelopeInternal().intersects(rightGeometry.getEnvelopeInternal())) {
                        continue;
                    }
                    Geometry intersection = geometryUtils.intersect(leftGeometry, rightGeometry);
                    if (intersection == null || intersection.isEmpty()) {
                        continue;
                    }
                    copyAttributesWithGeometry(left, outputType, intersection, builder);
                    outFeatures.add(builder.buildFeature("intersect-" + (id++)));
                    builder.reset();
                }
            }
        }

        SimpleFeatureCollection collection = new ListFeatureCollection(outputType, outFeatures);
        return new CollectionFeatureSource(collection);
    }

    public SimpleFeatureSource reproject(SimpleFeatureSource source, String targetEpsgCode) throws Exception {
        if (source == null) {
            throw new IllegalArgumentException("Source layer cannot be null");
        }
        if (targetEpsgCode == null || targetEpsgCode.isBlank()) {
            throw new IllegalArgumentException("Target CRS cannot be empty");
        }

        CoordinateReferenceSystem sourceCrs = source.getSchema().getCoordinateReferenceSystem();
        CoordinateReferenceSystem targetCrs = crsUtils.parseCRS(targetEpsgCode.trim());
        if (sourceCrs == null) {
            throw new IllegalArgumentException("Source layer CRS is missing; cannot reproject");
        }

        SimpleFeatureType outputType = createOutputType(source.getSchema(), targetCrs, "reproject_result");
        List<SimpleFeature> outFeatures = new ArrayList<>();
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(outputType);

        try (var it = source.getFeatures().features()) {
            int id = 0;
            while (it.hasNext()) {
                SimpleFeature in = it.next();
                Geometry geometry = (Geometry) in.getDefaultGeometry();
                if (geometry == null) {
                    continue;
                }

                Geometry transformed = crsUtils.reproject(geometry, sourceCrs, targetCrs);
                copyAttributesWithGeometry(in, outputType, transformed, builder);
                outFeatures.add(builder.buildFeature("reproject-" + (id++)));
                builder.reset();
            }
        }

        SimpleFeatureCollection collection = new ListFeatureCollection(outputType, outFeatures);
        return new CollectionFeatureSource(collection);
    }

    public SimpleFeatureSource clip(SimpleFeatureSource target, SimpleFeatureSource clipBoundary) throws Exception {
        if (target == null || clipBoundary == null) {
            throw new IllegalArgumentException("Target and clip layers are required");
        }

        CoordinateReferenceSystem targetCrs = target.getSchema().getCoordinateReferenceSystem();
        CoordinateReferenceSystem clipCrs = clipBoundary.getSchema().getCoordinateReferenceSystem();

        List<Geometry> clipGeometries = new ArrayList<>();
        try (var it = clipBoundary.getFeatures().features()) {
            while (it.hasNext()) {
                SimpleFeature feature = it.next();
                Geometry geometry = (Geometry) feature.getDefaultGeometry();
                if (geometry == null || geometry.isEmpty()) {
                    continue;
                }

                Geometry working = geometry;
                if (targetCrs != null && clipCrs != null && !targetCrs.equals(clipCrs)) {
                    working = crsUtils.reproject(working, clipCrs, targetCrs);
                }

                clipGeometries.add(working);
            }
        }

        SimpleFeatureType outputType = createOutputType(target.getSchema(), targetCrs, "clip_result");
        List<SimpleFeature> outFeatures = new ArrayList<>();
        Geometry clipMask = unaryUnion(clipGeometries);
        if (clipMask == null || clipMask.isEmpty()) {
            return new CollectionFeatureSource(new ListFeatureCollection(outputType, outFeatures));
        }

        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(outputType);
        try (var it = target.getFeatures().features()) {
            int id = 0;
            while (it.hasNext()) {
                SimpleFeature in = it.next();
                Geometry geometry = (Geometry) in.getDefaultGeometry();
                if (geometry == null || geometry.isEmpty()) {
                    continue;
                }

                Geometry clipped = geometryUtils.intersect(geometry, clipMask);
                if (clipped == null || clipped.isEmpty()) {
                    continue;
                }

                copyAttributesWithGeometry(in, outputType, clipped, builder);
                outFeatures.add(builder.buildFeature("clip-" + (id++)));
                builder.reset();
            }
        }

        return new CollectionFeatureSource(new ListFeatureCollection(outputType, outFeatures));
    }

    public SimpleFeatureSource dissolve(SimpleFeatureSource source, String dissolveField) throws Exception {
        if (source == null) {
            throw new IllegalArgumentException("Source layer cannot be null");
        }

        String normalizedField = normalizeDissolveField(source.getSchema(), dissolveField);
        CoordinateReferenceSystem crs = source.getSchema().getCoordinateReferenceSystem();
        SimpleFeatureType outputType = createDissolveOutputType(source.getSchema(), crs, normalizedField, "dissolve_result");
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(outputType);
        List<SimpleFeature> outFeatures = new ArrayList<>();

        GeometryDescriptor sourceGeom = source.getSchema().getGeometryDescriptor();
        if (sourceGeom == null) {
            throw new IllegalArgumentException("Source layer has no geometry descriptor");
        }

        String geomName = sourceGeom.getLocalName();
        if (normalizedField == null) {
            List<Geometry> dissolveGeometries = new ArrayList<>();
            try (var it = source.getFeatures().features()) {
                while (it.hasNext()) {
                    Geometry g = (Geometry) it.next().getDefaultGeometry();
                    if (g == null || g.isEmpty()) {
                        continue;
                    }
                    dissolveGeometries.add(g);
                }
            }

            Geometry unioned = unaryUnion(dissolveGeometries);

            if (unioned != null && !unioned.isEmpty()) {
                builder.set(geomName, unioned);
                outFeatures.add(builder.buildFeature("dissolve-0"));
            }
            return new CollectionFeatureSource(new ListFeatureCollection(outputType, outFeatures));
        }

        Map<Object, List<Geometry>> grouped = new LinkedHashMap<>();
        try (var it = source.getFeatures().features()) {
            while (it.hasNext()) {
                SimpleFeature feature = it.next();
                Object key = feature.getAttribute(normalizedField);
                Geometry g = (Geometry) feature.getDefaultGeometry();
                if (g == null || g.isEmpty()) {
                    continue;
                }

                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(g);
            }
        }

        int id = 0;
        for (Map.Entry<Object, List<Geometry>> entry : grouped.entrySet()) {
            Geometry g = unaryUnion(entry.getValue());
            if (g == null || g.isEmpty()) {
                continue;
            }

            builder.set(geomName, g);
            builder.set(normalizedField, entry.getKey());
            outFeatures.add(builder.buildFeature("dissolve-" + (id++)));
            builder.reset();
        }

        return new CollectionFeatureSource(new ListFeatureCollection(outputType, outFeatures));
    }

    private SimpleFeatureType createOutputType(
            SimpleFeatureType sourceType,
            CoordinateReferenceSystem targetCrs,
            String typeName) {
        SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
        tb.setName(typeName);

        if (targetCrs != null) {
            tb.setCRS(targetCrs);
        }

        sourceType.getAttributeDescriptors().forEach(descriptor -> {
            String localName = descriptor.getLocalName();
            Class<?> binding = descriptor.getType().getBinding();
            if (descriptor instanceof GeometryDescriptor) {
                tb.add(localName, Geometry.class);
                tb.setDefaultGeometry(localName);
            } else {
                tb.add(localName, binding);
            }
        });

        return tb.buildFeatureType();
    }

    private void copyAttributesWithGeometry(
            SimpleFeature source,
            SimpleFeatureType outputType,
            Geometry geometry,
            SimpleFeatureBuilder builder) {
        for (var descriptor : outputType.getAttributeDescriptors()) {
            String localName = descriptor.getLocalName();
            Object value = source.getAttribute(localName);
            if (value instanceof Geometry || Geometry.class.isAssignableFrom(descriptor.getType().getBinding())) {
                builder.add(geometry);
            } else {
                builder.add(value);
            }
        }
    }

    private Geometry unaryUnion(List<Geometry> geometries) {
        if (geometries == null || geometries.isEmpty()) {
            return null;
        }
        if (geometries.size() == 1) {
            return geometries.get(0);
        }
        return UnaryUnionOp.union(geometries);
    }

    private String normalizeDissolveField(SimpleFeatureType schema, String dissolveField) {
        if (dissolveField == null || dissolveField.isBlank()) {
            return null;
        }

        String field = dissolveField.trim();
        AttributeDescriptor descriptor = schema.getDescriptor(field);
        if (descriptor == null) {
            throw new IllegalArgumentException("Dissolve field does not exist: " + field);
        }

        Class<?> binding = descriptor.getType().getBinding();
        if (Geometry.class.isAssignableFrom(binding)) {
            throw new IllegalArgumentException("Dissolve field cannot be a geometry attribute: " + field);
        }

        return field;
    }

    private SimpleFeatureType createDissolveOutputType(
            SimpleFeatureType sourceType,
            CoordinateReferenceSystem targetCrs,
            String dissolveField,
            String typeName) {
        GeometryDescriptor sourceGeom = sourceType.getGeometryDescriptor();
        if (sourceGeom == null) {
            throw new IllegalArgumentException("Source layer has no geometry descriptor");
        }

        SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
        tb.setName(typeName);

        if (targetCrs != null) {
            tb.setCRS(targetCrs);
        }

        tb.add(sourceGeom.getLocalName(), sourceGeom.getType().getBinding());
        tb.setDefaultGeometry(sourceGeom.getLocalName());

        if (dissolveField != null) {
            AttributeDescriptor descriptor = sourceType.getDescriptor(dissolveField);
            tb.add(dissolveField, Objects.requireNonNull(descriptor).getType().getBinding());
        }

        return tb.buildFeatureType();
    }
}
