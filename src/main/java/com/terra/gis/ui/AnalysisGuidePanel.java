package com.terra.gis.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AnalysisGuidePanel provides contextual tutorial guidance for spatial analysis workflows.
 * Displays step-by-step instructions for each analysis operation to help users understand
 * how to use Buffer, Intersection, Reproject, Clip, and Dissolve effectively.
 */
public class AnalysisGuidePanel extends VBox {

    private static final double GUIDE_HEIGHT_REGULAR = 280;

    private Label operationTitle;
    private Label operationSummary;
    private VBox detailsBox;
    private ScrollPane guideScrollPane;
    private Label tipLabel;
    private Label pageLabel;
    private Button btnNextTip;
    private Button btnPreviousTip;
        private Button btnNextOperation;
        private Button btnPreviousOperation;
    private int currentTipIndex;
        private int currentOperationIndex;
    private String currentOperationKey = "Buffer";
    private final Map<String, AnalysisOperationTutorial> tutorials = new LinkedHashMap<>();
    private final List<String> orderedOperations = new ArrayList<>();

    private record AnalysisOperationTutorial(
            String title,
            String summary,
            String whenToUse,
            String[] steps,
            String[] inputs,
            String[] tips,
            String[] outputInfo) {
    }

    public AnalysisGuidePanel() {
        setPadding(new Insets(12));
        setSpacing(8);
        setFillWidth(true);
        setMinHeight(300);
        setPrefHeight(380);
        setStyle("-fx-background-color: transparent;");

        initializeTutorials();

        operationTitle = new Label("Analysis Guide");
        operationTitle.setWrapText(true);
        operationTitle.setMaxWidth(Double.MAX_VALUE);
        operationTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        operationSummary = new Label();
        operationSummary.setWrapText(true);
        operationSummary.setLineSpacing(2);
        operationSummary.setMaxWidth(Double.MAX_VALUE);
        operationSummary.setStyle("-fx-font-size: 11px; -fx-text-fill: #d1d5db;");

        detailsBox = new VBox(8);
        detailsBox.setFillWidth(true);

        guideScrollPane = new ScrollPane(detailsBox);
        guideScrollPane.setFitToWidth(true);
        guideScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        guideScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        guideScrollPane.setPrefHeight(GUIDE_HEIGHT_REGULAR);
        VBox.setVgrow(guideScrollPane, Priority.ALWAYS);

        VBox headerBox = new VBox(4, operationTitle, operationSummary);
        headerBox.setFillWidth(true);

        tipLabel = new Label();
        tipLabel.setWrapText(true);
        tipLabel.setLineSpacing(2);
        tipLabel.setMaxWidth(Double.MAX_VALUE);
        tipLabel.setMinHeight(Region.USE_PREF_SIZE);
        tipLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #34d399;");

        pageLabel = new Label();
        pageLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #9ca3af;");

        HBox buttonBox = new HBox(6);
        btnPreviousTip = new Button("◀ Previous");
        btnNextTip = new Button("Next ▶");
        btnPreviousTip.setOnAction(e -> showPreviousTip());
        btnNextTip.setOnAction(e -> showNextTip());
        buttonBox.getChildren().addAll(btnPreviousTip, btnNextTip, pageLabel);

                Label operationLabel = new Label();
                operationLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #9ca3af;");

                HBox operationBox = new HBox(6);
                btnPreviousOperation = new Button("< Previous Operation");
                btnNextOperation = new Button("Next Operation >");
                btnPreviousOperation.setOnAction(e -> showPreviousOperation());
                btnNextOperation.setOnAction(e -> showNextOperation());
                operationBox.getChildren().addAll(btnPreviousOperation, btnNextOperation, operationLabel);

        detailsBox.getChildren().add(tipLabel);
                getChildren().addAll(headerBox, guideScrollPane, buttonBox, operationBox);

        showOperation("Buffer");
    }

    private void initializeTutorials() {
        orderedOperations.clear();

        tutorials.put("Buffer", new AnalysisOperationTutorial(
                "Buffer: Create Proximity Zones",
                "Add a distance buffer around features to define service areas, safety zones, or search radii.",
                "Use when you need to find all features within a specific distance of something.",
                new String[]{
                        "Step 1: Load a vector layer with points, lines, or polygons.",
                        "Step 2: Click Analysis > Buffer from the toolbar.",
                        "Step 3: Select the layer to buffer.",
                        "Step 4: Enter the buffer distance (in layer CRS units, e.g., meters).",
                        "Step 5: A new layer with the buffered geometry appears on your map."
                },
                new String[]{
                        "Input Layer: Point, Line, or Polygon",
                        "Buffer Distance: Numeric value (e.g., 500)",
                        "Output: New layer with circular/rounded zones around input features"
                },
                new String[]{
                        "Positive distance creates outward buffer; negative creates inward.",
                        "Buffer distance is in the same units as your layer's CRS (check layer properties).",
                        "Use intersection after buffer to find features that fall within buffer zones.",
                        "For large layers, buffer computation may take a few seconds."
                },
                new String[]{
                        "Output: Buffer zone geometry with same attributes as input.",
                        "Overlapping buffers merge into single polygons.",
                        "Ideal for proximity analysis, catchment areas, and facility planning."
                }
        ));
        orderedOperations.add("Buffer");

        tutorials.put("Intersection", new AnalysisOperationTutorial(
                "Intersection: Find Overlapping Areas",
                "Compute the overlap between two layers to discover shared geography.",
                "Use to find features from one layer that fall inside another, or vice versa.",
                new String[]{
                        "Step 1: Load two vector layers (both must be in compatible CRS).",
                        "Step 2: Click Analysis > Intersection from the toolbar.",
                        "Step 3: Select Layer A (the features you want to keep parts of).",
                        "Step 4: Select Layer B (the boundary or filter to apply).",
                        "Step 5: A new layer shows only the overlapping geometry."
                },
                new String[]{
                        "Input Layer A: Source features (point, line, or polygon)",
                        "Input Layer B: Boundary or mask features",
                        "Output: Geometry that exists in both layers"
                },
                new String[]{
                        "Layer A and B must not be the same layer.",
                        "CRS mismatch is handled automatically via reprojection.",
                        "Example: Find buildings in flood zones, or parks in districts."
                },
                new String[]{
                        "Output: Only parts of Layer A that overlap with Layer B.",
                        "Point-polygon intersection returns points inside polygon.",
                        "Line-polygon intersection returns line segments inside polygon.",
                        "Polygon-polygon intersection returns overlapping area."
                }
        ));
        orderedOperations.add("Intersection");

        tutorials.put("Reproject", new AnalysisOperationTutorial(
                "Reproject: Change Coordinate System",
                "Transform a layer from one map projection (CRS) to another.",
                "Use when layers use different coordinate systems and need to align.",
                new String[]{
                        "Step 1: Select a vector layer you want to transform.",
                        "Step 2: Click Analysis > Reproject from the toolbar.",
                        "Step 3: Choose the source layer.",
                        "Step 4: Enter target EPSG code (e.g., EPSG:3857 for web mapping).",
                        "Step 5: A new layer in the target CRS is created."
                },
                new String[]{
                        "Input Layer: Vector layer in any CRS",
                        "Target CRS: EPSG code (e.g., EPSG:4326, EPSG:3857, EPSG:32633)",
                        "Output: New layer with geometries transformed to target CRS"
                },
                new String[]{
                        "Source CRS is read from layer metadata; check layer properties to confirm.",
                        "EPSG codes are the standard way to reference coordinate systems.",
                        "Web mapping usually uses EPSG:3857 (Web Mercator).",
                        "Local surveys often use projected systems like EPSG:32633 (UTM Zone 33N)."
                },
                new String[]{
                        "Output: All coordinates transformed to new system, geometry preserved.",
                        "Attribute data unchanged; only spatial coordinates are transformed.",
                        "Useful for aligning layers from different sources or preparing for web export."
                }
        ));
        orderedOperations.add("Reproject");

        tutorials.put("Clip", new AnalysisOperationTutorial(
                "Clip: Cut by Boundary",
                "Keep only the part of one layer that falls within a boundary.",
                "Use to extract a region of interest or cut data to administrative boundaries.",
                new String[]{
                        "Step 1: Load a target layer (data to clip) and a boundary layer.",
                        "Step 2: Click Analysis > Clip from the toolbar.",
                        "Step 3: Select Target Layer (features to cut).",
                        "Step 4: Select Boundary Layer (the cutting boundary).",
                        "Step 5: A new layer with clipped geometry appears, containing only parts inside boundary."
                },
                new String[]{
                        "Target Layer: Features to be clipped",
                        "Boundary Layer: The clipping boundary (polygon boundary is used)",
                        "Output: Features from target that intersect the boundary"
                },
                new String[]{
                        "Clipping removes all parts of target that fall outside boundary.",
                        "Target and boundary must be different layers.",
                        "CRS reprojection is automatic if layers use different systems.",
                        "Example: Clip all buildings to city boundary, or clip roads to county limits."
                },
                new String[]{
                        "Output: All target features clipped to boundary extent.",
                        "Ideal for extracting areas of interest or preparing datasets for specific regions.",
                        "Preserves original attributes but removes geometry outside boundary."
                }
        ));
        orderedOperations.add("Clip");

        tutorials.put("Dissolve", new AnalysisOperationTutorial(
                "Dissolve: Merge Features",
                "Combine adjacent or grouped features into larger single geometries.",
                "Use to remove internal boundaries, summarize by region, or simplify geometry.",
                new String[]{
                        "Step 1: Load a vector layer with features to merge.",
                        "Step 2: Click Analysis > Dissolve from the toolbar.",
                        "Step 3: Choose the input layer.",
                        "Step 4: Select dissolve field or choose '(Dissolve all features)'.",
                        "Step 5: Features are merged; grouped by field value if specified."
                },
                new String[]{
                        "Input Layer: Vector layer with features to merge",
                        "Dissolve Field: (Optional) Attribute to group by",
                        "Output: Merged geometries, one per unique field value"
                },
                new String[]{
                        "No field = all features merged into single geometry.",
                        "With field = features grouped by field value, each group merged.",
                        "Useful for removing internal boundaries in parcel or district layers.",
                        "Example: Merge parcels by owner to show total land area per owner."
                },
                new String[]{
                        "Output: Simplified geometries with internal boundaries removed.",
                        "Attributes: Geometry plus dissolve field (if specified).",
                        "Great for turning zoned cities into single-polygon regions or service areas."
                }
        ));
        orderedOperations.add("Dissolve");
    }

    public void showOperation(String operationKey) {
        if (!tutorials.containsKey(operationKey)) {
            return;
        }

        currentOperationKey = operationKey;
                currentOperationIndex = orderedOperations.indexOf(operationKey);
        currentTipIndex = 0;
        updateDisplay();
    }

        private void showNextOperation() {
                if (currentOperationIndex < orderedOperations.size() - 1) {
                        showOperation(orderedOperations.get(currentOperationIndex + 1));
                }
        }

        private void showPreviousOperation() {
                if (currentOperationIndex > 0) {
                        showOperation(orderedOperations.get(currentOperationIndex - 1));
                }
        }

    private void showNextTip() {
        AnalysisOperationTutorial tutorial = tutorials.get(currentOperationKey);
        int totalTips = tutorial.steps().length + tutorial.tips().length;
        if (currentTipIndex < totalTips - 1) {
            currentTipIndex++;
            updateDisplay();
        }
    }

    private void showPreviousTip() {
        if (currentTipIndex > 0) {
            currentTipIndex--;
            updateDisplay();
        }
    }

    private void updateDisplay() {
        AnalysisOperationTutorial tutorial = tutorials.get(currentOperationKey);
        operationTitle.setText(tutorial.title());
        operationSummary.setText(tutorial.summary() + "\n\nWhen to use: " + tutorial.whenToUse());

        detailsBox.getChildren().clear();

        int allTipsCount = tutorial.steps().length + tutorial.tips().length;
        if (currentTipIndex < tutorial.steps().length) {
            String step = tutorial.steps()[currentTipIndex];
            tipLabel.setText("📋 " + step);
        } else {
            int tipIdx = currentTipIndex - tutorial.steps().length;
            String tip = tutorial.tips()[tipIdx];
            tipLabel.setText("💡 " + tip);
        }

        if (!tutorial.inputs()[0].isEmpty()) {
            Label inputsLabel = new Label("Inputs:");
            inputsLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #9ca3af;");
            for (String input : tutorial.inputs()) {
                Label inputItem = new Label("• " + input);
                inputItem.setStyle("-fx-font-size: 10px; -fx-text-fill: #d1d5db;");
                inputItem.setWrapText(true);
                detailsBox.getChildren().add(inputItem);
            }
            detailsBox.getChildren().add(inputsLabel);
        }

        if (!tutorial.outputInfo()[0].isEmpty()) {
            Label outputLabel = new Label("Output Info:");
            outputLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #9ca3af;");
            for (String output : tutorial.outputInfo()) {
                Label outputItem = new Label("• " + output);
                outputItem.setStyle("-fx-font-size: 10px; -fx-text-fill: #d1d5db;");
                outputItem.setWrapText(true);
                detailsBox.getChildren().add(outputItem);
            }
            detailsBox.getChildren().add(outputLabel);
        }

        detailsBox.getChildren().add(tipLabel);

        pageLabel.setText((currentTipIndex + 1) + " / " + allTipsCount);

        btnPreviousTip.setDisable(currentTipIndex == 0);
        btnNextTip.setDisable(currentTipIndex >= allTipsCount - 1);

                btnPreviousOperation.setDisable(currentOperationIndex == 0);
                btnNextOperation.setDisable(currentOperationIndex >= orderedOperations.size() - 1);
    }
}
