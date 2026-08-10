package com.terra.gis.spatial;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.operation.overlay.snap.SnapIfNeededOverlayOp;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides mathematical geometric operations for spatial analysis using JTS (Java Topology Suite).
 * <p>
 * This utility class wraps common JTS operations for vector geometry manipulation:
 * <ul>
 *   <li><strong>Buffer:</strong> Create proximity zones around features (e.g., 100m around a road)</li>
 *   <li><strong>Intersection:</strong> Find overlapping areas between geometries (e.g., flood zones vs buildings)</li>
 *   <li><strong>Validation:</strong> Check geometric validity (no self-intersections, proper topology)</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> This class is thread-safe. Each instance maintains its own GeometryFactory.</p>
 * 
 * <p><strong>Coordinate Reference Systems:</strong> All operations are CRS-agnostic and work in the geometry's
 * native coordinate space. Use {@link CrsUtils} for coordinate transformations before geometric operations.</p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * GeometryUtils utils = new GeometryUtils();
 * Geometry river = ...;
 * Geometry buffer100m = utils.buffer(river, 100.0); // 100-meter buffer
 * 
 * Geometry floodZone = ...;
 * Geometry buildings = ...;
 * Geometry affectedBuildings = utils.intersect(floodZone, buildings);
 * 
 * if (!utils.isValid(userDrawnPolygon)) {
 *     System.err.println("Invalid geometry detected!");
 * }
 * }</pre>
 * 
 * @see org.locationtech.jts.geom.Geometry
 * @see org.locationtech.jts.geom.GeometryFactory
 * @see CrsUtils
 */
public class GeometryUtils {

    private static final Logger log = LoggerFactory.getLogger(GeometryUtils.class);
    private final GeometryFactory factory;

    public GeometryUtils() {
        // Default floating precision geometry factory
        this.factory = new GeometryFactory(new PrecisionModel());
        log.debug("GeometryUtils initialized with default precision model");
    }

    public GeometryFactory getFactory() {
        return factory;
    }

    /**
     * Creates a buffer around a geometry (e.g., buffering a river).
     * 
     * @param geom The geometry to buffer
     * @param distance The buffer distance (in same units as geometry CRS)
     * @return Buffered geometry
     * @throws IllegalArgumentException if geom is null
     */
    public Geometry buffer(Geometry geom, double distance) {
        if (geom == null) {
            throw new IllegalArgumentException("Geometry cannot be null");
        }
        log.debug("Buffering geometry with distance: {}", distance);
        return geom.buffer(distance);
    }

    /**
     * Calculates the intersection between two geometries (e.g., flood zone
     * intersecting a building).
     * 
     * @param a First geometry
     * @param b Second geometry
     * @return Intersection geometry
     * @throws IllegalArgumentException if either geometry is null
     */
    public Geometry intersect(Geometry a, Geometry b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Geometries cannot be null");
        }
        log.debug("Computing intersection of {} and {}", a.getGeometryType(), b.getGeometryType());
        Geometry left = prepareForOverlay(a);
        Geometry right = prepareForOverlay(b);
        try {
            return normalizeResult(SnapIfNeededOverlayOp.intersection(left, right));
        } catch (RuntimeException ex) {
            log.warn("Primary intersection failed, retrying with normalized geometries: {}", ex.getMessage());
            Geometry normalizedLeft = normalizeResult(left);
            Geometry normalizedRight = normalizeResult(right);
            return normalizeResult(SnapIfNeededOverlayOp.intersection(normalizedLeft, normalizedRight));
        }
    }

    private Geometry prepareForOverlay(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return geometry;
        }
        if (geometry.isValid()) {
            return geometry;
        }
        return normalizeResult(geometry);
    }

    private Geometry normalizeResult(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return geometry;
        }
        if (geometry.isValid()) {
            return geometry;
        }
        try {
            Geometry fixed = geometry.buffer(0);
            if (fixed != null && !fixed.isEmpty()) {
                return fixed;
            }
        } catch (RuntimeException ex) {
            log.debug("Geometry normalization via buffer(0) failed: {}", ex.getMessage());
        }
        return geometry;
    }

    /**
     * Validates if a user-drawn or AI-generated polygon is mathematically
     * simple/valid
     * (e.g., no self-intersections).
     * 
     * @param geom The geometry to validate
     * @return true if geometry is valid, false otherwise
     * @throws IllegalArgumentException if geom is null
     */
    public boolean isValid(Geometry geom) {
        if (geom == null) {
            throw new IllegalArgumentException("Geometry cannot be null");
        }
        IsValidOp validOp = new IsValidOp(geom);
        boolean valid = validOp.isValid();
        if (!valid) {
            log.warn("Invalid geometry detected: {}", validOp.getValidationError());
        }
        return valid;
    }
}
