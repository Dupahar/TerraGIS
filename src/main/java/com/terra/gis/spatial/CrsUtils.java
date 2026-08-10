package com.terra.gis.spatial;

import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles Coordinate Reference System (CRS) operations including parsing and coordinate transformations.
 * <p>
 * This utility class manages geographic projections using the GeoTools CRS authority factory (EPSG database).
 * Common use cases include:
 * <ul>
 *   <li><strong>CRS Parsing:</strong> Convert EPSG codes (e.g., "EPSG:4326") to CRS objects</li>
 *   <li><strong>Reprojection:</strong> Transform geometries between different coordinate systems</li>
 *   <li><strong>On-the-fly Transformation:</strong> Align multi-source datasets with different projections</li>
 * </ul>
 * 
 * <p><strong>Common EPSG Codes:</strong></p>
 * <ul>
 *   <li><strong>EPSG:4326</strong> - WGS84 Geographic (latitude/longitude in degrees)</li>
 *   <li><strong>EPSG:3857</strong> - Web Mercator (used by Google Maps, OpenStreetMap)</li>
 *   <li><strong>EPSG:32644</strong> - WGS84 / UTM Zone 44N (suitable for India)</li>
 *   <li><strong>EPSG:27700</strong> - British National Grid</li>
 * </ul>
 * 
 * <p><strong>Performance Note:</strong> First CRS operation may be slow (10-30 seconds) as GeoTools
 * initializes the EPSG database. Subsequent operations are fast due to internal caching.</p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * CrsUtils crsUtils = new CrsUtils();
 * 
 * // Parse CRS from EPSG code
 * CoordinateReferenceSystem wgs84 = crsUtils.parseCRS("EPSG:4326");
 * CoordinateReferenceSystem webMercator = crsUtils.parseCRS("EPSG:3857");
 * 
 * // Reproject geometry from WGS84 to Web Mercator
 * Geometry latLonGeom = ...; // in EPSG:4326
 * Geometry webMercGeom = crsUtils.reproject(latLonGeom, wgs84, webMercator);
 * }</pre>
 * 
 * @see org.geotools.referencing.CRS
 * @see org.geotools.api.referencing.crs.CoordinateReferenceSystem
 */
public class CrsUtils {

    private static final Logger log = LoggerFactory.getLogger(CrsUtils.class);

    /**
     * Parses an EPSG code string into a CoordinateReferenceSystem object.
     * 
     * @param epsgCode e.g., "EPSG:4326" or "EPSG:32644"
     * @return CoordinateReferenceSystem for the given EPSG code
     * @throws FactoryException if CRS cannot be decoded
     * @throws IllegalArgumentException if epsgCode is null or empty
     */
    public CoordinateReferenceSystem parseCRS(String epsgCode) throws FactoryException {
        if (epsgCode == null || epsgCode.trim().isEmpty()) {
            throw new IllegalArgumentException("EPSG code cannot be null or empty");
        }
        
        log.debug("Parsing CRS: {}", epsgCode);
        // 'true' allows longitude/latitude order which is common in GIS
        CoordinateReferenceSystem crs = CRS.decode(epsgCode, true);
        log.info("Successfully parsed CRS: {}", epsgCode);
        return crs;
    }

    /**
     * Reprojects a JTS Geometry from a source CRS to a target CRS.
     * 
     * @param geometry The geometry to reproject
     * @param sourceCRS The source coordinate reference system
     * @param targetCRS The target coordinate reference system
     * @return Reprojected geometry
     * @throws FactoryException if transformation cannot be created
     * @throws TransformException if geometry transformation fails
     * @throws IllegalArgumentException if any parameter is null
     */
    public Geometry reproject(Geometry geometry, CoordinateReferenceSystem sourceCRS,
            CoordinateReferenceSystem targetCRS)
            throws FactoryException, TransformException {

        if (geometry == null) {
            throw new IllegalArgumentException("Geometry cannot be null");
        }
        if (sourceCRS == null) {
            throw new IllegalArgumentException("Source CRS cannot be null");
        }
        if (targetCRS == null) {
            throw new IllegalArgumentException("Target CRS cannot be null");
        }

        log.debug("Reprojecting geometry from {} to {}", 
            sourceCRS.getName(), targetCRS.getName());
        
        MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, true);
        Geometry reprojected = JTS.transform(geometry, transform);
        
        log.info("Successfully reprojected {} to {}", 
            sourceCRS.getName(), targetCRS.getName());
        
        return reprojected;
    }
}
