package com.terra.gis.spatial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.data.collection.CollectionFeatureSource;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VectorExportServiceTest {

    private final VectorExportService service = new VectorExportService();

    @Test
    void detectFormat_identifiesKnownExtensions() {
        assertEquals(VectorExportService.ExportFormat.SHAPEFILE, service.detectFormat(new File("out.shp")));
        assertEquals(VectorExportService.ExportFormat.GEOPACKAGE, service.detectFormat(new File("out.gpkg")));
        assertEquals(VectorExportService.ExportFormat.GEOJSON, service.detectFormat(new File("out.geojson")));
    }

    @Test
    void detectFormat_throwsForUnsupportedExtension() {
        assertThrows(IllegalArgumentException.class, () -> service.detectFormat(new File("out.txt")));
    }

    @Test
    void validateExportTarget_throwsWhenDirectoryMissing() {
        File bad = new File("target/does-not-exist-folder/out.geojson");
        assertThrows(IOException.class, () -> service.validateExportTarget(bad));
    }

    @Test
    void validateExportTarget_acceptsExistingDirectory() throws Exception {
        File good = new File("target/out.geojson");
        service.validateExportTarget(good);
    }

    @Test
    void export_roundTripGeoJson_preservesGeometryAndAttributes(@TempDir File tempDir) throws Exception {
        roundTripAndAssert(new File(tempDir, "export.geojson"), false);
    }

    @Test
    void export_roundTripShapefile_preservesGeometryAttributesAndCrs(@TempDir File tempDir) throws Exception {
        roundTripAndAssert(new File(tempDir, "export.shp"), true);
        assertTrue(new File(tempDir, "export.dbf").exists());
        assertTrue(new File(tempDir, "export.shx").exists());
    }

    @Test
    void export_roundTripGeoPackage_preservesGeometryAttributesAndCrs(@TempDir File tempDir) throws Exception {
        roundTripAndAssert(new File(tempDir, "export.gpkg"), true);
    }

    private void roundTripAndAssert(File outputFile, boolean assertCrs) throws Exception {
        var source = createSampleFeatureSource();
        service.export(source, outputFile);

        assertTrue(outputFile.exists());
        assertTrue(outputFile.length() > 0);

        VectorImporter importer = new VectorImporter();
        var imported = importer.readVectorFile(outputFile);
        assertNotNull(imported);
        assertNotNull(imported.getSchema());
        assertTrue(imported.getSchema().getTypeName().length() > 0);
        assertEquals(2, imported.getFeatures().size());

        Map<Integer, SimpleFeature> byId = new HashMap<>();
        try (var features = imported.getFeatures().features()) {
            while (features.hasNext()) {
                SimpleFeature feature = features.next();
                Number idValue = (Number) feature.getAttribute("id");
                byId.put(idValue.intValue(), feature);
            }
        }

        assertEquals(2, byId.size());
        assertTrue(byId.containsKey(1));
        assertTrue(byId.containsKey(2));
        assertEquals("parcel-a", byId.get(1).getAttribute("name"));
        assertEquals("survey-start", byId.get(1).getAttribute("notes"));
        assertEquals("parcel-b", byId.get(2).getAttribute("name"));
        assertEquals("survey-end", byId.get(2).getAttribute("notes"));

        Point p1 = (Point) byId.get(1).getDefaultGeometry();
        Point p2 = (Point) byId.get(2).getDefaultGeometry();
        assertNotNull(p1);
        assertNotNull(p2);
        assertEquals(77.5946, p1.getX(), 1e-9);
        assertEquals(12.9716, p1.getY(), 1e-9);
        assertEquals(77.6000, p2.getX(), 1e-9);
        assertEquals(12.9750, p2.getY(), 1e-9);
        assertFalse(p1.isEmpty());
        assertFalse(p2.isEmpty());

        if (assertCrs) {
            CoordinateReferenceSystem importedCrs = imported.getSchema().getCoordinateReferenceSystem();
            assertNotNull(importedCrs);
            assertTrue(CRS.equalsIgnoreMetadata(source.getSchema().getCoordinateReferenceSystem(), importedCrs));
        }
    }

    private CollectionFeatureSource createSampleFeatureSource() throws Exception {
        GeometryFactory gf = new GeometryFactory();

        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName("survey_points");
        typeBuilder.setCRS(CRS.decode("EPSG:4326", true));
        typeBuilder.add("the_geom", Point.class);
        typeBuilder.add("id", Integer.class);
        typeBuilder.add("name", String.class);
        typeBuilder.add("notes", String.class);
        SimpleFeatureType type = typeBuilder.buildFeatureType();

        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(type);
        builder.add(gf.createPoint(new Coordinate(77.5946, 12.9716)));
        builder.add(1);
        builder.add("parcel-a");
        builder.add("survey-start");
        SimpleFeature f1 = builder.buildFeature("survey.1");
        builder.reset();

        builder.add(gf.createPoint(new Coordinate(77.6000, 12.9750)));
        builder.add(2);
        builder.add("parcel-b");
        builder.add("survey-end");
        SimpleFeature f2 = builder.buildFeature("survey.2");

        return new CollectionFeatureSource(new ListFeatureCollection(type, java.util.List.of(f1, f2)));
    }
}
