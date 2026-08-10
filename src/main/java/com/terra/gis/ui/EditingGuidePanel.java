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
 * EditingGuidePanel provides contextual tutorial guidance for feature editing workflows.
 * Displays step-by-step instructions for each editing mode to help users understand
 * the current operation and best practices.
 */
public class EditingGuidePanel extends VBox {

        private static final double GUIDE_HEIGHT_REGULAR = 300;
        private static final double GUIDE_HEIGHT_COMPACT = 210;

    private Label modeTitle;
    private Label modeSummary;
    private VBox detailsBox;
        private ScrollPane guideScrollPane;
    private Label shortcutsLabel;
    private VBox shortcutsList;
    private Label tipLabel;
    private Label pageLabel;
    private Button btnNextTip;
    private Button btnPreviousTip;
    private Button btnNextMode;
    private Button btnPreviousMode;
    private int currentTipIndex;
    private int currentModeIndex;
    private String currentModeKey = "Pan";
    private final Map<String, EditingModeTutorial> tutorials = new LinkedHashMap<>();
    private final List<String> orderedModes = new ArrayList<>();

    private record EditingModeTutorial(
            String title,
            String summary,
            String whenToUse,
            String[] steps,
            String[] shortcuts,
            String[] tips,
            String[] watchOutFor) {
    }

    public EditingGuidePanel() {
        setPadding(new Insets(14));
        setSpacing(10);
                setFillWidth(true);
        setMinHeight(420);
        setPrefHeight(560);
        setStyle("-fx-background-color: transparent;");

        initializeTutorials();

        modeTitle = new Label("Editing Guide");
        modeTitle.setWrapText(true);
        modeTitle.setMaxWidth(Double.MAX_VALUE);
        modeTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        modeSummary = new Label();
        modeSummary.setWrapText(true);
        modeSummary.setLineSpacing(2);
        modeSummary.setMaxWidth(Double.MAX_VALUE);
        modeSummary.setStyle("-fx-font-size: 12px; -fx-text-fill: #d1d5db;");

        detailsBox = new VBox(8);
        detailsBox.setFillWidth(true);

        guideScrollPane = new ScrollPane(detailsBox);
        guideScrollPane.setFitToWidth(true);
        guideScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        guideScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        guideScrollPane.setPrefHeight(GUIDE_HEIGHT_REGULAR);
        VBox.setVgrow(guideScrollPane, Priority.ALWAYS);

        VBox headerBox = new VBox(6, modeTitle, modeSummary);
        headerBox.setFillWidth(true);

        shortcutsLabel = new Label("Quick Actions");
        shortcutsLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 2 0 2 0;");

        shortcutsList = new VBox(4);
        shortcutsList.setFillWidth(true);
        tipLabel = new Label();
        tipLabel.setWrapText(true);
        tipLabel.setLineSpacing(2);
        tipLabel.setMaxWidth(Double.MAX_VALUE);
        tipLabel.setMinHeight(Region.USE_PREF_SIZE);
        tipLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #34d399;");
        shortcutsList.getChildren().add(tipLabel);

        pageLabel = new Label();
        pageLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #9ca3af;");

        HBox modeButtons = new HBox(6);
        btnPreviousMode = new Button("< Prev Tool");
        btnNextMode = new Button("Next Tool >");
        btnPreviousMode.setOnAction(e -> showPreviousMode());
        btnNextMode.setOnAction(e -> showNextMode());
        modeButtons.getChildren().addAll(btnPreviousMode, btnNextMode, pageLabel);

        HBox tipButtons = new HBox(6);
        btnPreviousTip = new Button("◀ Previous");
        btnNextTip = new Button("Next ▶");
        btnPreviousTip.setOnAction(e -> showPreviousTip());
        btnNextTip.setOnAction(e -> showNextTip());
        tipButtons.getChildren().addAll(btnPreviousTip, btnNextTip);

        getChildren().addAll(headerBox, guideScrollPane, shortcutsLabel, shortcutsList, tipButtons, modeButtons);
    }

    private void initializeTutorials() {
        orderedModes.clear();

        tutorials.put("Pan", new EditingModeTutorial(
                "Pan & Zoom Mode",
                "This is your camera mode. You are not changing data here, only moving your view.",
                "Use this before every edit so you can see clearly where you will draw or move.",
                new String[]{
                        "Step 1: Click and hold left mouse, then drag to move the map.",
                        "Step 2: Roll the mouse wheel to zoom in and zoom out.",
                        "Step 3: Stop when your target area is centered and easy to see.",
                        "Step 4: Switch to an editing tool only after you are zoomed in enough."
                },
                new String[]{
                        "Left + drag: Move map",
                        "Mouse wheel: Zoom",
                        "Right-click layer: Open layer options"
                },
                new String[]{
                        "Start every task here to avoid editing the wrong place.",
                        "Zoom closer than you think you need for cleaner edits.",
                        "Hide busy layers so your target is easy to spot."
                },
                new String[]{
                        "If you stay too zoomed out, point and vertex edits become inaccurate.",
                        "Do not draw while trying to pan. Switch mode first."
                }
        ));
        orderedModes.add("Pan");

        tutorials.put("Select Feature", new EditingModeTutorial(
                "Select Features",
                "Pick a shape on the map so you can inspect or update its details.",
                "Use this to review attributes, rename features, or confirm the right object before deleting.",
                new String[]{
                        "Step 1: Click one feature on the map.",
                        "Step 2: Confirm it highlights so you know it is selected.",
                        "Step 3: Look at the attribute table to review id, geometry, and notes.",
                        "Step 4: Edit name or notes in the table when needed."
                },
                new String[]{
                        "Click: Select one feature",
                        "Ctrl+Click: Add to selection",
                        "Right-click feature: Open actions"
                },
                new String[]{
                        "Always verify selection color before editing attributes.",
                        "Use this mode as a safety check before delete actions.",
                        "If text updates do not look right, re-select the feature once."
                },
                new String[]{
                        "Editing the wrong selected feature is the most common mistake.",
                        "Do not assume selection carried over after layer changes."
                }
        ));
        orderedModes.add("Select Feature");

        tutorials.put("Draw Point", new EditingModeTutorial(
                "Draw Points",
                "Add a single location marker, like a tree, hydrant, sign, or survey point.",
                "Use when you need one exact coordinate location.",
                new String[]{
                        "Step 1: Zoom in to where the point should go.",
                        "Step 2: Click once to place the point.",
                        "Step 3: If it lands wrong, use Undo and click again.",
                        "Step 4: Select the point and update name/notes in attributes."
                },
                new String[]{
                        "Click: Place point",
                        "Undo: Revert last point",
                        "Select mode: Edit placed point details"
                },
                new String[]{
                        "Turn on snapping if the point must align with an existing feature.",
                        "Keep map still while clicking so placement is precise.",
                        "Save often during long editing sessions."
                },
                new String[]{
                        "Double-clicking may create extra points by accident.",
                        "Do not place points while map is still moving from a drag."
                }
        ));
        orderedModes.add("Draw Point");

        tutorials.put("Draw Line", new EditingModeTutorial(
                "Draw Lines",
                "Create connected paths like roads, streams, or utility lines.",
                "Use when you need a start point, bends, and an end point.",
                new String[]{
                        "Step 1: Click once to start the line.",
                        "Step 2: Click more points to shape the path.",
                        "Step 3: Hold Ctrl to snap to nearby endpoints.",
                        "Step 4: Double-click or Finish Sketch to save."
                },
                new String[]{
                        "Click: Add vertex",
                        "Ctrl+Click: Snap to nearby point",
                        "Double-click / Finish Sketch: Save line"
                },
                new String[]{
                        "Zoom in at intersections to avoid tiny gaps.",
                        "Use snapping for cleaner connected lines."
                },
                new String[]{
                        "If you forget to finish, the line is not saved."
                }
        ));
        orderedModes.add("Draw Line");

        tutorials.put("Draw Polygon", new EditingModeTutorial(
                "Draw Polygons",
                "Create area shapes like parks, buildings, and parcels.",
                "Use when the feature must be a closed boundary.",
                new String[]{
                        "Step 1: Click to place the first corner.",
                        "Step 2: Click around the boundary point by point.",
                        "Step 3: Double-click or Finish Sketch to close it.",
                        "Step 4: Check the filled area matches your target."
                },
                new String[]{
                        "Click: Add vertex",
                        "Double-click / Finish Sketch: Save polygon",
                        "Clear Sketch: Start over"
                },
                new String[]{
                        "Use snapping on shared edges to avoid slivers.",
                        "Add extra points around curves for better shape."
                },
                new String[]{
                        "Do not cross edges; polygons must stay valid."
                }
        ));
        orderedModes.add("Draw Polygon");

        tutorials.put("Delete Feature", new EditingModeTutorial(
                "Delete Features",
                "Remove a feature you no longer want in the layer.",
                "Use for cleanup after mistakes, duplicates, or outdated geometry.",
                new String[]{
                        "Step 1: Switch to Select first and confirm the correct feature.",
                        "Step 2: Change to Delete mode.",
                        "Step 3: Click the feature to remove it immediately.",
                        "Step 4: Use Undo right away if you removed the wrong one."
                },
                new String[]{
                        "Click: Delete feature",
                        "Undo: Restore deleted feature",
                        "No undo after export"
                },
                new String[]{
                        "Delete in small batches so mistakes are easy to recover.",
                        "Use Undo immediately while context is fresh.",
                        "Save a copy before major cleanup work."
                },
                new String[]{
                        "Skipping the selection check can delete the wrong feature.",
                        "Bulk deletes without checkpoints are risky."
                }
        ));
        orderedModes.add("Delete Feature");

        tutorials.put("Move Vertex", new EditingModeTutorial(
                "Move Vertices",
                "Adjust the shape by dragging one corner or point at a time.",
                "Use when geometry is close, but needs precise alignment.",
                new String[]{
                        "Step 1: Zoom close to the vertex you need to fix.",
                        "Step 2: Click and drag that vertex to the new location.",
                        "Step 3: Hold Ctrl to force snapping while you drag.",
                        "Step 4: Release mouse to commit the new shape."
                },
                new String[]{
                        "Click+Drag: Move vertex",
                        "Ctrl+Snap: Snap while dragging",
                        "Undo: Revert last move"
                },
                new String[]{
                        "Move one vertex at a time for predictable results.",
                        "Snap to nearby lines when keeping shared boundaries.",
                        "Review geometry after each major adjustment."
                },
                new String[]{
                        "Dragging too far can fold polygons or distort lines.",
                        "Fast drags at low zoom can miss intended snap targets."
                }
        ));
        orderedModes.add("Move Vertex");
    }

    public void showGuideForMode(String editMode) {
        currentTipIndex = 0;
        currentModeKey = tutorials.containsKey(editMode) ? editMode : "Pan";
        currentModeIndex = Math.max(0, orderedModes.indexOf(currentModeKey));
        EditingModeTutorial tutorial = tutorials.get(currentModeKey);

        modeTitle.setText(tutorial.title());
        modeSummary.setText("What it does: " + tutorial.summary() + "\nWhen to use it: " + tutorial.whenToUse());

        boolean compactNoScrollMode = "Draw Line".equals(currentModeKey) || "Draw Polygon".equals(currentModeKey);
        guideScrollPane.setVbarPolicy(compactNoScrollMode
                ? ScrollPane.ScrollBarPolicy.NEVER
                : ScrollPane.ScrollBarPolicy.AS_NEEDED);
        guideScrollPane.setPannable(!compactNoScrollMode);
        guideScrollPane.setPrefHeight(compactNoScrollMode ? GUIDE_HEIGHT_COMPACT : GUIDE_HEIGHT_REGULAR);
        VBox.setVgrow(guideScrollPane, compactNoScrollMode ? Priority.NEVER : Priority.ALWAYS);
        detailsBox.setSpacing(8);

        detailsBox.getChildren().clear();
        detailsBox.getChildren().add(sectionTitle("Step-by-step"));
        for (String step : tutorial.steps()) {
            detailsBox.getChildren().add(sectionItem(step));
        }

        detailsBox.getChildren().add(sectionTitle("Watch out for"));
        for (String warning : tutorial.watchOutFor()) {
            detailsBox.getChildren().add(sectionItem(warning));
        }

        shortcutsList.getChildren().clear();
        for (String shortcut : tutorial.shortcuts()) {
            shortcutsList.getChildren().add(sectionItem(shortcut));
        }
        shortcutsList.getChildren().add(tipLabel);

        pageLabel.setText((currentModeIndex + 1) + " / " + orderedModes.size());
        btnPreviousMode.setDisable(currentModeIndex <= 0);
        btnNextMode.setDisable(currentModeIndex >= orderedModes.size() - 1);

        updateTipDisplay(tutorial);
    }

    private Label sectionTitle(String title) {
        Label label = new Label(title);
                label.setWrapText(true);
                label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #f3f4f6;");
        return label;
    }

    private Label sectionItem(String text) {
        Label label = new Label("- " + text);
        label.setWrapText(true);
                                label.setLineSpacing(2);
                label.setMaxWidth(Double.MAX_VALUE);
                label.setMinHeight(Region.USE_PREF_SIZE);
        label.setStyle("-fx-font-size: 11px;");
        return label;
    }

    private void showNextTip() {
        EditingModeTutorial current = tutorials.get(currentModeKey);
        if (current != null && currentTipIndex < current.tips().length - 1) {
            currentTipIndex++;
            updateTipDisplay(current);
        }
    }

    private void showPreviousTip() {
        EditingModeTutorial current = tutorials.get(currentModeKey);
        if (currentTipIndex > 0) {
            currentTipIndex--;
            updateTipDisplay(current);
        }
    }

    private void showNextMode() {
        if (currentModeIndex < orderedModes.size() - 1) {
            showGuideForMode(orderedModes.get(currentModeIndex + 1));
        }
    }

    private void showPreviousMode() {
        if (currentModeIndex > 0) {
            showGuideForMode(orderedModes.get(currentModeIndex - 1));
        }
    }

    private void updateTipDisplay(EditingModeTutorial tutorial) {
        if (currentTipIndex < tutorial.tips().length) {
            tipLabel.setText("Tip: " + tutorial.tips()[currentTipIndex]);
        } else {
            tipLabel.setText("Tip: Use Pan mode to check position before editing.");
        }

        btnPreviousTip.setDisable(currentTipIndex == 0);
        btnNextTip.setDisable(currentTipIndex >= tutorial.tips().length - 1);
    }

    public void resetGuide() {
        showGuideForMode("Pan");
    }
}
