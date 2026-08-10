package com.terra.gis.spatial;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RasterImporterTest {

    private final RasterImporter importer = new RasterImporter();

    @Test
    void openRasterReader_rejectsNullAndMissingFiles() {
        assertThrows(IllegalArgumentException.class, () -> importer.openRasterReader(null));
        assertThrows(IOException.class, () -> importer.openRasterReader(Path.of("does-not-exist.tif").toFile()));
    }

    @Test
    void openRasterReader_rejectsPlaceholderGeoTiffs(@TempDir Path tempDir) throws Exception {
        Path placeholder = tempDir.resolve("placeholder.tif");
        Files.writeString(placeholder, "dummy geotiff placeholder", StandardCharsets.UTF_8);

        IOException exception = assertThrows(IOException.class, () -> importer.openRasterReader(placeholder.toFile()));
        assertTrue(exception.getMessage().contains("placeholder"));
    }

    @Test
    void openRasterReader_rejectsUnsupportedRasterFormats(@TempDir Path tempDir) throws Exception {
        Path raster = tempDir.resolve("sample.bin");
        Files.writeString(raster, "not a supported raster format", StandardCharsets.UTF_8);

        IOException exception = assertThrows(IOException.class, () -> importer.openRasterReader(raster.toFile()));
        assertTrue(exception.getMessage().contains("Unsupported raster format"));
    }
}