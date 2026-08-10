package com.terra.gis.ui;

import javafx.geometry.Point2D;
import javafx.animation.PauseTransition;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.effect.Effect;
import javafx.util.Duration;
import org.geotools.api.data.FeatureSource;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.coverage.grid.GridCoverage;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.GeometryDescriptor;
import org.geotools.api.style.Style;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.AbstractGridCoverage2DReader;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.collection.CollectionFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.GridCoverageLayer;
import org.geotools.map.GridReaderLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.map.MapViewport;
import org.geotools.referencing.CRS;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.SLD;
import org.geotools.styling.StyleBuilder;
import org.geotools.api.style.Fill;
import org.geotools.api.style.Graphic;
import org.geotools.api.style.LineSymbolizer;
import org.geotools.api.style.PointSymbolizer;
import org.geotools.api.style.PolygonSymbolizer;
import org.geotools.api.style.RasterSymbolizer;
import org.geotools.api.style.Rule;
import org.geotools.api.style.Stroke;
import org.geotools.api.style.Symbolizer;
import org.geotools.api.style.TextSymbolizer;
import org.geotools.api.filter.expression.Expression;
import org.jfree.fx.FXGraphics2D;
import org.locationtech.jts.algorithm.LineIntersector;
import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.GeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.crs.GeographicCRS;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

/**
 * A custom JavaFX Canvas for rendering GeoTools MapContent with interactive map controls.
 * <p>
 * This canvas provides:
 * <ul>
 *   <li>Vector layer rendering using GeoTools StreamingRenderer</li>
 *   <li>Interactive pan and zoom controls (mouse drag and scroll)</li>
 *   <li>Layer management (add, remove, reorder layers)</li>
 *   <li>Automatic viewport management and bounds calculation</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * MapCanvas canvas = new MapCanvas();
 * canvas.addLayer(featureSource, style);
 * canvas.removeLayer(0);
 * canvas.moveLayerUp(1);
 * }</pre>
 * 
 * @see org.geotools.map.MapContent
 * @see org.geotools.renderer.lite.StreamingRenderer
 */
public class MapCanvas extends Canvas {

    public enum EditMode {
        PAN,
        SELECT,
        DRAW_POINT,
        DRAW_LINE,
        DRAW_POLYGON,
        DELETE,
        MOVE_VERTEX
    }

    private enum FeatureKind {
        POINT,
        LINE,
        POLYGON
    }

    private record FeatureRef(FeatureKind kind, int index) {}

    private static class FeatureAttributes {
        private final int id;
        private String name;
        private String notes;

        private FeatureAttributes(int id, String name, String notes) {
            this.id = id;
            this.name = name;
            this.notes = notes;
        }
    }

    public static record SelectedFeature(
            int id,
            String geometryType,
            double centerX,
            double centerY,
            String name,
            String notes) {
    }

    public interface SelectedFeatureListener {
        void onFeatureSelected(SelectedFeature selectedFeature);
    }

    public enum DigitizedExportKind {
        POINTS,
        LINES,
        POLYGONS
    }

    private enum VertexKind {
        POINT,
        LINE,
        POLYGON,
        SKETCH
    }

    private record VertexRef(VertexKind kind, int featureIndex, int vertexIndex, Coordinate original) {}

    private record VectorLayerColors(String fillColor, String boundaryColor) {}

    private static class SnappingManager {
        private boolean endpointMode = true;
        private boolean intersectionMode = false;
        private double toleranceRatio = 0.01;

        private boolean isEndpointMode() {
            return endpointMode;
        }

        private void setEndpointMode(boolean endpointMode) {
            this.endpointMode = endpointMode;
        }

        private boolean isIntersectionMode() {
            return intersectionMode;
        }

        private void setIntersectionMode(boolean intersectionMode) {
            this.intersectionMode = intersectionMode;
        }

        private double getToleranceRatio() {
            return toleranceRatio;
        }

        private void setToleranceRatio(double toleranceRatio) {
            this.toleranceRatio = Math.max(0.001, Math.min(0.1, toleranceRatio));
        }
    }

    private static final Logger log = LoggerFactory.getLogger(MapCanvas.class);
    private static final double MAX_VIEW_EXPANSION_FACTOR = 4.0;
    private static final double MIN_VIEW_SHRINK_FACTOR = 0.00001;
    private static final long INTERACTIVE_DRAW_INTERVAL_NANOS = 20_000_000L;
    private static final long STABLE_FRAME_CAPTURE_INTERVAL_NANOS = 250_000_000L;
    private static final long LOW_MEMORY_MODE_MAX_HEAP_BYTES = 3L * 1024L * 1024L * 1024L;
    private static final long SLOW_FRAME_WARN_NANOS = 45_000_000L;
    private static final int INTERACTION_SETTLE_REDRAW_MS = 140;
    private static final int RESIZE_REDRAW_DEBOUNCE_MS = 80;
    private static final double MIN_INTERACTIVE_PIXEL_DELTA = 0.75;
    private static final int MAX_INTERSECTION_SNAP_SEGMENTS = 1200;
    private static final int MAX_INTERSECTION_SNAP_RESULTS = 2500;
    private static final double GEOGRAPHIC_MIN_LON = -179.9999;
    private static final double GEOGRAPHIC_MAX_LON = 179.9999;
    private static final double GEOGRAPHIC_MIN_LAT = -89.9;
    private static final double GEOGRAPHIC_MAX_LAT = 89.9;
    private static final double MERCATOR_SAFE_MIN_LON = -179.0;
    private static final double MERCATOR_SAFE_MAX_LON = 179.0;
    private static final double MERCATOR_SAFE_MIN_LAT = -85.0;
    private static final double MERCATOR_SAFE_MAX_LAT = 85.0;

    private MapContent mapContent;
    private StreamingRenderer renderer;
    private FXGraphics2D fxgraphics;

    // Interaction state
    private Point2D lastMousePos;
    private boolean isPanning = false;
    private EditMode editMode = EditMode.PAN;
    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final SnappingManager snappingManager = new SnappingManager();
    private int nextDigitizedFeatureId = 1;
    private final List<Point> digitizedPoints = new ArrayList<>();
    private final List<FeatureAttributes> pointAttributes = new ArrayList<>();
    private final List<LineString> digitizedLines = new ArrayList<>();
    private final List<FeatureAttributes> lineAttributes = new ArrayList<>();
    private final List<Polygon> digitizedPolygons = new ArrayList<>();
    private final List<FeatureAttributes> polygonAttributes = new ArrayList<>();
    private final List<Coordinate> activeSketch = new ArrayList<>();
    private final Deque<Runnable> undoStack = new ArrayDeque<>();
    private FeatureRef selectedFeature;
    private SelectedFeatureListener selectedFeatureListener;
    private VertexRef activeVertexMove;
    private boolean vertexMoveChanged;
    private long lastInteractiveDrawNanos;
    private long lastStableCaptureNanos;
    private boolean layerStyleValidationPending = true;
    private final Map<Layer, Double> layerOpacityByLayer = new IdentityHashMap<>();
    private final Map<Layer, VectorLayerColors> vectorLayerColorsByLayer = new IdentityHashMap<>();
    private WritableImage stableFrameImage;
    private ReferencedEnvelope stableFrameViewport;
    private ReferencedEnvelope cachedDatasetBounds;
    private Boolean cachedMercatorSafeDomain;
    private final List<Coordinate> endpointSnapCandidates = new ArrayList<>();
    private final List<Coordinate> intersectionSnapCandidates = new ArrayList<>();
    private boolean snapCandidateCacheDirty = true;
    private boolean intersectionCandidateCacheDirty = true;
    private final PauseTransition interactionSettleRedraw = new PauseTransition(Duration.millis(INTERACTION_SETTLE_REDRAW_MS));
    private final PauseTransition resizeRedrawDebounce = new PauseTransition(Duration.millis(RESIZE_REDRAW_DEBOUNCE_MS));

    public MapCanvas() {
        super(com.terra.gis.AppConfig.getInstance().getMapCanvasDefaultWidth(), 
              com.terra.gis.AppConfig.getInstance().getMapCanvasDefaultHeight());
        
        com.terra.gis.AppConfig config = com.terra.gis.AppConfig.getInstance();
        log.info("Initializing MapCanvas with size: {}x{}", 
            config.getMapCanvasDefaultWidth(), config.getMapCanvasDefaultHeight());

        mapContent = new MapContent();
        mapContent.setTitle(config.getAppName() + " Map Canvas");
        mapContent.getViewport().setMatchingAspectRatio(true);

        renderer = new StreamingRenderer();
        renderer.setMapContent(mapContent);
        log.debug("StreamingRenderer configured");

        // Debounce resize redraws to reduce render pressure on slower devices.
        resizeRedrawDebounce.setOnFinished(event -> {
            resetInteractiveDrawThrottle();
            draw();
        });
        widthProperty().addListener(e -> handleCanvasSizeChanged());
        heightProperty().addListener(e -> handleCanvasSizeChanged());
        interactionSettleRedraw.setOnFinished(event -> {
            resetInteractiveDrawThrottle();
            draw();
        });

        setupEventHandlers();
    }

    private void handleCanvasSizeChanged() {
        stableFrameImage = null;
        stableFrameViewport = null;
        if (mapContent != null && mapContent.getViewport() != null && getWidth() > 0 && getHeight() > 0) {
            mapContent.getViewport().setScreenArea(new Rectangle(
                    0,
                    0,
                    Math.max(1, (int) Math.ceil(getWidth())),
                    Math.max(1, (int) Math.ceil(getHeight()))
            ));
        }
        resizeRedrawDebounce.playFromStart();
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    @Override
    public double prefWidth(double height) {
        return getWidth();
    }

    @Override
    public double prefHeight(double width) {
        return getHeight();
    }

    @Override
    public double minWidth(double height) {
        return 0.0;
    }

    @Override
    public double minHeight(double width) {
        return 0.0;
    }

    @Override
    public double maxWidth(double height) {
        return Double.MAX_VALUE;
    }

    @Override
    public double maxHeight(double width) {
        return Double.MAX_VALUE;
    }

    @Override
    public void resize(double width, double height) {
        double safeWidth = Math.max(1.0, width);
        double safeHeight = Math.max(1.0, height);
        if (Double.compare(getWidth(), safeWidth) != 0) {
            setWidth(safeWidth);
        }
        if (Double.compare(getHeight(), safeHeight) != 0) {
            setHeight(safeHeight);
        }
    }

    /**
     * Set the current MapContent and refresh.
     */
    public void setMapContent(MapContent mapContent) {
        this.mapContent = mapContent;
        renderer.setMapContent(this.mapContent);
        layerStyleValidationPending = true;
        invalidateRenderCaches();
        draw();
    }

    public MapContent getMapContent() {
        return mapContent;
    }

    /**
     * Marks cached map metadata stale after external code mutates MapContent directly.
     */
    public void invalidateLayerMetadataCache() {
        invalidateRenderCaches();
        layerStyleValidationPending = true;
    }

    /**
     * Add a feature layer directly to the map.
     * 
     * @param featureSource The feature source containing geographic data
     * @param style The visual style to apply to the layer
     * @throws IllegalArgumentException if featureSource or style is null
     */
    public void addLayer(FeatureSource<?, ?> featureSource, Style style) {
        if (featureSource == null || style == null) {
            throw new IllegalArgumentException("FeatureSource and Style cannot be null");
        }
        log.info("Adding new feature layer to map");
        FeatureLayer layer = new FeatureLayer(featureSource, style);
        mapContent.addLayer(layer);
        layerOpacityByLayer.put(layer, 1.0);
        vectorLayerColorsByLayer.remove(layer);
        layerStyleValidationPending = true;
        invalidateRenderCaches();

        // If this is the first layer, center the map to its bounds
        if (mapContent.layers().size() == 1) {
            try {
                ReferencedEnvelope bounds = mapContent.getMaxBounds();
                if (bounds != null) {
                    setViewportBounds(bounds, "feature layer bounds");
                    log.debug("Centered map to layer bounds: {}", bounds);
                }
            } catch (Exception e) {
                log.error("Failed to center map to layer bounds", e);
            }
        }
        draw();
    }

    /**
     * Adds a raster coverage layer to the map.
     *
     * @param coverage The raster coverage to render
     * @param layerName Display name for the layer
     * @throws IllegalArgumentException if coverage is null or unsupported type
     */
    public void addRasterLayer(GridCoverage coverage, String layerName) {
        if (coverage == null) {
            throw new IllegalArgumentException("Coverage cannot be null");
        }
        if (!(coverage instanceof GridCoverage2D gridCoverage2D)) {
            throw new IllegalArgumentException("Only GridCoverage2D is supported");
        }

        StyleBuilder styleBuilder = new StyleBuilder();
        Style rasterStyle = styleBuilder.createStyle(styleBuilder.createRasterSymbolizer());
        GridCoverageLayer rasterLayer = new GridCoverageLayer(gridCoverage2D, rasterStyle);
        if (layerName != null && !layerName.isBlank()) {
            rasterLayer.setTitle(layerName);
        }

        log.info("Adding raster layer to map: {}", layerName);
        mapContent.addLayer(rasterLayer);
        layerOpacityByLayer.put(rasterLayer, 1.0);
        layerStyleValidationPending = true;
        invalidateRenderCaches();

        if (mapContent.layers().size() == 1) {
            try {
                ReferencedEnvelope bounds = mapContent.getMaxBounds();
                if (bounds != null) {
                    setViewportBounds(bounds, "raster layer bounds");
                    log.debug("Centered map to raster bounds: {}", bounds);
                }
            } catch (Exception e) {
                log.error("Failed to center map to raster bounds", e);
            }
        }

        draw();
    }

    /**
     * Adds a raster reader-backed layer for lazy rendering (preferred for large rasters).
     *
     * @param reader raster reader
     * @param layerName display name
     */
    public void addRasterLayer(AbstractGridCoverage2DReader reader, String layerName) {
        if (reader == null) {
            throw new IllegalArgumentException("Reader cannot be null");
        }

        StyleBuilder styleBuilder = new StyleBuilder();
        Style rasterStyle = styleBuilder.createStyle(styleBuilder.createRasterSymbolizer());
        GridReaderLayer rasterLayer = new GridReaderLayer(reader, rasterStyle);
        if (layerName != null && !layerName.isBlank()) {
            rasterLayer.setTitle(layerName);
        }

        log.info("Adding reader-backed raster layer to map: {}", layerName);
        mapContent.addLayer(rasterLayer);
        layerOpacityByLayer.put(rasterLayer, 1.0);
        layerStyleValidationPending = true;
        invalidateRenderCaches();

        // QGIS-style behavior: initialize canvas extent from layer/provider extent immediately.
        initializeViewportForRasterLayer(rasterLayer, reader);

        draw();
    }

    /**
     * Adds a polygon overlay layer (for AI inference results) to the map.
     */
    public void addPolygonOverlayLayer(List<Polygon> polygons, String layerName) {
        if (polygons == null || polygons.isEmpty()) {
            throw new IllegalArgumentException("polygons cannot be null or empty");
        }

        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName("ai_overlay");
        typeBuilder.add("the_geom", Polygon.class);
        typeBuilder.add("label", String.class);
        typeBuilder.add("confidence", Double.class);
        SimpleFeatureType featureType = typeBuilder.buildFeatureType();

        List<SimpleFeature> features = new ArrayList<>(polygons.size());
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);
        for (int i = 0; i < polygons.size(); i++) {
            featureBuilder.add(polygons.get(i));
            featureBuilder.add("ai-segment");
            featureBuilder.add(1.0d);
            features.add(featureBuilder.buildFeature("ai-overlay-" + i));
            featureBuilder.reset();
        }

        SimpleFeatureCollection collection = new ListFeatureCollection(featureType, features);
        StyleBuilder styleBuilder = new StyleBuilder();
        boolean denseOverlay = polygons.size() > 1200;
        Style style = styleBuilder.createStyle(
                styleBuilder.createPolygonSymbolizer(
                styleBuilder.createStroke(new java.awt.Color(220, 30, 30, denseOverlay ? 120 : 180), denseOverlay ? 0.8 : 1.2),
                styleBuilder.createFill(new java.awt.Color(255, 80, 80, denseOverlay ? 20 : 45))));

        FeatureLayer layer = new FeatureLayer(collection, style);
        if (layerName != null && !layerName.isBlank()) {
            layer.setTitle(layerName);
        }
        mapContent.addLayer(layer);
        layerOpacityByLayer.put(layer, 1.0);
        layerStyleValidationPending = true;
        invalidateRenderCaches();

        try {
            ReferencedEnvelope bounds = layer.getBounds();
            if (isUsableBounds(bounds)) {
                setViewportBounds(bounds, "ai overlay bounds");
            }
        } catch (Throwable ex) {
            log.warn("Could not zoom to AI overlay bounds", ex);
        }

        draw();
    }

    /**
     * Returns geospatial raster bounds for a raster layer index, or null if unavailable.
     */
    public ReferencedEnvelope getRasterLayerBounds(int index) {
        if (index < 0 || index >= mapContent.layers().size()) {
            return null;
        }

        Layer layer = mapContent.layers().get(index);
        try {
            if (layer instanceof GridReaderLayer readerLayer) {
                return extractLayerBounds(readerLayer)
                        .or(() -> extractReaderBounds(readerLayer.getReader()))
                        .orElse(null);
            }

            if (layer instanceof GridCoverageLayer coverageLayer) {
                ReferencedEnvelope bounds = coverageLayer.getBounds();
                if (isUsableBounds(bounds)) {
                    return bounds;
                }
            }
        } catch (Throwable ex) {
            log.warn("Could not read raster bounds for layer index {}", index, ex);
        }
        return null;
    }

    /**
     * Converts pixel-space polygons (0..rasterWidth, 0..rasterHeight) into map coordinates.
     */
    public List<Polygon> toMapCoordinates(
            List<Polygon> pixelPolygons,
            int rasterWidth,
            int rasterHeight,
            ReferencedEnvelope rasterBounds) {
        if (pixelPolygons == null || pixelPolygons.isEmpty()) {
            return List.of();
        }
        if (rasterWidth <= 0 || rasterHeight <= 0 || !isUsableBounds(rasterBounds)) {
            throw new IllegalArgumentException("Invalid raster dimensions or bounds for coordinate transform");
        }

        double minX = rasterBounds.getMinX();
        double maxX = rasterBounds.getMaxX();
        double minY = rasterBounds.getMinY();
        double maxY = rasterBounds.getMaxY();
        double spanX = maxX - minX;
        double spanY = maxY - minY;

        List<Polygon> output = new ArrayList<>(pixelPolygons.size());
        for (Polygon pixelPolygon : pixelPolygons) {
            Coordinate[] src = pixelPolygon.getExteriorRing().getCoordinates();
            Coordinate[] dst = new Coordinate[src.length];
            for (int i = 0; i < src.length; i++) {
                // Pixel origin is top-left; map Y usually increases northward.
                double worldX = minX + (src[i].x / rasterWidth) * spanX;
                double worldY = maxY - (src[i].y / rasterHeight) * spanY;
                dst[i] = new Coordinate(worldX, worldY);
            }
            output.add(pixelPolygon.getFactory().createPolygon(dst));
        }
        return output;
    }

    public void exportRasterLayer(int index, File outputFile) throws IOException {
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null");
        }
        if (index < 0 || index >= mapContent.layers().size()) {
            throw new IllegalArgumentException("Invalid layer index: " + index);
        }

        Layer layer = mapContent.layers().get(index);
        GridCoverage2D coverage = null;

        if (layer instanceof GridCoverageLayer coverageLayer) {
            GridCoverage gridCoverage = coverageLayer.getCoverage();
            if (gridCoverage instanceof GridCoverage2D gridCoverage2D) {
                coverage = gridCoverage2D;
            }
        } else if (layer instanceof GridReaderLayer readerLayer && readerLayer.getReader() != null) {
            coverage = readerLayer.getReader().read(null);
        }

        if (coverage == null) {
            throw new IOException("Selected layer is not a readable raster layer");
        }

        GeoTiffWriter writer = new GeoTiffWriter(outputFile);
        try {
            writer.write(coverage, null);
        } finally {
            writer.dispose();
        }
    }

    /**
     * Saves a PNG snapshot of the current map canvas for welcome-screen previews.
     *
     * @param outputPath destination PNG path
     * @return true if saved, false otherwise
     */
    public boolean saveSnapshotImage(Path outputPath) {
        if (outputPath == null) {
            return false;
        }

        try {
            WritableImage snapshotImage = snapshot(null, null);
            if (snapshotImage == null) {
                return false;
            }

            File outputFile = outputPath.toFile();
            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return false;
            }

            return ImageIO.write(SwingFXUtils.fromFXImage(snapshotImage, null), "png", outputFile);
        } catch (Exception ex) {
            log.warn("Failed to save map snapshot image: {}", outputPath, ex);
            return false;
        }
    }

    public boolean isRasterLayer(int index) {
        if (index < 0 || index >= mapContent.layers().size()) {
            return false;
        }
        Layer layer = mapContent.layers().get(index);
        return layer instanceof GridCoverageLayer || layer instanceof GridReaderLayer;
    }

    /**
     * Returns raster dimensions [width, height] for the given layer index, or null if unavailable.
     */
    public int[] getRasterLayerDimensions(int index) {
        if (index < 0 || index >= mapContent.layers().size()) {
            return null;
        }

        Layer layer = mapContent.layers().get(index);
        try {
            if (layer instanceof GridCoverageLayer coverageLayer) {
                GridCoverage coverage = coverageLayer.getCoverage();
                if (coverage instanceof GridCoverage2D gridCoverage2D) {
                    return new int[] {
                            gridCoverage2D.getRenderedImage().getWidth(),
                            gridCoverage2D.getRenderedImage().getHeight()
                    };
                }
            }

            if (layer instanceof GridReaderLayer readerLayer && readerLayer.getReader() != null) {
                var gridRange = readerLayer.getReader().getOriginalGridRange();
                if (gridRange != null) {
                    return new int[] { gridRange.getSpan(0), gridRange.getSpan(1) };
                }
            }
        } catch (Throwable ex) {
            log.warn("Could not read raster dimensions for layer index {}", index, ex);
        }
        return null;
    }

    private void initializeViewportForRasterLayer(GridReaderLayer rasterLayer, AbstractGridCoverage2DReader reader) {
        Optional<ReferencedEnvelope> fromLayer = extractLayerBounds(rasterLayer);
        if (fromLayer.isPresent()) {
            setViewportBounds(fromLayer.get(), "layer bounds");
            return;
        }

        Optional<ReferencedEnvelope> fromReader = extractReaderBounds(reader);
        if (fromReader.isPresent()) {
            setViewportBounds(fromReader.get(), "reader bounds");
            return;
        }

        try {
            ReferencedEnvelope maxBounds = mapContent.getMaxBounds();
            if (isUsableBounds(maxBounds)) {
                setViewportBounds(maxBounds, "map max bounds");
                return;
            }
        } catch (Throwable ex) {
            log.warn("Could not determine map max bounds for raster", ex);
        }

        ReferencedEnvelope worldFallback = new ReferencedEnvelope(-180, 180, -90, 90, null);
        setViewportBounds(worldFallback, "world fallback");
    }

    private Optional<ReferencedEnvelope> extractLayerBounds(GridReaderLayer rasterLayer) {
        try {
            ReferencedEnvelope bounds = rasterLayer.getBounds();
            if (isUsableBounds(bounds)) {
                return Optional.of(bounds);
            }
        } catch (Throwable ex) {
            log.warn("Could not get raster layer bounds", ex);
        }
        return Optional.empty();
    }

    private Optional<ReferencedEnvelope> extractReaderBounds(GridCoverage2DReader reader) {
        try {
            var env = reader.getOriginalEnvelope();
            if (env == null) {
                return Optional.empty();
            }
            ReferencedEnvelope readerBounds = new ReferencedEnvelope(
                    env.getMinimum(0), env.getMaximum(0),
                    env.getMinimum(1), env.getMaximum(1),
                    reader.getCoordinateReferenceSystem());
            if (isUsableBounds(readerBounds)) {
                return Optional.of(readerBounds);
            }
        } catch (Throwable ex) {
            log.warn("Could not get raster reader bounds", ex);
        }
        return Optional.empty();
    }

    private boolean isUsableBounds(ReferencedEnvelope bounds) {
        if (bounds == null || bounds.isEmpty()) {
            return false;
        }
        return Double.isFinite(bounds.getMinX())
                && Double.isFinite(bounds.getMaxX())
                && Double.isFinite(bounds.getMinY())
                && Double.isFinite(bounds.getMaxY())
                && bounds.getMaxX() > bounds.getMinX()
                && bounds.getMaxY() > bounds.getMinY();
    }

    private void invalidateRenderCaches() {
        cachedDatasetBounds = null;
        cachedMercatorSafeDomain = null;
        stableFrameImage = null;
        stableFrameViewport = null;
    }

    private ReferencedEnvelope getCachedDatasetBounds() {
        if (cachedDatasetBounds != null && isUsableBounds(cachedDatasetBounds)) {
            return cachedDatasetBounds;
        }

        try {
            ReferencedEnvelope maxBounds = mapContent.getMaxBounds();
            if (isUsableBounds(maxBounds)) {
                cachedDatasetBounds = new ReferencedEnvelope(maxBounds);
                return cachedDatasetBounds;
            }
        } catch (Throwable ex) {
            log.debug("Unable to get max bounds for viewport constraint", ex);
        }
        return null;
    }

    private void setViewportBounds(ReferencedEnvelope bounds, String source) {
        try {
            MapViewport viewport = mapContent.getViewport();
            ReferencedEnvelope safeBounds = constrainEnvelopeToDomain(bounds);
            if (safeBounds.getCoordinateReferenceSystem() != null) {
                viewport.setCoordinateReferenceSystem(safeBounds.getCoordinateReferenceSystem());
            }
            viewport.setBounds(safeBounds);
            if (source != null && source.contains("interaction")) {
                log.debug("Updated viewport from {}: {}", source, safeBounds);
            } else {
                log.info("Initialized viewport from {}: {}", source, safeBounds);
            }
        } catch (Throwable ex) {
            log.warn("Failed to set viewport bounds from {}", source, ex);
        }
    }

    private ReferencedEnvelope constrainEnvelopeToDomain(ReferencedEnvelope envelope) {
        if (!isUsableBounds(envelope)) {
            return envelope;
        }

        CoordinateReferenceSystem crs = envelope.getCoordinateReferenceSystem();
        if (!isGeographicCrs(crs)) {
            return envelope;
        }

        boolean mercatorSafeDomain = requiresMercatorSafeDomain();
        double minLonLimit = mercatorSafeDomain ? MERCATOR_SAFE_MIN_LON : GEOGRAPHIC_MIN_LON;
        double maxLonLimit = mercatorSafeDomain ? MERCATOR_SAFE_MAX_LON : GEOGRAPHIC_MAX_LON;
        double minLatLimit = mercatorSafeDomain ? MERCATOR_SAFE_MIN_LAT : GEOGRAPHIC_MIN_LAT;
        double maxLatLimit = mercatorSafeDomain ? MERCATOR_SAFE_MAX_LAT : GEOGRAPHIC_MAX_LAT;

        double minX = Math.max(envelope.getMinX(), minLonLimit);
        double maxX = Math.min(envelope.getMaxX(), maxLonLimit);
        double minY = Math.max(envelope.getMinY(), minLatLimit);
        double maxY = Math.min(envelope.getMaxY(), maxLatLimit);

        if (!Double.isFinite(minX) || !Double.isFinite(maxX) || maxX <= minX) {
            minX = minLonLimit;
            maxX = maxLonLimit;
        }

        if (!Double.isFinite(minY) || !Double.isFinite(maxY) || maxY <= minY) {
            minY = minLatLimit;
            maxY = maxLatLimit;
        }

        return new ReferencedEnvelope(minX, maxX, minY, maxY, crs);
    }

    private boolean requiresMercatorSafeDomain() {
        if (cachedMercatorSafeDomain != null) {
            return cachedMercatorSafeDomain;
        }

        boolean required = false;
        if (mapContent == null) {
            return false;
        }

        try {
            MapViewport viewport = mapContent.getViewport();
            if (viewport != null && isMercatorCrs(viewport.getCoordinateReferenceSystem())) {
                required = true;
            }
        } catch (Throwable ex) {
            log.debug("Could not inspect viewport CRS for mercator-safe domain", ex);
        }

        if (!required) {
            try {
                for (Layer layer : mapContent.layers()) {
                    if (layer == null || !layer.isVisible()) {
                        continue;
                    }
                    if (isMercatorCrs(extractLayerCrs(layer))) {
                        required = true;
                        break;
                    }
                }
            } catch (Throwable ex) {
                log.debug("Could not inspect layer CRS for mercator-safe domain", ex);
            }
        }

        cachedMercatorSafeDomain = required;
        return required;
    }

    private CoordinateReferenceSystem extractLayerCrs(Layer layer) {
        if (layer == null) {
            return null;
        }

        try {
            if (layer instanceof FeatureLayer featureLayer
                    && featureLayer.getFeatureSource() instanceof SimpleFeatureSource simpleFeatureSource
                    && simpleFeatureSource.getSchema() != null) {
                CoordinateReferenceSystem crs = simpleFeatureSource.getSchema().getCoordinateReferenceSystem();
                if (crs != null) {
                    return crs;
                }
            }
        } catch (Throwable ex) {
            log.debug("Could not read feature layer CRS", ex);
        }

        try {
            if (layer instanceof GridReaderLayer readerLayer && readerLayer.getReader() != null) {
                CoordinateReferenceSystem crs = readerLayer.getReader().getCoordinateReferenceSystem();
                if (crs != null) {
                    return crs;
                }
            }
        } catch (Throwable ex) {
            log.debug("Could not read reader layer CRS", ex);
        }

        try {
            if (layer instanceof GridCoverageLayer coverageLayer && coverageLayer.getCoverage() != null) {
                CoordinateReferenceSystem crs = coverageLayer.getCoverage().getCoordinateReferenceSystem();
                if (crs != null) {
                    return crs;
                }
            }
        } catch (Throwable ex) {
            log.debug("Could not read coverage layer CRS", ex);
        }

        try {
            ReferencedEnvelope bounds = layer.getBounds();
            if (bounds != null && bounds.getCoordinateReferenceSystem() != null) {
                return bounds.getCoordinateReferenceSystem();
            }
        } catch (Throwable ex) {
            log.debug("Could not read layer bounds CRS", ex);
        }

        return null;
    }

    private boolean isMercatorCrs(CoordinateReferenceSystem crs) {
        if (crs == null) {
            return false;
        }

        try {
            String srs = CRS.toSRS(crs, true);
            if (srs != null) {
                String normalized = srs.trim().toUpperCase();
                if ("EPSG:3857".equals(normalized) || "EPSG:900913".equals(normalized) || "EPSG:102100".equals(normalized)) {
                    return true;
                }
            }
        } catch (Throwable ex) {
            log.debug("Could not resolve CRS SRS code", ex);
        }

        try {
            String name = crs.getName() != null ? crs.getName().toString() : crs.toString();
            return name != null && name.toLowerCase().contains("mercator");
        } catch (Throwable ex) {
            return false;
        }
    }

    private boolean isGeographicCrs(CoordinateReferenceSystem crs) {
        if (crs == null) {
            return false;
        }
        try {
            CoordinateReferenceSystem horizontal = CRS.getHorizontalCRS(crs);
            return horizontal instanceof GeographicCRS;
        } catch (Throwable ex) {
            return false;
        }
    }

    /**
     * Removes a layer from the map at the specified index.
     * Layer indices are 0-based, where 0 is the bottom layer.
     * 
     * @param index The index of the layer to remove (0-based)
     * @return true if layer was removed, false if index was invalid
     */
    public boolean removeLayer(int index) {
        if (index < 0 || index >= mapContent.layers().size()) {
            log.warn("Invalid layer index: {}", index);
            return false;
        }
        log.info("Removing layer at index: {}", index);
        Layer layer = mapContent.layers().get(index);
        mapContent.layers().remove(index);
        layerOpacityByLayer.remove(layer);
        vectorLayerColorsByLayer.remove(layer);
        layerStyleValidationPending = true;
        invalidateRenderCaches();
        if (layer instanceof GridReaderLayer readerLayer) {
            GridCoverage2DReader reader = readerLayer.getReader();
            if (reader != null) {
                try {
                    reader.dispose();
                    log.debug("Disposed raster reader for removed layer");
                } catch (Exception ex) {
                    log.warn("Failed to dispose raster reader on layer removal", ex);
                }
            }
        }
        draw();
        return true;
    }

    /**
     * Moves a layer up in the rendering order (towards top/foreground).
     * 
     * @param index The current index of the layer to move up
     * @return true if layer was moved, false if already at top or invalid index
     */
    public boolean moveLayerUp(int index) {
        if (index < 0 || index >= mapContent.layers().size() - 1) {
            log.debug("Cannot move layer up: index={}, size={}", index, mapContent.layers().size());
            return false;
        }
        log.info("Moving layer up: index {} -> {}", index, index + 1);
        mapContent.moveLayer(index, index + 1);
        layerStyleValidationPending = true;
        stableFrameImage = null;
        stableFrameViewport = null;
        draw();
        return true;
    }

    /**
     * Moves a layer down in the rendering order (towards bottom/background).
     * 
     * @param index The current index of the layer to move down
     * @return true if layer was moved, false if already at bottom or invalid index
     */
    public boolean moveLayerDown(int index) {
        if (index <= 0 || index >= mapContent.layers().size()) {
            log.debug("Cannot move layer down: index={}, size={}", index, mapContent.layers().size());
            return false;
        }
        log.info("Moving layer down: index {} -> {}", index, index - 1);
        mapContent.moveLayer(index, index - 1);
        layerStyleValidationPending = true;
        stableFrameImage = null;
        stableFrameViewport = null;
        draw();
        return true;
    }

    /**
     * Gets the total number of layers currently on the map.
     * 
     * @return Number of layers
     */
    public int getLayerCount() {
        return mapContent.layers().size();
    }

    /**
     * Sets the visibility of a layer.
     *
     * @param index layer index (0-based, bottom layer is 0)
     * @param visible true to render the layer, false to hide it
     * @return true when the layer exists and visibility was updated
     */
    public boolean setLayerVisible(int index, boolean visible) {
        if (index < 0 || index >= mapContent.layers().size()) {
            return false;
        }

        Layer layer = mapContent.layers().get(index);
        layer.setVisible(visible);
        invalidateRenderCaches();
        draw();
        return true;
    }

    /**
     * Sets the opacity of a layer.
     *
     * @param index layer index (0-based, bottom layer is 0)
     * @param opacity layer opacity in range [0.0, 1.0]
     * @return true when the layer exists and opacity was updated
     */
    public boolean setLayerOpacity(int index, double opacity) {
        if (index < 0 || index >= mapContent.layers().size()) {
            return false;
        }

        double clampedOpacity = Math.max(0.0, Math.min(1.0, opacity));
        Layer layer = mapContent.layers().get(index);
        layerOpacityByLayer.put(layer, clampedOpacity);
        applyOpacityToLayerStyle(layer, clampedOpacity);
        stableFrameImage = null;
        stableFrameViewport = null;
        draw();
        return true;
    }

    /**
     * Gets the opacity of a layer.
     *
     * @param index layer index (0-based, bottom layer is 0)
     * @return opacity in [0.0, 1.0], or 1.0 for invalid index
     */
    public double getLayerOpacity(int index) {
        if (index < 0 || index >= mapContent.layers().size()) {
            return 1.0;
        }

        Layer layer = mapContent.layers().get(index);
        return layerOpacityByLayer.getOrDefault(layer, 1.0);
    }

    public String getVectorLayerColor(int index) {
        return getVectorLayerBoundaryColor(index);
    }

    public String getVectorLayerFillColor(int index) {
        if (index < 0 || index >= mapContent.layers().size()) {
            return "";
        }

        Layer layer = mapContent.layers().get(index);
        VectorLayerColors colors = vectorLayerColorsByLayer.get(layer);
        return colors == null || colors.fillColor() == null ? "" : colors.fillColor();
    }

    public String getVectorLayerBoundaryColor(int index) {
        if (index < 0 || index >= mapContent.layers().size()) {
            return "";
        }

        Layer layer = mapContent.layers().get(index);
        VectorLayerColors colors = vectorLayerColorsByLayer.get(layer);
        return colors == null || colors.boundaryColor() == null ? "" : colors.boundaryColor();
    }

    public boolean setVectorLayerColor(int index, String hexColor) {
        return setVectorLayerBoundaryColor(index, hexColor);
    }

    public boolean setVectorLayerFillColor(int index, String hexColor) {
        return setVectorLayerColorPart(index, hexColor, true);
    }

    public boolean setVectorLayerBoundaryColor(int index, String hexColor) {
        return setVectorLayerColorPart(index, hexColor, false);
    }

    private boolean setVectorLayerColorPart(int index, String hexColor, boolean fillTarget) {
        if (index < 0 || index >= mapContent.layers().size() || hexColor == null || hexColor.isBlank()) {
            return false;
        }

        Layer layer = mapContent.layers().get(index);
        if (!(layer instanceof FeatureLayer featureLayer)) {
            return false;
        }

        try {
            if (!(featureLayer.getFeatureSource() instanceof SimpleFeatureSource simpleFeatureSource)
                    || simpleFeatureSource.getSchema() == null) {
                return false;
            }

            java.awt.Color color = java.awt.Color.decode(hexColor);
            VectorLayerColors currentColors = vectorLayerColorsByLayer.getOrDefault(layer, new VectorLayerColors("", ""));
            String normalized = normalizeHexColor(color);
            String fillColor = fillTarget ? normalized : currentColors.fillColor();
            String boundaryColor = fillTarget ? currentColors.boundaryColor() : normalized;
            featureLayer.setStyle(createVectorColorStyle(simpleFeatureSource.getSchema(), fillColor, boundaryColor));
            vectorLayerColorsByLayer.put(layer, new VectorLayerColors(
                    fillColor == null ? "" : fillColor,
                    boundaryColor == null ? "" : boundaryColor));
            applyOpacityToLayerStyle(layer, layerOpacityByLayer.getOrDefault(layer, 1.0));
            layerStyleValidationPending = true;
            stableFrameImage = null;
            stableFrameViewport = null;
            draw();
            return true;
        } catch (Exception ex) {
            log.warn("Failed to apply vector {} color {} to layer {}", fillTarget ? "fill" : "boundary", hexColor, layer.getTitle(), ex);
            return false;
        }
    }

    /**
     * Gets the visibility of a layer.
     *
     * @param index layer index (0-based, bottom layer is 0)
     * @return true when visible; false for invalid index or hidden layer
     */
    public boolean isLayerVisible(int index) {
        if (index < 0 || index >= mapContent.layers().size()) {
            return false;
        }

        Layer layer = mapContent.layers().get(index);
        return layer.isVisible();
    }

    /**
     * Applies a style to an existing layer.
     *
     * @param index layer index (0-based)
     * @param style style to apply
     * @return true when layer exists and style was applied
     */
    public boolean setLayerStyle(int index, Style style) {
        if (style == null || index < 0 || index >= mapContent.layers().size()) {
            return false;
        }

        Layer layer = mapContent.layers().get(index);
        if (layer instanceof FeatureLayer featureLayer) {
            featureLayer.setStyle(style);
        } else if (layer instanceof GridReaderLayer readerLayer) {
            readerLayer.setStyle(style);
        } else if (layer instanceof GridCoverageLayer coverageLayer) {
            coverageLayer.setStyle(style);
        } else {
            return false;
        }
        applyOpacityToLayerStyle(layer, layerOpacityByLayer.getOrDefault(layer, 1.0));
        layerStyleValidationPending = true;
        stableFrameImage = null;
        stableFrameViewport = null;
        draw();
        return true;
    }

    private void applyOpacityToLayerStyle(Layer layer, double opacity) {
        if (layer == null) {
            return;
        }

        Style style = null;
        if (layer instanceof FeatureLayer featureLayer) {
            style = featureLayer.getStyle();
        } else if (layer instanceof GridReaderLayer readerLayer) {
            style = readerLayer.getStyle();
        } else if (layer instanceof GridCoverageLayer coverageLayer) {
            style = coverageLayer.getStyle();
        }

        applyOpacityToStyle(style, opacity);
    }

    private void applyOpacityToStyle(Style style, double opacity) {
        if (style == null || style.featureTypeStyles() == null || style.featureTypeStyles().isEmpty()) {
            return;
        }

        StyleBuilder styleBuilder = new StyleBuilder();
        Expression opacityExpression = styleBuilder.literalExpression(Math.max(0.0, Math.min(1.0, opacity)));

        for (var featureTypeStyle : style.featureTypeStyles()) {
            if (featureTypeStyle == null || featureTypeStyle.rules() == null) {
                continue;
            }

            for (Rule rule : featureTypeStyle.rules()) {
                if (rule == null || rule.symbolizers() == null) {
                    continue;
                }

                for (Symbolizer symbolizer : rule.symbolizers()) {
                    if (symbolizer instanceof RasterSymbolizer rasterSymbolizer) {
                        rasterSymbolizer.setOpacity(opacityExpression);
                        continue;
                    }

                    if (symbolizer instanceof PointSymbolizer pointSymbolizer) {
                        Graphic graphic = pointSymbolizer.getGraphic();
                        if (graphic != null) {
                            graphic.setOpacity(opacityExpression);
                        }
                        continue;
                    }

                    if (symbolizer instanceof LineSymbolizer lineSymbolizer) {
                        Stroke stroke = lineSymbolizer.getStroke();
                        if (stroke != null) {
                            stroke.setOpacity(opacityExpression);
                        }
                        continue;
                    }

                    if (symbolizer instanceof PolygonSymbolizer polygonSymbolizer) {
                        Fill fill = polygonSymbolizer.getFill();
                        if (fill != null) {
                            fill.setOpacity(opacityExpression);
                        }
                        Stroke stroke = polygonSymbolizer.getStroke();
                        if (stroke != null) {
                            stroke.setOpacity(opacityExpression);
                        }
                        continue;
                    }

                    if (symbolizer instanceof TextSymbolizer textSymbolizer) {
                        Fill fill = textSymbolizer.getFill();
                        if (fill != null) {
                            fill.setOpacity(opacityExpression);
                        }
                    }
                }
            }
        }
    }

    private Style createVectorColorStyle(SimpleFeatureType schema, String fillHexColor, String boundaryHexColor) {
        StyleBuilder styleBuilder = new StyleBuilder();
        Class<?> geometryBinding = null;
        if (schema != null && schema.getGeometryDescriptor() != null && schema.getGeometryDescriptor().getType() != null) {
            geometryBinding = schema.getGeometryDescriptor().getType().getBinding();
        }

        java.awt.Color fillBase = parseAwtColor(fillHexColor, new java.awt.Color(47, 128, 237));
        java.awt.Color boundaryBase = parseAwtColor(boundaryHexColor, new java.awt.Color(47, 128, 237));
        java.awt.Color fillColor = new java.awt.Color(fillBase.getRed(), fillBase.getGreen(), fillBase.getBlue(), 80);
        java.awt.Color strokeColor = new java.awt.Color(boundaryBase.getRed(), boundaryBase.getGreen(), boundaryBase.getBlue(), 220);

        if (geometryBinding != null && Point.class.isAssignableFrom(geometryBinding)) {
            return SLD.createPointStyle("Circle", fillBase, boundaryBase, 0.85f, 7.0f);
        }
        if (geometryBinding != null && LineString.class.isAssignableFrom(geometryBinding)) {
            return styleBuilder.createStyle(styleBuilder.createLineSymbolizer(strokeColor, 2.0d));
        }
        if (geometryBinding != null && Geometry.class.isAssignableFrom(geometryBinding)) {
            return styleBuilder.createStyle(styleBuilder.createPolygonSymbolizer(
                    styleBuilder.createStroke(strokeColor, 1.7d),
                    styleBuilder.createFill(fillColor)));
        }

        return SLD.createSimpleStyle(schema);
    }

    private java.awt.Color parseAwtColor(String raw, java.awt.Color fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return java.awt.Color.decode(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String normalizeHexColor(java.awt.Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private void ensureRenderableLayerStyles() {
        StyleBuilder styleBuilder = new StyleBuilder();

        for (Layer layer : mapContent.layers()) {
            if (layer instanceof GridReaderLayer readerLayer && !isRenderableStyle(readerLayer.getStyle())) {
                readerLayer.setStyle(styleBuilder.createStyle(styleBuilder.createRasterSymbolizer()));
                log.warn("Recovered missing raster style for layer: {}", readerLayer.getTitle());
            } else if (layer instanceof GridCoverageLayer coverageLayer && !isRenderableStyle(coverageLayer.getStyle())) {
                coverageLayer.setStyle(styleBuilder.createStyle(styleBuilder.createRasterSymbolizer()));
                log.warn("Recovered missing raster style for layer: {}", coverageLayer.getTitle());
            } else if (layer instanceof FeatureLayer featureLayer && !isRenderableStyle(featureLayer.getStyle())) {
                try {
                    var source = featureLayer.getFeatureSource();
                    if (source instanceof SimpleFeatureSource simpleFeatureSource && simpleFeatureSource.getSchema() != null) {
                        featureLayer.setStyle(SLD.createSimpleStyle(simpleFeatureSource.getSchema()));
                    } else {
                        featureLayer.setStyle(createFallbackFeatureStyle(featureLayer));
                    }
                    log.warn("Recovered missing vector style for layer: {}", featureLayer.getTitle());
                } catch (Throwable ex) {
                    try {
                        featureLayer.setStyle(createFallbackFeatureStyle(featureLayer));
                        log.warn("Recovered vector style via fallback for layer: {}", featureLayer.getTitle());
                    } catch (Throwable fallbackEx) {
                        log.warn("Could not recover missing vector style for layer: {}", featureLayer.getTitle(), fallbackEx);
                    }
                }
            }
            applyOpacityToLayerStyle(layer, layerOpacityByLayer.getOrDefault(layer, 1.0));
        }
    }

    private Style createFallbackFeatureStyle(FeatureLayer featureLayer) {
        StyleBuilder styleBuilder = new StyleBuilder();
        Class<?> geometryBinding = null;

        try {
            var source = featureLayer.getFeatureSource();
            if (source != null && source.getSchema() != null) {
                GeometryDescriptor geometryDescriptor = source.getSchema().getGeometryDescriptor();
                if (geometryDescriptor != null && geometryDescriptor.getType() != null) {
                    geometryBinding = geometryDescriptor.getType().getBinding();
                }
            }
        } catch (Throwable ignored) {
            // Fallback below handles unknown geometry type.
        }

        if (geometryBinding != null && Point.class.isAssignableFrom(geometryBinding)) {
            return styleBuilder.createStyle(styleBuilder.createPointSymbolizer());
        }
        if (geometryBinding != null && LineString.class.isAssignableFrom(geometryBinding)) {
            return styleBuilder.createStyle(styleBuilder.createLineSymbolizer());
        }

        return styleBuilder.createStyle(styleBuilder.createPolygonSymbolizer());
    }

    private boolean isRenderableStyle(Style style) {
        if (style == null || style.featureTypeStyles() == null || style.featureTypeStyles().isEmpty()) {
            return false;
        }

        for (var featureTypeStyle : style.featureTypeStyles()) {
            if (featureTypeStyle == null || featureTypeStyle.rules() == null || featureTypeStyle.rules().isEmpty()) {
                continue;
            }
            return true;
        }

        return false;
    }

    public void setEditMode(EditMode mode) {
        if (mode == null) {
            mode = EditMode.PAN;
        }
        this.editMode = mode;
        if (mode == EditMode.PAN || mode == EditMode.SELECT || mode == EditMode.DELETE || mode == EditMode.DRAW_POINT || mode == EditMode.MOVE_VERTEX) {
            activeSketch.clear();
            invalidateSnapCandidateCaches();
        }
        if (mode != EditMode.MOVE_VERTEX) {
            activeVertexMove = null;
            vertexMoveChanged = false;
        }
        draw();
    }

    public EditMode getEditMode() {
        return editMode;
    }

    public void clearSketch() {
        activeSketch.clear();
        draw();
    }

    public boolean finishSketch() {
        if (editMode == EditMode.DRAW_LINE && activeSketch.size() >= 2) {
            LineString line = geometryFactory.createLineString(activeSketch.toArray(new Coordinate[0]));
            if (!isLineTopologySafe(line)) {
                return false;
            }
            int id = nextDigitizedFeatureId++;
            FeatureAttributes attributes = new FeatureAttributes(id, "line-" + id, "");
            digitizedLines.add(line);
            lineAttributes.add(attributes);
            undoStack.push(() -> {
                int i = digitizedLines.lastIndexOf(line);
                if (i >= 0) {
                    digitizedLines.remove(i);
                    lineAttributes.remove(i);
                    invalidateSnapCandidateCaches();
                }
            });
            selectedFeature = new FeatureRef(FeatureKind.LINE, digitizedLines.size() - 1);
            notifySelectedFeature();
            activeSketch.clear();
            invalidateSnapCandidateCaches();
            draw();
            return true;
        }

        if (editMode == EditMode.DRAW_POLYGON && activeSketch.size() >= 3) {
            Coordinate[] shell = new Coordinate[activeSketch.size() + 1];
            for (int i = 0; i < activeSketch.size(); i++) {
                shell[i] = activeSketch.get(i);
            }
            shell[shell.length - 1] = activeSketch.get(0);
            Polygon polygon = geometryFactory.createPolygon(shell);
            if (isPolygonTopologySafe(polygon)) {
                int id = nextDigitizedFeatureId++;
                FeatureAttributes attributes = new FeatureAttributes(id, "polygon-" + id, "");
                digitizedPolygons.add(polygon);
                polygonAttributes.add(attributes);
                undoStack.push(() -> {
                    int i = digitizedPolygons.lastIndexOf(polygon);
                    if (i >= 0) {
                        digitizedPolygons.remove(i);
                        polygonAttributes.remove(i);
                        invalidateSnapCandidateCaches();
                    }
                });
                selectedFeature = new FeatureRef(FeatureKind.POLYGON, digitizedPolygons.size() - 1);
                notifySelectedFeature();
                activeSketch.clear();
                invalidateSnapCandidateCaches();
                draw();
                return true;
            }
        }

        return false;
    }

    public int getDigitizedFeatureCount() {
        return digitizedPoints.size() + digitizedLines.size() + digitizedPolygons.size();
    }

    public String serializeDigitizedFeatures() {
        List<String> entries = new ArrayList<>();

        for (int i = 0; i < digitizedPoints.size(); i++) {
            FeatureAttributes attrs = pointAttributes.get(i);
            entries.add(encodeDigitizedEntry("POINT", attrs.id, attrs.name, attrs.notes, digitizedPoints.get(i).toText()));
        }
        for (int i = 0; i < digitizedLines.size(); i++) {
            FeatureAttributes attrs = lineAttributes.get(i);
            entries.add(encodeDigitizedEntry("LINE", attrs.id, attrs.name, attrs.notes, digitizedLines.get(i).toText()));
        }
        for (int i = 0; i < digitizedPolygons.size(); i++) {
            FeatureAttributes attrs = polygonAttributes.get(i);
            entries.add(encodeDigitizedEntry("POLYGON", attrs.id, attrs.name, attrs.notes, digitizedPolygons.get(i).toText()));
        }

        if (entries.isEmpty()) {
            return "";
        }
        return String.join(";", entries);
    }

    public int restoreDigitizedFeatures(String encodedState) {
        digitizedPoints.clear();
        pointAttributes.clear();
        digitizedLines.clear();
        lineAttributes.clear();
        digitizedPolygons.clear();
        polygonAttributes.clear();
        activeSketch.clear();
        undoStack.clear();
        selectedFeature = null;

        if (encodedState == null || encodedState.isBlank()) {
            nextDigitizedFeatureId = 1;
            notifySelectedFeature();
            draw();
            return 0;
        }

        int restored = 0;
        int maxId = 0;
        WKTReader wktReader = new WKTReader(geometryFactory);
        String[] entries = encodedState.split(";");
        for (String entry : entries) {
            RestoredDigitizedFeature feature = decodeDigitizedEntry(entry);
            if (feature == null) {
                continue;
            }

            try {
                Geometry geometry = wktReader.read(feature.wkt());
                FeatureAttributes attributes = new FeatureAttributes(feature.id(), feature.name(), feature.notes());
                switch (feature.kind()) {
                    case "POINT" -> {
                        if (geometry instanceof Point point) {
                            digitizedPoints.add(point);
                            pointAttributes.add(attributes);
                            restored++;
                        }
                    }
                    case "LINE" -> {
                        if (geometry instanceof LineString line) {
                            digitizedLines.add(line);
                            lineAttributes.add(attributes);
                            restored++;
                        }
                    }
                    case "POLYGON" -> {
                        if (geometry instanceof Polygon polygon) {
                            digitizedPolygons.add(polygon);
                            polygonAttributes.add(attributes);
                            restored++;
                        }
                    }
                    default -> {
                        // Skip unsupported kinds.
                    }
                }
                if (feature.id() > maxId) {
                    maxId = feature.id();
                }
            } catch (ParseException ex) {
                log.warn("Skipping invalid persisted digitized geometry");
            }
        }

        nextDigitizedFeatureId = Math.max(maxId + 1, 1);
        notifySelectedFeature();
        draw();
        return restored;
    }

    private String encodeDigitizedEntry(String kind, int id, String name, String notes, String wkt) {
        String payload = kind + "\n"
                + id + "\n"
                + encodeText(name) + "\n"
                + encodeText(notes) + "\n"
                + encodeText(wkt);
        return Base64.getUrlEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private RestoredDigitizedFeature decodeDigitizedEntry(String encodedEntry) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(encodedEntry), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\n", 5);
            if (parts.length != 5) {
                return null;
            }

            int id = Integer.parseInt(parts[1]);
            String name = decodeText(parts[2]);
            String notes = decodeText(parts[3]);
            String wkt = decodeText(parts[4]);
            if (wkt == null || wkt.isBlank()) {
                return null;
            }
            return new RestoredDigitizedFeature(parts[0], id, name, notes, wkt);
        } catch (IllegalArgumentException ex) {
            log.warn("Skipping invalid persisted digitized entry");
            return null;
        }
    }

    private String encodeText(String text) {
        String value = text == null ? "" : text;
        return Base64.getUrlEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeText(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }
        return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private record RestoredDigitizedFeature(String kind, int id, String name, String notes, String wkt) {
    }

    public void setSelectedFeatureListener(SelectedFeatureListener selectedFeatureListener) {
        this.selectedFeatureListener = selectedFeatureListener;
        notifySelectedFeature();
    }

    public boolean setSnapEndpointEnabled(boolean enabled) {
        snappingManager.setEndpointMode(enabled);
        return true;
    }

    public boolean setSnapIntersectionEnabled(boolean enabled) {
        snappingManager.setIntersectionMode(enabled);
        return true;
    }

    public void setSnapToleranceRatio(double toleranceRatio) {
        snappingManager.setToleranceRatio(toleranceRatio);
    }

    public boolean isSnapEndpointEnabled() {
        return snappingManager.isEndpointMode();
    }

    public boolean isSnapIntersectionEnabled() {
        return snappingManager.isIntersectionMode();
    }

    public double getSnapToleranceRatio() {
        return snappingManager.getToleranceRatio();
    }

    public boolean updateSelectedFeatureAttribute(String attributeKey, String value) {
        if (selectedFeature == null || attributeKey == null) {
            return false;
        }

        FeatureAttributes attributes = getAttributes(selectedFeature);
        if (attributes == null) {
            return false;
        }

        String normalized = attributeKey.trim().toLowerCase();
        if ("name".equals(normalized)) {
            String previous = attributes.name;
            attributes.name = value == null ? "" : value;
            undoStack.push(() -> {
                attributes.name = previous;
                notifySelectedFeature();
            });
        } else if ("notes".equals(normalized)) {
            String previous = attributes.notes;
            attributes.notes = value == null ? "" : value;
            undoStack.push(() -> {
                attributes.notes = previous;
                notifySelectedFeature();
            });
        } else {
            return false;
        }

        notifySelectedFeature();
        return true;
    }

    public boolean undoLastEdit() {
        if (!activeSketch.isEmpty() && (editMode == EditMode.DRAW_LINE || editMode == EditMode.DRAW_POLYGON)) {
            activeSketch.remove(activeSketch.size() - 1);
            draw();
            return true;
        }

        Runnable undoAction = undoStack.pollFirst();
        if (undoAction == null) {
            return false;
        }

        undoAction.run();
        draw();
        return true;
    }

    private FeatureAttributes getAttributes(FeatureRef ref) {
        if (ref == null) {
            return null;
        }

        return switch (ref.kind()) {
            case POINT -> (ref.index() >= 0 && ref.index() < pointAttributes.size()) ? pointAttributes.get(ref.index()) : null;
            case LINE -> (ref.index() >= 0 && ref.index() < lineAttributes.size()) ? lineAttributes.get(ref.index()) : null;
            case POLYGON -> (ref.index() >= 0 && ref.index() < polygonAttributes.size()) ? polygonAttributes.get(ref.index()) : null;
        };
    }

    private Geometry getGeometry(FeatureRef ref) {
        if (ref == null) {
            return null;
        }

        return switch (ref.kind()) {
            case POINT -> (ref.index() >= 0 && ref.index() < digitizedPoints.size()) ? digitizedPoints.get(ref.index()) : null;
            case LINE -> (ref.index() >= 0 && ref.index() < digitizedLines.size()) ? digitizedLines.get(ref.index()) : null;
            case POLYGON -> (ref.index() >= 0 && ref.index() < digitizedPolygons.size()) ? digitizedPolygons.get(ref.index()) : null;
        };
    }

    private SelectedFeature buildSelectedFeatureSnapshot() {
        if (selectedFeature == null) {
            return null;
        }

        Geometry geometry = getGeometry(selectedFeature);
        FeatureAttributes attributes = getAttributes(selectedFeature);
        if (geometry == null || attributes == null) {
            return null;
        }

        Point centroid = geometry.getCentroid();
        return new SelectedFeature(
                attributes.id,
                selectedFeature.kind().name(),
                centroid.getX(),
                centroid.getY(),
                attributes.name,
                attributes.notes);
    }

    private void notifySelectedFeature() {
        if (selectedFeatureListener == null) {
            return;
        }
        selectedFeatureListener.onFeatureSelected(buildSelectedFeatureSnapshot());
    }

    /**
     * Returns the vector feature source for a given layer index, if the layer is a vector layer.
     *
     * @param index map layer index
     * @return SimpleFeatureSource or null when layer is not vector/accessible
     */
    public SimpleFeatureSource getVectorFeatureSource(int index) {
        if (index < 0 || index >= mapContent.layers().size()) {
            return null;
        }

        Layer layer = mapContent.layers().get(index);
        if (!(layer instanceof FeatureLayer featureLayer)) {
            return null;
        }

        FeatureSource<?, ?> source = featureLayer.getFeatureSource();
        if (source instanceof SimpleFeatureSource simpleFeatureSource) {
            return simpleFeatureSource;
        }
        return null;
    }

    public SimpleFeatureSource getDigitizedFeatureSource(DigitizedExportKind exportKind) {
        if (exportKind == null) {
            return null;
        }

        return switch (exportKind) {
            case POINTS -> buildDigitizedPointSource();
            case LINES -> buildDigitizedLineSource();
            case POLYGONS -> buildDigitizedPolygonSource();
        };
    }

    public boolean hasDigitizedFeatures(DigitizedExportKind exportKind) {
        if (exportKind == null) {
            return false;
        }
        return switch (exportKind) {
            case POINTS -> !digitizedPoints.isEmpty();
            case LINES -> !digitizedLines.isEmpty();
            case POLYGONS -> !digitizedPolygons.isEmpty();
        };
    }

    private SimpleFeatureSource buildDigitizedPointSource() {
        if (digitizedPoints.isEmpty()) {
            return null;
        }

        SimpleFeatureType featureType = createDigitizedFeatureType("digitized_points", Point.class);
        List<SimpleFeature> features = new ArrayList<>(digitizedPoints.size());
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(featureType);

        for (int i = 0; i < digitizedPoints.size(); i++) {
            FeatureAttributes attrs = pointAttributes.get(i);
            builder.add(digitizedPoints.get(i));
            builder.add(attrs.id);
            builder.add(attrs.name);
            builder.add(attrs.notes);
            features.add(builder.buildFeature("digitized-point-" + attrs.id));
            builder.reset();
        }

        SimpleFeatureCollection collection = new ListFeatureCollection(featureType, features);
        return new CollectionFeatureSource(collection);
    }

    private SimpleFeatureSource buildDigitizedLineSource() {
        if (digitizedLines.isEmpty()) {
            return null;
        }

        SimpleFeatureType featureType = createDigitizedFeatureType("digitized_lines", LineString.class);
        List<SimpleFeature> features = new ArrayList<>(digitizedLines.size());
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(featureType);

        for (int i = 0; i < digitizedLines.size(); i++) {
            FeatureAttributes attrs = lineAttributes.get(i);
            builder.add(digitizedLines.get(i));
            builder.add(attrs.id);
            builder.add(attrs.name);
            builder.add(attrs.notes);
            features.add(builder.buildFeature("digitized-line-" + attrs.id));
            builder.reset();
        }

        SimpleFeatureCollection collection = new ListFeatureCollection(featureType, features);
        return new CollectionFeatureSource(collection);
    }

    private SimpleFeatureSource buildDigitizedPolygonSource() {
        if (digitizedPolygons.isEmpty()) {
            return null;
        }

        SimpleFeatureType featureType = createDigitizedFeatureType("digitized_polygons", Polygon.class);
        List<SimpleFeature> features = new ArrayList<>(digitizedPolygons.size());
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(featureType);

        for (int i = 0; i < digitizedPolygons.size(); i++) {
            FeatureAttributes attrs = polygonAttributes.get(i);
            builder.add(digitizedPolygons.get(i));
            builder.add(attrs.id);
            builder.add(attrs.name);
            builder.add(attrs.notes);
            features.add(builder.buildFeature("digitized-polygon-" + attrs.id));
            builder.reset();
        }

        SimpleFeatureCollection collection = new ListFeatureCollection(featureType, features);
        return new CollectionFeatureSource(collection);
    }

    private SimpleFeatureType createDigitizedFeatureType(String typeName, Class<?> geometryClass) {
        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName(typeName);
        if (mapContent != null && mapContent.getViewport() != null && mapContent.getViewport().getCoordinateReferenceSystem() != null) {
            typeBuilder.setCRS(mapContent.getViewport().getCoordinateReferenceSystem());
        }
        typeBuilder.add("the_geom", geometryClass);
        typeBuilder.add("id", Integer.class);
        typeBuilder.add("name", String.class);
        typeBuilder.add("notes", String.class);
        return typeBuilder.buildFeatureType();
    }

    /**
     * Triggers a redraw of the map canvas using the GeoTools renderer.
     */
    public void draw() {
        draw(false);
    }

    private void draw(boolean interactivePass) {
        long frameStart = System.nanoTime();
        double width = getWidth();
        double height = getHeight();

        if (width <= 0 || height <= 0 || mapContent == null) {
            return;
        }

        GraphicsContext gc = getGraphicsContext2D();
        prepareCanvasForFreshFrame(gc, width, height);

        // Map viewport needs to match canvas aspect ratio and bounds
        MapViewport viewport = mapContent.getViewport();
        if (viewport.getBounds() == null || viewport.getBounds().isEmpty()) {
            try {
                ReferencedEnvelope fallbackBounds = mapContent.getMaxBounds();
                if (isUsableBounds(fallbackBounds)) {
                    setViewportBounds(fallbackBounds, "draw fallback bounds");
                }
            } catch (Throwable ex) {
                log.warn("Could not initialize viewport bounds during draw", ex);
            }
            if (viewport.getBounds() == null || viewport.getBounds().isEmpty()) {
                renderCanvasMessage(gc, width, height,
                        "Map is ready",
                        "Open a vector or raster layer to display content.");
                return;
            }
        }

        Rectangle paintArea = new Rectangle(0, 0,
                Math.max(1, (int) Math.ceil(width)),
                Math.max(1, (int) Math.ceil(height)));
        viewport.setScreenArea(paintArea);

        fxgraphics = new FXGraphics2D(gc);

        // Apply rendering
        fxgraphics.setBackground(Color.WHITE);
        fxgraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        fxgraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

        try {
            ReferencedEnvelope viewportBounds = viewport.getBounds();
            boolean previewRendered = interactivePass && renderInteractivePreview(gc, viewportBounds);

            if (!previewRendered) {
                if (layerStyleValidationPending) {
                    ensureRenderableLayerStyles();
                    layerStyleValidationPending = false;
                }
                renderer.paint(fxgraphics, paintArea, viewportBounds);
            }

            renderDigitizingOverlay(gc, viewportBounds, interactivePass);

            if (!interactivePass && !previewRendered) {
                captureStableFrame(viewportBounds);
            }
        } catch (Throwable ex) {
            log.error("Error rendering map canvas", ex);
            renderCanvasMessage(gc, width, height,
                    "Map rendering issue",
                    "Rendering recovered in safe mode. Reopen project if needed.");
        } finally {
            long frameNanos = System.nanoTime() - frameStart;
            if (frameNanos > SLOW_FRAME_WARN_NANOS) {
                log.debug("Slow render frame: {} ms (interactive={})", frameNanos / 1_000_000.0, interactivePass);
            }
        }
    }

    private void renderCanvasMessage(GraphicsContext gc, double width, double height, String title, String detail) {
        prepareCanvasForFreshFrame(gc, width, height);

        gc.setFill(javafx.scene.paint.Color.web("#2f4f4f"));
        gc.setFont(javafx.scene.text.Font.font("Segoe UI", 18));
        gc.fillText(title, 20, Math.max(40, height * 0.5 - 10));

        gc.setFill(javafx.scene.paint.Color.web("#5f6f6f"));
        gc.setFont(javafx.scene.text.Font.font("Segoe UI", 13));
        gc.fillText(detail, 20, Math.max(64, height * 0.5 + 16));
    }

    private void prepareCanvasForFreshFrame(GraphicsContext gc, double width, double height) {
        gc.setTransform(1, 0, 0, 1, 0, 0);
        gc.setGlobalAlpha(1.0);
        gc.setGlobalBlendMode(javafx.scene.effect.BlendMode.SRC_OVER);
        gc.setEffect((Effect) null);
        gc.setLineDashes(null);
        gc.clearRect(0, 0, width, height);
        gc.setFill(javafx.scene.paint.Color.rgb(240, 240, 240));
        gc.fillRect(0, 0, width, height);
    }

    private boolean renderInteractivePreview(GraphicsContext gc, ReferencedEnvelope currentViewport) {
        if (stableFrameImage == null || stableFrameViewport == null || !isUsableBounds(currentViewport) || !isUsableBounds(stableFrameViewport)) {
            return false;
        }

        double currentWidth = currentViewport.getWidth();
        double currentHeight = currentViewport.getHeight();
        if (!Double.isFinite(currentWidth) || !Double.isFinite(currentHeight) || currentWidth <= 0 || currentHeight <= 0) {
            return false;
        }

        double scaleX = stableFrameViewport.getWidth() / currentWidth;
        double scaleY = stableFrameViewport.getHeight() / currentHeight;
        if (!Double.isFinite(scaleX) || !Double.isFinite(scaleY) || scaleX <= 0 || scaleY <= 0) {
            return false;
        }

        double tx = ((stableFrameViewport.getMinX() - currentViewport.getMinX()) / currentWidth) * getWidth();
        double ty = ((currentViewport.getMaxY() - stableFrameViewport.getMaxY()) / currentHeight) * getHeight();
        if (!Double.isFinite(tx) || !Double.isFinite(ty)) {
            return false;
        }

        double drawW = getWidth() * scaleX;
        double drawH = getHeight() * scaleY;
        if (!Double.isFinite(drawW) || !Double.isFinite(drawH) || drawW <= 0 || drawH <= 0) {
            return false;
        }

        gc.drawImage(stableFrameImage, tx, ty, drawW, drawH);
        return true;
    }

    private void captureStableFrame(ReferencedEnvelope viewportBounds) {
        long now = System.nanoTime();
        long captureIntervalNanos = isLowMemoryMode() ? 500_000_000L : STABLE_FRAME_CAPTURE_INTERVAL_NANOS;
        if (now - lastStableCaptureNanos < captureIntervalNanos) {
            return;
        }

        int w = Math.max(1, (int) Math.round(getWidth()));
        int h = Math.max(1, (int) Math.round(getHeight()));

        if (stableFrameImage == null || stableFrameImage.getWidth() != w || stableFrameImage.getHeight() != h) {
            stableFrameImage = new WritableImage(w, h);
        }

        snapshot(null, stableFrameImage);
        stableFrameViewport = new ReferencedEnvelope(viewportBounds);
        lastStableCaptureNanos = now;
    }

    private void drawInteractive() {
        long now = System.nanoTime();
        long throttleNanos = isLowMemoryMode() ? 45_000_000L : INTERACTIVE_DRAW_INTERVAL_NANOS;
        if (now - lastInteractiveDrawNanos < throttleNanos) {
            return;
        }
        lastInteractiveDrawNanos = now;
        draw(true);
    }

    private void resetInteractiveDrawThrottle() {
        lastInteractiveDrawNanos = 0L;
    }

    private boolean isLowMemoryMode() {
        return Runtime.getRuntime().maxMemory() <= LOW_MEMORY_MODE_MAX_HEAP_BYTES;
    }

    private void scheduleHighQualityRedraw() {
        interactionSettleRedraw.playFromStart();
    }

    private void renderDigitizingOverlay(GraphicsContext gc, ReferencedEnvelope env, boolean interactivePass) {
        if (!isUsableBounds(env)) {
            return;
        }

        gc.setLineWidth(interactivePass ? 1.5 : 2.0);

        int pointStep = interactivePass && digitizedPoints.size() > 3000 ? 3 : 1;
        int lineStep = interactivePass && digitizedLines.size() > 1200 ? 2 : 1;
        int polygonStep = interactivePass && digitizedPolygons.size() > 800 ? 2 : 1;

        gc.setStroke(javafx.scene.paint.Color.web("#1267b2"));
        gc.setFill(javafx.scene.paint.Color.web("#3da5ff", 0.9));
        for (int i = 0; i < digitizedPoints.size(); i += pointStep) {
            Point p = digitizedPoints.get(i);
            double sx = toScreenX(p.getX(), env);
            double sy = toScreenY(p.getY(), env);
            gc.fillOval(sx - 4, sy - 4, 8, 8);
        }

        gc.setStroke(javafx.scene.paint.Color.web("#f28f16"));
        for (int i = 0; i < digitizedLines.size(); i += lineStep) {
            LineString line = digitizedLines.get(i);
            drawLineString(gc, line.getCoordinates(), env);
        }

        gc.setStroke(javafx.scene.paint.Color.web("#d33a2c"));
        gc.setFill(javafx.scene.paint.Color.web("#ff5f52", 0.25));
        for (int i = 0; i < digitizedPolygons.size(); i += polygonStep) {
            Polygon poly = digitizedPolygons.get(i);
            if (interactivePass) {
                drawLineString(gc, poly.getExteriorRing().getCoordinates(), env);
            } else {
                drawPolygon(gc, poly.getExteriorRing().getCoordinates(), env);
            }
        }

        if ((editMode == EditMode.DRAW_LINE || editMode == EditMode.DRAW_POLYGON) && !activeSketch.isEmpty()) {
            gc.setStroke(javafx.scene.paint.Color.web("#16a34a"));
            gc.setLineDashes(6, 4);
            drawLineString(gc, activeSketch.toArray(new Coordinate[0]), env);
            gc.setLineDashes(null);

            gc.setFill(javafx.scene.paint.Color.web("#16a34a"));
            for (Coordinate c : activeSketch) {
                double sx = toScreenX(c.x, env);
                double sy = toScreenY(c.y, env);
                gc.fillOval(sx - 3, sy - 3, 6, 6);
            }
        }

        if (selectedFeature != null) {
            gc.setStroke(javafx.scene.paint.Color.web("#00c2ff"));
            gc.setLineWidth(3.0);
            if (selectedFeature.kind() == FeatureKind.POINT && selectedFeature.index() < digitizedPoints.size()) {
                Point p = digitizedPoints.get(selectedFeature.index());
                double sx = toScreenX(p.getX(), env);
                double sy = toScreenY(p.getY(), env);
                gc.strokeOval(sx - 8, sy - 8, 16, 16);
            } else if (selectedFeature.kind() == FeatureKind.LINE && selectedFeature.index() < digitizedLines.size()) {
                drawLineString(gc, digitizedLines.get(selectedFeature.index()).getCoordinates(), env);
            } else if (selectedFeature.kind() == FeatureKind.POLYGON && selectedFeature.index() < digitizedPolygons.size()) {
                drawPolygon(gc, digitizedPolygons.get(selectedFeature.index()).getExteriorRing().getCoordinates(), env);
            }
            gc.setLineWidth(2.0);
        }
    }

    private void drawLineString(GraphicsContext gc, Coordinate[] coords, ReferencedEnvelope env) {
        if (coords == null || coords.length < 2) {
            return;
        }
        for (int i = 0; i < coords.length - 1; i++) {
            double x1 = toScreenX(coords[i].x, env);
            double y1 = toScreenY(coords[i].y, env);
            double x2 = toScreenX(coords[i + 1].x, env);
            double y2 = toScreenY(coords[i + 1].y, env);
            gc.strokeLine(x1, y1, x2, y2);
        }
    }

    private void drawPolygon(GraphicsContext gc, Coordinate[] coords, ReferencedEnvelope env) {
        if (coords == null || coords.length < 4) {
            return;
        }
        double[] xs = new double[coords.length];
        double[] ys = new double[coords.length];
        for (int i = 0; i < coords.length; i++) {
            xs[i] = toScreenX(coords[i].x, env);
            ys[i] = toScreenY(coords[i].y, env);
        }
        gc.fillPolygon(xs, ys, coords.length);
        gc.strokePolygon(xs, ys, coords.length);
    }

    private double toScreenX(double worldX, ReferencedEnvelope env) {
        return ((worldX - env.getMinX()) / env.getWidth()) * getWidth();
    }

    private double toScreenY(double worldY, ReferencedEnvelope env) {
        return ((env.getMaxY() - worldY) / env.getHeight()) * getHeight();
    }

    private Coordinate toWorldCoordinate(double screenX, double screenY, ReferencedEnvelope env) {
        double worldX = env.getMinX() + (screenX / getWidth()) * env.getWidth();
        double worldY = env.getMaxY() - (screenY / getHeight()) * env.getHeight();
        return new Coordinate(worldX, worldY);
    }

    private void handleDigitizingClick(MouseEvent ev) {
        if (mapContent == null || mapContent.getViewport() == null) {
            return;
        }

        ReferencedEnvelope env = mapContent.getViewport().getBounds();
        if (!isUsableBounds(env)) {
            return;
        }

        Coordinate world = toWorldCoordinate(ev.getX(), ev.getY(), env);
        if (editMode == EditMode.DRAW_POINT || editMode == EditMode.DRAW_LINE || editMode == EditMode.DRAW_POLYGON) {
            world = snapCoordinate(world, env);
        }

        switch (editMode) {
            case DRAW_POINT -> {
                Point point = geometryFactory.createPoint(world);
                int id = nextDigitizedFeatureId++;
                FeatureAttributes attributes = new FeatureAttributes(id, "point-" + id, "");
                digitizedPoints.add(point);
                pointAttributes.add(attributes);
                invalidateSnapCandidateCaches();
                undoStack.push(() -> {
                    int i = digitizedPoints.lastIndexOf(point);
                    if (i >= 0) {
                        digitizedPoints.remove(i);
                        pointAttributes.remove(i);
                        invalidateSnapCandidateCaches();
                    }
                });
                selectedFeature = new FeatureRef(FeatureKind.POINT, digitizedPoints.size() - 1);
                notifySelectedFeature();
            }
            case DRAW_LINE -> {
                activeSketch.add(world);
                if (ev.getClickCount() >= 2) {
                    finishSketch();
                } else {
                    invalidateSnapCandidateCaches();
                    drawInteractive();
                    scheduleHighQualityRedraw();
                }
                return;
            }
            case DRAW_POLYGON -> {
                activeSketch.add(world);
                if (ev.getClickCount() >= 2) {
                    finishSketch();
                } else {
                    invalidateSnapCandidateCaches();
                    drawInteractive();
                    scheduleHighQualityRedraw();
                }
                return;
            }
            case DELETE -> deleteNearestFeature(world, env);
            case SELECT -> selectNearestFeature(world, env);
            case MOVE_VERTEX -> {
                return;
            }
            case PAN -> {
                return;
            }
        }

        draw();
    }

    private void deleteNearestFeature(Coordinate world, ReferencedEnvelope env) {
        Point clickPoint = geometryFactory.createPoint(world);
        double threshold = Math.max(env.getWidth(), env.getHeight()) * 0.02;

        FeatureRef nearest = findNearestFeature(clickPoint, threshold);
        if (nearest == null) {
            return;
        }

        removeFeature(nearest, true);
    }

    private void selectNearestFeature(Coordinate world, ReferencedEnvelope env) {
        Point clickPoint = geometryFactory.createPoint(world);
        double threshold = Math.max(env.getWidth(), env.getHeight()) * 0.02;
        selectedFeature = findNearestFeature(clickPoint, threshold);
        notifySelectedFeature();
    }

    private FeatureRef findNearestFeature(Point clickPoint, double threshold) {
        double bestDistance = Double.MAX_VALUE;
        FeatureRef bestRef = null;

        for (int i = 0; i < digitizedPoints.size(); i++) {
            double d = digitizedPoints.get(i).distance(clickPoint);
            if (d < bestDistance) {
                bestDistance = d;
                bestRef = new FeatureRef(FeatureKind.POINT, i);
            }
        }

        for (int i = 0; i < digitizedLines.size(); i++) {
            double d = digitizedLines.get(i).distance(clickPoint);
            if (d < bestDistance) {
                bestDistance = d;
                bestRef = new FeatureRef(FeatureKind.LINE, i);
            }
        }

        for (int i = 0; i < digitizedPolygons.size(); i++) {
            double d = digitizedPolygons.get(i).distance(clickPoint);
            if (d < bestDistance) {
                bestDistance = d;
                bestRef = new FeatureRef(FeatureKind.POLYGON, i);
            }
        }

        return bestDistance <= threshold ? bestRef : null;
    }

    private void removeFeature(FeatureRef featureRef, boolean trackUndo) {
        if (featureRef == null) {
            return;
        }

        if (featureRef.kind() == FeatureKind.POINT) {
            if (featureRef.index() < 0 || featureRef.index() >= digitizedPoints.size()) {
                return;
            }
            Point removedGeometry = digitizedPoints.remove(featureRef.index());
            FeatureAttributes removedAttributes = pointAttributes.remove(featureRef.index());
            int restoreIndex = featureRef.index();
            if (trackUndo) {
                undoStack.push(() -> {
                    int index = Math.min(restoreIndex, digitizedPoints.size());
                    digitizedPoints.add(index, removedGeometry);
                    pointAttributes.add(index, removedAttributes);
                    invalidateSnapCandidateCaches();
                });
            }
        } else if (featureRef.kind() == FeatureKind.LINE) {
            if (featureRef.index() < 0 || featureRef.index() >= digitizedLines.size()) {
                return;
            }
            LineString removedGeometry = digitizedLines.remove(featureRef.index());
            FeatureAttributes removedAttributes = lineAttributes.remove(featureRef.index());
            int restoreIndex = featureRef.index();
            if (trackUndo) {
                undoStack.push(() -> {
                    int index = Math.min(restoreIndex, digitizedLines.size());
                    digitizedLines.add(index, removedGeometry);
                    lineAttributes.add(index, removedAttributes);
                    invalidateSnapCandidateCaches();
                });
            }
        } else {
            if (featureRef.index() < 0 || featureRef.index() >= digitizedPolygons.size()) {
                return;
            }
            Polygon removedGeometry = digitizedPolygons.remove(featureRef.index());
            FeatureAttributes removedAttributes = polygonAttributes.remove(featureRef.index());
            int restoreIndex = featureRef.index();
            if (trackUndo) {
                undoStack.push(() -> {
                    int index = Math.min(restoreIndex, digitizedPolygons.size());
                    digitizedPolygons.add(index, removedGeometry);
                    polygonAttributes.add(index, removedAttributes);
                    invalidateSnapCandidateCaches();
                });
            }
        }

        invalidateSnapCandidateCaches();

        if (selectedFeature != null && selectedFeature.kind() == featureRef.kind()) {
            if (selectedFeature.index() == featureRef.index()) {
                selectedFeature = null;
            } else if (selectedFeature.index() > featureRef.index()) {
                selectedFeature = new FeatureRef(selectedFeature.kind(), selectedFeature.index() - 1);
            }
            notifySelectedFeature();
        }
    }

    private VertexRef findNearestVertex(Coordinate world, ReferencedEnvelope env) {
        double threshold = Math.max(env.getWidth(), env.getHeight()) * 0.02;
        double bestDistance = Double.MAX_VALUE;
        VertexRef best = null;

        for (int i = 0; i < digitizedPoints.size(); i++) {
            Coordinate c = digitizedPoints.get(i).getCoordinate();
            double d = c.distance(world);
            if (d < bestDistance) {
                bestDistance = d;
                best = new VertexRef(VertexKind.POINT, i, 0, new Coordinate(c.x, c.y));
            }
        }

        for (int i = 0; i < digitizedLines.size(); i++) {
            Coordinate[] coords = digitizedLines.get(i).getCoordinates();
            for (int v = 0; v < coords.length; v++) {
                double d = coords[v].distance(world);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = new VertexRef(VertexKind.LINE, i, v, new Coordinate(coords[v].x, coords[v].y));
                }
            }
        }

        for (int i = 0; i < digitizedPolygons.size(); i++) {
            Coordinate[] coords = digitizedPolygons.get(i).getExteriorRing().getCoordinates();
            for (int v = 0; v < coords.length - 1; v++) {
                double d = coords[v].distance(world);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = new VertexRef(VertexKind.POLYGON, i, v, new Coordinate(coords[v].x, coords[v].y));
                }
            }
        }

        for (int i = 0; i < activeSketch.size(); i++) {
            Coordinate c = activeSketch.get(i);
            double d = c.distance(world);
            if (d < bestDistance) {
                bestDistance = d;
                best = new VertexRef(VertexKind.SKETCH, 0, i, new Coordinate(c.x, c.y));
            }
        }

        return bestDistance <= threshold ? best : null;
    }

    private Coordinate getVertexCoordinate(VertexRef ref) {
        return switch (ref.kind()) {
            case POINT -> digitizedPoints.get(ref.featureIndex()).getCoordinate();
            case LINE -> digitizedLines.get(ref.featureIndex()).getCoordinateN(ref.vertexIndex());
            case POLYGON -> digitizedPolygons.get(ref.featureIndex()).getExteriorRing().getCoordinateN(ref.vertexIndex());
            case SKETCH -> activeSketch.get(ref.vertexIndex());
        };
    }

    private boolean setVertexCoordinate(VertexRef ref, Coordinate coordinate) {
        switch (ref.kind()) {
            case POINT -> {
                digitizedPoints.set(ref.featureIndex(), geometryFactory.createPoint(new Coordinate(coordinate.x, coordinate.y)));
                invalidateSnapCandidateCachesAfterVertexEdit();
                refreshSelectionAfterGeometryChange(ref);
                return true;
            }
            case LINE -> {
                Coordinate[] coords = digitizedLines.get(ref.featureIndex()).getCoordinates();
                coords[ref.vertexIndex()] = new Coordinate(coordinate.x, coordinate.y);
                LineString updated = geometryFactory.createLineString(coords);
                if (activeVertexMove == null && !isLineTopologySafe(updated)) {
                    return false;
                }
                digitizedLines.set(ref.featureIndex(), updated);
                invalidateSnapCandidateCachesAfterVertexEdit();
                refreshSelectionAfterGeometryChange(ref);
                return true;
            }
            case POLYGON -> {
                Coordinate[] coords = digitizedPolygons.get(ref.featureIndex()).getExteriorRing().getCoordinates();
                coords[ref.vertexIndex()] = new Coordinate(coordinate.x, coordinate.y);
                if (ref.vertexIndex() == 0) {
                    coords[coords.length - 1] = new Coordinate(coordinate.x, coordinate.y);
                }
                Polygon updated = geometryFactory.createPolygon(coords);
                if (activeVertexMove == null && !isPolygonTopologySafe(updated)) {
                    return false;
                }
                digitizedPolygons.set(ref.featureIndex(), updated);
                invalidateSnapCandidateCachesAfterVertexEdit();
                refreshSelectionAfterGeometryChange(ref);
                return true;
            }
            case SKETCH -> {
                activeSketch.set(ref.vertexIndex(), new Coordinate(coordinate.x, coordinate.y));
                invalidateSnapCandidateCachesAfterVertexEdit();
                return true;
            }
        }
        return false;
    }

    private void refreshSelectionAfterGeometryChange(VertexRef movedRef) {
        if (selectedFeature == null) {
            return;
        }

        FeatureKind targetKind = switch (movedRef.kind()) {
            case POINT -> FeatureKind.POINT;
            case LINE -> FeatureKind.LINE;
            case POLYGON -> FeatureKind.POLYGON;
            case SKETCH -> null;
        };

        if (targetKind != null && selectedFeature.kind() == targetKind && selectedFeature.index() == movedRef.featureIndex()) {
            notifySelectedFeature();
        }
    }

    private boolean isLineTopologySafe(LineString line) {
        return line != null && line.getNumPoints() >= 2 && line.isValid() && line.getLength() > 0;
    }

    private boolean isPolygonTopologySafe(Polygon polygon) {
        return polygon != null && polygon.isValid() && polygon.getArea() > 0;
    }

    private boolean isMovedVertexGeometrySafe(VertexRef ref) {
        return switch (ref.kind()) {
            case POINT, SKETCH -> true;
            case LINE -> isLineTopologySafe(digitizedLines.get(ref.featureIndex()));
            case POLYGON -> isPolygonTopologySafe(digitizedPolygons.get(ref.featureIndex()));
        };
    }

    private Coordinate snapCoordinate(Coordinate world, ReferencedEnvelope env) {
        if (!snappingManager.isEndpointMode() && !snappingManager.isIntersectionMode()) {
            return world;
        }

        double threshold = Math.max(env.getWidth(), env.getHeight()) * snappingManager.getToleranceRatio();
        Coordinate nearest = null;
        double best = Double.MAX_VALUE;

        if (snappingManager.isEndpointMode()) {
            for (Coordinate c : getEndpointSnapCandidates()) {
                double d = c.distance(world);
                if (d < best) {
                    best = d;
                    nearest = c;
                }
            }
        }

        if (snappingManager.isIntersectionMode()) {
            for (Coordinate c : getIntersectionSnapCandidates()) {
                double d = c.distance(world);
                if (d < best) {
                    best = d;
                    nearest = c;
                }
            }
        }

        if (nearest != null && best <= threshold) {
            return new Coordinate(nearest.x, nearest.y);
        }
        return world;
    }

    private List<Coordinate> getEndpointSnapCandidates() {
        if (!snapCandidateCacheDirty) {
            return endpointSnapCandidates;
        }

        endpointSnapCandidates.clear();
        for (Point p : digitizedPoints) {
            Coordinate c = p.getCoordinate();
            endpointSnapCandidates.add(new Coordinate(c.x, c.y));
        }
        for (LineString line : digitizedLines) {
            for (Coordinate c : line.getCoordinates()) {
                endpointSnapCandidates.add(new Coordinate(c.x, c.y));
            }
        }
        for (Polygon poly : digitizedPolygons) {
            for (Coordinate c : poly.getExteriorRing().getCoordinates()) {
                endpointSnapCandidates.add(new Coordinate(c.x, c.y));
            }
        }
        for (Coordinate c : activeSketch) {
            endpointSnapCandidates.add(new Coordinate(c.x, c.y));
        }
        snapCandidateCacheDirty = false;
        return endpointSnapCandidates;
    }

    private List<Coordinate> getIntersectionSnapCandidates() {
        if (!intersectionCandidateCacheDirty) {
            return intersectionSnapCandidates;
        }

        intersectionSnapCandidates.clear();
        intersectionSnapCandidates.addAll(collectIntersectionCandidates());
        intersectionCandidateCacheDirty = false;
        return intersectionSnapCandidates;
    }

    private void invalidateSnapCandidateCaches() {
        snapCandidateCacheDirty = true;
        intersectionCandidateCacheDirty = true;
    }

    private void invalidateSnapCandidateCachesAfterVertexEdit() {
        if (activeVertexMove == null) {
            invalidateSnapCandidateCaches();
        }
    }

    private List<Coordinate> collectIntersectionCandidates() {
        List<Coordinate[]> segments = new ArrayList<>();
        for (LineString line : digitizedLines) {
            addSegments(line.getCoordinates(), segments);
            if (segments.size() > MAX_INTERSECTION_SNAP_SEGMENTS) {
                return List.of();
            }
        }
        for (Polygon polygon : digitizedPolygons) {
            addSegments(polygon.getExteriorRing().getCoordinates(), segments);
            if (segments.size() > MAX_INTERSECTION_SNAP_SEGMENTS) {
                return List.of();
            }
        }
        addSegments(activeSketch.toArray(new Coordinate[0]), segments);
        if (segments.size() > MAX_INTERSECTION_SNAP_SEGMENTS) {
            return List.of();
        }

        List<Coordinate> intersections = new ArrayList<>();
        LineIntersector intersector = new RobustLineIntersector();
        for (int i = 0; i < segments.size(); i++) {
            Coordinate[] s1 = segments.get(i);
            for (int j = i + 1; j < segments.size(); j++) {
                Coordinate[] s2 = segments.get(j);
                intersector.computeIntersection(s1[0], s1[1], s2[0], s2[1]);
                if (!intersector.hasIntersection()) {
                    continue;
                }
                for (int k = 0; k < intersector.getIntersectionNum(); k++) {
                    Coordinate c = intersector.getIntersection(k);
                    if (c != null) {
                        intersections.add(new Coordinate(c.x, c.y));
                        if (intersections.size() >= MAX_INTERSECTION_SNAP_RESULTS) {
                            return intersections;
                        }
                    }
                }
            }
        }
        return intersections;
    }

    private void addSegments(Coordinate[] coordinates, List<Coordinate[]> segments) {
        if (coordinates == null || coordinates.length < 2) {
            return;
        }
        for (int i = 0; i < coordinates.length - 1; i++) {
            segments.add(new Coordinate[] {
                    new Coordinate(coordinates[i].x, coordinates[i].y),
                    new Coordinate(coordinates[i + 1].x, coordinates[i + 1].y)
            });
        }
    }

    private void setupEventHandlers() {
        setOnMousePressed((MouseEvent ev) -> {
            if (editMode == EditMode.MOVE_VERTEX && ev.getButton() == MouseButton.PRIMARY) {
                if (mapContent == null || mapContent.getViewport() == null) {
                    return;
                }
                ReferencedEnvelope env = mapContent.getViewport().getBounds();
                if (!isUsableBounds(env)) {
                    return;
                }

                Coordinate world = toWorldCoordinate(ev.getX(), ev.getY(), env);
                activeVertexMove = findNearestVertex(world, env);
                vertexMoveChanged = false;
                return;
            }

            if (editMode != EditMode.PAN && ev.getButton() == MouseButton.PRIMARY) {
                handleDigitizingClick(ev);
                return;
            }

            if (editMode != EditMode.PAN && ev.getButton() == MouseButton.SECONDARY
                    && (editMode == EditMode.DRAW_LINE || editMode == EditMode.DRAW_POLYGON)) {
                finishSketch();
                return;
            }

            if (editMode == EditMode.PAN && ev.isPrimaryButtonDown()) {
                lastMousePos = new Point2D(ev.getX(), ev.getY());
                isPanning = true;
                resetInteractiveDrawThrottle();
                setCursor(javafx.scene.Cursor.CLOSED_HAND);
            }
        });

        setOnMouseDragged((MouseEvent ev) -> {
            if (editMode == EditMode.MOVE_VERTEX && activeVertexMove != null && mapContent != null && mapContent.getViewport() != null) {
                ReferencedEnvelope env = mapContent.getViewport().getBounds();
                if (!isUsableBounds(env)) {
                    return;
                }

                Coordinate world = toWorldCoordinate(ev.getX(), ev.getY(), env);
                Coordinate snapped = snapCoordinate(world, env);
                if (setVertexCoordinate(activeVertexMove, snapped)) {
                    vertexMoveChanged = true;
                    drawInteractive();
                }
                return;
            }

            if (editMode == EditMode.PAN && isPanning && lastMousePos != null && mapContent != null) {
                double dx = ev.getX() - lastMousePos.getX();
                double dy = ev.getY() - lastMousePos.getY();
                if (Math.abs(dx) < MIN_INTERACTIVE_PIXEL_DELTA && Math.abs(dy) < MIN_INTERACTIVE_PIXEL_DELTA) {
                    return;
                }

                MapViewport viewport = mapContent.getViewport();
                ReferencedEnvelope env = viewport.getBounds();
                if (env != null && !env.isEmpty()) {
                    double envW = env.getWidth();
                    double envH = env.getHeight();

                    // Simple panning math matching screen pixels to map coordinates
                    double mapDx = (dx / getWidth()) * envW;
                    double mapDy = (dy / getHeight()) * envH;

                    ReferencedEnvelope newEnv = new ReferencedEnvelope(
                            env.getMinX() - mapDx, env.getMaxX() - mapDx,
                            env.getMinY() + mapDy, env.getMaxY() + mapDy,
                            env.getCoordinateReferenceSystem());

                        setViewportBounds(constrainViewportBounds(newEnv, env), "pan interaction");
                    drawInteractive();
                }

                lastMousePos = new Point2D(ev.getX(), ev.getY());
            }
        });

        setOnMouseReleased((MouseEvent ev) -> {
            if (editMode == EditMode.MOVE_VERTEX && activeVertexMove != null) {
                VertexRef movedRef = activeVertexMove;
                Coordinate current = getVertexCoordinate(movedRef);
                if (vertexMoveChanged && !current.equals2D(movedRef.original())) {
                    Coordinate original = new Coordinate(movedRef.original().x, movedRef.original().y);
                    if (isMovedVertexGeometrySafe(movedRef)) {
                        undoStack.push(() -> {
                            if (setVertexCoordinate(movedRef, original)) {
                                draw();
                            }
                        });
                    } else {
                        setVertexCoordinate(movedRef, original);
                        vertexMoveChanged = false;
                    }
                }
                if (vertexMoveChanged) {
                    invalidateSnapCandidateCaches();
                }
                activeVertexMove = null;
                vertexMoveChanged = false;
                resetInteractiveDrawThrottle();
                draw();
            }

            isPanning = false;
            if (editMode == EditMode.PAN) {
                resetInteractiveDrawThrottle();
                draw();
            }
            setCursor(javafx.scene.Cursor.DEFAULT);
        });

        setOnScroll((ScrollEvent ev) -> {
            if (mapContent == null)
                return;
            // Negative deltaY means scroll down (zoom out), Positive means scroll up (zoom
            // in)
            double zoomFactor = ev.getDeltaY() > 0 ? 0.9 : 1.1;

            MapViewport viewport = mapContent.getViewport();
            ReferencedEnvelope env = viewport.getBounds();
            if (env != null && !env.isEmpty()) {
                double centerX = env.getMedian(0);
                double centerY = env.getMedian(1);

                double newW = env.getWidth() * zoomFactor;
                double newH = env.getHeight() * zoomFactor;

                ReferencedEnvelope newEnv = new ReferencedEnvelope(
                        centerX - newW / 2, centerX + newW / 2,
                        centerY - newH / 2, centerY + newH / 2,
                        env.getCoordinateReferenceSystem());

                setViewportBounds(constrainViewportBounds(newEnv, env), "zoom interaction");
                drawInteractive();
                scheduleHighQualityRedraw();
            }
        });
    }

    private ReferencedEnvelope constrainViewportBounds(ReferencedEnvelope proposed, ReferencedEnvelope fallback) {
        if (!isUsableBounds(proposed)) {
            return fallback;
        }

        ReferencedEnvelope datasetBounds = getCachedDatasetBounds();

        if (datasetBounds == null) {
            return proposed;
        }

        double proposedW = proposed.getWidth();
        double proposedH = proposed.getHeight();
        if (!Double.isFinite(proposedW) || !Double.isFinite(proposedH) || proposedW <= 0 || proposedH <= 0) {
            return fallback;
        }

        double minW = Math.max(datasetBounds.getWidth() * MIN_VIEW_SHRINK_FACTOR, 1e-12);
        double minH = Math.max(datasetBounds.getHeight() * MIN_VIEW_SHRINK_FACTOR, 1e-12);
        double maxW = Math.max(datasetBounds.getWidth() * MAX_VIEW_EXPANSION_FACTOR, minW);
        double maxH = Math.max(datasetBounds.getHeight() * MAX_VIEW_EXPANSION_FACTOR, minH);

        double clampedW = Math.min(Math.max(proposedW, minW), maxW);
        double clampedH = Math.min(Math.max(proposedH, minH), maxH);

        double centerX = proposed.getMedian(0);
        double centerY = proposed.getMedian(1);

        ReferencedEnvelope clamped = new ReferencedEnvelope(
                centerX - clampedW / 2, centerX + clampedW / 2,
                centerY - clampedH / 2, centerY + clampedH / 2,
            proposed.getCoordinateReferenceSystem());
        return constrainEnvelopeToDomain(clamped);
    }
}
