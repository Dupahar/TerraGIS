package com.terra.gis.spatial;

import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.FeatureSource;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.data.collection.CollectionFeatureSource;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles the ingestion and parsing of vector geographic datasets.
 * <p>
 * Supports standard OGC vector formats using GeoTools DataStore API:
 * <ul>
 *   <li><strong>ESRI Shapefile (.shp):</strong> Industry-standard format with .shp/.shx/.dbf/.prj files</li>
 *   <li><strong>GeoPackage (.gpkg):</strong> SQLite-based container for multiple layers</li>
 *   <li><strong>GeoJSON (.json, .geojson):</strong> Web-friendly JSON encoding of features</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * VectorImporter importer = new VectorImporter();
 * File shapeFile = new File("data/roads.shp");
 * SimpleFeatureSource source = importer.readVectorFile(shapeFile);
 * System.out.println("Loaded " + source.getFeatures().size() + " features");
 * }</pre>
 * 
 * <p><strong>Error Handling:</strong></p>
 * <ul>
 *   <li>Throws IOException if file format is unsupported or corrupted</li>
 *   <li>Throws IllegalArgumentException for null/invalid parameters</li>
 *   <li>Automatically disposes DataStore resources on error</li>
 * </ul>
 * 
 * @see org.geotools.api.data.DataStoreFinder
 * @see org.geotools.api.data.SimpleFeatureSource
 */
public class VectorImporter {

    private static final Logger log = LoggerFactory.getLogger(VectorImporter.class);

    /**
     * Reads a vector file from disk and returns the primary feature source.
     * 
     * @param file The file to read (e.g., .shp, .gpkg, .json)
     * @return SimpleFeatureSource representing the vector layer
     * @throws IOException if file cannot be read or format is unsupported
     * @throws IllegalArgumentException if file is null
     */
    public SimpleFeatureSource readVectorFile(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        if (!file.exists()) {
            throw new IOException("File does not exist: " + file.getAbsolutePath());
        }
        
        log.info("Reading vector file: {}", file.getAbsolutePath());
        
        Map<String, Object> params = new HashMap<>();
        params.put("url", file.toURI().toURL());

        DataStore dataStore = DataStoreFinder.getDataStore(params);
        if (dataStore == null) {
            if (isGeoJson(file)) {
                return readGeoJsonFile(file);
            }
            if (isGeoPackage(file)) {
                return readGeoPackageFile(file);
            }
            log.error("Could not find suitable DataStore for file: {}", file.getName());
            throw new IOException("Could not find suitable DataStore for file: " + file.getName());
        }

        try {
            // Connect to the first type name (layer) available
            String[] typeNames = dataStore.getTypeNames();
            if (typeNames.length == 0) {
                log.error("DataStore contains no layers for file: {}", file.getName());
                throw new IOException("DataStore contains no layers.");
            }

            String typeName = typeNames[0];
            log.debug("Loading layer: {}", typeName);
            
            FeatureSource<?, ?> genericSource = dataStore.getFeatureSource(typeName);

            if (genericSource instanceof SimpleFeatureSource) {
                SimpleFeatureSource simpleSource = (SimpleFeatureSource) genericSource;
                log.info("Successfully loaded vector file with {} features", 
                    simpleSource.getFeatures().size());
                return detachSourceAndDispose(dataStore, simpleSource, file.getName());
            } else {
                log.error("Only SimpleFeatureSource is supported, got: {}", 
                    genericSource.getClass().getName());
                throw new IOException("Only SimpleFeatureSource is supported.");
            }
        } catch (Exception e) {
            // Clean up DataStore on error
            dataStore.dispose();
            throw e;
        }
    }

    /**
     * Prints the schema of the vector layer.
     * 
     * @param featureSource The feature source to inspect
     * @throws IllegalArgumentException if featureSource is null
     */
    public void printSchema(SimpleFeatureSource featureSource) {
        if (featureSource == null) {
            throw new IllegalArgumentException("FeatureSource cannot be null");
        }
        
        SimpleFeatureType schema = featureSource.getSchema();
        log.info("Layer Name: {}", schema.getTypeName());
        log.info("CRS: {}", schema.getCoordinateReferenceSystem());
        log.info("Attributes: {}", schema.getAttributeCount());
    }

    private boolean isGeoJson(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".geojson") || name.endsWith(".json");
    }

    private boolean isGeoPackage(File file) {
        return file.getName().toLowerCase().endsWith(".gpkg");
    }

    private SimpleFeatureSource readGeoJsonFile(File file) throws IOException {
        FeatureJSON featureJSON = new FeatureJSON();
        try (var input = new java.io.FileInputStream(file)) {
            FeatureCollection<?, ?> featureCollection = featureJSON.readFeatureCollection(input);
            if (!(featureCollection instanceof SimpleFeatureCollection simpleCollection)) {
                throw new IOException("GeoJSON did not contain a compatible simple feature collection");
            }
            SimpleFeatureCollection normalizedCollection = ensureGeoJsonCrs(simpleCollection, file.getName());
            log.info("Loaded GeoJSON feature collection from {} with {} features", file.getName(), normalizedCollection.size());
            return new CollectionFeatureSource(normalizedCollection);
        }
    }

    private SimpleFeatureCollection ensureGeoJsonCrs(SimpleFeatureCollection source, String sourceName) throws IOException {
        if (source == null || source.getSchema() == null) {
            throw new IOException("GeoJSON source schema is missing for " + sourceName);
        }

        CoordinateReferenceSystem crs = source.getSchema().getCoordinateReferenceSystem();
        if (crs != null) {
            return source;
        }

        // RFC 7946 GeoJSON defaults to WGS84 lon/lat when CRS is omitted.
        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.init(source.getSchema());
        typeBuilder.setCRS(DefaultGeographicCRS.WGS84);
        SimpleFeatureType retypedSchema = typeBuilder.buildFeatureType();

        List<org.geotools.api.feature.simple.SimpleFeature> features = new ArrayList<>();
        try (var iterator = source.features()) {
            while (iterator.hasNext()) {
                org.geotools.api.feature.simple.SimpleFeature feature = iterator.next();
                SimpleFeatureBuilder builder = new SimpleFeatureBuilder(retypedSchema);
                for (int attributeIndex = 0; attributeIndex < feature.getAttributeCount(); attributeIndex++) {
                    builder.add(feature.getAttribute(attributeIndex));
                }
                features.add(builder.buildFeature(feature.getID()));
            }
        }

        log.info("GeoJSON {} had no CRS metadata; defaulted to EPSG:4326 (WGS84)", sourceName);
        return new ListFeatureCollection(retypedSchema, features);
    }

    private SimpleFeatureSource readGeoPackageFile(File file) throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("dbtype", "geopkg");
        params.put("database", file.getAbsolutePath());

        DataStore dataStore = DataStoreFinder.getDataStore(params);
        if (dataStore == null) {
            throw new IOException("Could not open GeoPackage datastore: " + file.getAbsolutePath());
        }

        String[] typeNames = dataStore.getTypeNames();
        if (typeNames.length == 0) {
            throw new IOException("GeoPackage contains no layers: " + file.getAbsolutePath());
        }

        FeatureSource<?, ?> genericSource = dataStore.getFeatureSource(typeNames[0]);
        if (genericSource instanceof SimpleFeatureSource simpleSource) {
            log.info("Loaded GeoPackage layer {} from {} with {} features", typeNames[0], file.getName(), simpleSource.getFeatures().size());
            return detachSourceAndDispose(dataStore, simpleSource, file.getName());
        }

        throw new IOException("GeoPackage layer is not a SimpleFeatureSource: " + typeNames[0]);
    }

    private SimpleFeatureSource detachSourceAndDispose(DataStore dataStore, SimpleFeatureSource source, String sourceName)
            throws IOException {
        try {
            SimpleFeatureType schema = source.getSchema();
            List<org.geotools.api.feature.simple.SimpleFeature> features = new ArrayList<>();
            try (var iterator = source.getFeatures().features()) {
                while (iterator.hasNext()) {
                    features.add(SimpleFeatureBuilder.copy(iterator.next()));
                }
            }

            SimpleFeatureCollection detached = new ListFeatureCollection(schema, features);
            log.debug("Detached {} features from datastore-backed source {}", features.size(), sourceName);
            return new CollectionFeatureSource(detached);
        } finally {
            dataStore.dispose();
        }
    }
}
