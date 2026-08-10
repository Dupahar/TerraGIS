package com.terra.gis.ui;

import javafx.scene.control.Tooltip;
import javafx.util.Duration;

/**
 * TooltipHelper provides consistent, user-friendly tooltips for editing UI elements.
 * Centralizes tooltip configuration to ensure consistent help text across the application.
 */
public class TooltipHelper {

    private static final Duration SHOW_DELAY = new Duration(500);
    private static final Duration HIDE_DELAY = new Duration(200);

    public static Tooltip create(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(SHOW_DELAY);
        tooltip.setHideDelay(HIDE_DELAY);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(300);
        return tooltip;
    }

    // Edit Mode Tooltips
    public static Tooltip panModeTooltip() {
        return create("Pan & Zoom Mode\n\n"
                + "• Left+Drag to pan the map\n"
                + "• Scroll to zoom in/out\n"
                + "• Use before editing to navigate");
    }

    public static Tooltip selectFeatureTooltip() {
        return create("Select Features\n\n"
                + "• Click to select a feature\n"
                + "• View/edit attributes in the table\n"
                + "• Use Ctrl+Click for multi-select");
    }

    public static Tooltip drawPointTooltip() {
        return create("Draw Points\n\n"
                + "• Click to place points on the map\n"
                + "• Points are immediately created\n"
                + "• Edit attributes after placement");
    }

    public static Tooltip drawLineTooltip() {
        return create("Draw Lines\n\n"
                + "• Click each vertex along the path\n"
                + "• Press 'Finish Sketch' or double-click to complete\n"
                + "• Minimum 2 vertices required\n"
                + "• Hold Ctrl to snap to endpoints");
    }

    public static Tooltip drawPolygonTooltip() {
        return create("Draw Polygons\n\n"
                + "• Click each vertex to define the boundary\n"
                + "• Double-click or press 'Finish Sketch' to close\n"
                + "• Polygon must form a closed ring\n"
                + "• Snapping helps create precise boundaries");
    }

    public static Tooltip deleteFeatureTooltip() {
        return create("Delete Features\n\n"
                + "• Click on a feature to delete it\n"
                + "• Deletion is immediate\n"
                + "• Use Undo to restore if needed");
    }

    public static Tooltip moveVertexTooltip() {
        return create("Move Vertices\n\n"
                + "• Click and drag a vertex to move it\n"
                + "• Geometry updates in real-time\n"
                + "• Use snapping to maintain topology\n"
                + "• Hold Ctrl while dragging to force snap");
    }

    // Sketch Control Tooltips
    public static Tooltip finishSketchTooltip() {
        return create("Finish Sketch\n\n"
                + "Completes the current line or polygon drawing.\n"
                + "The feature will be added to the map.");
    }

    public static Tooltip clearSketchTooltip() {
        return create("Clear Sketch\n\n"
                + "Discards the current drawing without saving.\n"
                + "Useful if you want to start over.");
    }

    public static Tooltip undoEditTooltip() {
        return create("Undo Last Edit\n\n"
                + "Reverts the most recent change:\n"
                + "• Point/line/polygon creation\n"
                + "• Vertex movement\n"
                + "• Feature deletion or attribute edit");
    }

    // Snap Control Tooltips
    public static Tooltip snapEndpointsTooltip() {
        return create("Snap Endpoints\n\n"
                + "When enabled, new vertices snap to nearby\n"
                + "endpoints of existing features.\n"
                + "Helps create connected topology.");
    }

    public static Tooltip snapIntersectionsTooltip() {
        return create("Snap Intersections\n\n"
                + "When enabled, new vertices snap to\n"
                + "intersection points of existing features.\n"
                + "Useful for creating intersected geometries.");
    }

    public static Tooltip snapToleranceTooltip() {
        return create("Snap Tolerance\n\n"
                + "Controls the distance (in map units) within which\n"
                + "snapping is triggered.\n"
                + "Smaller = more precise, Larger = easier snapping");
    }

    // Layer Management Tooltips
    public static Tooltip layerVisibilityTooltip() {
        return create("Layer Visibility\n\n"
                + "• Checked = Layer visible on map\n"
                + "• Unchecked = Layer hidden\n"
                + "• Does not delete the layer data");
    }

    public static Tooltip layerContextMenuTooltip() {
        return create("Right-click for layer options:\n\n"
                + "• Show/Hide Layer\n"
                + "• Remove from project\n"
                + "• Change layer order (up/down)");
    }

    // Attribute Table Tooltips
    public static Tooltip attributeTableTooltip() {
        return create("Feature Attributes\n\n"
                + "• Shows properties of selected feature\n"
                + "• Edit 'name' and 'notes' directly\n"
                + "• Other fields are read-only (in MVP)");
    }

    // AI/Export Tooltips
    public static Tooltip exportVectorTooltip() {
        return create("Export Vector Data\n\n"
                + "Save features to file formats:\n"
                + "• Shapefile (.shp)\n"
                + "• GeoPackage (.gpkg)\n"
                + "• GeoJSON (.json)");
    }

    public static Tooltip connectAiTooltip() {
        return create("Connect AI Backend\n\n"
                + "Configure connection to the Python AI service\n"
                + "for segmentation and analysis tasks.");
    }

    public static Tooltip sendDiagnosticsTooltip() {
        return create("Send Diagnostics\n\n"
                + "Create a support bundle with logs and crash incidents\n"
                + "to help TerraGIS troubleshoot beta issues.");
    }

    // Analysis Operation Tooltips
    public static Tooltip bufferAnalysisTooltip() {
        return create("Buffer: Create Proximity Zones\n\n"
                + "• Draw buffer zones around features at a specified distance\n"
                + "• Great for service areas, safety zones, and search radii\n"
                + "• Input: Any vector layer + distance value\n"
                + "• Output: New layer with buffer zones");
    }

    public static Tooltip intersectionAnalysisTooltip() {
        return create("Intersection: Find Overlapping Areas\n\n"
                + "• Find geometry that overlaps between two layers\n"
                + "• Discover buildings in flood zones, parks in districts, etc.\n"
                + "• Input: Two vector layers with compatible CRS\n"
                + "• Output: Only overlapping features from Layer A");
    }

    public static Tooltip reprojectAnalysisTooltip() {
        return create("Reproject: Change Coordinate System\n\n"
                + "• Transform layer to a different map projection\n"
                + "• Align layers from different sources or prepare for web export\n"
                + "• Input: Layer + target EPSG code (e.g., EPSG:3857)\n"
                + "• Output: Layer with transformed coordinates");
    }

    public static Tooltip clipAnalysisTooltip() {
        return create("Clip: Cut by Boundary\n\n"
                + "• Keep only the part of a layer inside a boundary\n"
                + "• Extract region of interest or prepare datasets for regions\n"
                + "• Input: Target layer + boundary layer\n"
                + "• Output: Clipped features inside boundary extent");
    }

    public static Tooltip dissolveAnalysisTooltip() {
        return create("Dissolve: Merge Features\n\n"
                + "• Combine adjacent or grouped features into single geometries\n"
                + "• Remove internal boundaries or summarize by attribute\n"
                + "• Input: Layer + optional dissolve field\n"
                + "• Output: Merged geometries, optionally grouped by field value");
    }

    public static Tooltip analysisToolboxTooltip() {
        return create("Analysis Toolbox\n\n"
                + "Spatial analysis operations:\n"
                + "• Buffer: Create proximity zones\n"
                + "• Intersection: Find overlap\n"
                + "• Reproject: Change CRS\n"
                + "• Clip: Cut by boundary\n"
                + "• Dissolve: Merge features\n\n"
                + "All operations create new output layers.");
    }
}
