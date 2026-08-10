package com.terra.gis.spatial;

import org.geotools.coverage.grid.io.AbstractGridCoverage2DReader;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Handles the ingestion of raster geographic datasets like GeoTIFFs and massive
 * orthophotos.
 */
public class RasterImporter {

    private static final Logger log = LoggerFactory.getLogger(RasterImporter.class);

    /**
     * Opens a raster reader from disk for deferred/lazy rendering.
     *
     * @param file The raster file to read
     * @return Reader instance for map rendering
     * @throws IOException if file cannot be read or format is unsupported
     * @throws IllegalArgumentException if file is null
     */
    public AbstractGridCoverage2DReader openRasterReader(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        if (!file.exists()) {
            throw new IOException("File does not exist: " + file.getAbsolutePath());
        }

        validateRasterFileSignature(file);
        
        log.info("Reading raster file: {}", file.getAbsolutePath());

        AbstractGridFormat format = GridFormatFinder.findFormat(file);
        if (format == null || "Unknown Format".equalsIgnoreCase(format.getName())) {
            if (isLikelyGeoTiff(file)) {
                try {
                    log.debug("GridFormatFinder returned unknown format; trying explicit GeoTiffReader fallback");
                    return new GeoTiffReader(file);
                } catch (Exception ex) {
                    throw new IOException("Could not read GeoTIFF file: " + file.getAbsolutePath(), ex);
                }
            }

            log.error("Unsupported raster format for file: {}", file.getName());
            throw new IOException("Unsupported raster format for file: " + file.getName());
        }

        log.debug("Using format: {}", format.getName());
        
        AbstractGridCoverage2DReader reader;
        try {
            reader = format.getReader(file);
        } catch (UnsupportedOperationException ex) {
            if (isLikelyGeoTiff(file)) {
                try {
                    log.debug("Format reader failed; trying explicit GeoTiffReader fallback");
                    reader = new GeoTiffReader(file);
                } catch (Exception geoTiffEx) {
                    throw new IOException("Could not read GeoTIFF file: " + file.getAbsolutePath(), geoTiffEx);
                }
            } else {
                throw new IOException("Could not obtain reader for raster file: " + file.getName(), ex);
            }
        }
        if (reader == null) {
            log.error("Could not obtain reader for raster file: {}", file.getName());
            throw new IOException("Could not obtain reader for raster file: " + file.getName());
        }
        log.info("Successfully opened raster reader: {}", file.getName());
        return reader;
    }

    private boolean isLikelyGeoTiff(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".tif") || name.endsWith(".tiff");
    }

    private void validateRasterFileSignature(File file) throws IOException {
        long length = Files.size(file.toPath());
        if (length <= 0) {
            throw new IOException("Raster file is empty: " + file.getAbsolutePath());
        }

        // Read only a small header window; never load entire raster into memory.
        int sampleSize = (int) Math.min(4096L, length);
        byte[] bytes = new byte[sampleSize];
        int read;
        try (InputStream in = Files.newInputStream(file.toPath())) {
            read = in.read(bytes);
        }
        if (read <= 0) {
            throw new IOException("Could not read raster file header: " + file.getAbsolutePath());
        }

        String asText = new String(bytes, 0, read, StandardCharsets.UTF_8).trim().toLowerCase();
        if (asText.contains("dummy geotiff") || asText.contains("placeholder")) {
            throw new IOException(
                    "Raster artifact is a placeholder, not a real GeoTIFF: " + file.getAbsolutePath()
                            + ". Update TerraAI backend output writer to emit a valid TIFF.");
        }
    }
}
