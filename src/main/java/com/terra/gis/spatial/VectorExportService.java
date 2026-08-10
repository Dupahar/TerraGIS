package com.terra.gis.spatial;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.geotools.api.data.DataStore;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.data.SimpleFeatureStore;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.data.Transaction;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.geojson.feature.FeatureJSON;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Skeleton export service for upcoming vector export implementation.
 */
public class VectorExportService {

    public enum ExportFormat {
        SHAPEFILE,
        GEOPACKAGE,
        GEOJSON
    }

    public ExportFormat detectFormat(File outputFile) {
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null");
        }
        String name = outputFile.getName().toLowerCase();
        if (name.endsWith(".shp")) {
            return ExportFormat.SHAPEFILE;
        }
        if (name.endsWith(".gpkg")) {
            return ExportFormat.GEOPACKAGE;
        }
        if (name.endsWith(".geojson") || name.endsWith(".json")) {
            return ExportFormat.GEOJSON;
        }
        throw new IllegalArgumentException("Unsupported export extension: " + outputFile.getName());
    }

    public void validateExportTarget(File outputFile) throws IOException {
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null");
        }
        File parent = outputFile.getAbsoluteFile().getParentFile();
        if (parent == null || !parent.exists()) {
            throw new IOException("Output directory does not exist: " + outputFile.getAbsolutePath());
        }
        if (parent.isFile()) {
            throw new IOException("Output parent is not a directory: " + parent.getAbsolutePath());
        }

        detectFormat(outputFile);
    }

    public void export(SimpleFeatureSource source, File outputFile) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("Feature source cannot be null");
        }

        validateExportTarget(outputFile);
        ExportFormat format = detectFormat(outputFile);
        switch (format) {
            case SHAPEFILE -> exportShapefile(source, outputFile);
            case GEOPACKAGE -> exportGeopackage(source, outputFile);
            case GEOJSON -> exportGeoJson(source, outputFile);
            default -> throw new IllegalArgumentException("Unsupported export format: " + format);
        }
    }

    private void exportShapefile(SimpleFeatureSource source, File outputFile) throws IOException {
        ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();
        Map<String, Serializable> params = new HashMap<>();
        params.put("url", outputFile.toURI().toURL());
        params.put("create spatial index", Boolean.TRUE);

        DataStore created = factory.createNewDataStore(params);
        if (!(created instanceof ShapefileDataStore store)) {
            created.dispose();
            throw new IOException("Expected ShapefileDataStore but got: " + created.getClass().getName());
        }
        try {
            SimpleFeatureType schema = source.getSchema();
            store.createSchema(schema);
            if (schema.getCoordinateReferenceSystem() != null) {
                store.forceSchemaCRS(schema.getCoordinateReferenceSystem());
            }
            writeFeatures(store, source);
        } finally {
            store.dispose();
        }
    }

    private void exportGeopackage(SimpleFeatureSource source, File outputFile) throws IOException {
        if (outputFile.exists() && !outputFile.delete()) {
            throw new IOException("Could not replace existing output file: " + outputFile.getAbsolutePath());
        }

        try (GeoPackage geoPackage = new GeoPackage(outputFile)) {
            geoPackage.init();

            FeatureEntry entry = new FeatureEntry();
            String tableName = sanitizeTableName(source.getSchema().getTypeName(), outputFile.getName());
            entry.setTableName(tableName);
            entry.setIdentifier(tableName);
            entry.setDescription("TerraGIS export");

            geoPackage.add(entry, toSimpleFeatureCollection(source));
        }
    }

    private void exportGeoJson(SimpleFeatureSource source, File outputFile) throws IOException {
        FeatureJSON featureJSON = new FeatureJSON();
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            featureJSON.writeFeatureCollection(source.getFeatures(), writer);
        }
    }

    private void writeFeatures(DataStore store, SimpleFeatureSource source) throws IOException {
        String typeName = store.getTypeNames()[0];
        if (!(store.getFeatureSource(typeName) instanceof SimpleFeatureStore featureStore)) {
            throw new IOException("Export target is not writable: " + typeName);
        }

        Transaction tx = new DefaultTransaction("export");
        featureStore.setTransaction(tx);
        try {
            featureStore.addFeatures(source.getFeatures());
            tx.commit();
        } catch (Exception ex) {
            tx.rollback();
            throw new IOException("Failed writing features: " + ex.getMessage(), ex);
        } finally {
            tx.close();
        }
    }

    private SimpleFeatureCollection toSimpleFeatureCollection(SimpleFeatureSource source) throws IOException {
        SimpleFeatureType schema = source.getSchema();
        java.util.List<SimpleFeature> features = new ArrayList<>();
        try (var iterator = source.getFeatures().features()) {
            while (iterator.hasNext()) {
                features.add(iterator.next());
            }
        }
        return new ListFeatureCollection(schema, features);
    }

    private String sanitizeTableName(String preferred, String fallbackFileName) {
        String base = preferred;
        if (base == null || base.isBlank()) {
            int dot = fallbackFileName.lastIndexOf('.');
            base = dot > 0 ? fallbackFileName.substring(0, dot) : fallbackFileName;
        }

        String sanitized = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        if (sanitized.isBlank()) {
            return "terragis_export";
        }
        return sanitized;
    }
}
