package com.terra.gis.ui;

import com.terra.gis.spatial.RasterImporter;
import com.terra.gis.spatial.AnalysisService;
import com.terra.gis.spatial.VectorExportService;
import com.terra.gis.spatial.VectorImporter;
import com.terra.gis.api.AiRoundTripService;
import com.terra.gis.api.AiBackendManager;
import com.terra.gis.api.TerraAiJobService;
import com.terra.gis.api.TerraAiOrchestratorClient;
import com.terra.gis.api.TerraAiOrchestratorManager;
import com.terra.gis.project.ProjectSessionManager;
import com.terra.gis.project.ProjectManager;
import com.terra.gis.api.CancellationToken;
import com.terra.gis.api.TerraApiClient;
import com.terra.gis.AppConfig;
import com.terra.gis.diagnostics.CrashDiagnosticsManager;
import com.terra.gis.diagnostics.DiagnosticsBundleService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Transform;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.embed.swing.SwingFXUtils;
import org.geotools.coverage.grid.io.AbstractGridCoverage2DReader;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.ows.wmts.WebMapTileServer;
import org.geotools.ows.wmts.map.WMTSMapLayer;
import org.geotools.ows.wmts.model.WMTSCapabilities;
import org.geotools.ows.wmts.model.WMTSLayer;
import org.geotools.api.style.Style;
import org.geotools.api.feature.type.GeometryDescriptor;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.Layer;
import org.geotools.styling.SLD;
import org.geotools.styling.StyleBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Lineal;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.Puntal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.prefs.BackingStoreException;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import javafx.util.Duration;

/**
 * Main application window layout containing the map viewer, toolbars, and layer management panels.
 * <p>
 * This is the primary UI component that orchestrates all visual elements of the TerraGIS application:
 * <ul>
 *   <li><strong>Top Toolbar:</strong> File operations (Open Vector/Raster), AI backend connection, persona mode selector</li>
 *   <li><strong>Left Panel:</strong> Layer Manager with drag-and-drop reordering and context menu actions</li>
 *   <li><strong>Center:</strong> Interactive MapCanvas for geographic data visualization</li>
 *   <li><strong>Bottom:</strong> Status bar with progress indicator for long-running operations</li>
 * </ul>
 * 
 * <p><strong>Layer Management:</strong></p>
 * <ul>
 *   <li>Right-click on any layer for context menu: Show/Hide, Remove, Move Up, Move Down</li>
 *   <li>Layers are rendered bottom-to-top (first layer in list = background)</li>
 *   <li>Progress feedback shows file size, feature count, and multi-stage loading</li>
 * </ul>
 * 
 * <p><strong>Supported Formats:</strong></p>
 * <ul>
 *   <li>Vector: Shapefile (.shp), GeoPackage (.gpkg), GeoJSON (.json, .geojson)</li>
 *   <li>Raster: GeoTIFF (.tif, .tiff), Arc/Info ASCII Grid (.asc), Erdas Imagine (.img)</li>
 * </ul>
 * 
 * @see MapCanvas
 * @see com.terra.gis.spatial.VectorImporter
 */
public class MainView extends BorderPane {

    private static final Logger log = LoggerFactory.getLogger(MainView.class);
    private static final String BASE_MAP_LABEL = "Base Map";
    private static final String NO_LAYERS_LABEL = "No layers loaded";
    private static final String NO_MATCHING_LAYERS_LABEL = "No matching layers";
    private static final String GROUP_HEADER_PREFIX = "Group: ";
    private static final String PREF_KEY_LAYER_STATE = "mainView.layerState.v1";
    private static final String PREF_KEY_DIGITIZED_STATE = "mainView.digitizedState.v1";
    private static final String PREF_KEY_AI_ACTION_STATE = "mainView.aiActionState.v1";
    private static final String PREF_KEY_LAYOUT_EXPORT_PRESETS = "mainView.layoutExportPresets.v1";
    private static final String PREF_KEY_LAYOUT_EXPORT_LAST_PRESET = "mainView.layoutExportLastPreset.v1";
    private static final String PROJECT_PREVIEW_FILE = "project-preview.png";
    private static final String ACTION_SCOPE_SELECTED_RASTER = "selected-raster-layer";
    private static final String ACTION_SCOPE_TERRA_AI_SOC = "terra-ai-soc";
    private static final String ACTION_SCOPE_TERRA_AI_SOC_PRECOMPUTED = "terra-ai-soc-precomputed-folder";
    private static final String BASEMAP_LAYER_TITLE = "Basemap";
    private static final String BASEMAP_NONE = "None";
    private static final String BASEMAP_SATELLITE = "Satellite";
    private static final String BASEMAP_STREETS = "Streets";
    private static final String BASEMAP_TERRAIN = "Terrain";
    private static final String LEGACY_BASEMAP_LAYER_TITLE = "Satellite Basemap";
    private static final String OLD_BASEMAP_LAYER_TITLE = "Basemap (previous)";
    private static final long BASEMAP_SWITCH_DEBOUNCE_MS = 300;
    private static final int BASEMAP_MAX_LOAD_ATTEMPTS = 3;
    private static final long BASEMAP_RETRY_BACKOFF_MS = 350;
    private static final long HEAVY_RASTER_PIXEL_THRESHOLD = 80_000_000L;
    private static final String BASEMAP_GLOBAL_FALLBACK_WMTS = "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/1.0.0/WMTSCapabilities.xml";

    private MapCanvas mapCanvas;
    private ListView<String> layerList;
    private TableView<AttributeRow> attributeTable;
    private TextField layerSearchField;
    private ComboBox<String> editModeCombo;
    private String currentEditModeLabel = "Pan";
    private Label selectedFeatureBadge;
    private Label statusLabel;
    private Label projectTitleLabel;
    private Label projectMetaLabel;
    private Label insightSummaryLabel;
    private Label insightDetailLabel;
    private Label mapContextLabel;
    private Label layerCountBadge;
    private VBox workspaceInsightsPanel;
    private Button workspaceExpandButton;
    private Pane mapSurface;
    private boolean workspacePanelCollapsed;
    private ProgressBar progressBar;
    private ComboBox<String> aiActionCombo;
    private ComboBox<String> basemapCombo;
    private Button btnRunSelectedAiAction;
    private final AtomicLong basemapLoadSequence = new AtomicLong(0);
    private Task<LoadedBasemap> basemapLoadTask;
    private final PauseTransition basemapSwitchDebounce = new PauseTransition(Duration.millis(BASEMAP_SWITCH_DEBOUNCE_MS));
    private String pendingBasemapSelection = BASEMAP_NONE;
    private String pendingBasemapPreviousSelection = BASEMAP_NONE;
    private String activeBasemapName = BASEMAP_NONE;
    private boolean suppressBasemapSelectionEvent = false;
    private final PauseTransition stateSaveDebounce = new PauseTransition(Duration.millis(350));
    private final PauseTransition mapSurfaceResizeDebounce = new PauseTransition(Duration.millis(35));
    private final Preferences prefs = Preferences.userNodeForPackage(MainView.class);
    private final ProjectManager projectManager = new ProjectManager();
    private final AnalysisService analysisService = new AnalysisService();
    private final DiagnosticsBundleService diagnosticsBundleService = new DiagnosticsBundleService();
    private final List<Control> readOnlyControls = new ArrayList<>();
    private final Map<String, String> rasterLayerPathByLabel = new HashMap<>();
    private final Map<String, String> vectorLayerPathByLabel = new HashMap<>();
    private final Map<String, String> analysisSessionLayerPathByLabel = new HashMap<>();
    private final Map<String, String> provenanceByLayerLabel = new HashMap<>();
    private final Map<String, String> layerGroupByTitle = new HashMap<>();
    private final List<String> layerGroupOrder = new ArrayList<>();
    private final List<LayoutExportPreset> layoutExportPresets = new ArrayList<>();
    private boolean layoutExportPresetsLoaded;
    private final ObservableList<JobCenterEntry> jobCenterRows = FXCollections.observableArrayList();
        private final Map<String, AiModelDefinition> aiModelsById = new LinkedHashMap<>();
        private final List<AiActionDefinition> aiActions = new ArrayList<>();
    private TableView<JobCenterEntry> jobCenterTable;
    private java.nio.file.Path currentProjectPath;
    private Runnable onBackToMainMenu;
    private boolean readOnlyMode;
    private String readOnlyReason = "";
    private double lastSyncedMapSurfaceWidth = -1.0;
    private double lastSyncedMapSurfaceHeight = -1.0;

    private record BasemapDefinition(String endpoint, String preferredLayerName, String statusLabel) {
    }

    private record WmtsCandidate(String endpoint, String preferredLayerName, String sourceLabel) {
    }

    private record LoadedBasemap(WMTSMapLayer layer, int attempt, String sourceLabel) {
    }

    private static final Map<String, BasemapDefinition> BASEMAP_DEFINITIONS = Map.of(
        BASEMAP_SATELLITE,
        new BasemapDefinition(
            "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/WMTS/1.0.0/WMTSCapabilities.xml",
            "World_Imagery",
            "Satellite basemap"
        ),
        BASEMAP_STREETS,
        new BasemapDefinition(
            "https://services.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/WMTS/1.0.0/WMTSCapabilities.xml",
            "World_Street_Map",
            "Streets basemap"
        ),
        BASEMAP_TERRAIN,
        new BasemapDefinition(
            "https://services.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/WMTS/1.0.0/WMTSCapabilities.xml",
            "World_Topo_Map",
            "Terrain basemap"
        )
    );

        private record AiModelDefinition(
            String id,
            String displayName,
            String modelName,
            String sourceType,
            String sourceRef,
            String inputType) {
        }

        private record AiActionDefinition(
            String actionName,
            String modelId,
            String scope,
            int tileSize) {
        }

            private record TerraAiRunOutcome(
                boolean success,
                String message,
                String errorCode,
                String errorDetail,
                Path outputDirectory,
                List<Path> artifactPaths) {
            }

        private record LoadedRaster(AbstractGridCoverage2DReader reader, boolean heavyRaster) {
        }

    private static class JobCenterEntry {
        private String status;
        private String phase;
        private int progressPercent;
        private String message;
        private String updatedAtUtc;

        private JobCenterEntry(String status) {
            this.status = status;
            this.phase = "Queued";
            this.message = "Waiting to start";
            this.progressPercent = 0;
            this.updatedAtUtc = "";
        }
    }

    private static class AttributeRow {
        private final SimpleStringProperty key;
        private final SimpleStringProperty value;

        private AttributeRow(String key, String value) {
            this.key = new SimpleStringProperty(key);
            this.value = new SimpleStringProperty(value == null ? "" : value);
        }

        public String getKey() {
            return key.get();
        }

        public SimpleStringProperty keyProperty() {
            return key;
        }

        public void setValue(String value) {
            this.value.set(value == null ? "" : value);
        }

        public SimpleStringProperty valueProperty() {
            return value;
        }
    }

    public MainView() {
        log.debug("Initializing MainView");
        initComponents();
        buildLayout();
        setupKeyboardShortcuts();
        // Don't restore state here - wait for project or explicit call
        // This ensures new projects start fresh
    }

    private void initComponents() {
        mapCanvas = new MapCanvas();
        mapSurfaceResizeDebounce.setOnFinished(event -> {
            if (syncMapCanvasToSurface() && mapCanvas != null) {
                mapCanvas.draw();
            }
        });
        initAiModelRegistry();
        stateSaveDebounce.setOnFinished(event -> persistLayerStateNow());
        basemapSwitchDebounce.setOnFinished(event ->
            performBasemapSelection(pendingBasemapSelection, pendingBasemapPreviousSelection));

        layerSearchField = new TextField();
        layerSearchField.setPromptText("Search layers");

        layerList = new ListView<>();
        layerList.getItems().addAll(BASE_MAP_LABEL, NO_LAYERS_LABEL);
        setupLayerContextMenu();

        attributeTable = new TableView<>();
        attributeTable.setEditable(true);
        attributeTable.setPlaceholder(new Label("Select a digitized feature"));

        TableColumn<AttributeRow, String> keyColumn = new TableColumn<>("Property");
        keyColumn.setCellValueFactory(cell -> cell.getValue().keyProperty());
        keyColumn.setEditable(false);
        keyColumn.setPrefWidth(110);

        TableColumn<AttributeRow, String> valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(cell -> cell.getValue().valueProperty());
        valueColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        valueColumn.setOnEditCommit(event -> {
            AttributeRow row = event.getRowValue();
            if (row == null) {
                return;
            }
            String key = row.getKey();
            String newValue = event.getNewValue() == null ? "" : event.getNewValue();

            // Only name and notes are editable in the MVP attribute table.
            if ("name".equalsIgnoreCase(key) || "notes".equalsIgnoreCase(key)) {
                if (mapCanvas.updateSelectedFeatureAttribute(key, newValue)) {
                    row.setValue(newValue);
                    setStatus("Updated " + key + " attribute");
                }
            }
        });
        valueColumn.setPrefWidth(120);

        attributeTable.getColumns().addAll(keyColumn, valueColumn);
        attributeTable.setItems(FXCollections.observableArrayList());
        attributeTable.setPrefHeight(190);

        mapCanvas.setSelectedFeatureListener(this::updateAttributeTable);

        statusLabel = new Label("Ready");
        progressBar = new ProgressBar(0);
        progressBar.setVisible(false);
    }

    private void initAiModelRegistry() {
        aiModelsById.clear();
        AiModelDefinition backendDefault = new AiModelDefinition(
                "backend-default",
                "Backend Default Model",
                "default-segmentation",
                "backend",
                "localhost:6565",
                "raster");
        aiModelsById.put(backendDefault.id(), backendDefault);

        String configuredModelPath = System.getenv("TERRAGIS_MODEL_PATH");
        if (configuredModelPath != null && !configuredModelPath.isBlank()) {
            AiModelDefinition envModel = new AiModelDefinition(
                    "env-model",
                    "Environment Model (TERRAGIS_MODEL_PATH)",
                    "default-segmentation",
                    "local-path",
                    configuredModelPath,
                    "raster");
            aiModelsById.put(envModel.id(), envModel);
        }

        aiActions.clear();
        aiActions.add(new AiActionDefinition("Run TERRA.AI (SOC Prediction)", "backend-default", ACTION_SCOPE_TERRA_AI_SOC, -1));
        aiActions.add(new AiActionDefinition("Run SOC from Precomputed Covariates Folder", "backend-default", ACTION_SCOPE_TERRA_AI_SOC_PRECOMPUTED, -1));
        aiActions.add(new AiActionDefinition("Segment Active Raster", "backend-default", ACTION_SCOPE_SELECTED_RASTER, -1));
    }

    /**
     * Sets up the context menu for layer management operations.
     * Allows users to remove layers or change their rendering order.
     */
    private void setupLayerContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        PauseTransition opacityApplyDebounce = new PauseTransition(Duration.millis(40));
        final boolean[] syncOpacitySlider = {false};

        MenuItem visibilityItem = new MenuItem("Hide Layer");
        visibilityItem.setOnAction(e -> handleToggleLayerVisibility());

        Label opacityMenuLabel = new Label("Opacity");
        opacityMenuLabel.setMinWidth(52);
        Slider opacityContextSlider = new Slider(0.0, 100.0, 100.0);
        opacityContextSlider.setPrefWidth(120);
        opacityContextSlider.setBlockIncrement(1.0);
        opacityContextSlider.setMajorTickUnit(1.0);
        opacityContextSlider.setMinorTickCount(0);
        opacityContextSlider.setSnapToTicks(true);
        Label opacityValueLabel = new Label("100%");
        opacityValueLabel.setMinWidth(44);
        HBox opacityContent = new HBox(8, opacityMenuLabel, opacityContextSlider, opacityValueLabel);
        CustomMenuItem opacitySliderItem = new CustomMenuItem(opacityContent, false);

        Runnable applyContextOpacity = () -> {
            int selectedIndex = layerList.getSelectionModel().getSelectedIndex();
            String selectedItem = layerList.getSelectionModel().getSelectedItem();
            if (selectedIndex < 0 || selectedItem == null || !isLayerListLayerItem(selectedItem)) {
                return;
            }
            double opacity = Math.max(0.0, Math.min(100.0, opacityContextSlider.getValue())) / 100.0;
            applyLayerOpacityForListIndex(selectedIndex, selectedItem, opacity);
        };

        opacityContextSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                return;
            }

            double clampedPct = Math.max(0.0, Math.min(100.0, newVal.doubleValue()));
            int roundedPct = (int) Math.round(clampedPct);
            if (Math.abs(clampedPct - roundedPct) > 0.0001) {
                opacityContextSlider.setValue(roundedPct);
                return;
            }

            double opacity = roundedPct / 100.0;
            opacityValueLabel.setText(formatOpacityPercent(opacity));

            if (syncOpacitySlider[0]) {
                return;
            }

            opacityApplyDebounce.stop();
            opacityApplyDebounce.setOnFinished(event -> applyContextOpacity.run());
            opacityApplyDebounce.playFromStart();
        });

        opacityContextSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (Boolean.TRUE.equals(wasChanging) && Boolean.FALSE.equals(isChanging)) {
                opacityApplyDebounce.stop();
                applyContextOpacity.run();
            }
        });
        
        MenuItem removeItem = new MenuItem("Remove Layer");
        removeItem.setOnAction(e -> handleRemoveLayer());

        MenuItem renameItem = new MenuItem("Rename Layer");
        renameItem.setOnAction(e -> handleInlineRenameSelectedLayer());

        MenuItem colorItem = new MenuItem("Layer Color...");
        colorItem.setOnAction(e -> handleChangeVectorLayerColor());
        
        MenuItem moveUpItem = new MenuItem("Move Up");
        moveUpItem.setOnAction(e -> handleMoveLayerUp());
        
        MenuItem moveDownItem = new MenuItem("Move Down");
        moveDownItem.setOnAction(e -> handleMoveLayerDown());
        
        contextMenu.getItems().addAll(visibilityItem, opacitySliderItem, colorItem, renameItem, removeItem, new SeparatorMenuItem(), moveUpItem, moveDownItem);
        
        layerList.setContextMenu(contextMenu);

        // Ensure right-click actions apply to the item under the mouse, not a stale selection.
        layerList.setCellFactory(listView -> {
            ListCell<String> cell = new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }

                    if (isDecorativeLayerItem(item)) {
                        setText(item);
                        setGraphic(null);
                        setStyle("-fx-text-fill: #dcecf5; -fx-font-style: italic;");
                        return;
                    }

                    if (isGroupHeaderItem(item)) {
                        Label groupLabel = new Label(item);
                        groupLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #9ff4df;");
                        setText(null);
                        setGraphic(groupLabel);
                        setStyle("-fx-background-color: #233b45;");
                        return;
                    }

                    int layerIndex = toMapLayerIndex(getIndex());
                    boolean visible = mapCanvas.isLayerVisible(layerIndex);

                    CheckBox visibilityToggle = new CheckBox();
                    visibilityToggle.setSelected(visible);
                    visibilityToggle.setOnAction(event -> {
                        int currentIndex = getIndex();
                        if (currentIndex < 0) {
                            return;
                        }
                        int currentLayerIndex = toMapLayerIndex(currentIndex);
                        if (currentLayerIndex < 0) {
                            return;
                        }

                        boolean targetVisible = visibilityToggle.isSelected();
                        if (mapCanvas.setLayerVisible(currentLayerIndex, targetVisible)) {
                            layerList.getSelectionModel().select(currentIndex);
                            setStatus((targetVisible ? "Showing layer: " : "Hid layer: ") + item);
                            layerList.refresh();
                            saveLayerState();
                        }
                    });

                    String typeBadge = mapCanvas.isRasterLayer(layerIndex) ? "R" : "V";
                    Label badge = new Label(typeBadge);
                    badge.setMinWidth(22);
                    badge.setAlignment(Pos.CENTER);
                    badge.setStyle(
                            "-fx-font-size: 10px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-text-fill: #1f3f3a; " +
                            "-fx-background-color: #dcefe9; " +
                            "-fx-background-radius: 5; " +
                            "-fx-padding: 2 5 2 5;"
                    );

                    Label layerName = new Label(item);
                    layerName.setMaxWidth(180);
                    layerName.setStyle("-fx-text-fill: #ffffff;");
                    layerName.setOnMouseClicked(event -> {
                        if (event.getClickCount() == 2) {
                            TextField renameField = new TextField(item);
                            renameField.setOnAction(commitEvent -> {
                                performLayerRename(item, renameField.getText());
                                layerList.refresh();
                            });
                            renameField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                                if (Boolean.TRUE.equals(wasFocused) && Boolean.FALSE.equals(isFocused)) {
                                    performLayerRename(item, renameField.getText());
                                    layerList.refresh();
                                }
                            });

                            HBox renameContent = new HBox(8, visibilityToggle, renameField);
                            setText(null);
                            setGraphic(renameContent);
                            Platform.runLater(() -> {
                                renameField.requestFocus();
                                renameField.selectAll();
                            });
                            event.consume();
                        }
                    });
                    HBox content = new HBox(8, visibilityToggle, badge, layerName);
                    content.setAlignment(Pos.CENTER_LEFT);
                    setText(null);
                    setGraphic(content);
                    setStyle(null);
                }
            };
            cell.setOnDragDetected(event -> {
                if (cell.isEmpty()) {
                    return;
                }
                String item = cell.getItem();
                if (!isLayerListLayerItem(item)) {
                    return;
                }

                Dragboard dragboard = cell.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(item);
                dragboard.setContent(content);
                event.consume();
            });
            cell.setOnDragOver(event -> {
                Dragboard dragboard = event.getDragboard();
                if (!dragboard.hasString()) {
                    return;
                }

                String dragged = dragboard.getString();
                String target = cell.getItem();
                if (dragged == null || target == null || dragged.equals(target)) {
                    return;
                }

                if (isLayerListLayerItem(target) || isGroupHeaderItem(target)) {
                    event.acceptTransferModes(TransferMode.MOVE);
                }
                event.consume();
            });
            cell.setOnDragDropped(event -> {
                Dragboard dragboard = event.getDragboard();
                boolean success = false;
                if (dragboard.hasString() && !cell.isEmpty()) {
                    success = handleLayerDrop(dragboard.getString(), cell.getItem());
                }
                event.setDropCompleted(success);
                event.consume();
            });
            cell.setOnMouseClicked(event -> {
                if (!cell.isEmpty()) {
                    layerList.getSelectionModel().select(cell.getIndex());
                }
            });
            cell.setOnContextMenuRequested(event -> {
                if (!cell.isEmpty()) {
                    layerList.getSelectionModel().select(cell.getIndex());
                }
            });
            return cell;
        });
        
        // Enable/disable menu items based on selection
        layerList.setOnContextMenuRequested(event -> {
            int selectedIndex = layerList.getSelectionModel().getSelectedIndex();
            String selectedItem = layerList.getSelectionModel().getSelectedItem();
            
            boolean isActualLayer = selectedIndex >= 0
                && selectedItem != null
                && isLayerListLayerItem(selectedItem);

            int actualLayerIndex = isActualLayer ? toMapLayerIndex(selectedIndex) : -1;
            boolean isVisible = isActualLayer && mapCanvas.isLayerVisible(actualLayerIndex);

            visibilityItem.setDisable(!isActualLayer);
            visibilityItem.setText(isVisible ? "Hide Layer" : "Show Layer");

            opacitySliderItem.setDisable(!isActualLayer);
            if (isActualLayer) {
                double opacity = mapCanvas.getLayerOpacity(actualLayerIndex);
                syncOpacitySlider[0] = true;
                try {
                    opacityContextSlider.setValue(Math.round(opacity * 100.0));
                    opacityValueLabel.setText(formatOpacityPercent(opacity));
                } finally {
                    syncOpacitySlider[0] = false;
                }
            }

            removeItem.setDisable(!isActualLayer);
            renameItem.setDisable(!isActualLayer);
            colorItem.setDisable(!isActualLayer || actualLayerIndex < 0 || mapCanvas.isRasterLayer(actualLayerIndex));

            int totalLayers = mapCanvas.getLayerCount();
            
            moveUpItem.setDisable(!isActualLayer || actualLayerIndex >= totalLayers - 1);
            moveDownItem.setDisable(!isActualLayer || actualLayerIndex <= 0);
        });
    }

    private boolean isDecorativeLayerItem(String item) {
        return BASE_MAP_LABEL.equals(item) || NO_LAYERS_LABEL.equals(item) || NO_MATCHING_LAYERS_LABEL.equals(item);
    }

    private boolean isGroupHeaderItem(String item) {
        return item != null && item.startsWith(GROUP_HEADER_PREFIX);
    }

    private boolean isLayerListLayerItem(String item) {
        return item != null && !isDecorativeLayerItem(item) && !isGroupHeaderItem(item);
    }

    private String formatGroupHeader(String groupName) {
        return GROUP_HEADER_PREFIX + groupName;
    }

    private String parseGroupNameFromHeader(String header) {
        if (!isGroupHeaderItem(header)) {
            return "";
        }
        return header.substring(GROUP_HEADER_PREFIX.length()).trim();
    }

    private int toMapLayerIndex(int selectedIndex) {
        String selectedItem = (selectedIndex >= 0 && selectedIndex < layerList.getItems().size())
                ? layerList.getItems().get(selectedIndex)
                : null;
        if (!isLayerListLayerItem(selectedItem)) {
            return -1;
        }
        return findLayerIndexByTitle(selectedItem);
    }

    private void refreshLayerList(String preferredLabel) {
        List<String> items = new ArrayList<>();
        items.add(BASE_MAP_LABEL);

        List<Layer> layers = mapCanvas.getMapContent().layers();
        List<String> topToBottomTitles = new ArrayList<>();
        for (int index = layers.size() - 1; index >= 0; index--) {
            Layer layer = layers.get(index);
            String title = layer.getTitle();
            if (title == null || title.isBlank()) {
                title = "Layer " + (index + 1);
            }
            topToBottomTitles.add(title);
        }

        Map<String, List<String>> groupedTitles = new LinkedHashMap<>();
        List<String> ungroupedTitles = new ArrayList<>();
        for (String title : topToBottomTitles) {
            String group = layerGroupByTitle.get(title);
            if (group == null || group.isBlank()) {
                ungroupedTitles.add(title);
                continue;
            }

            groupedTitles.computeIfAbsent(group, ignored -> new ArrayList<>()).add(title);
            if (!layerGroupOrder.contains(group)) {
                layerGroupOrder.add(group);
            }
        }

        items.addAll(ungroupedTitles);

        for (String group : layerGroupOrder) {
            items.add(formatGroupHeader(group));
            List<String> groupedLayers = groupedTitles.get(group);
            if (groupedLayers != null) {
                items.addAll(groupedLayers);
            }
        }

        if (layers.isEmpty()) {
            items.add(NO_LAYERS_LABEL);
        }

        List<String> visibleItems = applyLayerSearchFilter(items);
        layerList.getItems().setAll(visibleItems);
        if (preferredLabel != null && visibleItems.contains(preferredLabel)) {
            layerList.getSelectionModel().select(preferredLabel);
        } else {
            layerList.getSelectionModel().clearSelection();
        }
        layerList.refresh();
        updateWorkspaceInsights();
    }

    private List<String> applyLayerSearchFilter(List<String> items) {
        String query = layerSearchField == null ? "" : layerSearchField.getText();
        if (query == null || query.trim().isEmpty()) {
            return items;
        }

        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        filtered.add(BASE_MAP_LABEL);

        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i);
            if (item == null || BASE_MAP_LABEL.equals(item) || NO_LAYERS_LABEL.equals(item)) {
                continue;
            }

            if (isGroupHeaderItem(item)) {
                String groupName = parseGroupNameFromHeader(item);
                List<String> groupChildren = new ArrayList<>();
                List<String> matchingGroupChildren = new ArrayList<>();
                int cursor = i + 1;
                while (cursor < items.size() && !isGroupHeaderItem(items.get(cursor))) {
                    String child = items.get(cursor);
                    if (isLayerListLayerItem(child)) {
                        groupChildren.add(child);
                        if (child.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                            matchingGroupChildren.add(child);
                        }
                    }
                    cursor++;
                }

                boolean groupMatches = groupName.toLowerCase(Locale.ROOT).contains(normalizedQuery);
                if (groupMatches || !matchingGroupChildren.isEmpty()) {
                    filtered.add(item);
                    filtered.addAll(groupMatches ? groupChildren : matchingGroupChildren);
                }
                i = cursor - 1;
                continue;
            }

            if (isLayerListLayerItem(item) && item.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                filtered.add(item);
            }
        }

        if (filtered.size() == 1) {
            filtered.add(NO_MATCHING_LAYERS_LABEL);
        }
        return filtered;
    }

    private void handleToggleLayerVisibility() {
        int selectedIndex = layerList.getSelectionModel().getSelectedIndex();
        String selectedItem = layerList.getSelectionModel().getSelectedItem();

        if (selectedIndex < 0 || selectedItem == null || !isLayerListLayerItem(selectedItem)) {
            return;
        }

        int layerIndex = toMapLayerIndex(selectedIndex);
        if (layerIndex < 0) {
            return;
        }

        boolean currentVisible = mapCanvas.isLayerVisible(layerIndex);
        boolean targetVisible = !currentVisible;
        if (mapCanvas.setLayerVisible(layerIndex, targetVisible)) {
            layerList.refresh();
            setStatus((targetVisible ? "Showing layer: " : "Hid layer: ") + selectedItem);
            saveLayerState();
        }
    }

    private void applyLayerOpacityForListIndex(int listIndex, String layerName, double opacity) {
        int layerIndex = toMapLayerIndex(listIndex);
        if (layerIndex < 0) {
            return;
        }

        if (mapCanvas.setLayerOpacity(layerIndex, opacity)) {
            setStatus("Layer opacity: " + layerName + " = " + formatOpacityPercent(opacity));
            saveLayerState();
        }
    }

    private void handleChangeVectorLayerColor() {
        int selectedIndex = layerList.getSelectionModel().getSelectedIndex();
        String selectedItem = layerList.getSelectionModel().getSelectedItem();
        if (selectedIndex < 0 || selectedItem == null || !isLayerListLayerItem(selectedItem)) {
            return;
        }

        int layerIndex = toMapLayerIndex(selectedIndex);
        if (layerIndex < 0 || mapCanvas.isRasterLayer(layerIndex)) {
            setStatus("Layer color is available for vector layers only");
            return;
        }

        Dialog<LayerColorChoice> dialog = new Dialog<>();
        dialog.setTitle("Layer Color");
        dialog.setHeaderText("Choose what to recolor");
        ButtonType applyButtonType = new ButtonType("Apply Color", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyButtonType, ButtonType.CANCEL);

        ComboBox<String> targetCombo = new ComboBox<>(FXCollections.observableArrayList("Fill color", "Boundary color"));
        targetCombo.setValue("Boundary color");
        targetCombo.setMaxWidth(Double.MAX_VALUE);

        ColorPicker colorPicker = new ColorPicker(parseFxColor(mapCanvas.getVectorLayerBoundaryColor(layerIndex), javafx.scene.paint.Color.web("#2F80ED")));
        colorPicker.setMaxWidth(Double.MAX_VALUE);
        targetCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            String stored = "Fill color".equals(newValue)
                    ? mapCanvas.getVectorLayerFillColor(layerIndex)
                    : mapCanvas.getVectorLayerBoundaryColor(layerIndex);
            colorPicker.setValue(parseFxColor(stored, javafx.scene.paint.Color.web("#2F80ED")));
        });

        Label layerLabel = new Label(selectedItem);
        layerLabel.setWrapText(true);
        layerLabel.setStyle("-fx-font-weight: bold;");

        VBox content = new VBox(10, layerLabel, targetCombo, colorPicker);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> button == applyButtonType
                ? new LayerColorChoice(targetCombo.getValue(), colorPicker.getValue())
                : null);

        Optional<LayerColorChoice> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        LayerColorChoice choice = result.get();
        String hexColor = toHexColor(choice.color());
        boolean applied = "Fill color".equals(choice.target())
                ? mapCanvas.setVectorLayerFillColor(layerIndex, hexColor)
                : mapCanvas.setVectorLayerBoundaryColor(layerIndex, hexColor);
        if (applied) {
            setStatus("Updated " + choice.target().toLowerCase(Locale.ROOT) + ": " + selectedItem + " = " + hexColor);
            saveLayerState();
            layerList.refresh();
        } else {
            showError("Layer Color", "Could not apply color to the selected vector layer.");
        }
    }

    private javafx.scene.paint.Color parseFxColor(String raw, javafx.scene.paint.Color fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return javafx.scene.paint.Color.web(raw);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private String toHexColor(javafx.scene.paint.Color color) {
        int red = (int) Math.round(Math.max(0.0, Math.min(1.0, color.getRed())) * 255.0);
        int green = (int) Math.round(Math.max(0.0, Math.min(1.0, color.getGreen())) * 255.0);
        int blue = (int) Math.round(Math.max(0.0, Math.min(1.0, color.getBlue())) * 255.0);
        return String.format("#%02X%02X%02X", red, green, blue);
    }

    private record LayerColorChoice(String target, javafx.scene.paint.Color color) {
    }

    private String formatOpacityPercent(double opacity) {
        int pct = (int) Math.round(Math.max(0.0, Math.min(1.0, opacity)) * 100.0);
        return pct + "%";
    }

    /**
     * Handles the "Remove Layer" context menu action.
     * Removes the selected layer from both the layer list and map canvas.
     */
    private void handleRemoveLayer() {
        int selectedIndex = layerList.getSelectionModel().getSelectedIndex();
        String selectedItem = layerList.getSelectionModel().getSelectedItem();
        
        if (selectedIndex < 0 || selectedItem == null) {
            return;
        }
        
        if (!isLayerListLayerItem(selectedItem)) {
            return;
        }
        
        log.info("Removing layer: {}", selectedItem);
        int layerIndex = toMapLayerIndex(selectedIndex);
        if (layerIndex < 0) {
            return;
        }
        if (mapCanvas.isRasterLayer(layerIndex)) {
            rasterLayerPathByLabel.remove(selectedItem);
        } else {
            vectorLayerPathByLabel.remove(selectedItem);
        }
        analysisSessionLayerPathByLabel.remove(selectedItem);
        provenanceByLayerLabel.remove(selectedItem);
        layerGroupByTitle.remove(selectedItem);
        
        if (mapCanvas.removeLayer(layerIndex)) {
            refreshLayerList(null);
            setStatus("Removed layer: " + selectedItem);
            log.info("Layer removed successfully: {}", selectedItem);
            saveLayerState();
        } else {
            log.error("Failed to remove layer at index: {}", layerIndex);
            showError("Remove Layer Failed", "Could not remove the selected layer.");
        }
    }

    /**
     * Handles the "Move Up" context menu action.
     * Moves the selected layer up in the rendering order (towards foreground).
     */
    private void handleMoveLayerUp() {
        int selectedIndex = layerList.getSelectionModel().getSelectedIndex();
        String selectedItem = layerList.getSelectionModel().getSelectedItem();
        
        if (selectedIndex < 0 || selectedItem == null || !isLayerListLayerItem(selectedItem)) {
            return;
        }
        
        int layerIndex = toMapLayerIndex(selectedIndex);
        if (layerIndex < 0) {
            return;
        }
        
        if (mapCanvas.moveLayerUp(layerIndex)) {
            refreshLayerList(selectedItem);
            setStatus("Moved layer up: " + selectedItem);
            log.info("Layer moved up: {} (index {} -> {})", selectedItem, layerIndex, layerIndex + 1);
            saveLayerState();
        }
    }

    /**
     * Handles the "Move Down" context menu action.
     * Moves the selected layer down in the rendering order (towards background).
     */
    private void handleMoveLayerDown() {
        int selectedIndex = layerList.getSelectionModel().getSelectedIndex();
        String selectedItem = layerList.getSelectionModel().getSelectedItem();
        
        if (selectedIndex < 0 || selectedItem == null || !isLayerListLayerItem(selectedItem)) {
            return;
        }
        
        int layerIndex = toMapLayerIndex(selectedIndex);
        if (layerIndex < 0) {
            return;
        }
        
        if (mapCanvas.moveLayerDown(layerIndex)) {
            refreshLayerList(selectedItem);
            setStatus("Moved layer down: " + selectedItem);
            log.info("Layer moved down: {} (index {} -> {})", selectedItem, layerIndex, layerIndex - 1);
            saveLayerState();
        }
    }

    private void buildLayout() {
        setStyle("-fx-background-color: #f4f7f6;");

        // TOP: Toolbar
        ToolBar toolBar = new ToolBar();
        toolBar.setStyle(
            "-fx-background-color: #ffffff; " +
                "-fx-border-color: #d9e3df; " +
                "-fx-border-width: 0 0 1 0; " +
                "-fx-effect: dropshadow(gaussian, rgba(27, 55, 49, 0.10), 10, 0.12, 0, 2);"
        );
        Button btnMainMenu = new Button("Projects");
        Button btnOpenVector = new Button("Vector");
        Button btnOpenRaster = new Button("Raster");
        basemapCombo = new ComboBox<>();
        basemapCombo.getItems().addAll(BASEMAP_NONE, BASEMAP_SATELLITE, BASEMAP_STREETS, BASEMAP_TERRAIN);
        basemapCombo.setValue(BASEMAP_NONE);
        basemapCombo.setPrefWidth(132);
        Button btnExportVector = new Button("Vector");
        btnExportVector.setTooltip(TooltipHelper.exportVectorTooltip());
        Button btnExportRaster = new Button("Raster");
        Button btnExportLayout = new Button("Map");
        Button btnAnalysis = new Button("Analyze");
        btnAnalysis.setTooltip(TooltipHelper.analysisToolboxTooltip());
        Button btnConnectAI = new Button("Connect AI");
        btnConnectAI.setTooltip(TooltipHelper.connectAiTooltip());
        Button btnManageAiActions = new Button("AI Actions");
        aiActionCombo = new ComboBox<>();
        aiActionCombo.setPrefWidth(224);
        btnRunSelectedAiAction = new Button("Run");
        Button btnFinishSketch = new Button("Finish");
        Button btnClearSketch = new Button("Clear");
        Button btnUndoEdit = new Button("Undo");
        Button btnHelpTutorial = new Button("Editing Help");
        btnHelpTutorial.setTooltip(TooltipHelper.create("Open editing tutorial popup"));
        Button btnAnalysisHelp = new Button("Analysis Guide");
        btnAnalysisHelp.setTooltip(TooltipHelper.create("Open analysis operations guide"));
        Button btnSendDiagnostics = new Button("Diagnostics");
        btnSendDiagnostics.setTooltip(TooltipHelper.sendDiagnosticsTooltip());
        btnFinishSketch.setTooltip(TooltipHelper.finishSketchTooltip());
        btnClearSketch.setTooltip(TooltipHelper.clearSketchTooltip());
        btnUndoEdit.setTooltip(TooltipHelper.undoEditTooltip());
        editModeCombo = new ComboBox<>();
        editModeCombo.getItems().addAll("Pan", "Select Feature", "Draw Point", "Draw Line", "Draw Polygon", "Delete Feature", "Move Vertex");
        editModeCombo.setValue("Pan");
        editModeCombo.setPrefWidth(142);
        editModeCombo.setTooltip(TooltipHelper.panModeTooltip());
        selectedFeatureBadge = new Label("Selected: none");
        selectedFeatureBadge.setStyle(
            "-fx-font-size: 11px; " +
                "-fx-text-fill: #24534b; " +
                "-fx-padding: 4 9 4 9; " +
                "-fx-background-color: #e6f3ef; " +
                "-fx-background-radius: 8; " +
                "-fx-border-color: #b9d9d0; " +
                "-fx-border-radius: 8;"
        );

        CheckBox snapEndpointsCheck = new CheckBox("Snap Endpoints");
        snapEndpointsCheck.setSelected(mapCanvas.isSnapEndpointEnabled());
        snapEndpointsCheck.setTooltip(TooltipHelper.snapEndpointsTooltip());
        CheckBox snapIntersectionsCheck = new CheckBox("Snap Intersections");
        snapIntersectionsCheck.setSelected(mapCanvas.isSnapIntersectionEnabled());
        snapIntersectionsCheck.setTooltip(TooltipHelper.snapIntersectionsTooltip());
        Spinner<Double> snapToleranceSpinner = new Spinner<>(0.001, 0.1, mapCanvas.getSnapToleranceRatio(), 0.001);
        snapToleranceSpinner.setEditable(true);
        snapToleranceSpinner.setPrefWidth(88);
        snapToleranceSpinner.setTooltip(TooltipHelper.snapToleranceTooltip());

        // Wire up button handlers
        btnOpenVector.setOnAction(e -> handleOpenVector());
        btnOpenRaster.setOnAction(e -> handleOpenRaster());
        basemapCombo.setOnAction(e -> {
            if (suppressBasemapSelectionEvent) {
                return;
            }
            String selected = basemapCombo.getValue();
            if (selected != null) {
                queueBasemapSelection(selected);
            }
        });
        btnExportVector.setOnAction(e -> handleExportVector());
        btnExportRaster.setOnAction(e -> handleExportRaster());
        btnExportLayout.setOnAction(e -> handleExportLayout());
        btnAnalysis.setOnAction(e -> handleOpenAnalysisToolbox());
        btnMainMenu.setOnAction(e -> handleBackToMainMenu());
        btnConnectAI.setOnAction(e -> handleConnectAI());
        btnManageAiActions.setOnAction(e -> handleManageAiActions());
        btnRunSelectedAiAction.setOnAction(e -> handleRunSelectedAiAction());
        btnHelpTutorial.setOnAction(e -> handleShowEditingTutorial());
        btnAnalysisHelp.setOnAction(e -> handleShowAnalysisTutorial());
        btnSendDiagnostics.setOnAction(e -> handleSendDiagnostics());
        btnFinishSketch.setOnAction(e -> {
            boolean finished = mapCanvas.finishSketch();
            if (finished) {
                setStatus("Sketch committed. Digitized features: " + mapCanvas.getDigitizedFeatureCount());
            } else {
                setStatus("Sketch not committed. Add enough vertices first.");
            }
        });
        btnClearSketch.setOnAction(e -> {
            mapCanvas.clearSketch();
            setStatus("Sketch cleared");
        });
        btnUndoEdit.setOnAction(e -> {
            boolean undone = mapCanvas.undoLastEdit();
            if (undone) {
                setStatus("Undo completed. Digitized features: " + mapCanvas.getDigitizedFeatureCount());
            } else {
                setStatus("Nothing to undo");
            }
        });

        editModeCombo.setOnAction(e -> {
            String selected = editModeCombo.getValue();
            if (selected == null) {
                return;
            }
            currentEditModeLabel = selected;

            // Update edit mode tooltip based on selection
            switch (selected) {
                case "Select Feature" -> {
                    mapCanvas.setEditMode(MapCanvas.EditMode.SELECT);
                    editModeCombo.setTooltip(TooltipHelper.selectFeatureTooltip());
                }
                case "Draw Point" -> {
                    mapCanvas.setEditMode(MapCanvas.EditMode.DRAW_POINT);
                    editModeCombo.setTooltip(TooltipHelper.drawPointTooltip());
                }
                case "Draw Line" -> {
                    mapCanvas.setEditMode(MapCanvas.EditMode.DRAW_LINE);
                    editModeCombo.setTooltip(TooltipHelper.drawLineTooltip());
                }
                case "Draw Polygon" -> {
                    mapCanvas.setEditMode(MapCanvas.EditMode.DRAW_POLYGON);
                    editModeCombo.setTooltip(TooltipHelper.drawPolygonTooltip());
                }
                case "Delete Feature" -> {
                    mapCanvas.setEditMode(MapCanvas.EditMode.DELETE);
                    editModeCombo.setTooltip(TooltipHelper.deleteFeatureTooltip());
                }
                case "Move Vertex" -> {
                    mapCanvas.setEditMode(MapCanvas.EditMode.MOVE_VERTEX);
                    editModeCombo.setTooltip(TooltipHelper.moveVertexTooltip());
                }
                default -> {
                    mapCanvas.setEditMode(MapCanvas.EditMode.PAN);
                    editModeCombo.setTooltip(TooltipHelper.panModeTooltip());
                }
            }

            boolean lineOrPoly = mapCanvas.getEditMode() == MapCanvas.EditMode.DRAW_LINE
                    || mapCanvas.getEditMode() == MapCanvas.EditMode.DRAW_POLYGON;
            btnFinishSketch.setDisable(!lineOrPoly);
            btnClearSketch.setDisable(!lineOrPoly);
            setStatus("Edit mode: " + selected);
        });

        snapEndpointsCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            mapCanvas.setSnapEndpointEnabled(Boolean.TRUE.equals(newVal));
            setStatus("Snap endpoints: " + (Boolean.TRUE.equals(newVal) ? "on" : "off"));
        });

        snapIntersectionsCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            mapCanvas.setSnapIntersectionEnabled(Boolean.TRUE.equals(newVal));
            setStatus("Snap intersections: " + (Boolean.TRUE.equals(newVal) ? "on" : "off"));
        });

        snapToleranceSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                mapCanvas.setSnapToleranceRatio(newVal);
            }
        });

        registerReadOnlyControl(btnOpenVector);
        registerReadOnlyControl(btnOpenRaster);
        registerReadOnlyControl(btnExportVector);
        registerReadOnlyControl(btnExportRaster);
        registerReadOnlyControl(btnExportLayout);
        registerReadOnlyControl(btnAnalysis);
        registerReadOnlyControl(btnRunSelectedAiAction);
        registerReadOnlyControl(btnFinishSketch);
        registerReadOnlyControl(btnClearSketch);
        registerReadOnlyControl(btnUndoEdit);
        registerReadOnlyControl(editModeCombo);
        applyReadOnlyMode();

        btnFinishSketch.setDisable(true);
        btnClearSketch.setDisable(true);

        // Persona switch - load from configuration
        ComboBox<String> personaCombo = new ComboBox<>();
        com.terra.gis.AppConfig config = com.terra.gis.AppConfig.getInstance();
        personaCombo.getItems().addAll(config.getAvailablePersonas());
        personaCombo.setValue(config.getDefaultPersona());
        personaCombo.setPrefWidth(132);

        toolBar.getItems().addAll(
            btnMainMenu,
            new Separator(),
            btnOpenVector,
            btnOpenRaster,
            new Label("Basemap:"),
            basemapCombo,
            btnAnalysis,
            new Separator(),
            btnConnectAI,
            btnManageAiActions,
            new Label("Action:"),
            aiActionCombo,
            btnRunSelectedAiAction,
            new Separator(),
            btnExportVector,
            btnExportRaster,
            btnExportLayout,
            new Separator(),
            new Label("Edit:"),
            editModeCombo,
            btnFinishSketch,
            btnClearSketch,
            btnUndoEdit,
            btnHelpTutorial,
            btnAnalysisHelp,
            btnSendDiagnostics,
            new Separator(),
            snapEndpointsCheck,
            snapIntersectionsCheck,
            new Label("Snap Tol:"),
            snapToleranceSpinner,
            new Separator(),
            selectedFeatureBadge,
            new Separator(),
            new Label("Mode:"),
            personaCombo);
        refreshAiActionPicker();

        HBox toolbarPrimary = new HBox(6);
        toolbarPrimary.setAlignment(Pos.CENTER_LEFT);
        toolbarPrimary.setFillHeight(true);
        toolbarPrimary.setPadding(new Insets(8, 10, 8, 10));
        toolbarPrimary.setStyle(
            "-fx-background-color: #ffffff; " +
                "-fx-border-color: #d9e3df; " +
                "-fx-border-width: 0 0 1 0; " +
                "-fx-effect: dropshadow(gaussian, rgba(27, 55, 49, 0.10), 10, 0.12, 0, 2);"
        );

        FlowPane toolbarOverflow = new FlowPane(6, 6);
        toolbarOverflow.setPadding(new Insets(8, 12, 10, 12));
        toolbarOverflow.setStyle(
            "-fx-background-color: #ffffff; " +
                "-fx-border-color: #d9e3df; " +
                "-fx-border-width: 1; " +
                "-fx-effect: dropshadow(gaussian, rgba(27, 55, 49, 0.14), 16, 0.18, 0, 6);"
        );

        Popup toolbarOverflowPopup = new Popup();
        toolbarOverflowPopup.setAutoHide(true);
        toolbarOverflowPopup.getContent().add(toolbarOverflow);

        Button btnToolbarOverflow = new Button("More");
        btnToolbarOverflow.setPrefWidth(58);
        btnToolbarOverflow.setStyle("-fx-font-size: 12; -fx-padding: 7 10 7 10;");
        btnToolbarOverflow.setTooltip(TooltipHelper.create("Show more toolbar items"));
        btnToolbarOverflow.setText("More");

        SimpleBooleanProperty overflowExpanded = new SimpleBooleanProperty(false);
        btnToolbarOverflow.setOnAction(e -> {
            if (toolbarOverflow.getChildren().isEmpty()) {
                return;
            }

            if (toolbarOverflowPopup.isShowing()) {
                toolbarOverflowPopup.hide();
                return;
            }

            if (getScene() == null || getScene().getWindow() == null) {
                return;
            }

            javafx.geometry.Bounds buttonBounds = btnToolbarOverflow.localToScreen(btnToolbarOverflow.getBoundsInLocal());
            if (buttonBounds == null) {
                return;
            }

            overflowExpanded.set(true);
            btnToolbarOverflow.setText("Close");
            toolbarOverflowPopup.show(getScene().getWindow(), buttonBounds.getMinX(), buttonBounds.getMaxY() + 4);
        });

        List<Node> toolbarItems = new ArrayList<>();
        toolbarItems.add(btnMainMenu);
        toolbarItems.add(new Separator());
        toolbarItems.add(new Label("Import"));
        toolbarItems.add(btnOpenVector);
        toolbarItems.add(btnOpenRaster);
        toolbarItems.add(new Label("Basemap:"));
        toolbarItems.add(basemapCombo);
        toolbarItems.add(btnAnalysis);
        toolbarItems.add(new Separator());
        toolbarItems.add(btnConnectAI);
        toolbarItems.add(btnManageAiActions);
        toolbarItems.add(new Label("Action:"));
        toolbarItems.add(aiActionCombo);
        toolbarItems.add(btnRunSelectedAiAction);
        toolbarItems.add(new Separator());
        toolbarItems.add(new Label("Export"));
        toolbarItems.add(btnExportVector);
        toolbarItems.add(btnExportRaster);
        toolbarItems.add(btnExportLayout);
        toolbarItems.add(new Separator());
        toolbarItems.add(new Label("Edit:"));
        toolbarItems.add(editModeCombo);
        toolbarItems.add(btnFinishSketch);
        toolbarItems.add(btnClearSketch);
        toolbarItems.add(btnUndoEdit);
        toolbarItems.add(btnHelpTutorial);
        toolbarItems.add(btnAnalysisHelp);
        toolbarItems.add(btnSendDiagnostics);
        toolbarItems.add(new Separator());
        toolbarItems.add(snapEndpointsCheck);
        toolbarItems.add(snapIntersectionsCheck);
        toolbarItems.add(new Label("Snap Tol:"));
        toolbarItems.add(snapToleranceSpinner);
        toolbarItems.add(new Separator());
        toolbarItems.add(selectedFeatureBadge);
        toolbarItems.add(new Separator());
        toolbarItems.add(new Label("Mode:"));
        toolbarItems.add(personaCombo);

        toolbarItems.forEach(item -> {
            if (item instanceof Button button) {
                button.setStyle(
                        "-fx-font-size: 12px; " +
                        "-fx-padding: 7 10 7 10; " +
                        "-fx-background-radius: 7; " +
                        "-fx-border-radius: 7;"
                );
            } else if (item instanceof Label label) {
                label.setStyle("-fx-text-fill: #49675f; -fx-font-size: 11px; -fx-font-weight: 600;");
            }
        });

        java.util.function.ToDoubleFunction<Node> measureToolbarNode = node -> {
            if (node instanceof Region region) {
                region.applyCss();
                region.autosize();
                double width = region.prefWidth(-1);
                if (!Double.isFinite(width) || width <= 0) {
                    width = region.getBoundsInLocal().getWidth();
                }
                return (Double.isFinite(width) && width > 0) ? width : 80.0;
            }
            return 80.0;
        };

        Runnable refreshToolbarLayout = () -> {
            if (getScene() == null || toolbarItems.isEmpty()) {
                return;
            }

            double containerWidth = getWidth();
            if (!Double.isFinite(containerWidth) || containerWidth <= 0) {
                containerWidth = 1280;
            }

            double usableWidth = Math.max(0, containerWidth - 20);
            double itemGap = 6.0;
            double overflowButtonWidth = measureToolbarNode.applyAsDouble(btnToolbarOverflow);

            List<Node> visibleItems = new ArrayList<>();
            List<Node> hiddenItems = new ArrayList<>();

            double usedWidth = 0;
            for (Node item : toolbarItems) {
                double itemWidth = measureToolbarNode.applyAsDouble(item);
                double requiredWidth = visibleItems.isEmpty() ? itemWidth : usedWidth + itemGap + itemWidth;
                if (requiredWidth <= usableWidth) {
                    visibleItems.add(item);
                    usedWidth = requiredWidth;
                } else {
                    hiddenItems.add(item);
                }
            }

            if (!hiddenItems.isEmpty()) {
                visibleItems.clear();
                hiddenItems.clear();
                usedWidth = 0;
                usableWidth = Math.max(0, usableWidth - overflowButtonWidth - itemGap);

                for (Node item : toolbarItems) {
                    double itemWidth = measureToolbarNode.applyAsDouble(item);
                    double requiredWidth = visibleItems.isEmpty() ? itemWidth : usedWidth + itemGap + itemWidth;
                    if (requiredWidth <= usableWidth) {
                        visibleItems.add(item);
                        usedWidth = requiredWidth;
                    } else {
                        hiddenItems.add(item);
                    }
                }
            }

            toolbarPrimary.getChildren().setAll(visibleItems);
            toolbarOverflow.getChildren().setAll(hiddenItems);

            boolean hasOverflow = !hiddenItems.isEmpty();
            btnToolbarOverflow.setVisible(hasOverflow);
            btnToolbarOverflow.setManaged(hasOverflow);

            if (!hasOverflow) {
                overflowExpanded.set(false);
                toolbarOverflowPopup.hide();
            }

            if (hasOverflow && !toolbarPrimary.getChildren().contains(btnToolbarOverflow)) {
                toolbarPrimary.getChildren().add(btnToolbarOverflow);
            } else if (!hasOverflow) {
                toolbarPrimary.getChildren().remove(btnToolbarOverflow);
            }
        };

        overflowExpanded.addListener((obs, oldVal, newVal) -> {
            if (!Boolean.TRUE.equals(newVal)) {
                toolbarOverflowPopup.hide();
                btnToolbarOverflow.setText("More");
            }
        });

        toolbarOverflowPopup.setOnHidden(e -> {
            overflowExpanded.set(false);
            btnToolbarOverflow.setText("More");
        });

        widthProperty().addListener((obs, oldVal, newVal) -> refreshToolbarLayout.run());
        toolbarPrimary.widthProperty().addListener((obs, oldVal, newVal) -> refreshToolbarLayout.run());
        toolbarOverflow.widthProperty().addListener((obs, oldVal, newVal) -> refreshToolbarLayout.run());
        Platform.runLater(refreshToolbarLayout);

        setTop(toolbarPrimary);

        // LEFT: Layer Manager
        VBox leftPanel = new VBox(8);
        leftPanel.setPadding(new Insets(12));
        leftPanel.setPrefWidth(292);
        leftPanel.setMinWidth(260);
        leftPanel.setStyle(
            "-fx-background-color: #10202a; " +
                "-fx-border-color: #2c4653; " +
                "-fx-border-width: 0 1 0 0;"
        );
        Label lblLayers = new Label("Layers");
        lblLayers.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff; -fx-font-size: 13px;");
        lblLayers.setTooltip(TooltipHelper.layerContextMenuTooltip());
        layerCountBadge = new Label("0 total");
        layerCountBadge.setStyle(
                "-fx-font-size: 10px; " +
                "-fx-text-fill: #ffffff; " +
                "-fx-padding: 2 7 2 7; " +
                "-fx-background-color: #284653; " +
                "-fx-background-radius: 9;"
        );
        Button btnNewGroup = new Button("New Group");
        btnNewGroup.setOnAction(e -> handleCreateLayerGroup());
        btnNewGroup.setTooltip(TooltipHelper.create("Create a layer group for drag-and-drop organization"));
        Region layerHeaderSpacer = new Region();
        HBox.setHgrow(layerHeaderSpacer, Priority.ALWAYS);
        HBox layerHeader = new HBox(8, lblLayers, layerCountBadge, layerHeaderSpacer, btnNewGroup);
        layerHeader.setAlignment(Pos.CENTER_LEFT);
        layerSearchField.setStyle(
                "-fx-background-color: #172b36; " +
                "-fx-background-radius: 7; " +
                "-fx-border-color: #385766; " +
                "-fx-border-radius: 7; " +
                "-fx-text-fill: #ffffff; " +
                "-fx-prompt-text-fill: #9fb7c4; " +
                "-fx-padding: 7 9 7 9;"
        );
        layerSearchField.textProperty().addListener((obs, oldText, newText) -> refreshLayerList(layerList.getSelectionModel().getSelectedItem()));
        Label lblAttributes = new Label("Selection Attributes");
        lblAttributes.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff; -fx-font-size: 13px;");
        lblAttributes.setTooltip(TooltipHelper.attributeTableTooltip());

        layerList.setStyle(
            "-fx-background-color: #0c1720; " +
                "-fx-control-inner-background: #0c1720; " +
                "-fx-background-radius: 6; " +
                "-fx-border-color: #385766; " +
                "-fx-border-radius: 6; " +
                "-fx-text-fill: #ffffff;"
        );
        attributeTable.setStyle(
            "-fx-background-color: #0c1720; " +
                "-fx-control-inner-background: #0c1720; " +
                "-fx-background-radius: 6; " +
                "-fx-border-color: #385766; " +
                "-fx-border-radius: 6; " +
                "-fx-text-fill: #ffffff;"
        );
        VBox.setVgrow(layerList, Priority.ALWAYS);
        leftPanel.getChildren().addAll(layerHeader, layerSearchField, layerList, lblAttributes, attributeTable);
        setLeft(leftPanel);

        // CENTER: Map Canvas
        AnchorPane canvasContainer = new AnchorPane();
        canvasContainer.setMinSize(0, 0);
        canvasContainer.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        canvasContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        canvasContainer.setStyle(
            "-fx-background-color: #f0f0f0;"
        );
        mapSurface = canvasContainer;
        mapCanvas.setManaged(true);
        AnchorPane.setTopAnchor(mapCanvas, 0.0);
        AnchorPane.setRightAnchor(mapCanvas, 0.0);
        AnchorPane.setBottomAnchor(mapCanvas, 0.0);
        AnchorPane.setLeftAnchor(mapCanvas, 0.0);
        canvasContainer.getChildren().add(mapCanvas);
        canvasContainer.widthProperty().addListener((obs, oldWidth, newWidth) -> scheduleMapSurfaceSync());
        canvasContainer.heightProperty().addListener((obs, oldHeight, newHeight) -> scheduleMapSurfaceSync());
        installMapSurfaceEventForwarding(canvasContainer);
        installMapSurfaceSceneSync(canvasContainer);

        workspaceExpandButton = new Button("Workspace");
        workspaceExpandButton.setVisible(false);
        workspaceExpandButton.managedProperty().bind(workspaceExpandButton.visibleProperty());
        workspaceExpandButton.setTooltip(TooltipHelper.create("Open workspace insights"));
        workspaceExpandButton.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-background-radius: 7; " +
                "-fx-border-color: #b9d9d0; " +
                "-fx-border-radius: 7; " +
                "-fx-text-fill: #24534b; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 7 10 7 10; " +
                "-fx-effect: dropshadow(gaussian, rgba(27, 55, 49, 0.14), 10, 0.12, 0, 2);"
        );
        workspaceExpandButton.setOnAction(e -> setWorkspacePanelCollapsed(false));
        AnchorPane.setTopAnchor(workspaceExpandButton, 12.0);
        AnchorPane.setRightAnchor(workspaceExpandButton, 12.0);
        canvasContainer.getChildren().add(workspaceExpandButton);

        workspaceInsightsPanel = createWorkspaceInsightsPanel();
        workspaceInsightsPanel.setVisible(true);
        workspaceInsightsPanel.managedProperty().bind(workspaceInsightsPanel.visibleProperty());
        AnchorPane.setTopAnchor(workspaceInsightsPanel, 0.0);
        AnchorPane.setRightAnchor(workspaceInsightsPanel, 0.0);
        AnchorPane.setBottomAnchor(workspaceInsightsPanel, 0.0);
        canvasContainer.getChildren().add(workspaceInsightsPanel);
        setCenter(canvasContainer);

        // BOTTOM: Status Bar
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(6, 12, 6, 12));
        statusBar.setStyle(
            "-fx-background-color: #ffffff; " +
                "-fx-border-color: #d9e3df; " +
                "-fx-border-width: 1 0 0 0;"
        );
        statusLabel.setStyle("-fx-text-fill: #2f5049;");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        mapContextLabel = new Label("");
        mapContextLabel.setStyle("-fx-text-fill: #6b827b; -fx-font-size: 11px;");
        statusBar.getChildren().addAll(statusLabel, spacer, mapContextLabel, progressBar);
        setBottom(statusBar);
        updateWorkspaceInsights();
    }

    private VBox createWorkspaceInsightsPanel() {
        VBox panel = new VBox(12);
        panel.setPrefWidth(260);
        panel.setMinWidth(230);
        panel.setPadding(new Insets(14));
        panel.setStyle(
                "-fx-background-color: #fbfdfc; " +
                "-fx-border-color: #d9e3df; " +
                "-fx-border-width: 0 0 0 1;"
        );

        Label heading = new Label("Workspace");
        heading.setStyle("-fx-font-weight: bold; -fx-text-fill: #1f3f3a; -fx-font-size: 13px;");
        Button collapseButton = new Button("<");
        collapseButton.setMinWidth(28);
        collapseButton.setTooltip(TooltipHelper.create("Collapse workspace panel"));
        collapseButton.setOnAction(e -> setWorkspacePanelCollapsed(true));
        Region headingSpacer = new Region();
        HBox.setHgrow(headingSpacer, Priority.ALWAYS);
        HBox panelHeader = new HBox(8, heading, headingSpacer, collapseButton);
        panelHeader.setAlignment(Pos.CENTER_LEFT);

        projectTitleLabel = new Label("Untitled workspace");
        projectTitleLabel.setWrapText(true);
        projectTitleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #182f2a;");

        projectMetaLabel = new Label("0 layer(s) / 0 digitized feature(s)");
        projectMetaLabel.setWrapText(true);
        projectMetaLabel.setStyle("-fx-text-fill: #5f746e; -fx-font-size: 11px;");

        VBox projectCard = new VBox(5, projectTitleLabel, projectMetaLabel);
        projectCard.setStyle(
                "-fx-background-color: #f0f7f4; " +
                "-fx-background-radius: 8; " +
                "-fx-border-color: #d5e6e0; " +
                "-fx-border-radius: 8; " +
                "-fx-padding: 10;"
        );

        Label insightHeading = new Label("Insights");
        insightHeading.setStyle("-fx-font-weight: bold; -fx-text-fill: #1f3f3a; -fx-font-size: 13px;");

        insightSummaryLabel = new Label("Ready for source data");
        insightSummaryLabel.setWrapText(true);
        insightSummaryLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #24534b;");

        insightDetailLabel = new Label("");
        insightDetailLabel.setWrapText(true);
        insightDetailLabel.setStyle("-fx-text-fill: #5f746e; -fx-font-size: 11px; -fx-line-spacing: 3px;");

        VBox insightCard = new VBox(7, insightSummaryLabel, insightDetailLabel);
        insightCard.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-background-radius: 8; " +
                "-fx-border-color: #dce8e4; " +
                "-fx-border-radius: 8; " +
                "-fx-padding: 10;"
        );

        Label nextHeading = new Label("Next Best Actions");
        nextHeading.setStyle("-fx-font-weight: bold; -fx-text-fill: #1f3f3a; -fx-font-size: 13px;");

        VBox actions = new VBox(6,
                createActionHint("Import layers to build context"),
                createActionHint("Select a feature to inspect attributes"),
                createActionHint("Run analysis when vector layers are ready"),
                createActionHint("Export a map after review")
        );

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        panel.getChildren().addAll(panelHeader, projectCard, insightHeading, insightCard, nextHeading, actions, spacer);
        return panel;
    }

    private void setWorkspacePanelCollapsed(boolean collapsed) {
        workspacePanelCollapsed = collapsed;
        if (workspaceInsightsPanel != null) {
            workspaceInsightsPanel.setVisible(!collapsed);
            workspaceInsightsPanel.setMouseTransparent(collapsed);
        }
        if (workspaceExpandButton != null) {
            workspaceExpandButton.setVisible(collapsed);
            workspaceExpandButton.setMouseTransparent(!collapsed);
        }
        if (mapSurface != null) {
            scheduleMapSurfaceSync();
        }
    }

    private void installMapSurfaceSceneSync(Pane canvasContainer) {
        canvasContainer.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }

            newScene.widthProperty().addListener((widthObs, oldWidth, newWidth) -> scheduleMapSurfaceSync());
            newScene.heightProperty().addListener((heightObs, oldHeight, newHeight) -> scheduleMapSurfaceSync());
            newScene.windowProperty().addListener((windowObs, oldWindow, newWindow) -> {
                if (newWindow instanceof Stage stage) {
                    stage.widthProperty().addListener((stageWidthObs, oldWidth, newWidth) -> scheduleMapSurfaceSync());
                    stage.heightProperty().addListener((stageHeightObs, oldHeight, newHeight) -> scheduleMapSurfaceSync());
                    stage.maximizedProperty().addListener((maxObs, wasMaximized, isMaximized) -> scheduleMapSurfaceSync());
                    stage.fullScreenProperty().addListener((fullObs, wasFullScreen, isFullScreen) -> scheduleMapSurfaceSync());
                    scheduleMapSurfaceSync();
                }
            });
            scheduleMapSurfaceSync();
        });
    }

    private void installMapSurfaceEventForwarding(Pane canvasContainer) {
        canvasContainer.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> forwardMapSurfaceMouseEvent(event, canvasContainer));
        canvasContainer.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> forwardMapSurfaceMouseEvent(event, canvasContainer));
        canvasContainer.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> forwardMapSurfaceMouseEvent(event, canvasContainer));
        canvasContainer.addEventHandler(ScrollEvent.SCROLL, event -> forwardMapSurfaceScrollEvent(event, canvasContainer));
    }

    private void forwardMapSurfaceMouseEvent(MouseEvent event, Pane canvasContainer) {
        if (event.getTarget() != canvasContainer || mapCanvas == null) {
            return;
        }
        syncMapCanvasToSurface();
        mapCanvas.fireEvent(event.copyFor(mapCanvas, mapCanvas));
        event.consume();
    }

    private void forwardMapSurfaceScrollEvent(ScrollEvent event, Pane canvasContainer) {
        if (event.getTarget() != canvasContainer || mapCanvas == null) {
            return;
        }
        syncMapCanvasToSurface();
        mapCanvas.fireEvent(event.copyFor(mapCanvas, mapCanvas));
        event.consume();
    }

    private void scheduleMapSurfaceSync() {
        if (mapSurface == null) {
            return;
        }
        mapSurface.requestLayout();
        mapSurfaceResizeDebounce.playFromStart();
        Platform.runLater(() -> {
            if (mapSurface != null) {
                mapSurface.requestLayout();
                mapSurfaceResizeDebounce.playFromStart();
            }
        });
    }

    private boolean syncMapCanvasToSurface() {
        if (mapSurface == null || mapCanvas == null) {
            return false;
        }

        double targetWidth = largestFinitePositive(
                mapSurface.getWidth(),
                mapSurface.getLayoutBounds().getWidth(),
                mapSurface.getBoundsInParent().getWidth());
        double targetHeight = largestFinitePositive(
                mapSurface.getHeight(),
                mapSurface.getLayoutBounds().getHeight(),
                mapSurface.getBoundsInParent().getHeight());

        if (mapSurface.getScene() != null) {
            Bounds sceneBounds = mapSurface.localToScene(mapSurface.getBoundsInLocal());
            targetWidth = largestFinitePositive(targetWidth, sceneBounds.getWidth());
            targetHeight = largestFinitePositive(targetHeight, sceneBounds.getHeight());
        }

        targetWidth = Math.max(1.0, targetWidth);
        targetHeight = Math.max(1.0, targetHeight);
        boolean changed = Math.abs(targetWidth - lastSyncedMapSurfaceWidth) > 0.5
                || Math.abs(targetHeight - lastSyncedMapSurfaceHeight) > 0.5
                || Math.abs(targetWidth - mapCanvas.getWidth()) > 0.5
                || Math.abs(targetHeight - mapCanvas.getHeight()) > 0.5;

        if (changed) {
            mapCanvas.resizeRelocate(0, 0, targetWidth, targetHeight);
            lastSyncedMapSurfaceWidth = targetWidth;
            lastSyncedMapSurfaceHeight = targetHeight;
            log.info("Map canvas synchronized to surface bounds: {}x{}",
                    Math.round(targetWidth),
                    Math.round(targetHeight));
        }

        return changed;
    }

    private double largestFinitePositive(double... values) {
        double largest = 0.0;
        for (double value : values) {
            if (Double.isFinite(value) && value > largest) {
                largest = value;
            }
        }
        return largest;
    }

    private Label createActionHint(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle(
                "-fx-text-fill: #49675f; " +
                "-fx-font-size: 11px; " +
                "-fx-padding: 7 9 7 9; " +
                "-fx-background-color: #f6faf8; " +
                "-fx-background-radius: 7; " +
                "-fx-border-color: #e0ebe7; " +
                "-fx-border-radius: 7;"
        );
        return label;
    }

    private JobCenterEntry createJobCenterEntry(boolean retry) {
        JobCenterEntry entry = new JobCenterEntry(retry ? "RETRY_QUEUED" : "QUEUED");
        jobCenterRows.add(0, entry);
        if (jobCenterTable != null) {
            jobCenterTable.getSelectionModel().select(entry);
        }
        return entry;
    }

    private void updateJobCenterEntry(
            JobCenterEntry entry,
            String status,
            String phase,
            int progressPercent,
            String message,
            String updatedAtUtc,
            String errorCode,
            String errorDetail,
            List<Path> artifactPaths) {
        if (entry == null) {
            return;
        }
        Runnable update = () -> {
            entry.status = status == null ? entry.status : status;
            entry.phase = phase == null ? entry.phase : phase;
            entry.progressPercent = Math.max(0, Math.min(100, progressPercent));
            entry.message = message == null ? entry.message : message;
            entry.updatedAtUtc = updatedAtUtc == null ? entry.updatedAtUtc : updatedAtUtc;
            if (jobCenterTable != null) {
                jobCenterTable.refresh();
            }
        };

        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    public MapCanvas getMapCanvas() {
        return mapCanvas;
    }

    public void setStatus(String message) {
        statusLabel.setText(message);
        updateWorkspaceInsights();
        log.debug("Status updated: {}", message);
    }

    private void updateWorkspaceInsights() {
        if (mapCanvas == null) {
            return;
        }

        int totalLayers = mapCanvas.getLayerCount();
        int vectorLayers = 0;
        int rasterLayers = 0;
        for (int i = 0; i < totalLayers; i++) {
            if (mapCanvas.isRasterLayer(i)) {
                rasterLayers++;
            } else if (mapCanvas.getVectorFeatureSource(i) != null) {
                vectorLayers++;
            }
        }

        int digitizedFeatures = mapCanvas.getDigitizedFeatureCount();
        String projectName = currentProjectPath == null || currentProjectPath.getFileName() == null
                ? "Untitled workspace"
                : currentProjectPath.getFileName().toString();

        if (projectTitleLabel != null) {
            projectTitleLabel.setText(projectName);
        }
        if (projectMetaLabel != null) {
            projectMetaLabel.setText(totalLayers + " layer(s) / " + digitizedFeatures + " digitized feature(s)");
        }
        if (layerCountBadge != null) {
            layerCountBadge.setText(totalLayers + " total");
        }
        if (insightSummaryLabel != null) {
            if (totalLayers == 0) {
                insightSummaryLabel.setText("Ready for source data");
            } else if (digitizedFeatures > 0) {
                insightSummaryLabel.setText("Review work in progress");
            } else {
                insightSummaryLabel.setText("Project context restored");
            }
        }
        if (insightDetailLabel != null) {
            String basemap = activeBasemapName == null ? BASEMAP_NONE : activeBasemapName;
            insightDetailLabel.setText(
                    "Vectors: " + vectorLayers + "\n" +
                    "Rasters: " + rasterLayers + "\n" +
                    "Basemap: " + basemap + "\n" +
                    "Last status: " + statusLabel.getText()
            );
        }
        if (mapContextLabel != null) {
            mapContextLabel.setText("Layers " + totalLayers + " | Digitized " + digitizedFeatures + " | Basemap " + activeBasemapName);
        }
    }

    public void setReadOnlyMode(boolean enabled, String reason) {
        this.readOnlyMode = enabled;
        this.readOnlyReason = reason == null ? "" : reason;
        applyReadOnlyMode();

        if (enabled && !this.readOnlyReason.isBlank()) {
            setStatus("Read-only mode: " + this.readOnlyReason);
        }
    }

    private void registerReadOnlyControl(Control control) {
        if (control != null) {
            readOnlyControls.add(control);
        }
    }

    private void applyReadOnlyMode() {
        for (Control control : readOnlyControls) {
            control.setDisable(readOnlyMode);
        }

        if (readOnlyMode && mapCanvas != null) {
            mapCanvas.setEditMode(MapCanvas.EditMode.PAN);
        }
    }

    public void setOnBackToMainMenu(Runnable onBackToMainMenu) {
        this.onBackToMainMenu = onBackToMainMenu;
    }

    private void handleBackToMainMenu() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Return to Main Menu");
        confirm.setHeaderText("Return to project browser?");
        confirm.setContentText("Current project session will be saved before leaving.");

        ButtonType saveAndGo = new ButtonType("Save and Go", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(saveAndGo, cancel);

        if (confirm.showAndWait().orElse(cancel) != saveAndGo) {
            return;
        }

        saveProjectSession();
        if (onBackToMainMenu != null) {
            onBackToMainMenu.run();
        }
    }

    private void handleShowEditingTutorial() {
        EditingGuidePanel tutorialPanel = new EditingGuidePanel();
        tutorialPanel.showGuideForMode(currentEditModeLabel);

        Dialog<Void> tutorialDialog = new Dialog<>();
        tutorialDialog.setTitle("Feature Editing Tutorial");
        tutorialDialog.setHeaderText("Simple step-by-step guide for each editing tool");
        tutorialDialog.initModality(Modality.APPLICATION_MODAL);

        DialogPane dialogPane = tutorialDialog.getDialogPane();
        dialogPane.setContent(tutorialPanel);
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);
        dialogPane.setPrefWidth(760);
        dialogPane.setPrefHeight(620);

        tutorialDialog.showAndWait();
    }

    private void handleShowAnalysisTutorial() {
        AnalysisGuidePanel analysisPanel = new AnalysisGuidePanel();
        analysisPanel.showOperation("Buffer");

        Dialog<Void> analysisDialog = new Dialog<>();
        analysisDialog.setTitle("Analysis Toolbox Tutorial");
        analysisDialog.setHeaderText("Step-by-step guide for spatial analysis operations");
        analysisDialog.initModality(Modality.APPLICATION_MODAL);

        DialogPane dialogPane = analysisDialog.getDialogPane();
        dialogPane.setContent(analysisPanel);
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);
        dialogPane.setPrefWidth(800);
        dialogPane.setPrefHeight(650);

        analysisDialog.showAndWait();
    }

    private void handleSendDiagnostics() {
        Dialog<ButtonType> consentDialog = new Dialog<>();
        consentDialog.setTitle("Send Diagnostics");
        consentDialog.setHeaderText("Create diagnostics bundle for TerraGIS support");

        ButtonType createBundleType = new ButtonType("Create Bundle", ButtonBar.ButtonData.OK_DONE);
        consentDialog.getDialogPane().getButtonTypes().addAll(createBundleType, ButtonType.CANCEL);

        Label details = new Label(
                "The bundle includes application logs and captured crash incidents.\n"
                        + "Review the privacy notice before sharing with support.");
        details.setWrapText(true);

        CheckBox consentCheck = new CheckBox("I consent to creating a diagnostics bundle for support.");
        consentCheck.setWrapText(true);

        VBox content = new VBox(10, details, consentCheck);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(5, 0, 0, 0));
        consentDialog.getDialogPane().setContent(content);

        Node createButton = consentDialog.getDialogPane().lookupButton(createBundleType);
        createButton.disableProperty().bind(consentCheck.selectedProperty().not());

        Optional<ButtonType> result = consentDialog.showAndWait();
        if (result.isEmpty() || result.get() != createBundleType) {
            setStatus("Diagnostics bundle creation cancelled");
            return;
        }

        try {
            String appVersion = AppConfig.getInstance().getAppVersion();
            String sessionId = CrashDiagnosticsManager.getSessionId();
            DiagnosticsBundleService.BundleResult bundleResult = diagnosticsBundleService.createBundle(appVersion, sessionId);
            setStatus("Diagnostics bundle created: " + bundleResult.bundlePath().getFileName());
            showInfoPopup(
                    "Diagnostics Bundle Ready",
                    "Bundle created with " + bundleResult.fileCount() + " files:\n" + bundleResult.bundlePath());
        } catch (Exception ex) {
            log.error("Failed to create diagnostics bundle", ex);
            showError("Diagnostics Error", "Failed to create diagnostics bundle: " + conciseMessage(ex));
        }
    }

    private void updateAttributeTable(MapCanvas.SelectedFeature selectedFeature) {
        if (selectedFeature == null) {
            attributeTable.getItems().clear();
            if (selectedFeatureBadge != null) {
                selectedFeatureBadge.setText("Selected: none");
            }
            return;
        }

        if (selectedFeatureBadge != null) {
            selectedFeatureBadge.setText("Selected: " + selectedFeature.geometryType() + " #" + selectedFeature.id());
        }
        setStatus("Selected feature: " + selectedFeature.geometryType() + " #" + selectedFeature.id());

        attributeTable.getItems().setAll(
                new AttributeRow("id", Integer.toString(selectedFeature.id())),
                new AttributeRow("geometry", selectedFeature.geometryType()),
                new AttributeRow("center_x", String.format(Locale.ROOT, "%.6f", selectedFeature.centerX())),
                new AttributeRow("center_y", String.format(Locale.ROOT, "%.6f", selectedFeature.centerY())),
                new AttributeRow("name", selectedFeature.name()),
                new AttributeRow("notes", selectedFeature.notes()));
    }

    public void setProgress(double progress) {
        // Unbind in case it was bound to a task
        progressBar.progressProperty().unbind();
        
        if (progress < 0) {
            progressBar.setVisible(false);
        } else {
            progressBar.setVisible(true);
            progressBar.setProgress(progress);
        }
    }

    /**
     * Handler for "Open Vector..." button.
     * Shows file chooser and loads vector file (Shapefile, GeoPackage, GeoJSON).
     */
    private void handleOpenVector() {
        log.info("Opening vector file dialog");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Vector File");
        FileChooser.ExtensionFilter vectorFilter =
                new FileChooser.ExtensionFilter("Vector Files", "*.shp", "*.gpkg", "*.geojson", "*.json");
        FileChooser.ExtensionFilter shapeFilter = new FileChooser.ExtensionFilter("Shapefile", "*.shp");
        FileChooser.ExtensionFilter gpkgFilter = new FileChooser.ExtensionFilter("GeoPackage", "*.gpkg");
        FileChooser.ExtensionFilter geoJsonFilter = new FileChooser.ExtensionFilter("GeoJSON", "*.json", "*.geojson");
        FileChooser.ExtensionFilter allFilter = new FileChooser.ExtensionFilter("All Files", "*.*");

        fileChooser.getExtensionFilters().addAll(vectorFilter, shapeFilter, gpkgFilter, geoJsonFilter, allFilter);
        fileChooser.setSelectedExtensionFilter(vectorFilter);

        File file = fileChooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            loadVectorFile(file);
        }
    }

    /**
     * Handler for "Open Raster..." button.
     * Shows file chooser and loads raster file (GeoTIFF).
     */
    private void handleOpenRaster() {
        log.info("Opening raster file dialog");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Raster File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Raster Files", "*.tif", "*.tiff", "*.asc", "*.img"),
            new FileChooser.ExtensionFilter("GeoTIFF", "*.tif", "*.tiff"),
            new FileChooser.ExtensionFilter("Arc/Info ASCII Grid", "*.asc"),
            new FileChooser.ExtensionFilter("Erdas Imagine", "*.img"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            loadRasterFile(file);
        }
    }

    private void queueBasemapSelection(String basemapName) {
        pendingBasemapSelection = basemapName == null ? BASEMAP_NONE : basemapName;
        pendingBasemapPreviousSelection = activeBasemapName;
        basemapSwitchDebounce.playFromStart();
        setStatus("Switching basemap...");
    }

    private void performBasemapSelection(String basemapName, String previousBasemapName) {
        String requested = basemapName == null ? BASEMAP_NONE : basemapName;
        String previousBasemap = previousBasemapName == null ? activeBasemapName : previousBasemapName;

        if (requested.equals(previousBasemap) && findMapLayerIndexByTitle(BASEMAP_LAYER_TITLE) >= 0) {
            setStatus(requested + " basemap already active");
            return;
        }

        if (basemapLoadTask != null && basemapLoadTask.isRunning()) {
            basemapLoadTask.cancel(true);
        }

        long requestId = basemapLoadSequence.incrementAndGet();
        basemapCombo.setDisable(true);

        if (BASEMAP_NONE.equalsIgnoreCase(basemapName)) {
            boolean removed = removeActiveBasemapLayer();
            if (removed) {
                refreshLayerList(null);
                saveLayerState();
            }
            activeBasemapName = BASEMAP_NONE;
            basemapCombo.setDisable(false);
            setStatus("Basemap cleared");
            return;
        }

        BasemapDefinition definition = BASEMAP_DEFINITIONS.get(basemapName);
        if (definition == null) {
            basemapCombo.setDisable(false);
            setStatus("Unknown basemap: " + basemapName);
            return;
        }

        loadBasemapLayer(definition, requested, previousBasemap, requestId);
    }

    private void loadBasemapLayer(BasemapDefinition definition, String requestedBasemapName, String previousBasemapName, long requestId) {
        setStatus("Connecting to " + definition.statusLabel() + "...");
        setProgress(0.2);

        Task<LoadedBasemap> loadTask = new Task<>() {
            @Override
            protected LoadedBasemap call() throws Exception {
                List<WmtsCandidate> candidates = buildWmtsCandidates(definition);
                Exception lastError = null;

                for (WmtsCandidate candidate : candidates) {
                    for (int attempt = 1; attempt <= BASEMAP_MAX_LOAD_ATTEMPTS; attempt++) {
                        if (isCancelled()) {
                            throw new InterruptedException("Basemap load cancelled");
                        }

                        try {
                            updateMessage("Loading " + definition.statusLabel() + " (attempt " + attempt + "/" + BASEMAP_MAX_LOAD_ATTEMPTS + ")");
                            WMTSMapLayer layer = loadWmtsLayer(candidate);
                            layer.setTitle(BASEMAP_LAYER_TITLE);
                            return new LoadedBasemap(layer, attempt, candidate.sourceLabel());
                        } catch (Exception ex) {
                            lastError = ex;
                            if (attempt < BASEMAP_MAX_LOAD_ATTEMPTS) {
                                updateMessage("Basemap tiles delayed; retrying...");
                                try {
                                    Thread.sleep(BASEMAP_RETRY_BACKOFF_MS * attempt);
                                } catch (InterruptedException interruptedException) {
                                    Thread.currentThread().interrupt();
                                    throw interruptedException;
                                }
                            }
                        }
                    }
                }

                if (lastError != null) {
                    throw lastError;
                }
                throw new IllegalStateException("Basemap load failed without explicit error");
            }

            @Override
            protected void succeeded() {
                if (requestId != basemapLoadSequence.get()) {
                    return;
                }
                try {
                    alignViewportForBasemap();

                    int previousLayerIndex = findMapLayerIndexByTitle(BASEMAP_LAYER_TITLE);
                    if (previousLayerIndex >= 0) {
                        mapCanvas.getMapContent().layers().get(previousLayerIndex).setTitle(OLD_BASEMAP_LAYER_TITLE);
                    }

                    LoadedBasemap loadedBasemap = getValue();
                    WMTSMapLayer basemapLayer = loadedBasemap.layer();
                    mapCanvas.getMapContent().addLayer(basemapLayer);
                    int newLayerIndex = findMapLayerIndexByTitle(BASEMAP_LAYER_TITLE);
                    if (newLayerIndex > 0) {
                        mapCanvas.getMapContent().moveLayer(newLayerIndex, 0);
                    }
                    mapCanvas.invalidateLayerMetadataCache();

                    int oldLayerIndex = findMapLayerIndexByTitle(OLD_BASEMAP_LAYER_TITLE);
                    if (oldLayerIndex >= 0) {
                        mapCanvas.removeLayer(oldLayerIndex);
                    }

                    mapCanvas.draw();
                    refreshLayerList(BASEMAP_LAYER_TITLE);
                    saveLayerState();
                    activeBasemapName = requestedBasemapName;
                    String sourceLabel = loadedBasemap.sourceLabel().toLowerCase(Locale.ROOT);
                    if (loadedBasemap.attempt() > 1 || sourceLabel.contains("fallback")) {
                        if (sourceLabel.contains("global-fallback")) {
                            setStatus(definition.statusLabel() + " loaded via alternate provider");
                        } else {
                            setStatus(definition.statusLabel() + " loaded after retry");
                        }
                    } else {
                        setStatus(definition.statusLabel() + " loaded");
                    }
                } catch (Throwable ex) {
                    log.error("Failed to attach basemap layer", ex);
                    setStatus("Basemap tiles delayed; keeping previous basemap");
                } finally {
                    basemapCombo.setDisable(false);
                    setProgress(-1);
                }
            }

            @Override
            protected void failed() {
                if (requestId != basemapLoadSequence.get()) {
                    return;
                }
                Throwable ex = getException();
                log.warn("Failed to load basemap", ex);
                setStatus("Basemap tiles delayed; keeping previous basemap");
                basemapCombo.setDisable(false);
                if (previousBasemapName != null && !previousBasemapName.equals(basemapCombo.getValue())) {
                    suppressBasemapSelectionEvent = true;
                    basemapCombo.setValue(previousBasemapName);
                    suppressBasemapSelectionEvent = false;
                }
                setProgress(-1);
            }

            @Override
            protected void cancelled() {
                if (requestId != basemapLoadSequence.get()) {
                    return;
                }
                basemapCombo.setDisable(false);
                setProgress(-1);
                setStatus("Basemap switch updated");
            }
        };

        basemapLoadTask = loadTask;
        Thread thread = new Thread(loadTask, "load-basemap");
        thread.setDaemon(true);
        thread.start();
    }

    private void alignViewportForBasemap() {
        try {
            var viewport = mapCanvas.getMapContent().getViewport();
            if (viewport == null) {
                return;
            }

            ReferencedEnvelope currentBounds = viewport.getBounds();

            // Preserve current extent/CRS whenever possible so vector overlays stay aligned
            // when toggling WMTS basemaps over GeoJSON layers.
            if (currentBounds != null && !currentBounds.isEmpty() && currentBounds.getCoordinateReferenceSystem() != null) {
                return;
            }

            if (currentBounds != null && !currentBounds.isEmpty()) {
                if (looksLikeLonLatBounds(currentBounds)) {
                    org.geotools.api.referencing.crs.CoordinateReferenceSystem wgs84 =
                            org.geotools.referencing.CRS.decode("EPSG:4326", true);
                    viewport.setCoordinateReferenceSystem(wgs84);
                    viewport.setBounds(new ReferencedEnvelope(
                            currentBounds.getMinX(),
                            currentBounds.getMaxX(),
                            currentBounds.getMinY(),
                            currentBounds.getMaxY(),
                            wgs84));
                    return;
                }

                var inferredCrs = inferViewportCrsFromNonBasemapLayers();
                if (inferredCrs != null) {
                    viewport.setCoordinateReferenceSystem(inferredCrs);
                    viewport.setBounds(new ReferencedEnvelope(
                            currentBounds.getMinX(),
                            currentBounds.getMaxX(),
                            currentBounds.getMinY(),
                            currentBounds.getMaxY(),
                            inferredCrs));
                    return;
                }
            }

            org.geotools.api.referencing.crs.CoordinateReferenceSystem webMercator =
                    org.geotools.referencing.CRS.decode("EPSG:3857", true);
            viewport.setCoordinateReferenceSystem(webMercator);
            viewport.setBounds(defaultWebMercatorWorldBounds());
        } catch (Exception ex) {
            log.warn("Failed to align viewport CRS for basemap; using world fallback", ex);
            try {
                var viewport = mapCanvas.getMapContent().getViewport();
                if (viewport != null) {
                    org.geotools.api.referencing.crs.CoordinateReferenceSystem webMercator =
                            org.geotools.referencing.CRS.decode("EPSG:3857", true);
                    viewport.setCoordinateReferenceSystem(webMercator);
                    viewport.setBounds(defaultWebMercatorWorldBounds());
                }
            } catch (Exception ignored) {
                // Keep basemap flow resilient.
            }
        }
    }

    private org.geotools.api.referencing.crs.CoordinateReferenceSystem inferViewportCrsFromNonBasemapLayers() {
        List<Layer> layers = mapCanvas.getMapContent().layers();
        for (Layer layer : layers) {
            if (layer == null) {
                continue;
            }

            String title = layer.getTitle();
            if (BASEMAP_LAYER_TITLE.equals(title) || LEGACY_BASEMAP_LAYER_TITLE.equals(title) || OLD_BASEMAP_LAYER_TITLE.equals(title)) {
                continue;
            }

            try {
                ReferencedEnvelope layerBounds = layer.getBounds();
                if (layerBounds != null && !layerBounds.isEmpty() && layerBounds.getCoordinateReferenceSystem() != null) {
                    return layerBounds.getCoordinateReferenceSystem();
                }
            } catch (Exception ignored) {
                // Best-effort inference only.
            }
        }
        return null;
    }

    private ReferencedEnvelope defaultWebMercatorWorldBounds() {
        try {
            org.geotools.api.referencing.crs.CoordinateReferenceSystem webMercator =
                    org.geotools.referencing.CRS.decode("EPSG:3857", true);
            return new ReferencedEnvelope(
                    -20_037_508.3427892,
                    20_037_508.3427892,
                    -20_037_508.3427892,
                    20_037_508.3427892,
                    webMercator);
        } catch (Exception ex) {
            return new ReferencedEnvelope(
                    -20_037_508.3427892,
                    20_037_508.3427892,
                    -20_037_508.3427892,
                    20_037_508.3427892,
                    null);
        }
    }

    private boolean looksLikeLonLatBounds(ReferencedEnvelope bounds) {
        if (bounds == null || bounds.isEmpty()) {
            return false;
        }
        return bounds.getMinX() >= -180.0
                && bounds.getMaxX() <= 180.0
                && bounds.getMinY() >= -90.0
                && bounds.getMaxY() <= 90.0;
    }

    private boolean removeActiveBasemapLayer() {
        int current = findMapLayerIndexByTitle(BASEMAP_LAYER_TITLE);
        if (current >= 0) {
            return mapCanvas.removeLayer(current);
        }

        // Backward compatibility with previous persisted title.
        int legacy = findMapLayerIndexByTitle(LEGACY_BASEMAP_LAYER_TITLE);
        if (legacy >= 0) {
            return mapCanvas.removeLayer(legacy);
        }
        return false;
    }

    private List<WmtsCandidate> buildWmtsCandidates(BasemapDefinition definition) {
        List<WmtsCandidate> candidates = new ArrayList<>();
        candidates.add(new WmtsCandidate(definition.endpoint(), definition.preferredLayerName(), "primary"));

        // Built-in cross-provider fallback to reduce single-vendor outages.
        candidates.add(new WmtsCandidate(BASEMAP_GLOBAL_FALLBACK_WMTS, null, "global-fallback"));

        String fallbackUrl = firstNonBlank(
                System.getenv("TERRAGIS_BASEMAP_FALLBACK_WMTS"),
                System.getProperty("terragis.basemap.fallback.wmts"));
        if (fallbackUrl != null && !fallbackUrl.isBlank()) {
            candidates.add(new WmtsCandidate(fallbackUrl, definition.preferredLayerName(), "fallback"));
        }

        return candidates;
    }

    private WMTSMapLayer loadWmtsLayer(WmtsCandidate candidate) throws Exception {
        URL endpoint = URI.create(candidate.endpoint()).toURL();
        WebMapTileServer wmts = new WebMapTileServer(endpoint);
        WMTSCapabilities capabilities = wmts.getCapabilities();
        List<WMTSLayer> layerList = capabilities == null ? null : capabilities.getLayerList();
        if (layerList == null || layerList.isEmpty()) {
            throw new IllegalStateException("Basemap WMTS returned no layers");
        }

        WMTSLayer selectedLayer = null;
        String preferred = candidate.preferredLayerName();
        if (preferred != null && !preferred.isBlank()) {
            List<String> preferredLayerNames = List.of(preferred, preferred.toLowerCase(Locale.ROOT));
            for (String preferredName : preferredLayerNames) {
                for (WMTSLayer layer : layerList) {
                    if (layer != null && preferredName.equalsIgnoreCase(layer.getName())) {
                        selectedLayer = layer;
                        break;
                    }
                }
                if (selectedLayer != null) {
                    break;
                }
            }
        }

        if (selectedLayer == null) {
            for (WMTSLayer layer : layerList) {
                if (layer == null || layer.getName() == null || layer.getName().isBlank()) {
                    continue;
                }
                String nameLower = layer.getName().toLowerCase(Locale.ROOT);
                if (nameLower.contains("imagery") || nameLower.contains("street") || nameLower.contains("topo") || nameLower.contains("terrain")) {
                    selectedLayer = layer;
                    break;
                }
            }
        }

        if (selectedLayer == null) {
            for (WMTSLayer layer : layerList) {
                if (layer != null && layer.getName() != null && !layer.getName().isBlank()) {
                    selectedLayer = layer;
                    break;
                }
            }
        }

        if (selectedLayer == null) {
            throw new IllegalStateException("Could not resolve basemap layer from WMTS capabilities");
        }

        return new WMTSMapLayer(wmts, selectedLayer);
    }

    private int findMapLayerIndexByTitle(String title) {
        if (title == null || title.isBlank()) {
            return -1;
        }

        List<Layer> layers = mapCanvas.getMapContent().layers();
        for (int index = 0; index < layers.size(); index++) {
            Layer layer = layers.get(index);
            if (layer != null && title.equals(layer.getTitle())) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Handler for "Connect AI Backend..." button.
     * Placeholder for future AI integration.
     */
    private void handleConnectAI() {
        log.info("AI Backend connection requested");
        setStatus("Attempting AI handshake...");

        Task<AiRoundTripService.RoundTripResult> task = new Task<>() {
            @Override
            protected AiRoundTripService.RoundTripResult call() {
                if (!AiBackendManager.ensureBackendRunning()) {
                    log.warn("AI backend not reachable and auto-start did not succeed: {}", AiBackendManager.getLastStartupIssue());
                    return null;
                }

                try (TerraApiClient client = new TerraApiClient("localhost", 6565)) {
                    return new AiRoundTripService(client).runHandshake("terragis-desktop");
                } catch (Exception ex) {
                    log.warn("AI handshake failed: {}", ex.getMessage());
                    return null;
                }
            }

            @Override
            protected void succeeded() {
                AiRoundTripService.RoundTripResult result = getValue();
                if (result == null) {
                    setStatus("AI backend unavailable on localhost:6565 (set TERRAGIS_MODEL_PATH and Python deps)");
                    String issue = AiBackendManager.getLastStartupIssue();
                    String detail = (issue == null || issue.isBlank())
                            ? "Could not connect to AI backend at localhost:6565. Ensure ai_backend dependencies are installed and TERRAGIS_MODEL_PATH is set."
                            : issue;
                    showInfoPopup("AI Backend", detail);
                    return;
                }

                setStatus("AI handshake successful: " + result.message());
                showInfoPopup("AI Backend", "Connected successfully: " + result.message());
            }

            @Override
            protected void failed() {
                Throwable error = getException();
                log.error("Unexpected AI handshake error", error);
                setStatus("AI handshake failed unexpectedly; see logs");
                String issue = AiBackendManager.getLastStartupIssue();
                String detail = (issue == null || issue.isBlank())
                        ? (error != null && error.getMessage() != null && !error.getMessage().isBlank()
                                ? error.getMessage()
                                : "Unexpected connection error. See logs for details.")
                        : issue;
                showInfoPopup("AI Backend", detail);
            }
        };

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void handleManageAiActions() {
        openAiManagerWindow();
    }

    private Path choosePrecomputedCovariatesFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Precomputed Covariates Folder");
        File selected = chooser.showDialog(getScene().getWindow());
        if (selected == null) {
            return null;
        }

        Path folder = selected.toPath();
        if (!Files.isDirectory(folder)) {
            showError("Run SOC from Precomputed Covariates Folder", "Selected path is not a folder.");
            return null;
        }

        if (!looksLikePrecomputedCovariatesFolder(folder)) {
            showError(
                    "Run SOC from Precomputed Covariates Folder",
                    "Folder does not appear to contain the required covariate TIFFs (dtm, slope, twi, cropland mask, bsi).");
            return null;
        }

        return folder;
    }

    private boolean looksLikePrecomputedCovariatesFolder(Path folder) {
        try {
            List<String> names = Files.list(folder)
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                    .toList();

            boolean hasDtm = names.stream().anyMatch(name -> name.endsWith(".tif") || name.endsWith(".tiff"))
                    && names.stream().anyMatch(name -> name.contains("dtm") && (name.endsWith(".tif") || name.endsWith(".tiff")));
            boolean hasSlope = names.stream().anyMatch(name -> name.contains("slope") && (name.endsWith(".tif") || name.endsWith(".tiff")));
            boolean hasTwi = names.stream().anyMatch(name -> name.contains("twi") && (name.endsWith(".tif") || name.endsWith(".tiff")));
            boolean hasCropland = names.stream().anyMatch(name -> (name.contains("cropland") || name.contains("mask")) && (name.endsWith(".tif") || name.endsWith(".tiff")));
            boolean hasBsi = names.stream().anyMatch(name -> name.contains("bsi") && (name.endsWith(".tif") || name.endsWith(".tiff")));
            return hasDtm && hasSlope && hasTwi && hasCropland && hasBsi;
        } catch (Exception ex) {
            log.warn("Failed to inspect covariates folder: {}", folder, ex);
            return false;
        }
    }

    private void startTerraAiJob(String selectedRasterPath, boolean retry) {
        setStatus(retry ? "Retrying TERRA.AI job..." : "Starting TERRA.AI job...");
        setProgress(0.0);
        JobCenterEntry jobEntry = createJobCenterEntry(retry);

        Task<TerraAiRunOutcome> task = new Task<>() {
            @Override
            protected TerraAiRunOutcome call() {
                TerraAiRunOutcome grpcOutcome = runViaGrpcOrchestrator(selectedRasterPath, jobEntry);
                if (grpcOutcome != null) {
                    return grpcOutcome;
                }

                updateMessage("Orchestrator unavailable; falling back to local runner");
                updateJobCenterEntry(jobEntry, "RUNNING", "Local runner", 5,
                        "Orchestrator unavailable; using local runner", "", null, null, List.of());
                TerraAiJobService jobService = new TerraAiJobService();
                TerraAiJobService.JobRunResult local = jobService.runLocalJob();
                return new TerraAiRunOutcome(
                        local.success(),
                        local.message(),
                        null,
                        null,
                        local.outputDirectory(),
                        List.of());
            }

            private TerraAiRunOutcome runViaGrpcOrchestrator(String rasterPath, JobCenterEntry entry) {
                OrchestratorEndpoint endpoint = resolveOrchestratorEndpoint();
                long pollIntervalMs = parseIntOrDefault(System.getenv("TERRAGIS_TERRA_AI_POLL_MS"), 5000);
                long timeoutMinutes = parseIntOrDefault(System.getenv("TERRAGIS_TERRA_AI_JOB_TIMEOUT_MINUTES"), 120);

                if (!TerraAiOrchestratorManager.ensureOrchestratorRunning(endpoint.host(), endpoint.port())) {
                    String issue = TerraAiOrchestratorManager.getLastStartupIssue();
                    updateJobCenterEntry(entry, "FAILED", "Startup", 0,
                            issue == null ? "Orchestrator auto-start failed" : issue,
                            "", "ERR_ORCHESTRATOR_UNAVAILABLE", issue, List.of());
                    return null;
                }

                try (TerraAiOrchestratorClient client = new TerraAiOrchestratorClient(endpoint.host(), endpoint.port())) {
                    updateMessage("Submitting job to TerraAI orchestrator");
                    updateJobCenterEntry(entry, "SUBMITTING", "Queue", 1, "Submitting job", "", null, null, List.of());
                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("requestedBy", "terragis-desktop");
                    if (rasterPath != null && !rasterPath.isBlank()) {
                        try {
                            Path inputPath = Path.of(rasterPath);
                            if (Files.isDirectory(inputPath)) {
                                metadata.put("input_mode", "precomputed_covariates");
                                metadata.put("covariates_dir", inputPath.toString());
                            }
                        } catch (Exception ignored) {
                            // Keep metadata minimal if rasterPath is not a valid local path.
                        }
                    }
                    TerraAiOrchestratorClient.JobSubmission submission = client.submitJob(
                            "terragis-" + UUID.randomUUID(),
                            "",
                            rasterPath == null ? "" : rasterPath,
                            "soc-default",
                            10,
                            metadata);

                        String jobId = submission.jobId();
                    long deadline = System.currentTimeMillis() + (timeoutMinutes * 60_000L);

                    while (System.currentTimeMillis() < deadline) {
                        TerraAiOrchestratorClient.JobStatus status = client.getJobStatus(jobId);
                        int progress = Math.max(0, Math.min(100, status.progressPercent()));
                        updateProgress(progress, 100);

                        String phase = (status.phase() == null || status.phase().isBlank()) ? "running" : status.phase();
                        String msg = (status.message() == null || status.message().isBlank())
                                ? phase + " (" + progress + "%)"
                                : status.message();
                        updateMessage(msg);
                        updateJobCenterEntry(entry,
                            status.status(),
                            phase,
                            progress,
                            msg,
                            status.updatedAtUtc(),
                            status.error() == null ? null : status.error().code(),
                            status.error() == null ? null : status.error().detail(),
                            null);

                        String normalized = status.status() == null ? "" : status.status().toLowerCase(Locale.ROOT);
                        if ("succeeded".equals(normalized) || "completed".equals(normalized)) {
                            List<Path> artifactPaths = new ArrayList<>();
                            for (TerraAiOrchestratorClient.Artifact artifact : status.artifacts()) {
                                Path p = toLocalPath(artifact.uri());
                                if (p != null) {
                                    artifactPaths.add(p);
                                }
                            }
                            updateJobCenterEntry(entry, "COMPLETED", phase, 100,
                                    "Job completed", status.updatedAtUtc(), null, null, artifactPaths);
                            return new TerraAiRunOutcome(true, "TERRA.AI job completed", null, null, null, artifactPaths);
                        }

                        if ("failed".equals(normalized) || "cancelled".equals(normalized) || "expired".equals(normalized)) {
                            String code = status.error() == null ? null : status.error().code();
                            String detail = status.error() == null ? status.message() : status.error().detail();
                            updateJobCenterEntry(entry, status.status(), phase, progress, msg,
                                    status.updatedAtUtc(), code, detail, List.of());
                            return new TerraAiRunOutcome(false, "TERRA.AI job did not complete", code, detail, null, List.of());
                        }

                        Thread.sleep(Math.max(1000L, pollIntervalMs));
                    }

                    updateJobCenterEntry(entry, "FAILED", "Timeout", entry.progressPercent,
                            "Timed out waiting for completion", "", "ERR_JOB_TIMEOUT",
                            "The orchestrator did not complete within the configured timeout.", List.of());
                    return new TerraAiRunOutcome(false, "TERRA.AI job timed out", "ERR_JOB_TIMEOUT", "The orchestrator did not complete within the configured timeout.", null, List.of());
                } catch (Exception ex) {
                    log.info("TerraAI orchestrator path unavailable, local fallback will be used: {}", conciseMessage(ex));
                    updateJobCenterEntry(entry, "FALLBACK", "Local runner", entry.progressPercent,
                            "Orchestrator unavailable; switching to local runner", "", null, null, List.of());
                    return null;
                }
            }

            @Override
            protected void succeeded() {
                progressBar.progressProperty().unbind();
                setProgress(-1);

                TerraAiRunOutcome result = getValue();
                if (result == null) {
                    showError("Run TERRA.AI Job", "No result was returned from the local job runner.");
                    return;
                }

                if (!result.success()) {
                    setStatus("TERRA.AI job failed");
                    updateJobCenterEntry(jobEntry, "FAILED", jobEntry.phase, jobEntry.progressPercent,
                            result.message(), "", result.errorCode(), result.errorDetail(), List.of());
                    String friendly = mapTypedErrorToMessage(result.errorCode(), result.errorDetail());
                    showError("Run TERRA.AI Job", friendly);
                    return;
                }

                if (result.artifactPaths() != null && !result.artifactPaths().isEmpty()) {
                    updateJobCenterEntry(jobEntry, "COMPLETED", "Importing", 100,
                            "Importing artifacts", "", null, null, result.artifactPaths());
                    importResolvedArtifacts(result.artifactPaths());
                    return;
                }

                Path outputDir = result.outputDirectory();
                if (outputDir == null || !Files.isDirectory(outputDir)) {
                    setStatus("TERRA.AI job completed but output folder not found");
                    updateJobCenterEntry(jobEntry, "FAILED", "Import", 100,
                            "Output folder not found", "", "ERR_OUTPUT_DIR_MISSING", null, List.of());
                    showInfoPopup("Run TERRA.AI Job", "Job completed, but no output directory was detected. Configure TERRAGIS_TERRA_AI_OUTPUT_DIR.");
                    return;
                }

                updateJobCenterEntry(jobEntry, "COMPLETED", "Importing", 100,
                        "Importing local deliverables", "", null, null, List.of());
                importTerraAiDeliverables(outputDir);
            }

            @Override
            protected void failed() {
                progressBar.progressProperty().unbind();
                setProgress(-1);
                Throwable error = getException();
                log.error("Run TERRA.AI job failed", error);
                updateJobCenterEntry(jobEntry, "FAILED", jobEntry.phase, jobEntry.progressPercent,
                        conciseMessage(error), "", "ERR_CLIENT_EXCEPTION", conciseMessage(error), List.of());
                showError("Run TERRA.AI Job", conciseMessage(error));
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());
        task.messageProperty().addListener((obs, oldMsg, newMsg) -> {
            if (newMsg != null && !newMsg.isBlank()) {
                setStatus("TERRA.AI - " + newMsg);
            }
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }
    private void importResolvedArtifacts(List<Path> artifactPaths) {
        if (artifactPaths == null || artifactPaths.isEmpty()) {
            showInfoPopup("TERRA.AI Import", "No importable artifacts returned by orchestrator.");
            return;
        }

        List<Path> rasters = new ArrayList<>();
        List<Path> vectors = new ArrayList<>();
        List<Path> manifests = new ArrayList<>();

        for (Path p : artifactPaths) {
            if (p == null || !Files.exists(p) || !Files.isRegularFile(p)) {
                continue;
            }
            String f = p.getFileName().toString().toLowerCase(Locale.ROOT);
            if (f.endsWith(".tif") || f.endsWith(".tiff")) {
                rasters.add(p);
            } else if (f.endsWith(".shp") || f.endsWith(".gpkg") || f.endsWith(".geojson")) {
                vectors.add(p);
            } else if (f.endsWith(".json") && (f.contains("provenance") || f.contains("manifest"))) {
                manifests.add(p);
            }
        }

        String provenanceSummary = readProvenanceSummary(manifests);
        for (Path raster : rasters) {
            loadRasterFile(raster.toFile(), true, provenanceSummary);
        }
        for (Path vector : vectors) {
            loadVectorFile(vector.toFile());
        }

        setStatus("Imported orchestrator artifacts: " + rasters.size() + " raster(s), " + vectors.size() + " vector(s)");
        showInfoPopup("TERRA.AI Import", "Imported orchestrator artifacts.\nRasters: " + rasters.size() + "\nVectors: " + vectors.size());
    }

    private Path toLocalPath(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        try {
            String normalized = uri.trim();
            if (normalized.startsWith("file://")) {
                return Path.of(java.net.URI.create(normalized));
            }
            if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
                return null;
            }
            return Path.of(normalized);
        } catch (Exception ex) {
            log.debug("Could not parse artifact URI {}", uri, ex);
            return null;
        }
    }

    private record OrchestratorEndpoint(String host, int port) {
    }

    private OrchestratorEndpoint resolveOrchestratorEndpoint() {
        String host = readEnvOrDefault("TERRAGIS_TERRA_AI_HOST", "localhost");
        String configuredPort = firstNonBlank(
                System.getenv("TERRAGIS_TERRA_AI_PORT"),
                System.getenv("TERRA_AI_PORT"));
        int port = parseIntOrDefault(configuredPort, 50051);
        return new OrchestratorEndpoint(host, port);
    }

    private int parseIntOrDefault(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String readEnvOrDefault(String envName, String fallback) {
        String value = System.getenv(envName);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String resolveSelectedRasterPath() {
        int selectedIndex = layerList.getSelectionModel().getSelectedIndex();
        String selectedItem = layerList.getSelectionModel().getSelectedItem();
        if (selectedIndex < 0 || selectedItem == null || !isLayerListLayerItem(selectedItem)) {
            return null;
        }

        int mapLayerIndex = toMapLayerIndex(selectedIndex);
        if (mapLayerIndex < 0 || !mapCanvas.isRasterLayer(mapLayerIndex)) {
            return null;
        }
        return rasterLayerPathByLabel.get(selectedItem);
    }

    private String mapTypedErrorToMessage(String errorCode, String detail) {
        if (errorCode == null || errorCode.isBlank()) {
            return detail == null || detail.isBlank() ? "The AI job failed. Check backend logs and retry." : detail;
        }

        return switch (errorCode) {
            case "ERR_INVALID_POINT_CLOUD_SCHEMA" -> "The uploaded LiDAR file is missing required schema/CRS fields. Please verify the source file before retrying.";
            case "ERR_MISSING_CRS" -> "Input data is missing coordinate reference information. Please assign CRS and retry.";
            case "ERR_JOB_TIMEOUT" -> "The AI job exceeded the allowed runtime window. Try a smaller AOI or retry later.";
            case "ERR_PERMISSION_DENIED" -> "TerraGIS cannot access one or more backend artifacts. Check service permissions and paths.";
            default -> (detail == null || detail.isBlank())
                    ? ("AI job failed with code " + errorCode + ".")
                    : ("AI job failed (" + errorCode + "): " + detail);
        };
    }

    private void importTerraAiDeliverables(Path outputDir) {
        TerraAiJobService jobService = new TerraAiJobService();
        TerraAiJobService.Deliverables deliverables = jobService.discoverDeliverables(outputDir);
        if (deliverables.rasters().isEmpty() && deliverables.vectors().isEmpty()) {
            showError("Import TERRA.AI Result", "No raster/vector deliverables were found in: " + outputDir);
            return;
        }

        String provenanceSummary = readProvenanceSummary(deliverables.manifests());

        int rasterCount = 0;
        for (Path raster : deliverables.rasters()) {
            loadRasterFile(raster.toFile(), true, provenanceSummary);
            rasterCount++;
        }

        int vectorCount = 0;
        for (Path vector : deliverables.vectors()) {
            String name = vector.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".json") && !name.endsWith(".geojson")) {
                continue;
            }
            loadVectorFile(vector.toFile());
            vectorCount++;
        }

        setStatus("Imported TERRA.AI deliverables: " + rasterCount + " raster(s), " + vectorCount + " vector(s)");
        showInfoPopup("TERRA.AI Import", "Imported " + rasterCount + " raster(s) and " + vectorCount
                + " vector(s) from:\n" + outputDir
                + (provenanceSummary == null ? "" : "\n\nProvenance:\n" + provenanceSummary));
    }

    private String readProvenanceSummary(List<Path> manifests) {
        if (manifests == null || manifests.isEmpty()) {
            return null;
        }

        Path manifest = manifests.get(0);
        try {
            String json = Files.readString(manifest, StandardCharsets.UTF_8);
            String jobId = findJsonField(json, "job_id", "jobId");
            String modelVersion = findJsonField(json, "model_version", "modelVersion");
            String seedHash = findJsonField(json, "seed_hash", "seedHash");
            String sourceTs = findJsonField(json, "source_timestamp", "sourceTimestamp");

            List<String> parts = new ArrayList<>();
            if (jobId != null) {
                parts.add("Job ID: " + jobId);
            }
            if (modelVersion != null) {
                parts.add("Model: " + modelVersion);
            }
            if (seedHash != null) {
                parts.add("Seed Hash: " + seedHash);
            }
            if (sourceTs != null) {
                parts.add("Source Timestamp: " + sourceTs);
            }

            if (parts.isEmpty()) {
                return "Manifest: " + manifest.getFileName();
            }
            return String.join("\n", parts);
        } catch (Exception ex) {
            log.warn("Failed to read provenance manifest {}", manifest, ex);
            return "Manifest: " + manifest.getFileName();
        }
    }

    private String findJsonField(String json, String... keys) {
        if (json == null || json.isBlank()) {
            return null;
        }

        for (String key : keys) {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private Style buildSocRasterStyle() {
        StyleBuilder sb = new StyleBuilder();
        return sb.createStyle(sb.createRasterSymbolizer());
    }

    private boolean looksLikeSocRaster(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.contains("soc") || name.contains("vm0042") || name.contains("carbon");
    }

    private void openAiManagerWindow() {
        Stage managerStage = new Stage();
        managerStage.setTitle("AI Model & Action Manager");
        managerStage.initModality(Modality.WINDOW_MODAL);
        managerStage.initOwner(getScene().getWindow());
        managerStage.setWidth(900);
        managerStage.setHeight(500);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Title label
        Label titleLabel = new Label("AI Models & Actions Manager");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        VBox titleBox = new VBox(5, titleLabel);
        titleBox.setPadding(new Insets(0, 0, 10, 0));
        root.setTop(titleBox);

        // Main content: Split pane with actions and models
        SplitPane splitPane = new SplitPane();
        splitPane.setPrefWidth(880);
        splitPane.setPrefHeight(400);

        // LEFT PANEL: AI Actions
        VBox actionsPanel = createActionsPanel();
        
        // RIGHT PANEL: AI Models
        VBox modelsPanel = createModelsPanel();

        splitPane.getItems().addAll(actionsPanel, modelsPanel);
        splitPane.setDividerPositions(0.5);
        root.setCenter(splitPane);

        // Close button
        Button closeBtn = new Button("Close");
        closeBtn.setPrefWidth(100);
        closeBtn.setOnAction(e -> {
            managerStage.close();
            refreshAiActionPicker();
            saveLayerState();
        });
        HBox buttonBox = new HBox(10, closeBtn);
        buttonBox.setStyle("-fx-alignment: center-right;");
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        root.setBottom(buttonBox);

        Scene scene = new Scene(root);
        managerStage.setScene(scene);
        managerStage.showAndWait();
    }

    private VBox createActionsPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(0, 5, 0, 0));

        Label titleLabel = new Label("AI Actions");
        titleLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        // TableView for actions
        TableView<AiActionDefinition> table = new TableView<>();
        table.setItems(FXCollections.observableArrayList(aiActions));
        table.setPrefHeight(350);

        TableColumn<AiActionDefinition, String> nameCol = new TableColumn<>("Action Name");
        nameCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().actionName()));
        nameCol.setPrefWidth(150);

        TableColumn<AiActionDefinition, String> modelCol = new TableColumn<>("Model");
        modelCol.setCellValueFactory(param -> {
            AiModelDefinition model = aiModelsById.get(param.getValue().modelId());
            String displayName = model != null ? model.displayName() : "Unknown";
            return new SimpleStringProperty(displayName);
        });
        modelCol.setPrefWidth(150);

        TableColumn<AiActionDefinition, String> tileSizeCol = new TableColumn<>("Tile Size");
        tileSizeCol.setCellValueFactory(param -> {
            int tileSize = param.getValue().tileSize();
            String displayValue = tileSize <= 0 ? "Auto" : String.valueOf(tileSize);
            return new SimpleStringProperty(displayValue);
        });
        tileSizeCol.setPrefWidth(100);

        table.getColumns().addAll(nameCol, modelCol, tileSizeCol);

        // Buttons
        HBox buttonBox = new HBox(8);
        buttonBox.setPadding(new Insets(5, 0, 0, 0));

        Button addBtn = new Button("+ New Action");
        addBtn.setPrefWidth(110);
        addBtn.setStyle("-fx-font-size: 11px;");
        addBtn.setOnAction(e -> {
            createAiAction();
            var updatedList = new ArrayList<>(aiActions);
            table.setItems(FXCollections.observableArrayList(updatedList));
        });

        Button editBtn = new Button("✎ Edit");
        editBtn.setPrefWidth(80);
        editBtn.setStyle("-fx-font-size: 11px;");
        editBtn.setOnAction(e -> {
            AiActionDefinition selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showInfoPopup("Edit AI Action", "Please select an action to edit.");
                return;
            }
            editAiActionInline(selected);
            var updatedList = new ArrayList<>(aiActions);
            table.setItems(FXCollections.observableArrayList(updatedList));
        });

        Button deleteBtn = new Button("✕ Delete");
        deleteBtn.setPrefWidth(80);
        deleteBtn.setStyle("-fx-font-size: 11px;");
        deleteBtn.setOnAction(e -> {
            AiActionDefinition selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showInfoPopup("Delete AI Action", "Please select an action to delete.");
                return;
            }
            aiActions.removeIf(action -> action.actionName().equalsIgnoreCase(selected.actionName()));
            ensureAtLeastOneAiAction();
            var updatedList = new ArrayList<>(aiActions);
            table.setItems(FXCollections.observableArrayList(updatedList));
            setStatus("Deleted AI action: " + selected.actionName());
        });

        buttonBox.getChildren().addAll(addBtn, editBtn, deleteBtn);
        panel.getChildren().addAll(titleLabel, table, buttonBox);
        return panel;
    }

    private VBox createModelsPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(0, 0, 0, 5));

        Label titleLabel = new Label("AI Models");
        titleLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        // TableView for models
        TableView<AiModelDefinition> table = new TableView<>();
        table.setItems(FXCollections.observableArrayList(aiModelsById.values()));
        table.setPrefHeight(350);

        TableColumn<AiModelDefinition, String> nameCol = new TableColumn<>("Display Name");
        nameCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().displayName()));
        nameCol.setPrefWidth(140);

        TableColumn<AiModelDefinition, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(param -> {
            String type = "backend".equals(param.getValue().sourceType()) ? "Backend" : "Local";
            return new SimpleStringProperty(type);
        });
        typeCol.setPrefWidth(80);

        TableColumn<AiModelDefinition, String> sourceCol = new TableColumn<>("Source");
        sourceCol.setCellValueFactory(param -> {
            String source = param.getValue().sourceRef();
            if (source.length() > 35) {
                source = "..." + source.substring(source.length() - 32);
            }
            return new SimpleStringProperty(source);
        });
        sourceCol.setPrefWidth(150);

        table.getColumns().addAll(nameCol, typeCol, sourceCol);

        // Buttons
        HBox buttonBox = new HBox(8);
        buttonBox.setPadding(new Insets(5, 0, 0, 0));

        Button registerBtn = new Button("+ Register");
        registerBtn.setPrefWidth(110);
        registerBtn.setStyle("-fx-font-size: 11px;");
        registerBtn.setOnAction(e -> {
            registerLocalModel();
            var updatedList = new ArrayList<>(aiModelsById.values());
            table.setItems(FXCollections.observableArrayList(updatedList));
        });

        Button removeBtn = new Button("✕ Remove");
        removeBtn.setPrefWidth(80);
        removeBtn.setStyle("-fx-font-size: 11px;");
        removeBtn.setOnAction(e -> {
            AiModelDefinition selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showInfoPopup("Remove Model", "Please select a model to remove.");
                return;
            }

            // Check if it's a system model
            if ("backend-default".equals(selected.id()) || "env-model".equals(selected.id())) {
                showError("Remove Model", "Cannot remove system models.");
                return;
            }

            aiModelsById.remove(selected.id());
            aiActions.removeIf(action -> action.modelId().equals(selected.id()));
            ensureAtLeastOneAiAction();
            var updatedList = new ArrayList<>(aiModelsById.values());
            table.setItems(FXCollections.observableArrayList(updatedList));
            setStatus("Removed model: " + selected.displayName());
        });

        buttonBox.getChildren().addAll(registerBtn, removeBtn);
        panel.getChildren().addAll(titleLabel, table, buttonBox);
        return panel;
    }

    private void editAiActionInline(AiActionDefinition current) {
        TextInputDialog nameDialog = new TextInputDialog(current.actionName());
        nameDialog.setTitle("Edit AI Action");
        nameDialog.setHeaderText("Update action name");
        nameDialog.setContentText("Action name:");
        var updatedName = nameDialog.showAndWait();
        if (updatedName.isEmpty() || updatedName.get().trim().isBlank()) {
            return;
        }

        List<String> modelChoices = aiModelsById.values().stream()
                .map(model -> model.displayName() + " [" + model.id() + "]")
                .toList();
        String defaultChoice = modelChoices.stream()
                .filter(value -> value.endsWith("[" + current.modelId() + "]"))
                .findFirst()
                .orElse(modelChoices.get(0));
        ChoiceDialog<String> modelDialog = new ChoiceDialog<>(defaultChoice, modelChoices);
        modelDialog.setTitle("Edit AI Action");
        modelDialog.setHeaderText("Update model selection");
        modelDialog.setContentText("Model:");
        var updatedModel = modelDialog.showAndWait();
        if (updatedModel.isEmpty()) {
            return;
        }

        String modelId = extractModelId(updatedModel.get());
        if (modelId == null) {
            showError("Edit AI Action", "Invalid model selection.");
            return;
        }

        TextInputDialog tileDialog = new TextInputDialog(current.tileSize() <= 0 ? "auto" : Integer.toString(current.tileSize()));
        tileDialog.setTitle("Edit AI Action");
        tileDialog.setHeaderText("Update tile size");
        tileDialog.setContentText("Tile size (auto or integer >= 128):");
        var tileValue = tileDialog.showAndWait();
        if (tileValue.isEmpty()) {
            return;
        }

        int tileSize = parseTileSize(tileValue.get());
        if (tileSize == Integer.MIN_VALUE) {
            showError("Edit AI Action", "Tile size must be 'auto' or an integer >= 128.");
            return;
        }

        aiActions.removeIf(action -> action.actionName().equalsIgnoreCase(current.actionName()));
        aiActions.add(new AiActionDefinition(updatedName.get().trim(), modelId, ACTION_SCOPE_SELECTED_RASTER, tileSize));
        setStatus("Updated AI action: " + updatedName.get().trim());
    }

    private void createAiAction() {
        TextInputDialog nameDialog = new TextInputDialog("New Action");
        nameDialog.setTitle("Create AI Action");
        nameDialog.setHeaderText("Create a reusable AI button action");
        nameDialog.setContentText("Action name:");
        var actionNameOpt = nameDialog.showAndWait();
        if (actionNameOpt.isEmpty()) {
            return;
        }
        String actionName = actionNameOpt.get().trim();
        if (actionName.isBlank()) {
            showError("Create AI Action", "Action name cannot be empty.");
            return;
        }

        List<String> modelChoices = aiModelsById.values().stream()
                .map(model -> model.displayName() + " [" + model.id() + "]")
                .toList();
        if (modelChoices.isEmpty()) {
            showError("Create AI Action", "No models are available. Register a model first.");
            return;
        }

        ChoiceDialog<String> modelDialog = new ChoiceDialog<>(modelChoices.get(0), modelChoices);
        modelDialog.setTitle("Create AI Action");
        modelDialog.setHeaderText("Select model for the action");
        modelDialog.setContentText("Model:");
        var modelChoice = modelDialog.showAndWait();
        if (modelChoice.isEmpty()) {
            return;
        }

        String modelId = extractModelId(modelChoice.get());
        if (modelId == null) {
            showError("Create AI Action", "Invalid model selection.");
            return;
        }

        ChoiceDialog<String> scopeDialog = new ChoiceDialog<>("Selected Raster Layer", List.of("Selected Raster Layer"));
        scopeDialog.setTitle("Create AI Action");
        scopeDialog.setHeaderText("Select action scope");
        scopeDialog.setContentText("Scope:");
        if (scopeDialog.showAndWait().isEmpty()) {
            return;
        }

        TextInputDialog tileDialog = new TextInputDialog("auto");
        tileDialog.setTitle("Create AI Action");
        tileDialog.setHeaderText("Tile size (pixels)");
        tileDialog.setContentText("Tile size (auto or integer >= 128):");
        var tileValue = tileDialog.showAndWait();
        if (tileValue.isEmpty()) {
            return;
        }

        int tileSize = parseTileSize(tileValue.get());
        if (tileSize == Integer.MIN_VALUE) {
            showError("Create AI Action", "Tile size must be 'auto' or an integer >= 128.");
            return;
        }

        aiActions.removeIf(action -> action.actionName().equalsIgnoreCase(actionName));
        aiActions.add(new AiActionDefinition(actionName, modelId, ACTION_SCOPE_SELECTED_RASTER, tileSize));
        setStatus("Created AI action: " + actionName);
    }





    private void registerLocalModel() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select AI Model File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Model Files", "*.onnx", "*.pt", "*.pth"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File modelFile = chooser.showOpenDialog(getScene().getWindow());
        if (modelFile == null) {
            return;
        }

        TextInputDialog nameDialog = new TextInputDialog(modelFile.getName());
        nameDialog.setTitle("Register Local Model");
        nameDialog.setHeaderText("Name this model for dropdown selection");
        nameDialog.setContentText("Model display name:");
        var displayNameOpt = nameDialog.showAndWait();
        if (displayNameOpt.isEmpty()) {
            return;
        }

        String displayName = displayNameOpt.get().trim();
        if (displayName.isBlank()) {
            showError("Register Local Model", "Display name cannot be empty.");
            return;
        }

        String modelId = "local-" + Integer.toHexString((displayName + "|" + modelFile.getAbsolutePath()).hashCode());
        AiModelDefinition definition = new AiModelDefinition(
                modelId,
                displayName,
                "default-segmentation",
                "local-path",
                modelFile.getAbsolutePath(),
                "raster");
        aiModelsById.put(modelId, definition);
        setStatus("Registered model: " + displayName);
    }



    private void handleRunSelectedAiAction() {
        if (aiActionCombo == null || aiActionCombo.getValue() == null || aiActionCombo.getValue().isBlank()) {
            showError("Run AI Action", "Select an AI action from the dropdown first.");
            return;
        }

        AiActionDefinition action = findAiActionByName(aiActionCombo.getValue());
        if (action == null) {
            showError("Run AI Action", "Selected action was not found. Refresh and try again.");
            return;
        }

        runAiAction(action);
    }

    private void refreshAiActionPicker() {
        ensureAtLeastOneAiAction();
        List<String> names = aiActions.stream().map(AiActionDefinition::actionName).toList();
        if (aiActionCombo != null) {
            String current = aiActionCombo.getValue();
            aiActionCombo.getItems().setAll(names);
            if (current != null && names.contains(current)) {
                aiActionCombo.setValue(current);
            } else if (!names.isEmpty()) {
                aiActionCombo.setValue(names.get(0));
            }
        }
        if (btnRunSelectedAiAction != null) {
            btnRunSelectedAiAction.setDisable(names.isEmpty());
        }
    }

    private void ensureAtLeastOneAiAction() {
        if (aiActions.isEmpty()) {
            aiActions.add(new AiActionDefinition("Run TERRA.AI (SOC Prediction)", "backend-default", ACTION_SCOPE_TERRA_AI_SOC, -1));
            aiActions.add(new AiActionDefinition("Run SOC from Precomputed Covariates Folder", "backend-default", ACTION_SCOPE_TERRA_AI_SOC_PRECOMPUTED, -1));
            aiActions.add(new AiActionDefinition("Segment Active Raster", "backend-default", ACTION_SCOPE_SELECTED_RASTER, -1));
            return;
        }

        boolean hasSoc = aiActions.stream().anyMatch(action -> ACTION_SCOPE_TERRA_AI_SOC.equals(action.scope()));
        boolean hasPrecomputedSoc = aiActions.stream().anyMatch(action -> ACTION_SCOPE_TERRA_AI_SOC_PRECOMPUTED.equals(action.scope()));
        boolean hasSegment = aiActions.stream().anyMatch(action -> ACTION_SCOPE_SELECTED_RASTER.equals(action.scope()));

        if (!hasSoc) {
            aiActions.add(new AiActionDefinition("Run TERRA.AI (SOC Prediction)", "backend-default", ACTION_SCOPE_TERRA_AI_SOC, -1));
        }
        if (!hasPrecomputedSoc) {
            aiActions.add(new AiActionDefinition("Run SOC from Precomputed Covariates Folder", "backend-default", ACTION_SCOPE_TERRA_AI_SOC_PRECOMPUTED, -1));
        }
        if (!hasSegment) {
            aiActions.add(new AiActionDefinition("Segment Active Raster", "backend-default", ACTION_SCOPE_SELECTED_RASTER, -1));
        }
    }

    private AiActionDefinition findAiActionByName(String name) {
        if (name == null) {
            return null;
        }
        for (AiActionDefinition action : aiActions) {
            if (action.actionName().equals(name)) {
                return action;
            }
        }
        return null;
    }

    private String extractModelId(String decoratedChoice) {
        if (decoratedChoice == null) {
            return null;
        }
        int start = decoratedChoice.lastIndexOf('[');
        int end = decoratedChoice.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        return decoratedChoice.substring(start + 1, end).trim();
    }

    private int parseTileSize(String value) {
        if (value == null || value.isBlank() || "auto".equalsIgnoreCase(value.trim())) {
            return -1;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= 128 ? parsed : Integer.MIN_VALUE;
        } catch (NumberFormatException ex) {
            return Integer.MIN_VALUE;
        }
    }

    private void handleExportVector() {
        int selectedIndex = layerList.getSelectionModel().getSelectedIndex();
        String selectedItem = layerList.getSelectionModel().getSelectedItem();

        Map<String, SimpleFeatureSource> exportCandidates = new LinkedHashMap<>();

        if (selectedIndex >= 0 && selectedItem != null && isLayerListLayerItem(selectedItem)) {
            int mapLayerIndex = toMapLayerIndex(selectedIndex);
            SimpleFeatureSource selectedLayerSource = mapCanvas.getVectorFeatureSource(mapLayerIndex);
            if (selectedLayerSource != null) {
                exportCandidates.put("Selected map layer: " + selectedItem, selectedLayerSource);
            }
        }

        if (mapCanvas.hasDigitizedFeatures(MapCanvas.DigitizedExportKind.POINTS)) {
            SimpleFeatureSource src = mapCanvas.getDigitizedFeatureSource(MapCanvas.DigitizedExportKind.POINTS);
            if (src != null) {
                exportCandidates.put("Digitized points (with attributes)", src);
            }
        }
        if (mapCanvas.hasDigitizedFeatures(MapCanvas.DigitizedExportKind.LINES)) {
            SimpleFeatureSource src = mapCanvas.getDigitizedFeatureSource(MapCanvas.DigitizedExportKind.LINES);
            if (src != null) {
                exportCandidates.put("Digitized lines (with attributes)", src);
            }
        }
        if (mapCanvas.hasDigitizedFeatures(MapCanvas.DigitizedExportKind.POLYGONS)) {
            SimpleFeatureSource src = mapCanvas.getDigitizedFeatureSource(MapCanvas.DigitizedExportKind.POLYGONS);
            if (src != null) {
                exportCandidates.put("Digitized polygons (with attributes)", src);
            }
        }

        if (exportCandidates.isEmpty()) {
            showError("Export Vector", "No exportable vector source found. Select a vector layer or create digitized features.");
            return;
        }

        String selectedSourceLabel;
        if (exportCandidates.size() == 1) {
            selectedSourceLabel = exportCandidates.keySet().iterator().next();
        } else {
            ChoiceDialog<String> sourceDialog = new ChoiceDialog<>(
                    exportCandidates.keySet().iterator().next(),
                    new ArrayList<>(exportCandidates.keySet()));
            sourceDialog.setTitle("Export Vector Source");
            sourceDialog.setHeaderText("Choose vector source to export");
            sourceDialog.setContentText("Source:");

            var selected = sourceDialog.showAndWait();
            if (selected.isEmpty()) {
                return;
            }
            selectedSourceLabel = selected.get();
        }

        SimpleFeatureSource source = exportCandidates.get(selectedSourceLabel);
        if (source == null) {
            showError("Export Vector", "Could not resolve selected export source.");
            return;
        }

        String exportBaseName = defaultExportBaseNameForVectorSource(selectedSourceLabel);

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Vector Layer");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Shapefile", "*.shp"),
                new FileChooser.ExtensionFilter("GeoPackage", "*.gpkg"),
                new FileChooser.ExtensionFilter("GeoJSON", "*.geojson", "*.json"));
        chooser.setInitialFileName(defaultExportNameForFilter(chooser.getSelectedExtensionFilter(), exportBaseName));
        chooser.selectedExtensionFilterProperty().addListener((obs, oldFilter, newFilter) -> {
            if (newFilter == null) {
                return;
            }
            chooser.setInitialFileName(defaultExportNameForFilter(newFilter, exportBaseName));
        });

        File output = chooser.showSaveDialog(getScene().getWindow());
        if (output == null) {
            return;
        }
        output = ensureExportExtension(output, chooser.getSelectedExtensionFilter());

        VectorExportService exportService = new VectorExportService();
        try {
            exportService.export(source, output);
            setStatus("Exported vector source to " + output.getName());
            showInfoPopup("Export Vector", "Export completed: " + output.getAbsolutePath());
        } catch (Exception ex) {
            showError("Export Vector", conciseMessage(ex));
        }
    }

    private void handleExportRaster() {
        int selectedIndex = layerList.getSelectionModel().getSelectedIndex();
        String selectedItem = layerList.getSelectionModel().getSelectedItem();

        if (selectedIndex < 0 || selectedItem == null || !isLayerListLayerItem(selectedItem)) {
            showError("Export Raster", "Select a raster layer to export.");
            return;
        }

        int mapLayerIndex = toMapLayerIndex(selectedIndex);
        if (!mapCanvas.isRasterLayer(mapLayerIndex)) {
            showError("Export Raster", "Selected layer is not a raster layer.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Raster Layer");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("GeoTIFF", "*.tif", "*.tiff"));
        chooser.setInitialFileName("export.tif");

        File output = chooser.showSaveDialog(getScene().getWindow());
        if (output == null) {
            return;
        }

        output = ensureRasterExportExtension(output);

        try {
            mapCanvas.exportRasterLayer(mapLayerIndex, output);
            setStatus("Exported raster layer to " + output.getName());
            showInfoPopup("Export Raster", "Export completed: " + output.getAbsolutePath());
        } catch (Exception ex) {
            showError("Export Raster", conciseMessage(ex));
        }
    }

    private void handleExportLayout() {
        LayoutExportPreset preset = chooseLayoutExportPreset();
        if (preset == null) {
            return;
        }

        ensureLayoutExportPresetsLoaded();
        prefs.put(PREF_KEY_LAYOUT_EXPORT_LAST_PRESET, preset.id());

        String baseName = buildDefaultLayoutExportFileBaseName(preset);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Layout");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PDF", "*.pdf"),
                new FileChooser.ExtensionFilter("PNG", "*.png"),
                new FileChooser.ExtensionFilter("JPEG", "*.jpg", "*.jpeg"));
        chooser.setSelectedExtensionFilter(filterForLayoutFormat(chooser, preset.format()));
        chooser.setInitialFileName(defaultLayoutExportFileName(chooser.getSelectedExtensionFilter(), baseName));
        chooser.selectedExtensionFilterProperty().addListener((obs, oldFilter, newFilter) -> {
            if (newFilter == null) {
                return;
            }
            chooser.setInitialFileName(defaultLayoutExportFileName(newFilter, baseName));
        });

        File output = chooser.showSaveDialog(getScene().getWindow());
        if (output == null) {
            return;
        }

        output = ensureLayoutExportExtension(output, chooser.getSelectedExtensionFilter());
        try {
            int[] pixelSize = layoutPixelSize(preset);
            WritableImage image = captureMapSnapshot(pixelSize[0], pixelSize[1]);
            writeLayoutImage(output, image, chooser.getSelectedExtensionFilter(), preset);
            setStatus("Exported layout to " + output.getName());
            showInfoPopup("Export Layout", "Export completed: " + output.getAbsolutePath());
        } catch (Exception ex) {
            showError("Export Layout", conciseMessage(ex));
        }
    }

    private LayoutExportPreset chooseLayoutExportPreset() {
        ensureLayoutExportPresetsLoaded();
        if (layoutExportPresets.isEmpty()) {
            layoutExportPresets.addAll(buildDefaultLayoutExportPresets());
            saveLayoutExportPresets();
        }

        Dialog<LayoutExportPreset> dialog = new Dialog<>();
        dialog.setTitle("Layout Export Presets");
        dialog.setHeaderText("Choose or manage an export preset");
        ButtonType exportButtonType = new ButtonType("Use Preset", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(exportButtonType, ButtonType.CANCEL);

        ComboBox<LayoutExportPreset> presetCombo = new ComboBox<>();
        presetCombo.setPrefWidth(420);
        presetCombo.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(LayoutExportPreset item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(layoutPresetLabel(item));
                }
            }
        });
        presetCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(LayoutExportPreset item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(layoutPresetLabel(item));
                }
            }
        });

        Label details = new Label();
        details.setWrapText(true);
        details.setMaxWidth(420);

        Runnable refreshCombo = () -> {
            presetCombo.setItems(FXCollections.observableArrayList(layoutExportPresets));
            String lastPresetId = prefs.get(PREF_KEY_LAYOUT_EXPORT_LAST_PRESET, "");
            LayoutExportPreset selected = null;
            if (lastPresetId != null && !lastPresetId.isBlank()) {
                for (LayoutExportPreset candidate : layoutExportPresets) {
                    if (candidate.id().equals(lastPresetId)) {
                        selected = candidate;
                        break;
                    }
                }
            }
            if (selected == null && !layoutExportPresets.isEmpty()) {
                selected = layoutExportPresets.get(0);
            }
            presetCombo.getSelectionModel().select(selected);
            details.setText(layoutPresetDetails(selected));
        };
        refreshCombo.run();

        presetCombo.valueProperty().addListener((obs, oldValue, newValue) -> details.setText(layoutPresetDetails(newValue)));

        Button btnNew = new Button("New...");
        Button btnEdit = new Button("Edit...");
        Button btnDelete = new Button("Delete");
        HBox manageRow = new HBox(8, btnNew, btnEdit, btnDelete);

        btnNew.setOnAction(evt -> {
            LayoutExportPreset created = showLayoutPresetEditor(null, null);
            if (created == null) {
                return;
            }
            layoutExportPresets.add(created);
            saveLayoutExportPresets();
            refreshCombo.run();
            presetCombo.getSelectionModel().select(created);
        });

        btnEdit.setOnAction(evt -> {
            LayoutExportPreset current = presetCombo.getValue();
            if (current == null) {
                return;
            }
            LayoutExportPreset edited = showLayoutPresetEditor(current, current.id());
            if (edited == null) {
                return;
            }
            for (int i = 0; i < layoutExportPresets.size(); i++) {
                if (layoutExportPresets.get(i).id().equals(current.id())) {
                    layoutExportPresets.set(i, edited);
                    break;
                }
            }
            saveLayoutExportPresets();
            refreshCombo.run();
            presetCombo.getSelectionModel().select(edited);
        });

        btnDelete.setOnAction(evt -> {
            LayoutExportPreset current = presetCombo.getValue();
            if (current == null || layoutExportPresets.size() <= 1) {
                return;
            }
            layoutExportPresets.removeIf(p -> p.id().equals(current.id()));
            saveLayoutExportPresets();
            refreshCombo.run();
        });

        dialog.getDialogPane().setContent(new VBox(10,
                new Label("Preset:"),
                presetCombo,
                details,
                new Separator(),
                manageRow,
                new Label("Filename tokens: {project}, {date}, {preset}")));

        Node okButton = dialog.getDialogPane().lookupButton(exportButtonType);
        okButton.disableProperty().bind(presetCombo.valueProperty().isNull());

        dialog.setResultConverter(buttonType -> buttonType == exportButtonType ? presetCombo.getValue() : null);
        return dialog.showAndWait().orElse(null);
    }

    private LayoutExportPreset showLayoutPresetEditor(LayoutExportPreset existing, String fixedId) {
        Dialog<LayoutExportPreset> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "New Export Preset" : "Edit Export Preset");
        dialog.setHeaderText(existing == null ? "Create preset" : "Update preset");
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField nameField = new TextField(existing == null ? "" : existing.name());
        ComboBox<LayoutExportFormat> formatCombo = new ComboBox<>();
        formatCombo.getItems().addAll(LayoutExportFormat.values());
        formatCombo.setValue(existing == null ? LayoutExportFormat.PDF : existing.format());

        Spinner<Integer> dpiSpinner = new Spinner<>(72, 600, existing == null ? 300 : existing.dpi(), 10);
        dpiSpinner.setEditable(true);

        ComboBox<LayoutPageSize> pageSizeCombo = new ComboBox<>();
        pageSizeCombo.getItems().addAll(LayoutPageSize.values());
        pageSizeCombo.setValue(existing == null ? LayoutPageSize.A4 : existing.pageSize());

        ComboBox<LayoutOrientation> orientationCombo = new ComboBox<>();
        orientationCombo.getItems().addAll(LayoutOrientation.values());
        orientationCombo.setValue(existing == null ? LayoutOrientation.LANDSCAPE : existing.orientation());

        TextField filePatternField = new TextField(existing == null ? "{project}_{date}_{preset}" : existing.filenamePattern());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("Name"), nameField);
        grid.addRow(1, new Label("Format"), formatCombo);
        grid.addRow(2, new Label("DPI"), dpiSpinner);
        grid.addRow(3, new Label("Page"), pageSizeCombo);
        grid.addRow(4, new Label("Orientation"), orientationCombo);
        grid.addRow(5, new Label("Filename Pattern"), filePatternField);
        dialog.getDialogPane().setContent(grid);

        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.disableProperty().bind(nameField.textProperty().isEmpty().or(filePatternField.textProperty().isEmpty()));

        dialog.setResultConverter(buttonType -> {
            if (buttonType != saveButtonType) {
                return null;
            }
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            String pattern = filePatternField.getText() == null ? "" : filePatternField.getText().trim();
            if (name.isBlank() || pattern.isBlank()) {
                return null;
            }
            String lowerName = name.toLowerCase(Locale.ROOT);
            for (LayoutExportPreset preset : layoutExportPresets) {
                if (fixedId != null && fixedId.equals(preset.id())) {
                    continue;
                }
                if (preset.name().trim().toLowerCase(Locale.ROOT).equals(lowerName)) {
                    showError("Export Preset", "A preset with this name already exists.");
                    return null;
                }
            }

            int dpi = dpiSpinner.getValue() == null ? 300 : Math.max(72, Math.min(600, dpiSpinner.getValue()));
            return new LayoutExportPreset(
                    fixedId != null ? fixedId : UUID.randomUUID().toString(),
                    name,
                    formatCombo.getValue() == null ? LayoutExportFormat.PDF : formatCombo.getValue(),
                    dpi,
                    pageSizeCombo.getValue() == null ? LayoutPageSize.A4 : pageSizeCombo.getValue(),
                    orientationCombo.getValue() == null ? LayoutOrientation.LANDSCAPE : orientationCombo.getValue(),
                    pattern);
        });

        return dialog.showAndWait().orElse(null);
    }

    private String layoutPresetLabel(LayoutExportPreset preset) {
        if (preset == null) {
            return "";
        }
        return preset.name() + " (" + preset.format() + " / " + preset.dpi() + " DPI)";
    }

    private String layoutPresetDetails(LayoutExportPreset preset) {
        if (preset == null) {
            return "";
        }
        return "Format: " + preset.format()
                + " | DPI: " + preset.dpi()
                + " | Page: " + preset.pageSize().label()
                + " " + preset.orientation()
                + "\nPattern: " + preset.filenamePattern();
    }

    private void ensureLayoutExportPresetsLoaded() {
        if (layoutExportPresetsLoaded) {
            return;
        }
        layoutExportPresetsLoaded = true;
        layoutExportPresets.clear();
        String encoded = prefs.get(PREF_KEY_LAYOUT_EXPORT_PRESETS, "");
        if (encoded == null || encoded.isBlank()) {
            layoutExportPresets.addAll(buildDefaultLayoutExportPresets());
            saveLayoutExportPresets();
            return;
        }

        for (String token : encoded.split(";")) {
            if (token == null || token.isBlank()) {
                continue;
            }
            try {
                String decoded = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
                String[] parts = decoded.split("\\|", -1);
                if (parts.length < 7) {
                    continue;
                }
                LayoutExportPreset preset = new LayoutExportPreset(
                        parts[0],
                        parts[1],
                        LayoutExportFormat.valueOf(parts[2]),
                        Integer.parseInt(parts[3]),
                        LayoutPageSize.valueOf(parts[4]),
                        LayoutOrientation.valueOf(parts[5]),
                        parts[6]);
                layoutExportPresets.add(preset);
            } catch (Exception ignored) {
                // Ignore malformed preset payload and continue loading other entries.
            }
        }

        if (layoutExportPresets.isEmpty()) {
            layoutExportPresets.addAll(buildDefaultLayoutExportPresets());
            saveLayoutExportPresets();
        }
    }

    private List<LayoutExportPreset> buildDefaultLayoutExportPresets() {
        return List.of(
                new LayoutExportPreset(UUID.randomUUID().toString(), "Print PDF", LayoutExportFormat.PDF, 300, LayoutPageSize.A4, LayoutOrientation.LANDSCAPE, "{project}_{date}_{preset}"),
                new LayoutExportPreset(UUID.randomUUID().toString(), "Sharing PNG", LayoutExportFormat.PNG, 180, LayoutPageSize.A4, LayoutOrientation.LANDSCAPE, "{project}_{date}_{preset}"),
                new LayoutExportPreset(UUID.randomUUID().toString(), "Slide JPEG", LayoutExportFormat.JPEG, 150, LayoutPageSize.LETTER, LayoutOrientation.LANDSCAPE, "{project}_{date}_{preset}")
        );
    }

    private void saveLayoutExportPresets() {
        List<String> encoded = new ArrayList<>();
        for (LayoutExportPreset preset : layoutExportPresets) {
            String raw = String.join("|",
                    preset.id(),
                    preset.name(),
                    preset.format().name(),
                    String.valueOf(preset.dpi()),
                    preset.pageSize().name(),
                    preset.orientation().name(),
                    preset.filenamePattern());
            encoded.add(Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        }
        prefs.put(PREF_KEY_LAYOUT_EXPORT_PRESETS, String.join(";", encoded));
    }

    private FileChooser.ExtensionFilter filterForLayoutFormat(FileChooser chooser, LayoutExportFormat format) {
        if (chooser == null || chooser.getExtensionFilters().isEmpty()) {
            return null;
        }
        String suffix = switch (format) {
            case PDF -> "*.pdf";
            case PNG -> "*.png";
            case JPEG -> "*.jpg";
        };

        for (FileChooser.ExtensionFilter filter : chooser.getExtensionFilters()) {
            for (String ext : filter.getExtensions()) {
                if (suffix.equalsIgnoreCase(ext)) {
                    return filter;
                }
            }
        }
        return chooser.getExtensionFilters().get(0);
    }

    private String defaultLayoutExportFileName(FileChooser.ExtensionFilter selectedFilter, String baseName) {
        String safeBaseName = sanitizeExportBaseName(baseName);
        String extension = ".pdf";
        if (selectedFilter != null && !selectedFilter.getExtensions().isEmpty()) {
            String ext = selectedFilter.getExtensions().get(0).toLowerCase(Locale.ROOT);
            if (ext.endsWith(".png")) {
                extension = ".png";
            } else if (ext.endsWith(".jpg") || ext.endsWith(".jpeg")) {
                extension = ".jpg";
            }
        }
        return safeBaseName + extension;
    }

    private File ensureLayoutExportExtension(File output, FileChooser.ExtensionFilter selectedFilter) {
        if (output == null) {
            return null;
        }

        String name = output.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return output;
        }

        String extension = ".pdf";
        if (selectedFilter != null && !selectedFilter.getExtensions().isEmpty()) {
            String ext = selectedFilter.getExtensions().get(0).toLowerCase(Locale.ROOT);
            if (ext.endsWith(".png")) {
                extension = ".png";
            } else if (ext.endsWith(".jpg") || ext.endsWith(".jpeg")) {
                extension = ".jpg";
            }
        }

        return new File(output.getParentFile(), output.getName() + extension);
    }

    private String buildDefaultLayoutExportFileBaseName(LayoutExportPreset preset) {
        String pattern = preset == null || preset.filenamePattern() == null || preset.filenamePattern().isBlank()
                ? "{project}_{date}_{preset}"
                : preset.filenamePattern();
        String projectName = resolveProjectNameForExport();
        String dateStamp = java.time.LocalDate.now().toString();
        String presetToken = preset == null ? "layout" : preset.name();

        String resolved = pattern
                .replace("{project}", sanitizeExportBaseName(projectName))
                .replace("{date}", sanitizeExportBaseName(dateStamp))
                .replace("{preset}", sanitizeExportBaseName(presetToken));

        return sanitizeExportBaseName(resolved);
    }

    private String resolveProjectNameForExport() {
        if (currentProjectPath != null && currentProjectPath.getFileName() != null) {
            return currentProjectPath.getFileName().toString();
        }
        return "terragis_layout";
    }

    private int[] layoutPixelSize(LayoutExportPreset preset) {
        LayoutPageSize page = preset.pageSize();
        double widthIn = page.widthIn();
        double heightIn = page.heightIn();
        if (preset.orientation() == LayoutOrientation.LANDSCAPE) {
            double tmp = widthIn;
            widthIn = heightIn;
            heightIn = tmp;
        }

        int width = Math.max(256, (int) Math.round(widthIn * preset.dpi()));
        int height = Math.max(256, (int) Math.round(heightIn * preset.dpi()));
        width = Math.min(width, 12000);
        height = Math.min(height, 12000);
        return new int[] { width, height };
    }

    private WritableImage captureMapSnapshot(int width, int height) {
        double canvasWidth = Math.max(1.0, mapCanvas.getWidth());
        double canvasHeight = Math.max(1.0, mapCanvas.getHeight());
        double scaleX = width / canvasWidth;
        double scaleY = height / canvasHeight;

        SnapshotParameters params = new SnapshotParameters();
        params.setTransform(Transform.scale(scaleX, scaleY));
        WritableImage snapshot = new WritableImage(width, height);
        return mapCanvas.snapshot(params, snapshot);
    }

    private void writeLayoutImage(File output, WritableImage image, FileChooser.ExtensionFilter selectedFilter, LayoutExportPreset preset) throws IOException {
        String ext = ".pdf";
        if (selectedFilter != null && !selectedFilter.getExtensions().isEmpty()) {
            String selected = selectedFilter.getExtensions().get(0).toLowerCase(Locale.ROOT);
            if (selected.endsWith(".png")) {
                ext = ".png";
            } else if (selected.endsWith(".jpg") || selected.endsWith(".jpeg")) {
                ext = ".jpg";
            }
        }

        BufferedImage buffered = SwingFXUtils.fromFXImage(image, null);
        if (buffered == null) {
            throw new IOException("Could not capture map snapshot for export");
        }

        if (".pdf".equals(ext)) {
            writePdfLayout(output, buffered, preset);
            return;
        }

        if (".png".equals(ext)) {
            ImageIO.write(buffered, "png", output);
            return;
        }

        BufferedImage rgb = new BufferedImage(buffered.getWidth(), buffered.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(buffered, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(rgb, "jpg", output);
    }

    private void writePdfLayout(File output, BufferedImage image, LayoutExportPreset preset) throws IOException {
        double pageWidthPt = preset.pageSize().widthIn() * 72.0;
        double pageHeightPt = preset.pageSize().heightIn() * 72.0;
        if (preset.orientation() == LayoutOrientation.LANDSCAPE) {
            double tmp = pageWidthPt;
            pageWidthPt = pageHeightPt;
            pageHeightPt = tmp;
        }

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle((float) pageWidthPt, (float) pageHeightPt));
            document.addPage(page);

            PDImageXObject imageXObject = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.drawImage(imageXObject, 0, 0, (float) pageWidthPt, (float) pageHeightPt);
            }

            document.save(output);
        }
    }

    private void handleOpenAnalysisToolbox() {
        List<String> operations = List.of("Buffer", "Intersection", "Reproject", "Clip", "Dissolve");
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Analysis Toolbox");
        dialog.setHeaderText("Prepare spatial analysis");

        ButtonType runButtonType = new ButtonType("Run Analysis", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(runButtonType, ButtonType.CANCEL);

        ComboBox<String> operationCombo = new ComboBox<>(FXCollections.observableArrayList(operations));
        operationCombo.setValue(operations.get(0));
        operationCombo.setMaxWidth(Double.MAX_VALUE);

        Label stepLabel = new Label("1. Choose operation\n2. Confirm inputs\n3. TerraGIS creates a new result layer");
        stepLabel.setWrapText(true);
        stepLabel.setStyle("-fx-text-fill: #b8c9d3; -fx-font-size: 12px;");

        Label description = new Label(analysisDescription(operationCombo.getValue()));
        description.setWrapText(true);
        description.setStyle(
                "-fx-padding: 10; " +
                "-fx-background-color: rgba(255,255,255,0.06); " +
                "-fx-background-radius: 8; " +
                "-fx-border-color: rgba(255,255,255,0.12); " +
                "-fx-border-radius: 8;"
        );
        operationCombo.valueProperty().addListener((obs, oldValue, newValue) -> description.setText(analysisDescription(newValue)));

        GridPane content = new GridPane();
        content.setHgap(10);
        content.setVgap(12);
        content.setPadding(new Insets(8));
        content.add(new Label("Operation:"), 0, 0);
        content.add(operationCombo, 1, 0);
        content.add(stepLabel, 0, 1, 2, 1);
        content.add(description, 0, 2, 2, 1);
        GridPane.setHgrow(operationCombo, Priority.ALWAYS);

        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> button == runButtonType ? operationCombo.getValue() : null);

        var selected = dialog.showAndWait();
        if (selected.isEmpty()) {
            return;
        }

        switch (selected.get()) {
            case "Buffer" -> runBufferAnalysis();
            case "Intersection" -> runIntersectionAnalysis();
            case "Reproject" -> runReprojectAnalysis();
            case "Clip" -> runClipAnalysis();
            case "Dissolve" -> runDissolveAnalysis();
            default -> setStatus("Unknown analysis operation selected");
        }
    }

    private String analysisDescription(String operation) {
        return switch (operation == null ? "" : operation) {
            case "Buffer" -> "Creates distance zones around features. Best for influence areas, road setbacks, and parcel proximity checks.";
            case "Intersection" -> "Finds overlapping geometry between two vector layers and writes the shared area or features as a new layer.";
            case "Reproject" -> "Converts a vector layer into a target EPSG coordinate reference system for alignment or export.";
            case "Clip" -> "Cuts one vector layer by another boundary layer. Useful for study areas, villages, districts, and AOIs.";
            case "Dissolve" -> "Merges features by a chosen attribute, or into one unified layer when no field is selected.";
            default -> "Choose an operation to see what TerraGIS will produce.";
        };
    }

    private void runBufferAnalysis() {
        List<VectorLayerOption> vectorLayers = getVectorLayerOptions();
        if (vectorLayers.isEmpty()) {
            showError("Analysis - Buffer", "No vector layers available. Load a vector layer first.");
            return;
        }

        ChoiceDialog<String> layerDialog = new ChoiceDialog<>(vectorLayers.get(0).label(), vectorLayers.stream().map(VectorLayerOption::label).toList());
        layerDialog.setTitle("Buffer");
        layerDialog.setHeaderText("Choose input layer");
        layerDialog.setContentText("Layer:");
        var layerChoice = layerDialog.showAndWait();
        if (layerChoice.isEmpty()) {
            return;
        }

        TextInputDialog distanceDialog = new TextInputDialog("10");
        distanceDialog.setTitle("Buffer");
        distanceDialog.setHeaderText("Buffer distance");
        distanceDialog.setContentText("Distance (in layer CRS units):");
        var distanceValue = distanceDialog.showAndWait();
        if (distanceValue.isEmpty()) {
            return;
        }

        double distance;
        try {
            distance = Double.parseDouble(distanceValue.get().trim());
        } catch (NumberFormatException ex) {
            showError("Analysis - Buffer", "Distance must be a valid number.");
            return;
        }

        VectorLayerOption selectedLayer = findVectorLayerByLabel(vectorLayers, layerChoice.get());
        if (selectedLayer == null) {
            showError("Analysis - Buffer", "Selected layer is no longer available.");
            return;
        }

        runAnalysisTask(
                "Buffer",
                () -> analysisService.buffer(selectedLayer.source(), distance),
                "Buffer - " + selectedLayer.baseName() + " (" + distance + ")");
    }

    private void runIntersectionAnalysis() {
        List<VectorLayerOption> vectorLayers = getVectorLayerOptions();
        if (vectorLayers.size() < 2) {
            showError("Analysis - Intersection", "At least two vector layers are required for intersection.");
            return;
        }

        List<String> layerLabels = vectorLayers.stream().map(VectorLayerOption::label).toList();

        ChoiceDialog<String> firstDialog = new ChoiceDialog<>(layerLabels.get(0), layerLabels);
        firstDialog.setTitle("Intersection");
        firstDialog.setHeaderText("Choose first input layer");
        firstDialog.setContentText("Layer A:");
        var firstChoice = firstDialog.showAndWait();
        if (firstChoice.isEmpty()) {
            return;
        }

        ChoiceDialog<String> secondDialog = new ChoiceDialog<>(layerLabels.get(1), layerLabels);
        secondDialog.setTitle("Intersection");
        secondDialog.setHeaderText("Choose second input layer");
        secondDialog.setContentText("Layer B:");
        var secondChoice = secondDialog.showAndWait();
        if (secondChoice.isEmpty()) {
            return;
        }

        if (firstChoice.get().equals(secondChoice.get())) {
            showError("Analysis - Intersection", "Layer A and Layer B must be different.");
            return;
        }

        VectorLayerOption first = findVectorLayerByLabel(vectorLayers, firstChoice.get());
        VectorLayerOption second = findVectorLayerByLabel(vectorLayers, secondChoice.get());
        if (first == null || second == null) {
            showError("Analysis - Intersection", "One or more selected layers are no longer available.");
            return;
        }

        runAnalysisTask(
                "Intersection",
                () -> analysisService.intersect(first.source(), second.source()),
                "Intersect - " + first.baseName() + " x " + second.baseName());
    }

    private void runReprojectAnalysis() {
        List<VectorLayerOption> vectorLayers = getVectorLayerOptions();
        if (vectorLayers.isEmpty()) {
            showError("Analysis - Reproject", "No vector layers available. Load a vector layer first.");
            return;
        }

        ChoiceDialog<String> layerDialog = new ChoiceDialog<>(vectorLayers.get(0).label(), vectorLayers.stream().map(VectorLayerOption::label).toList());
        layerDialog.setTitle("Reproject");
        layerDialog.setHeaderText("Choose input layer");
        layerDialog.setContentText("Layer:");
        var layerChoice = layerDialog.showAndWait();
        if (layerChoice.isEmpty()) {
            return;
        }

        TextInputDialog epsgDialog = new TextInputDialog("EPSG:4326");
        epsgDialog.setTitle("Reproject");
        epsgDialog.setHeaderText("Target coordinate reference system");
        epsgDialog.setContentText("EPSG code (e.g. EPSG:3857):");
        var epsgValue = epsgDialog.showAndWait();
        if (epsgValue.isEmpty() || epsgValue.get().trim().isBlank()) {
            return;
        }

        VectorLayerOption selectedLayer = findVectorLayerByLabel(vectorLayers, layerChoice.get());
        if (selectedLayer == null) {
            showError("Analysis - Reproject", "Selected layer is no longer available.");
            return;
        }

        String targetEpsg = epsgValue.get().trim();
        runAnalysisTask(
                "Reproject",
                () -> analysisService.reproject(selectedLayer.source(), targetEpsg),
                "Reproject - " + selectedLayer.baseName() + " -> " + targetEpsg);
    }

    private void runClipAnalysis() {
        List<VectorLayerOption> vectorLayers = getVectorLayerOptions();
        if (vectorLayers.size() < 2) {
            showError("Analysis - Clip", "At least two vector layers are required for clip.");
            return;
        }

        List<String> layerLabels = vectorLayers.stream().map(VectorLayerOption::label).toList();

        ChoiceDialog<String> targetDialog = new ChoiceDialog<>(layerLabels.get(0), layerLabels);
        targetDialog.setTitle("Clip");
        targetDialog.setHeaderText("Choose target layer to clip");
        targetDialog.setContentText("Target Layer:");
        var targetChoice = targetDialog.showAndWait();
        if (targetChoice.isEmpty()) {
            return;
        }

        ChoiceDialog<String> boundaryDialog = new ChoiceDialog<>(layerLabels.get(1), layerLabels);
        boundaryDialog.setTitle("Clip");
        boundaryDialog.setHeaderText("Choose clip boundary layer");
        boundaryDialog.setContentText("Boundary Layer:");
        var boundaryChoice = boundaryDialog.showAndWait();
        if (boundaryChoice.isEmpty()) {
            return;
        }

        if (targetChoice.get().equals(boundaryChoice.get())) {
            showError("Analysis - Clip", "Target and boundary layers must be different.");
            return;
        }

        VectorLayerOption target = findVectorLayerByLabel(vectorLayers, targetChoice.get());
        VectorLayerOption boundary = findVectorLayerByLabel(vectorLayers, boundaryChoice.get());
        if (target == null || boundary == null) {
            showError("Analysis - Clip", "One or more selected layers are no longer available.");
            return;
        }

        runAnalysisTask(
                "Clip",
                () -> analysisService.clip(target.source(), boundary.source()),
                "Clip - " + target.baseName() + " by " + boundary.baseName());
    }

    private void runDissolveAnalysis() {
        List<VectorLayerOption> vectorLayers = getVectorLayerOptions();
        if (vectorLayers.isEmpty()) {
            showError("Analysis - Dissolve", "No vector layers available. Load a vector layer first.");
            return;
        }

        ChoiceDialog<String> layerDialog = new ChoiceDialog<>(vectorLayers.get(0).label(), vectorLayers.stream().map(VectorLayerOption::label).toList());
        layerDialog.setTitle("Dissolve");
        layerDialog.setHeaderText("Choose input layer");
        layerDialog.setContentText("Layer:");
        var layerChoice = layerDialog.showAndWait();
        if (layerChoice.isEmpty()) {
            return;
        }

        VectorLayerOption selectedLayer = findVectorLayerByLabel(vectorLayers, layerChoice.get());
        if (selectedLayer == null) {
            showError("Analysis - Dissolve", "Selected layer is no longer available.");
            return;
        }

        List<String> fieldOptions = new ArrayList<>();
        fieldOptions.add("(Dissolve all features)");
        try {
            for (var descriptor : selectedLayer.source().getSchema().getAttributeDescriptors()) {
                Class<?> binding = descriptor.getType().getBinding();
                if (!org.locationtech.jts.geom.Geometry.class.isAssignableFrom(binding)) {
                    fieldOptions.add(descriptor.getLocalName());
                }
            }
        } catch (Exception ex) {
            showError("Analysis - Dissolve", "Could not inspect layer schema: " + conciseMessage(ex));
            return;
        }

        ChoiceDialog<String> fieldDialog = new ChoiceDialog<>(fieldOptions.get(0), fieldOptions);
        fieldDialog.setTitle("Dissolve");
        fieldDialog.setHeaderText("Dissolve by field");
        fieldDialog.setContentText("Field:");
        var fieldChoice = fieldDialog.showAndWait();
        if (fieldChoice.isEmpty()) {
            return;
        }

        String selectedFieldOption = fieldChoice.get();
        String dissolveField = selectedFieldOption.equals("(Dissolve all features)") ? "" : selectedFieldOption;
        String outputName = dissolveField.isEmpty()
                ? "Dissolve - " + selectedLayer.baseName() + " (all)"
                : "Dissolve - " + selectedLayer.baseName() + " by " + dissolveField;

        runAnalysisTask(
                "Dissolve",
                () -> analysisService.dissolve(selectedLayer.source(), dissolveField),
                outputName);
    }

    private void runAnalysisTask(String operation, AnalysisOperation action, String outputLayerName) {
        setStatus(operation + " started...");
        setProgress(0.05);

        Task<SimpleFeatureSource> task = new Task<>() {
            @Override
            protected SimpleFeatureSource call() throws Exception {
                updateProgress(2, 10);
                updateMessage("Validating inputs...");
                Thread.sleep(60);

                updateProgress(6, 10);
                updateMessage("Running " + operation.toLowerCase(Locale.ROOT) + "...");
                SimpleFeatureSource result = action.execute();

                updateProgress(9, 10);
                updateMessage("Preparing output layer...");
                return result;
            }

            @Override
            protected void succeeded() {
                try {
                    SimpleFeatureSource resultSource = getValue();
                    if (resultSource == null || resultSource.getFeatures().isEmpty()) {
                        setStatus(operation + " completed with no output features");
                        showInfoPopup("Analysis - " + operation, "Operation completed, but no output features were produced.");
                        return;
                    }

                    Style style;
                    try {
                            if (usesTransparentAnalysisStyle(operation)) {
                                style = createTransparentAnalysisStyle(resultSource.getSchema(), operation);
                            } else {
                                style = SLD.createSimpleStyle(resultSource.getSchema());
                            }
                    } catch (Exception ex) {
                            style = usesTransparentAnalysisStyle(operation)
                                    ? createTransparentAnalysisStyle(resultSource.getSchema(), operation)
                                    : createFallbackStyle(resultSource.getSchema());
                    }
                    mapCanvas.addLayer(resultSource, style);

                    int outputCount = resultSource.getCount(Query.ALL);
                    if (outputCount < 0) {
                        outputCount = resultSource.getFeatures().size();
                    }

                    String titledLayer = outputLayerName + " (" + outputCount + " features)";
                    mapCanvas.getMapContent().layers().get(mapCanvas.getLayerCount() - 1).setTitle(titledLayer);
                    refreshLayerList(titledLayer);
                    saveLayerState();

                    setStatus(operation + " completed: " + outputCount + " features");
                    showInfoPopup("Analysis - " + operation, "Output layer created: " + titledLayer);
                } catch (Exception ex) {
                    showError("Analysis - " + operation, conciseMessage(ex));
                    setStatus(operation + " failed");
                } finally {
                    setProgress(-1);
                }
            }

            @Override
            protected void failed() {
                Throwable error = getException();
                showError("Analysis - " + operation, conciseMessage(error));
                setStatus(operation + " failed");
                setProgress(-1);
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());
        task.messageProperty().addListener((obs, oldMsg, newMsg) -> {
            if (newMsg != null && !newMsg.isBlank()) {
                setStatus(operation + " - " + newMsg);
            }
        });

        Thread thread = new Thread(task, "analysis-" + operation.toLowerCase(Locale.ROOT));
        thread.setDaemon(true);
        thread.start();
    }

    private Style createFallbackStyle(SimpleFeatureType schema) {
        StyleBuilder styleBuilder = new StyleBuilder();

        if (schema == null) {
            return styleBuilder.createStyle(styleBuilder.createPolygonSymbolizer());
        }

        GeometryDescriptor geometryDescriptor = schema.getGeometryDescriptor();
        Class<?> geometryBinding = geometryDescriptor != null ? geometryDescriptor.getType().getBinding() : null;

        if (geometryBinding != null && Point.class.isAssignableFrom(geometryBinding)) {
            return styleBuilder.createStyle(styleBuilder.createPointSymbolizer());
        }
        if (geometryBinding != null && LineString.class.isAssignableFrom(geometryBinding)) {
            return styleBuilder.createStyle(styleBuilder.createLineSymbolizer());
        }
        if (geometryBinding != null && Geometry.class.isAssignableFrom(geometryBinding)) {
            return styleBuilder.createStyle(styleBuilder.createPolygonSymbolizer());
        }

        return styleBuilder.createStyle(styleBuilder.createPolygonSymbolizer());
    }

    private boolean usesTransparentAnalysisStyle(String operation) {
        return "Buffer".equals(operation)
                || "Intersection".equals(operation)
                || "Clip".equals(operation);
    }

    private Style createTransparentAnalysisStyle(SimpleFeatureType schema, String operation) {
        StyleBuilder styleBuilder = new StyleBuilder();
        java.awt.Color outline = switch (operation) {
            case "Intersection" -> new java.awt.Color(35, 95, 180, 185);
            case "Clip" -> new java.awt.Color(25, 130, 90, 185);
            default -> new java.awt.Color(40, 40, 40, 180);
        };

        Class<?> geometryBinding = null;
        if (schema != null && schema.getGeometryDescriptor() != null && schema.getGeometryDescriptor().getType() != null) {
            geometryBinding = schema.getGeometryDescriptor().getType().getBinding();
        }

        if (geometryBinding != null && Point.class.isAssignableFrom(geometryBinding)) {
            return styleBuilder.createStyle(styleBuilder.createPointSymbolizer());
        }
        if (geometryBinding != null && LineString.class.isAssignableFrom(geometryBinding)) {
            return styleBuilder.createStyle(styleBuilder.createLineSymbolizer(outline, 2.0d));
        }

        return styleBuilder.createStyle(styleBuilder.createPolygonSymbolizer(outline, 1.6d));
    }

    private List<VectorLayerOption> getVectorLayerOptions() {
        List<VectorLayerOption> options = new ArrayList<>();
        List<Layer> layers = mapCanvas.getMapContent().layers();

        for (int index = 0; index < layers.size(); index++) {
            Layer layer = layers.get(index);
            if (layer == null) {
                continue;
            }

            SimpleFeatureSource source = mapCanvas.getVectorFeatureSource(index);
            if (source == null) {
                continue;
            }

            String title = layer.getTitle();
            if (title == null || title.isBlank()) {
                title = "Layer " + (index + 1);
            }

            String crs = "Unknown CRS";
            try {
                SimpleFeatureType schema = source.getSchema();
                if (schema != null && schema.getCoordinateReferenceSystem() != null) {
                    String srs = org.geotools.referencing.CRS.toSRS(schema.getCoordinateReferenceSystem(), true);
                    if (srs != null && !srs.isBlank()) {
                        crs = srs;
                    }
                }
            } catch (Exception ignored) {
                // Keep label resilient even when CRS code lookup fails.
            }

            options.add(new VectorLayerOption(title + " [" + crs + "]", title, source));
        }

        return options;
    }

    private VectorLayerOption findVectorLayerByLabel(List<VectorLayerOption> options, String label) {
        if (options == null || label == null) {
            return null;
        }
        for (VectorLayerOption option : options) {
            if (label.equals(option.label())) {
                return option;
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface AnalysisOperation {
        SimpleFeatureSource execute() throws Exception;
    }

    private record VectorLayerOption(String label, String baseName, SimpleFeatureSource source) {
    }

    private void runAiAction(AiActionDefinition action) {
        if (action == null) {
            showError("Run AI Action", "No AI action is selected.");
            return;
        }

        if (ACTION_SCOPE_TERRA_AI_SOC.equals(action.scope())) {
            String selectedRasterPath = resolveSelectedRasterPath();
            startTerraAiJob(selectedRasterPath, false);
            return;
        }

        if (ACTION_SCOPE_TERRA_AI_SOC_PRECOMPUTED.equals(action.scope())) {
            Path covariatesFolder = choosePrecomputedCovariatesFolder();
            if (covariatesFolder == null) {
                return;
            }
            startTerraAiJob(covariatesFolder.toString(), false);
            return;
        }

        AiModelDefinition model = aiModelsById.get(action.modelId());
        if (model == null) {
            showError("Run AI Action", "Action references a missing model. Update the action and try again.");
            return;
        }

        int selectedIndex = layerList.getSelectionModel().getSelectedIndex();
        String selectedItem = layerList.getSelectionModel().getSelectedItem();

        if (selectedIndex < 0 || selectedItem == null || !isLayerListLayerItem(selectedItem)) {
            showError("Run AI Action", "Select a raster layer first.");
            return;
        }

        int mapLayerIndex = toMapLayerIndex(selectedIndex);
        if (!mapCanvas.isRasterLayer(mapLayerIndex)) {
            showError("Run AI Action", "Selected layer is not a raster layer.");
            return;
        }

        int[] dimensions = mapCanvas.getRasterLayerDimensions(mapLayerIndex);
        if (dimensions == null || dimensions[0] <= 0 || dimensions[1] <= 0) {
            showError("Run AI Action", "Could not determine raster dimensions for segmentation.");
            return;
        }

        String selectedLabel = layerList.getSelectionModel().getSelectedItem();
        String rasterPath = rasterLayerPathByLabel.get(selectedLabel);
        if (rasterPath == null || rasterPath.isBlank()) {
            showError("Run AI Action", "Could not resolve source raster path for this layer. Re-open the raster and try again.");
            return;
        }

        setStatus("Running AI action: " + action.actionName());
        setProgress(0.0);
        final int tileSize = action.tileSize() > 0 ? action.tileSize() : chooseSegmentationTileSize(dimensions[0], dimensions[1]);
        final String resolvedRasterPath = rasterPath;
        final String modelName = model.modelName();
        final String actionName = action.actionName();

        Task<AiRoundTripService.PipelineResult> task = new Task<>() {
            @Override
            protected AiRoundTripService.PipelineResult call() {
                CancellationToken token = new CancellationToken();
                if (!AiBackendManager.ensureBackendRunning()) {
                    String issue = AiBackendManager.getLastStartupIssue();
                    String message = (issue == null || issue.isBlank())
                            ? "AI backend unavailable at localhost:6565. Set TERRAGIS_MODEL_PATH and install ai_backend dependencies."
                            : issue;
                    updateMessage(message);
                    return new AiRoundTripService.PipelineResult(
                            false,
                            message,
                            0,
                            0,
                            0,
                            false,
                            Collections.emptyList());
                }

                try (TerraApiClient client = new TerraApiClient("localhost", 6565)) {
                    AiRoundTripService service = new AiRoundTripService(client);
                    return service.runSegmentationPipeline(
                            dimensions[0],
                            dimensions[1],
                            tileSize,
                            modelName,
                            true,
                            resolvedRasterPath,
                            token,
                            (stage, completed, total, message) -> {
                                if (total > 0) {
                                    updateProgress(completed, total);
                                }
                                updateMessage(message);
                            });
                        } catch (Exception ex) {
                    log.warn("AI segmentation backend not reachable; synthetic fallback disabled: {}", ex.getMessage());
                            String issue = AiBackendManager.getLastStartupIssue();
                            String message = (issue == null || issue.isBlank())
                                ? "AI backend unavailable at localhost:6565. Auto-start failed; check ai_backend setup."
                                : issue;
                            updateMessage(message);
                    return new AiRoundTripService.PipelineResult(
                            false,
                                message,
                            0,
                            0,
                            0,
                            false,
                            Collections.emptyList());
                }
            }

            @Override
            protected void succeeded() {
                try {
                    AiRoundTripService.PipelineResult result = getValue();
                    progressBar.progressProperty().unbind();
                    setProgress(-1);

                    if (result == null || !result.success()) {
                        setStatus("AI action did not complete: " + actionName);
                        String msg = (result != null && result.message() != null && !result.message().isBlank())
                                ? result.message()
                                : "Segmentation was cancelled or failed.";
                        showInfoPopup("Run AI Action", msg);
                        return;
                    }

                    if (!result.polygons().isEmpty()) {
                        ReferencedEnvelope rasterBounds = mapCanvas.getRasterLayerBounds(mapLayerIndex);
                        if (rasterBounds == null) {
                            throw new IllegalStateException("Could not determine raster geospatial bounds for overlay mapping");
                        }

                        List<Polygon> mappedPolygons = mapCanvas.toMapCoordinates(
                                result.polygons(),
                                dimensions[0],
                                dimensions[1],
                                rasterBounds);

                        String overlayLabel = actionName + " (" + mappedPolygons.size() + " polygons)";
                        mapCanvas.addPolygonOverlayLayer(mappedPolygons, overlayLabel);
                        refreshLayerList(overlayLabel);

                        if (result.message() != null && result.message().toLowerCase(Locale.ROOT).contains("limit")) {
                            showInfoPopup("Run AI Action", "Overlay capped for safety at " + mappedPolygons.size() + " polygons.");
                        }
                    }

                    setStatus("AI action complete: " + actionName + " -> " + result.polygonCount() + " polygons");
                    showInfoPopup("Run AI Action", "Completed " + actionName + " with " + result.polygonCount() + " polygons.");
                } catch (Throwable ex) {
                    log.error("AI action post-processing failed", ex);
                    showError("Run AI Action", "Failed while rendering segmentation result: " + conciseMessage(ex));
                    setStatus("AI action failed during rendering: " + actionName);
                }
            }

            @Override
            protected void failed() {
                progressBar.progressProperty().unbind();
                setProgress(-1);
                Throwable error = getException();
                log.error("AI action failed", error);
                showError("Run AI Action", conciseMessage(error));
                setStatus("AI action failed: " + actionName);
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());
        task.messageProperty().addListener((obs, oldMsg, newMsg) -> {
            if (newMsg != null && !newMsg.isBlank()) {
                setStatus(actionName + " - " + newMsg);
            }
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    public void shutdown() {
        saveProjectSession();
        AiBackendManager.shutdown();
    }

    /**
     * Sets the current project path.
     * 
     * @param projectPath Path to the project directory
     */
    public void setProjectPath(java.nio.file.Path projectPath) {
        this.currentProjectPath = projectPath;
        updateWorkspaceInsights();
        log.debug("Project path set to: {}", projectPath);
    }

    /**
     * Gets the current project path.
     * 
     * @return Path to the current project, or null if none set
     */
    public java.nio.file.Path getProjectPath() {
        return currentProjectPath;
    }

    /**
     * Restores the project session (layers and state).
     * Should be called after setProjectPath().
     * For new projects, loads blank state. For existing projects, loads saved state.
     */
    public void restoreProjectSession() {
        try {
            if (currentProjectPath == null) {
                log.debug("No project path set, restoring legacy global session state");
                restoreLayerState();
                return;
            }

            rasterLayerPathByLabel.clear();
            vectorLayerPathByLabel.clear();
            analysisSessionLayerPathByLabel.clear();
            layerGroupByTitle.clear();
            layerGroupOrder.clear();

            log.info("Restoring project session from: {}", currentProjectPath);

            // Load project-specific session
            ProjectSessionManager.ProjectSession session = ProjectSessionManager.loadProjectSession(currentProjectPath);

            if (session == null) {
                // New project - initialize with blank state
                log.debug("Creating blank session for new project");
                session = ProjectSessionManager.createBlankSession(currentProjectPath);
            }

            // Restore layer state from project
            if (session.layerState != null && !session.layerState.isEmpty()) {
                restorePersistedLayers(session.layerState);
            }

            // Restore digitized features from project
            if (session.digitizedState != null && !session.digitizedState.isEmpty()) {
                mapCanvas.restoreDigitizedFeatures(session.digitizedState);
            }

            restoreAiActionState(session.aiActionState);

            String title = currentProjectPath.getFileName().toString();
            setStatus("Project loaded: " + title);
        } catch (Throwable ex) {
            log.error("Failed to restore project session for {}", currentProjectPath, ex);
            setStatus("Project opened with recovery mode (session restore failed)");
        }
    }

    /**
     * Saves the current session to the project.
     * Called on shutdown or periodically.
     */
    public void saveProjectSession() {
        if (currentProjectPath == null) {
            // No project - just save to global preferences (legacy)
            persistLayerStateNow();
            return;
        }

        // Serialize current state
        String layerState = serializeCurrentLayerState();
        String digitizedState = mapCanvas.serializeDigitizedFeatures();
        String aiActionState = serializeAiActionState();
        
        // Save to project directory
        boolean saved = ProjectSessionManager.saveProjectSession(currentProjectPath, layerState, digitizedState, aiActionState);
        if (saved) {
            java.nio.file.Path previewPath = currentProjectPath.resolve(PROJECT_PREVIEW_FILE);
            mapCanvas.saveSnapshotImage(previewPath);
            // Update project last modified timestamp
            projectManager.updateProjectLastModified(currentProjectPath);
            log.debug("Project session saved for: {}", currentProjectPath);
        } else {
            log.warn("Failed to save project session for: {}", currentProjectPath);
        }
    }

    private String serializeCurrentLayerState() {
        List<String> encodedEntries = buildEncodedLayerEntries(true);
        return encodedEntries.isEmpty() ? "" : String.join(";", encodedEntries);
    }

    /**
     * Loads a vector file asynchronously and adds it to the map.
     * Shows detailed progress feedback including file size and loading stages.
     * 
     * @param file The vector file to load
     */
    private void loadVectorFile(File file) {
        log.info("Loading vector file: {}", file.getName());
        
        // Get file size for progress display
        long fileSize = file.length();
        String fileSizeStr = formatFileSize(fileSize);
        setStatus("Loading " + file.getName() + " (" + fileSizeStr + ")...");
        setProgress(0.1); // Show initial progress

        Task<SimpleFeatureSource> loadTask = new Task<>() {
            @Override
            protected SimpleFeatureSource call() throws Exception {
                // Stage 1: Opening file (10%)
                updateProgress(1, 10);
                updateMessage("Opening file...");
                
                VectorImporter importer = new VectorImporter();
                
                // Stage 2: Reading data (50%)
                updateProgress(5, 10);
                updateMessage("Reading data...");
                SimpleFeatureSource source = importer.readVectorFile(file);
                
                // Stage 3: Processing features (80%)
                updateProgress(8, 10);
                updateMessage("Processing features...");
                
                // Allow some time for user to see progress
                if (fileSize > 1024 * 1024) { // >1MB
                    Thread.sleep(100);
                }
                
                return source;
            }

            @Override
            protected void succeeded() {
                try {
                    SimpleFeatureSource featureSource = getValue();
                    
                    // Stage 4: Rendering (90%)
                    setStatus("Rendering " + file.getName() + "...");
                    setProgress(0.9);
                    
                    // Create a style for the layer
                    Style style = createDefaultVectorStyle(featureSource);
                    
                    // Add layer to map
                    mapCanvas.addLayer(featureSource, style);

                    // Get feature count for display
                    int featureCount = featureSource.getCount(Query.ALL);
                    if (featureCount < 0) {
                        featureCount = featureSource.getFeatures().size();
                    }
                    String layerInfo = file.getName() + " (" + featureCount + " features)";
                    mapCanvas.getMapContent().layers().get(mapCanvas.getLayerCount() - 1).setTitle(layerInfo);
                    vectorLayerPathByLabel.put(layerInfo, file.getAbsolutePath());
                    layerGroupByTitle.remove(layerInfo);
                    refreshLayerList(layerInfo);
                    saveLayerState();
                    
                    // Complete (100%)
                    setProgress(1.0);
                    setStatus("Loaded " + file.getName() + " - " + featureCount + " features (" + fileSizeStr + ")");
                    log.info("Vector file loaded successfully: {} with {} features", file.getName(), featureCount);

                    // Hide progress bar after a short delay without blocking the FX thread.
                    PauseTransition hideDelay = new PauseTransition(Duration.millis(1500));
                    hideDelay.setOnFinished(evt -> setProgress(-1));
                    hideDelay.play();
                } catch (Exception e) {
                    log.error("Error processing vector layer", e);
                    showError("Error Loading Vector", "Failed to process vector layer: " + conciseMessage(e));
                    setProgress(-1);
                }
            }

            @Override
            protected void failed() {
                Throwable error = getException();
                log.error("Failed to load vector file: {}", file.getName(), error);
                showError("Error Loading Vector", "Failed to load " + file.getName() + ": " + conciseMessage(error));
                setStatus("Failed to load " + file.getName());
                setProgress(-1);
            }
        };
        
        // Bind progress bar to task progress
        progressBar.progressProperty().bind(loadTask.progressProperty());
        
        // Update status with task message
        loadTask.messageProperty().addListener((obs, oldMsg, newMsg) -> {
            if (newMsg != null && !newMsg.isEmpty()) {
                setStatus(file.getName() + " - " + newMsg);
            }
        });

        // Run task in background thread
        Thread thread = new Thread(loadTask);
        thread.setDaemon(true);
        thread.start();
    }

    private Style createDefaultVectorStyle(SimpleFeatureSource featureSource) {
        if (featureSource == null || featureSource.getSchema() == null) {
            StyleBuilder fallbackBuilder = new StyleBuilder();
            return fallbackBuilder.createStyle(fallbackBuilder.createPolygonSymbolizer());
        }

        SimpleFeatureType schema = featureSource.getSchema();
        GeometryDescriptor geometryDescriptor = schema.getGeometryDescriptor();
        if (geometryDescriptor == null || geometryDescriptor.getType() == null) {
            return SLD.createSimpleStyle(schema);
        }

        Class<?> geometryBinding = geometryDescriptor.getType().getBinding();
        StyleBuilder sb = new StyleBuilder();

        if (geometryBinding != null
            && (Polygon.class.isAssignableFrom(geometryBinding)
            || Polygonal.class.isAssignableFrom(geometryBinding))) {
            return sb.createStyle(
                    sb.createPolygonSymbolizer(
                            sb.createStroke(new java.awt.Color(0, 102, 204, 180), 1.2),
                            sb.createFill(new java.awt.Color(0, 140, 255, 55))));
        }

        if (geometryBinding != null
            && (LineString.class.isAssignableFrom(geometryBinding)
            || Lineal.class.isAssignableFrom(geometryBinding))) {
            return sb.createStyle(sb.createLineSymbolizer(new java.awt.Color(0, 120, 215), 1.8));
        }

        if (geometryBinding != null
            && (Point.class.isAssignableFrom(geometryBinding)
            || Puntal.class.isAssignableFrom(geometryBinding))) {
            return sb.createStyle(sb.createPointSymbolizer());
        }

        return SLD.createSimpleStyle(schema);
    }
    
    /**
     * Formats file size in human-readable format.
     * 
     * @param size File size in bytes
     * @return Formatted string (e.g., "1.5 MB")
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private int chooseSegmentationTileSize(int rasterWidth, int rasterHeight) {
        long pixels = (long) rasterWidth * (long) rasterHeight;
        long targetTiles = 1024L;
        int adaptive = (int) Math.ceil(Math.sqrt(pixels / (double) targetTiles));
        return Math.max(256, adaptive);
    }

    /**
     * Loads a raster file asynchronously and adds it to the map.
     * 
     * @param file The raster file to load
     */
    private void loadRasterFile(File file) {
        loadRasterFile(file, false, null);
    }

    private void loadRasterFile(File file, boolean preferSocStyle, String provenanceSummary) {
        log.info("Loading raster file: {}", file.getName());

        long fileSize = file.length();
        String fileSizeStr = formatFileSize(fileSize);
        setStatus("Loading raster " + file.getName() + " (" + fileSizeStr + ")...");
        setProgress(0.1);

        Task<LoadedRaster> loadTask = new Task<>() {
            @Override
            protected LoadedRaster call() throws Exception {
                updateProgress(1, 10);
                updateMessage("Opening raster...");

                RasterImporter importer = new RasterImporter();

                updateProgress(6, 10);
                updateMessage("Preparing raster reader...");
                AbstractGridCoverage2DReader reader = importer.openRasterReader(file);
                boolean heavyRaster = isHeavyRaster(reader);

                updateProgress(9, 10);
                updateMessage("Preparing map render...");
                return new LoadedRaster(reader, heavyRaster);
            }

            @Override
            protected void succeeded() {
                try {
                    LoadedRaster loadedRaster = getValue();
                    AbstractGridCoverage2DReader reader = loadedRaster.reader();

                    if (loadedRaster.heavyRaster()) {
                        disableBasemapForHeavyRaster(file.getName());
                    }

                    String layerLabel = file.getName() + " (raster)";
                    mapCanvas.addRasterLayer(reader, layerLabel);
                    int addedLayerIndex = mapCanvas.getLayerCount() - 1;
                    if (addedLayerIndex >= 0 && (preferSocStyle || looksLikeSocRaster(file))) {
                        mapCanvas.setLayerStyle(addedLayerIndex, buildSocRasterStyle());
                    }
                    if (provenanceSummary != null && !provenanceSummary.isBlank()) {
                        provenanceByLayerLabel.put(layerLabel, provenanceSummary);
                    }
                    rasterLayerPathByLabel.put(layerLabel, file.getAbsolutePath());
                    layerGroupByTitle.remove(layerLabel);
                    refreshLayerList(layerLabel);
                    saveLayerState();
                    setProgress(1.0);
                    setStatus("Loaded raster " + file.getName() + " (" + fileSizeStr + ")");
                    log.info("Raster file loaded successfully: {}", file.getName());
                } catch (Throwable e) {
                    log.error("Error processing raster layer", e);
                    showError("Error Loading Raster", "Failed to process raster layer: " + conciseMessage(e));
                    setStatus("Failed to load raster " + file.getName());
                } finally {
                    setProgress(-1);
                }
            }

            @Override
            protected void failed() {
                Throwable error = getException();
                log.error("Failed to load raster file: {}", file.getName(), error);
                showError("Error Loading Raster", "Failed to load " + file.getName() + ": " + conciseMessage(error));
                setStatus("Failed to load raster " + file.getName());
                setProgress(-1);
            }
        };

        progressBar.progressProperty().bind(loadTask.progressProperty());
        loadTask.messageProperty().addListener((obs, oldMsg, newMsg) -> {
            if (newMsg != null && !newMsg.isEmpty()) {
                setStatus(file.getName() + " - " + newMsg);
            }
        });

        Thread thread = new Thread(loadTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void restoreLayerState() {
        String encodedState = prefs.get(PREF_KEY_LAYER_STATE, "");
        restoreAiActionState(prefs.get(PREF_KEY_AI_ACTION_STATE, ""));
        if (encodedState == null || encodedState.isBlank()) {
            restoreDigitizedState();
            return;
        }

        String[] encodedEntries = encodedState.split(";");
        int restoredCount = 0;
        List<PersistedLayer> deferredRasters = new ArrayList<>();

        for (String encodedEntry : encodedEntries) {
            PersistedLayer persistedLayer = decodePersistedLayer(encodedEntry);
            if (persistedLayer == null) {
                continue;
            }

            File sourceFile = new File(persistedLayer.path());
            if (!sourceFile.exists()) {
                log.warn("Skipping persisted layer; file missing: {}", persistedLayer.path());
                continue;
            }

            if ("RASTER".equals(persistedLayer.type())) {
                if (!isRasterSessionRestoreEnabled()) {
                    log.warn("Skipping persisted raster layer during session restore (disabled by TERRAGIS_RESTORE_RASTER_SESSION): {}",
                            persistedLayer.path());
                    continue;
                }
                deferredRasters.add(persistedLayer);
                continue;
            }

            boolean loaded = switch (persistedLayer.type()) {
                case "VECTOR" -> restoreVectorLayer(sourceFile, persistedLayer);
                default -> false;
            };

            if (loaded) {
                restoredCount++;
            }
        }

        if (restoredCount > 0) {
            refreshLayerList(null);
            int restoredDigitized = restoreDigitizedState();
            if (restoredDigitized > 0) {
                setStatus("Restored " + restoredCount + " layer(s) and " + restoredDigitized + " digitized feature(s)");
            } else {
                setStatus("Restored " + restoredCount + " layer(s) from previous session");
            }
            persistLayerStateNow();
            if (!deferredRasters.isEmpty()) {
                scheduleDeferredRasterRestore(deferredRasters);
            }
            return;
        }

        int restoredDigitized = restoreDigitizedState();
        if (restoredDigitized > 0) {
            setStatus("Restored " + restoredDigitized + " digitized feature(s)");
        }

        if (!deferredRasters.isEmpty()) {
            scheduleDeferredRasterRestore(deferredRasters);
        }
    }

    private void restorePersistedLayers(String encodedState) {
        if (encodedState == null || encodedState.isBlank()) {
            return;
        }

        String[] encodedEntries = encodedState.split(";");
        int restoredCount = 0;
        List<PersistedLayer> deferredRasters = new ArrayList<>();

        for (String encodedEntry : encodedEntries) {
            PersistedLayer persistedLayer = decodePersistedLayer(encodedEntry);
            if (persistedLayer == null) {
                continue;
            }

            File sourceFile = new File(persistedLayer.path());
            if (!sourceFile.exists()) {
                log.warn("Skipping persisted layer; file missing: {}", persistedLayer.path());
                continue;
            }

            if ("RASTER".equals(persistedLayer.type())) {
                if (!isRasterSessionRestoreEnabled()) {
                    log.warn("Skipping persisted raster layer during session restore (disabled by TERRAGIS_RESTORE_RASTER_SESSION): {}",
                            persistedLayer.path());
                    continue;
                }
                deferredRasters.add(persistedLayer);
                continue;
            }

            boolean loaded = switch (persistedLayer.type()) {
                case "VECTOR" -> restoreVectorLayer(sourceFile, persistedLayer);
                default -> false;
            };

            if (loaded) {
                restoredCount++;
            }
        }

        if (restoredCount > 0) {
            refreshLayerList(null);
            log.info("Restored {} layer(s) from project session", restoredCount);
        }

        if (!deferredRasters.isEmpty()) {
            scheduleDeferredRasterRestore(deferredRasters);
        }
    }

    private boolean isRasterSessionRestoreEnabled() {
        String raw = firstNonBlank(
                System.getenv("TERRAGIS_RESTORE_RASTER_SESSION"),
                System.getenv("TERRAGIS_RESTORE_RASTER_LAYERS"));
        if (raw == null) {
            return true;
        }

        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "0", "false", "no", "off" -> false;
            default -> true;
        };
    }

    private void scheduleDeferredRasterRestore(List<PersistedLayer> rasterLayers) {
        if (rasterLayers == null || rasterLayers.isEmpty()) {
            return;
        }

        List<PersistedLayer> queue = List.copyOf(rasterLayers);
        log.info("Queued {} raster layer(s) for deferred session restore", queue.size());
        setStatus("Restoring " + queue.size() + " raster layer(s) in background...");

        PauseTransition startupDelay = new PauseTransition(Duration.millis(900));
        startupDelay.setOnFinished(evt -> restoreNextRasterFromQueue(queue, 0, 0));
        startupDelay.play();
    }

    private void restoreNextRasterFromQueue(List<PersistedLayer> queue, int index, int restored) {
        if (index >= queue.size()) {
            if (restored > 0) {
                refreshLayerList(null);
                log.info("Deferred restore completed: {} raster layer(s) restored", restored);
                setStatus("Restored " + restored + " raster layer(s) from session");
                saveLayerState();
            } else {
                setStatus("No raster layers restored from session");
            }
            return;
        }

        PersistedLayer persistedLayer = queue.get(index);
        File sourceFile = new File(persistedLayer.path());
        if (!sourceFile.exists()) {
            log.warn("Skipping deferred raster restore; file missing: {}", persistedLayer.path());
            Platform.runLater(() -> restoreNextRasterFromQueue(queue, index + 1, restored));
            return;
        }

        log.info("Deferred restore loading raster ({}/{}): {}", index + 1, queue.size(), sourceFile.getAbsolutePath());

        restoreRasterLayerDeferred(sourceFile, persistedLayer, success -> {
            int nextRestored = success ? restored + 1 : restored;
            Platform.runLater(() -> restoreNextRasterFromQueue(queue, index + 1, nextRestored));
        });
    }

    private void restoreRasterLayerDeferred(File file, PersistedLayer persistedLayer, java.util.function.Consumer<Boolean> onDone) {
        Task<AbstractGridCoverage2DReader> task = new Task<>() {
            @Override
            protected AbstractGridCoverage2DReader call() throws Exception {
                RasterImporter importer = new RasterImporter();
                return importer.openRasterReader(file);
            }

            @Override
            protected void succeeded() {
                try {
                    AbstractGridCoverage2DReader reader = getValue();
                    String layerLabel = file.getName() + " (raster)";
                    String restoredTitle = persistedLayer.title();
                    if (restoredTitle != null && !restoredTitle.isBlank()) {
                        layerLabel = restoredTitle;
                    }
                    mapCanvas.addRasterLayer(reader, layerLabel);
                    int addedLayerIndex = mapCanvas.getLayerCount() - 1;
                    moveLayerToSavedOrder(addedLayerIndex, persistedLayer.order());
                    int currentLayerIndex = findLayerIndexByTitle(layerLabel);
                    if (currentLayerIndex < 0) {
                        currentLayerIndex = addedLayerIndex;
                    }
                    mapCanvas.setLayerVisible(currentLayerIndex, persistedLayer.visible());
                    mapCanvas.setLayerOpacity(currentLayerIndex, persistedLayer.opacity());
                    rasterLayerPathByLabel.put(layerLabel, file.getAbsolutePath());
                    applyLayerGroup(layerLabel, persistedLayer.group());
                    log.info("Deferred raster restore succeeded: {}", file.getAbsolutePath());
                    onDone.accept(true);
                } catch (Throwable ex) {
                    log.warn("Deferred raster restore failed for {}: {}", file.getAbsolutePath(), conciseMessage(ex));
                    onDone.accept(false);
                }
            }

            @Override
            protected void failed() {
                Throwable ex = getException();
                log.warn("Deferred raster restore failed for {}: {}", file.getAbsolutePath(), conciseMessage(ex));
                onDone.accept(false);
            }
        };

        Thread thread = new Thread(task, "restore-raster-" + file.getName());
        thread.setDaemon(true);
        thread.start();
    }

    private boolean restoreVectorLayer(File file, PersistedLayer persistedLayer) {
        try {
            VectorImporter importer = new VectorImporter();
            SimpleFeatureSource featureSource = importer.readVectorFile(file);
            String operation = persistedLayer.analysisOperation();
            Style style;
            if (operation != null && !operation.isBlank() && usesTransparentAnalysisStyle(operation)) {
                style = createTransparentAnalysisStyle(featureSource.getSchema(), operation);
            } else {
                style = SLD.createSimpleStyle(featureSource.getSchema());
            }
            mapCanvas.addLayer(featureSource, style);

            int featureCount = featureSource.getCount(Query.ALL);
            if (featureCount < 0) {
                featureCount = featureSource.getFeatures().size();
            }

            String restoredTitle = persistedLayer.title();
            String layerInfo = (restoredTitle == null || restoredTitle.isBlank())
                    ? file.getName() + " (" + featureCount + " features)"
                    : restoredTitle;
            int addedLayerIndex = mapCanvas.getLayerCount() - 1;
            mapCanvas.getMapContent().layers().get(addedLayerIndex).setTitle(layerInfo);
            moveLayerToSavedOrder(addedLayerIndex, persistedLayer.order());
            int currentLayerIndex = findLayerIndexByTitle(layerInfo);
            if (currentLayerIndex < 0) {
                currentLayerIndex = addedLayerIndex;
            }
            vectorLayerPathByLabel.put(layerInfo, file.getAbsolutePath());
            mapCanvas.setLayerVisible(currentLayerIndex, persistedLayer.visible());
            mapCanvas.setLayerOpacity(currentLayerIndex, persistedLayer.opacity());
            if (persistedLayer.fillColor() != null && !persistedLayer.fillColor().isBlank()) {
                mapCanvas.setVectorLayerFillColor(currentLayerIndex, persistedLayer.fillColor());
            }
            if (persistedLayer.boundaryColor() != null && !persistedLayer.boundaryColor().isBlank()) {
                mapCanvas.setVectorLayerBoundaryColor(currentLayerIndex, persistedLayer.boundaryColor());
            }
            applyLayerGroup(layerInfo, persistedLayer.group());
            return true;
        } catch (Exception ex) {
            log.warn("Failed to restore vector layer from {}: {}", file.getAbsolutePath(), conciseMessage(ex));
            return false;
        }
    }

    private void saveLayerState() {
        stateSaveDebounce.playFromStart();
    }

    private boolean isHeavyRaster(AbstractGridCoverage2DReader reader) {
        if (reader == null) {
            return false;
        }

        try {
            var gridRange = reader.getOriginalGridRange();
            if (gridRange == null) {
                return false;
            }
            long width = gridRange.getSpan(0);
            long height = gridRange.getSpan(1);
            long pixels = width * height;
            return pixels >= HEAVY_RASTER_PIXEL_THRESHOLD;
        } catch (Exception ex) {
            log.debug("Could not determine raster dimensions for heavy-raster check", ex);
            return false;
        }
    }

    private void disableBasemapForHeavyRaster(String rasterName) {
        int basemapLayerIndex = findMapLayerIndexByTitle(BASEMAP_LAYER_TITLE);
        if (basemapLayerIndex < 0) {
            return;
        }

        boolean removed = removeActiveBasemapLayer();
        if (removed) {
            suppressBasemapSelectionEvent = true;
            try {
                basemapCombo.setValue(BASEMAP_NONE);
            } finally {
                suppressBasemapSelectionEvent = false;
            }
            activeBasemapName = BASEMAP_NONE;
            refreshLayerList(null);
            log.warn("Disabled basemap while loading heavy raster: {}", rasterName);
            setStatus("Basemap disabled for heavy raster performance. Re-enable it after load if needed.");
            saveLayerState();
        }
    }

    private void persistLayerStateNow() {
        List<String> encodedEntries = buildEncodedLayerEntries(false);

        if (encodedEntries.isEmpty()) {
            prefs.remove(PREF_KEY_LAYER_STATE);
        } else {
            prefs.put(PREF_KEY_LAYER_STATE, String.join(";", encodedEntries));
        }

        String encodedDigitizedState = mapCanvas.serializeDigitizedFeatures();
        if (encodedDigitizedState == null || encodedDigitizedState.isBlank()) {
            prefs.remove(PREF_KEY_DIGITIZED_STATE);
        } else {
            prefs.put(PREF_KEY_DIGITIZED_STATE, encodedDigitizedState);
        }

        String encodedAiActionState = serializeAiActionState();
        if (encodedAiActionState == null || encodedAiActionState.isBlank()) {
            prefs.remove(PREF_KEY_AI_ACTION_STATE);
        } else {
            prefs.put(PREF_KEY_AI_ACTION_STATE, encodedAiActionState);
        }

        try {
            prefs.flush();
        } catch (BackingStoreException ex) {
            log.warn("Failed to flush preference state to disk", ex);
        }

        if (currentProjectPath != null) {
            ProjectSessionManager.saveProjectSession(currentProjectPath, serializeCurrentLayerState(), encodedDigitizedState, encodedAiActionState);
        }
    }

    private int restoreDigitizedState() {
        String encodedDigitizedState = prefs.get(PREF_KEY_DIGITIZED_STATE, "");
        return mapCanvas.restoreDigitizedFeatures(encodedDigitizedState);
    }

    private String serializeAiActionState() {
        List<String> entries = new ArrayList<>();

        for (AiModelDefinition model : aiModelsById.values()) {
            entries.add(encodeAiStateEntry(
                    "MODEL",
                    model.id(),
                    model.displayName(),
                    model.modelName(),
                    model.sourceType(),
                    model.sourceRef(),
                    model.inputType()));
        }

        for (AiActionDefinition action : aiActions) {
            entries.add(encodeAiStateEntry(
                    "ACTION",
                    action.actionName(),
                    action.modelId(),
                    action.scope(),
                    Integer.toString(action.tileSize())));
        }

        return entries.isEmpty() ? "" : String.join(";", entries);
    }

    private void restoreAiActionState(String encodedState) {
        initAiModelRegistry();

        if (encodedState == null || encodedState.isBlank()) {
            refreshAiActionPicker();
            return;
        }

        List<AiActionDefinition> loadedActions = new ArrayList<>();
        String[] entries = encodedState.split(";");
        for (String entry : entries) {
            String[] parts = decodeAiStateEntry(entry);
            if (parts == null || parts.length < 2) {
                continue;
            }

            if ("MODEL".equals(parts[0]) && parts.length >= 7) {
                AiModelDefinition model = new AiModelDefinition(
                        parts[1],
                        parts[2],
                        parts[3],
                        parts[4],
                        parts[5],
                        parts[6]);
                aiModelsById.put(model.id(), model);
            }

            if ("ACTION".equals(parts[0]) && parts.length >= 5) {
                int tileSize = -1;
                try {
                    tileSize = Integer.parseInt(parts[4]);
                } catch (NumberFormatException ignored) {
                    tileSize = -1;
                }
                loadedActions.add(new AiActionDefinition(parts[1], parts[2], parts[3], tileSize));
            }
        }

        aiActions.clear();
        if (!loadedActions.isEmpty()) {
            aiActions.addAll(loadedActions);
        }

        aiActions.removeIf(action -> !aiModelsById.containsKey(action.modelId()));
        ensureAtLeastOneAiAction();
        refreshAiActionPicker();
    }

    private String encodeAiStateEntry(String... fields) {
        String payload = String.join("\n", fields);
        return Base64.getUrlEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private String[] decodeAiStateEntry(String encodedEntry) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(encodedEntry), StandardCharsets.UTF_8);
            return decoded.split("\\n", -1);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid AI state entry encountered; skipping");
            return null;
        }
    }

    private String encodePersistedLayer(PersistedLayer persistedLayer) {
        String payload = persistedLayer.type() + "\n"
                + (persistedLayer.visible() ? "1" : "0") + "\n"
                + persistedLayer.order() + "\n"
                + (persistedLayer.title() == null ? "" : persistedLayer.title()) + "\n"
                + (persistedLayer.analysisOperation() == null ? "" : persistedLayer.analysisOperation()) + "\n"
                + String.format(Locale.ROOT, "%.6f", persistedLayer.opacity()) + "\n"
                + (persistedLayer.group() == null ? "" : persistedLayer.group()) + "\n"
                + (persistedLayer.fillColor() == null ? "" : persistedLayer.fillColor()) + "\n"
                + (persistedLayer.boundaryColor() == null ? "" : persistedLayer.boundaryColor()) + "\n"
                + persistedLayer.path();
        return Base64.getUrlEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private PersistedLayer decodePersistedLayer(String encodedEntry) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(encodedEntry), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\n", -1);
            if (parts.length == 3) {
                return new PersistedLayer(parts[0], parts[2], "1".equals(parts[1]), 1.0, -1, "", "", "", "", "");
            }
            if (parts.length >= 4) {
                int order = -1;
                try {
                    order = Integer.parseInt(parts[2]);
                } catch (NumberFormatException ignored) {
                    order = -1;
                }
                if (parts.length >= 7) {
                    double opacity = parsePersistedOpacity(parts[5]);
                    if (parts.length >= 8) {
                        if (parts.length >= 10) {
                            return new PersistedLayer(parts[0], parts[9], "1".equals(parts[1]), opacity, order, parts[3], parts[4], parts[6], parts[7], parts[8]);
                        }
                        if (parts.length >= 9) {
                            return new PersistedLayer(parts[0], parts[8], "1".equals(parts[1]), opacity, order, parts[3], parts[4], parts[6], "", parts[7]);
                        }
                        return new PersistedLayer(parts[0], parts[7], "1".equals(parts[1]), opacity, order, parts[3], parts[4], parts[6], "", "");
                    }
                    return new PersistedLayer(parts[0], parts[6], "1".equals(parts[1]), opacity, order, parts[3], parts[4], "", "", "");
                }
                if (parts.length >= 6) {
                    return new PersistedLayer(parts[0], parts[5], "1".equals(parts[1]), 1.0, order, parts[3], parts[4], "", "", "");
                }
                return new PersistedLayer(parts[0], parts[3], "1".equals(parts[1]), 1.0, order, "", "", "", "", "");
            }
            return null;
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid persisted layer entry encountered; skipping");
            return null;
        }
    }

    private double parsePersistedOpacity(String rawOpacity) {
        if (rawOpacity == null || rawOpacity.isBlank()) {
            return 1.0;
        }

        try {
            double opacity = Double.parseDouble(rawOpacity);
            return Math.max(0.0, Math.min(1.0, opacity));
        } catch (NumberFormatException ex) {
            return 1.0;
        }
    }

    private List<String> buildEncodedLayerEntries(boolean includeProjectManagedAnalysisLayers) {
        List<String> encodedEntries = new ArrayList<>();
        List<Layer> layers = mapCanvas.getMapContent().layers();

        for (int index = 0; index < layers.size(); index++) {
            Layer layer = layers.get(index);
            if (layer == null) {
                continue;
            }

            String title = layer.getTitle();
            if (title == null || title.isBlank()) {
                continue;
            }

            String type = mapCanvas.isRasterLayer(index) ? "RASTER" : "VECTOR";
            String operation = "VECTOR".equals(type) ? deriveAnalysisOperationFromLayerTitle(title) : "";
            String path = resolvePersistableLayerPath(layer, index, title, type, includeProjectManagedAnalysisLayers);
            if (path == null || path.isBlank()) {
                continue;
            }

            String group = layerGroupByTitle.getOrDefault(title, "");
            String fillColor = "VECTOR".equals(type) ? mapCanvas.getVectorLayerFillColor(index) : "";
            String boundaryColor = "VECTOR".equals(type) ? mapCanvas.getVectorLayerBoundaryColor(index) : "";
            encodedEntries.add(encodePersistedLayer(new PersistedLayer(type, path, layer.isVisible(), mapCanvas.getLayerOpacity(index), index, title, operation, group, fillColor, boundaryColor)));
        }

        return encodedEntries;
    }

    private String resolvePersistableLayerPath(Layer layer, int layerIndex, String title, String type, boolean includeProjectManagedAnalysisLayers) {
        String path = "RASTER".equals(type) ? rasterLayerPathByLabel.get(title) : vectorLayerPathByLabel.get(title);
        if (path != null && !path.isBlank()) {
            return path;
        }

        if (!includeProjectManagedAnalysisLayers || currentProjectPath == null || !"VECTOR".equals(type)) {
            return null;
        }

        String existing = analysisSessionLayerPathByLabel.get(title);
        if (existing != null && !existing.isBlank() && Files.exists(Path.of(existing))) {
            return existing;
        }

        SimpleFeatureSource source = mapCanvas.getVectorFeatureSource(layerIndex);
        if (source == null) {
            return null;
        }

        try {
            Path sessionLayerDir = currentProjectPath.resolve("layers").resolve("session-analysis");
            Files.createDirectories(sessionLayerDir);
            String fileName = sanitizeForSessionFile(title) + "_" + layerIndex + ".gpkg";
            Path outputPath = sessionLayerDir.resolve(fileName);

            new VectorExportService().export(source, outputPath.toFile());
            analysisSessionLayerPathByLabel.put(title, outputPath.toString());
            vectorLayerPathByLabel.put(title, outputPath.toString());
            return outputPath.toString();
        } catch (Exception ex) {
            log.warn("Failed to persist generated analysis layer '{}' into project session: {}", title, conciseMessage(ex));
            return null;
        }
    }

    private String sanitizeForSessionFile(String value) {
        String sanitized = value == null ? "analysis_layer" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
        sanitized = sanitized.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        if (sanitized.isBlank()) {
            return "analysis_layer";
        }
        return sanitized;
    }

    private String deriveAnalysisOperationFromLayerTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        String normalized = title.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("buffer -")) {
            return "Buffer";
        }
        if (normalized.startsWith("intersection -")) {
            return "Intersection";
        }
        if (normalized.startsWith("clip -")) {
            return "Clip";
        }
        if (normalized.startsWith("dissolve -")) {
            return "Dissolve";
        }
        if (normalized.startsWith("reproject -")) {
            return "Reproject";
        }
        return "";
    }

    private int findLayerIndexByTitle(String layerTitle) {
        if (layerTitle == null || layerTitle.isBlank()) {
            return -1;
        }

        List<Layer> layers = mapCanvas.getMapContent().layers();
        for (int index = 0; index < layers.size(); index++) {
            Layer layer = layers.get(index);
            if (layer == null) {
                continue;
            }
            if (layerTitle.equals(layer.getTitle())) {
                return index;
            }
        }
        return -1;
    }

    private void moveLayerToSavedOrder(int fromIndex, int savedOrder) {
        if (savedOrder < 0) {
            return;
        }

        int layerCount = mapCanvas.getLayerCount();
        if (fromIndex < 0 || fromIndex >= layerCount || layerCount <= 1) {
            return;
        }

        int clampedTarget = Math.max(0, Math.min(savedOrder, layerCount - 1));
        if (fromIndex == clampedTarget) {
            return;
        }

        mapCanvas.getMapContent().moveLayer(fromIndex, clampedTarget);
        mapCanvas.invalidateLayerMetadataCache();
    }

    private boolean handleLayerDrop(String draggedLayerTitle, String dropTarget) {
        if (!isLayerListLayerItem(draggedLayerTitle) || dropTarget == null) {
            return false;
        }

        if (isGroupHeaderItem(dropTarget)) {
            String groupName = parseGroupNameFromHeader(dropTarget);
            applyLayerGroup(draggedLayerTitle, groupName);
            refreshLayerList(draggedLayerTitle);
            saveLayerState();
            setStatus("Moved layer into group: " + groupName);
            return true;
        }

        if (!isLayerListLayerItem(dropTarget)) {
            return false;
        }

        int fromIndex = findLayerIndexByTitle(draggedLayerTitle);
        int toIndex = findLayerIndexByTitle(dropTarget);
        if (fromIndex < 0 || toIndex < 0) {
            return false;
        }

        moveMapLayerToIndex(fromIndex, toIndex);
        String targetGroup = layerGroupByTitle.getOrDefault(dropTarget, "");
        applyLayerGroup(draggedLayerTitle, targetGroup);
        refreshLayerList(draggedLayerTitle);
        saveLayerState();
        setStatus("Moved layer: " + draggedLayerTitle);
        return true;
    }

    private void moveMapLayerToIndex(int fromIndex, int toIndex) {
        if (fromIndex == toIndex) {
            return;
        }

        if (fromIndex < toIndex) {
            int current = fromIndex;
            while (current < toIndex && mapCanvas.moveLayerUp(current)) {
                current++;
            }
            return;
        }

        int current = fromIndex;
        while (current > toIndex && mapCanvas.moveLayerDown(current)) {
            current--;
        }
    }

    private void handleCreateLayerGroup() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Layer Group");
        dialog.setHeaderText("Create a layer group");
        dialog.setContentText("Group name:");

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        String groupName = result.get().trim();
        if (groupName.isBlank()) {
            setStatus("Group name cannot be empty");
            return;
        }

        for (String existing : layerGroupOrder) {
            if (existing.equalsIgnoreCase(groupName)) {
                setStatus("Group already exists: " + existing);
                return;
            }
        }

        layerGroupOrder.add(groupName);
        refreshLayerList(formatGroupHeader(groupName));
        saveLayerState();
        setStatus("Created group: " + groupName);
    }

    private void handleInlineRenameSelectedLayer() {
        int selectedIndex = layerList.getSelectionModel().getSelectedIndex();
        String selectedItem = layerList.getSelectionModel().getSelectedItem();
        if (selectedIndex < 0 || !isLayerListLayerItem(selectedItem)) {
            return;
        }
        beginInlineRename(selectedItem);
    }

    private void beginInlineRename(String oldTitle) {
        TextInputDialog dialog = new TextInputDialog(oldTitle);
        dialog.setTitle("Rename Layer");
        dialog.setHeaderText("Rename selected layer");
        dialog.setContentText("Layer name:");

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        String newTitle = result.get().trim();
        if (newTitle.isBlank() || newTitle.equals(oldTitle)) {
            return;
        }

        performLayerRename(oldTitle, newTitle);
    }

    private void performLayerRename(String oldTitle, String proposedNewTitle) {
        if (oldTitle == null || oldTitle.isBlank() || proposedNewTitle == null) {
            return;
        }

        String newTitle = proposedNewTitle.trim();
        if (newTitle.isBlank() || newTitle.equals(oldTitle)) {
            return;
        }

        int layerIndex = findLayerIndexByTitle(oldTitle);
        if (layerIndex < 0) {
            return;
        }

        if (findLayerIndexByTitle(newTitle) >= 0) {
            showError("Rename Layer", "A layer with this name already exists.");
            return;
        }

        mapCanvas.getMapContent().layers().get(layerIndex).setTitle(newTitle);

        if (vectorLayerPathByLabel.containsKey(oldTitle)) {
            vectorLayerPathByLabel.put(newTitle, vectorLayerPathByLabel.remove(oldTitle));
        }
        if (rasterLayerPathByLabel.containsKey(oldTitle)) {
            rasterLayerPathByLabel.put(newTitle, rasterLayerPathByLabel.remove(oldTitle));
        }
        if (analysisSessionLayerPathByLabel.containsKey(oldTitle)) {
            analysisSessionLayerPathByLabel.put(newTitle, analysisSessionLayerPathByLabel.remove(oldTitle));
        }
        if (provenanceByLayerLabel.containsKey(oldTitle)) {
            provenanceByLayerLabel.put(newTitle, provenanceByLayerLabel.remove(oldTitle));
        }
        if (layerGroupByTitle.containsKey(oldTitle)) {
            layerGroupByTitle.put(newTitle, layerGroupByTitle.remove(oldTitle));
        }

        refreshLayerList(newTitle);
        saveLayerState();
        setStatus("Renamed layer: " + oldTitle + " -> " + newTitle);
    }

    private void applyLayerGroup(String layerTitle, String groupName) {
        if (layerTitle == null || layerTitle.isBlank()) {
            return;
        }

        String normalizedGroup = groupName == null ? "" : groupName.trim();
        if (normalizedGroup.isBlank()) {
            layerGroupByTitle.remove(layerTitle);
            return;
        }

        layerGroupByTitle.put(layerTitle, normalizedGroup);
        if (!layerGroupOrder.contains(normalizedGroup)) {
            layerGroupOrder.add(normalizedGroup);
        }
    }

    private record PersistedLayer(String type, String path, boolean visible, double opacity, int order, String title, String analysisOperation, String group, String fillColor, String boundaryColor) {
    }

    private enum LayoutExportFormat {
        PDF,
        PNG,
        JPEG
    }

    private enum LayoutPageSize {
        A4("A4", 8.27, 11.69),
        LETTER("Letter", 8.5, 11.0),
        A3("A3", 11.69, 16.54);

        private final String label;
        private final double widthIn;
        private final double heightIn;

        LayoutPageSize(String label, double widthIn, double heightIn) {
            this.label = label;
            this.widthIn = widthIn;
            this.heightIn = heightIn;
        }

        public String label() {
            return label;
        }

        public double widthIn() {
            return widthIn;
        }

        public double heightIn() {
            return heightIn;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum LayoutOrientation {
        LANDSCAPE,
        PORTRAIT
    }

    private record LayoutExportPreset(
            String id,
            String name,
            LayoutExportFormat format,
            int dpi,
            LayoutPageSize pageSize,
            LayoutOrientation orientation,
            String filenamePattern) {
    }

    /**
     * Shows an error dialog to the user.
     * 
     * @param title Dialog title
     * @param message Error message
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfoPopup(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initModality(Modality.NONE);
        alert.show();
    }

    private File ensureExportExtension(File output, FileChooser.ExtensionFilter selectedFilter) {
        if (output == null) {
            return null;
        }
        String name = output.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".shp") || name.endsWith(".gpkg") || name.endsWith(".geojson") || name.endsWith(".json")) {
            return output;
        }

        String extension = ".geojson";
        if (selectedFilter != null && !selectedFilter.getExtensions().isEmpty()) {
            String pattern = selectedFilter.getExtensions().get(0).toLowerCase(Locale.ROOT);
            if (pattern.endsWith(".shp")) {
                extension = ".shp";
            } else if (pattern.endsWith(".gpkg")) {
                extension = ".gpkg";
            } else if (pattern.endsWith(".json") || pattern.endsWith(".geojson")) {
                extension = ".geojson";
            }
        }
        return new File(output.getParentFile(), output.getName() + extension);
    }

    private String defaultExportNameForFilter(FileChooser.ExtensionFilter selectedFilter, String baseName) {
        String safeBaseName = sanitizeExportBaseName(baseName);
        if (selectedFilter == null || selectedFilter.getExtensions().isEmpty()) {
            return safeBaseName + ".geojson";
        }

        String pattern = selectedFilter.getExtensions().get(0).toLowerCase(Locale.ROOT);
        if (pattern.endsWith(".shp")) {
            return safeBaseName + ".shp";
        }
        if (pattern.endsWith(".gpkg")) {
            return safeBaseName + ".gpkg";
        }
        return safeBaseName + ".geojson";
    }

    private String defaultExportBaseNameForVectorSource(String sourceLabel) {
        if (sourceLabel == null || sourceLabel.isBlank()) {
            return "export";
        }

        final String selectedPrefix = "Selected map layer: ";
        if (sourceLabel.startsWith(selectedPrefix)) {
            return sourceLabel.substring(selectedPrefix.length());
        }

        return sourceLabel
                .replace("Digitized points (with attributes)", "digitized_points")
                .replace("Digitized lines (with attributes)", "digitized_lines")
                .replace("Digitized polygons (with attributes)", "digitized_polygons")
                .replaceAll("[^a-zA-Z0-9._-]+", "_");
    }

    private String sanitizeExportBaseName(String value) {
        String base = value == null ? "export" : value.trim();
        base = base.replaceAll("[^a-zA-Z0-9._-]+", "_");
        base = base.replaceAll("_+", "_");
        base = base.replaceAll("^_+|_+$", "");
        if (base.isBlank()) {
            return "export";
        }
        return base;
    }

    private File ensureRasterExportExtension(File output) {
        if (output == null) {
            return null;
        }
        String name = output.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".tif") || name.endsWith(".tiff")) {
            return output;
        }
        return new File(output.getParentFile(), output.getName() + ".tif");
    }

    private String conciseMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return "Unknown error";
        }
        String msg = error.getMessage().replace('\n', ' ').replace('\r', ' ').trim();
        return msg.length() > 220 ? msg.substring(0, 220) + "..." : msg;
    }

    private void setupKeyboardShortcuts() {
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalShortcut);
            }
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalShortcut);
            }
        });
    }

    private void handleGlobalShortcut(KeyEvent event) {
        if (event == null) {
            return;
        }

        if (event.getTarget() instanceof TextInputControl) {
            return;
        }

        if (event.getCode() == KeyCode.S) {
            if (editModeCombo != null) {
                editModeCombo.setValue("Select Feature");
            }
            mapCanvas.setEditMode(MapCanvas.EditMode.SELECT);
            setStatus("Edit mode: Select Feature (shortcut S)");
            event.consume();
            return;
        }

        if (event.getCode() == KeyCode.U) {
            boolean undone = mapCanvas.undoLastEdit();
            if (undone) {
                setStatus("Undo completed. Digitized features: " + mapCanvas.getDigitizedFeatureCount() + " (shortcut U)");
            } else {
                setStatus("Nothing to undo (shortcut U)");
            }
            event.consume();
        }
    }
}
