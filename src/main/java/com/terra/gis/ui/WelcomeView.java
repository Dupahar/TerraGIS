package com.terra.gis.ui;

import com.terra.gis.project.ProjectManager;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Professional welcome screen shown at application startup.
 * <p>
 * QGIS-inspired project browser with:
 * <ul>
 *   <li>Logo/branding header</li>
 *   <li>Recent projects panel</li>
 *   <li>All projects browser table</li>
 *   <li>Quick action buttons</li>
 * </ul>
 */
public class WelcomeView extends BorderPane {

    private static final Logger log = LoggerFactory.getLogger(WelcomeView.class);
    private static final String PROJECT_PREVIEW_FILE = "project-preview.png";
    private enum ThemeMode {
        NEO_DARK,
        LIGHT_STUDIO
    }

    private final ProjectManager projectManager;
    private Runnable onProjectSelected;
    private Path selectedProjectPath;
    private ThemeMode themeMode = ThemeMode.NEO_DARK;

    public WelcomeView() {
        this.projectManager = new ProjectManager();
        buildLayout();
    }

    private void buildLayout() {
        // Top: Header/Logo
        VBox headerBox = createHeaderBox();

        // Center: Main content  
        HBox centerBox = createMainContentBox();

        // Set layout
        setTop(headerBox);
        setCenter(centerBox);
        setPadding(new Insets(0));
        setStyle("-fx-background-color: linear-gradient(to bottom right, " + colorBackgroundStart() + ", " + colorBackgroundMid() + ", " + colorBackgroundEnd() + ");");

        Platform.runLater(() -> playPanelEntrance(centerBox));
    }

    private VBox createHeaderBox() {
        VBox header = new VBox(12);
        header.setPadding(new Insets(26, 52, 24, 52));
        header.setStyle("-fx-background-color: " + colorHeaderBackground() + "; -fx-border-color: " + colorBorderSoft() + "; -fx-border-width: 0 0 1 0;");

        Label appName = new Label("TerraGIS");
        appName.setFont(Font.font("Segoe UI", 56));
        appName.setTextFill(Color.web(colorPrimary()));
        appName.setStyle("-fx-font-weight: bold;");

        Label tagline = new Label("Land intelligence workspace for mapping, analysis, and export");
        tagline.setFont(Font.font("Segoe UI", 15));
        tagline.setTextFill(Color.web(colorTextSecondary()));
        tagline.setStyle("-fx-padding: 2 0 0 0;");

        VBox titleStack = new VBox(1, appName, tagline);
        titleStack.setAlignment(Pos.CENTER_LEFT);

        StackPane brandLogo = createBrandLogo();

        Button themeToggleBtn = new Button(themeMode == ThemeMode.NEO_DARK ? "Light" : "Dark");
        themeToggleBtn.setStyle(buildThemeToggleStyle(false));
        themeToggleBtn.setOnMouseEntered(e -> themeToggleBtn.setStyle(buildThemeToggleStyle(true)));
        themeToggleBtn.setOnMouseExited(e -> themeToggleBtn.setStyle(buildThemeToggleStyle(false)));
        themeToggleBtn.setOnAction(e -> {
            themeMode = themeMode == ThemeMode.NEO_DARK ? ThemeMode.LIGHT_STUDIO : ThemeMode.NEO_DARK;
            buildLayout();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox headerRow = new HBox(16, brandLogo, titleStack, spacer, themeToggleBtn);
        headerRow.setAlignment(Pos.CENTER_LEFT);

    header.getChildren().addAll(headerRow);
        return header;
    }

    private StackPane createBrandLogo() {
    StackPane logo = new StackPane();
    logo.setMinSize(84, 84);
    logo.setPrefSize(84, 84);
    logo.setMaxSize(84, 84);
    logo.setStyle(
        "-fx-background-color: rgba(255,255,255,0.08); " +
            "-fx-background-radius: 22; " +
            "-fx-border-color: " + colorGlassBorder() + "; " +
            "-fx-border-radius: 22; " +
            "-fx-effect: dropshadow(gaussian, " + colorPanelShadow() + ", 10, 0.2, 0, 2);"
    );

    Image logoImage = loadBrandImage();
    if (logoImage != null) {
        ImageView logoView = new ImageView(logoImage);
        logoView.setFitWidth(74);
        logoView.setFitHeight(74);
        logoView.setPreserveRatio(true);
        logo.getChildren().add(logoView);
    } else {
        Label fallback = new Label("TG");
        fallback.setStyle(
            "-fx-text-fill: " + colorTextPrimary() + "; " +
                "-fx-font-size: 18px; " +
                "-fx-font-weight: bold;"
        );
        logo.getChildren().add(fallback);
    }

    logo.setAccessibleText("Brand logo");
    return logo;
    }

    private Image loadBrandImage() {
    Image image = BrandImageLoader.loadTrimmedBrandImage(getClass(), log);
    if (image == null) {
        log.warn("Brand logo not available from classpath or workspace fallback");
    }
    return image;
    }

    private HBox createMainContentBox() {
        HBox main = new HBox(20);
        main.setPadding(new Insets(30, 40, 30, 40));
        main.setStyle("-fx-background-color: transparent;");

        // Left: command center
        VBox leftPanel = createAllProjectsPanel();

        // Center: start/resume actions
        VBox centerPanel = createRecentProjectsPanel();

        // Right: project memory
        VBox rightPanel = createRightInfoPanel();

        HBox.setHgrow(leftPanel, Priority.ALWAYS);

        main.getChildren().addAll(leftPanel, centerPanel, rightPanel);
        return main;
    }

    private VBox createRecentProjectsPanel() {
        VBox panel = new VBox(15);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setPrefWidth(320);
        panel.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, " + colorPanelGlassTop() + ", " + colorPanelGlassBottom() + "); " +
                "-fx-background-radius: 12; " +
                "-fx-border-color: " + colorGlassBorder() + "; " +
                "-fx-border-radius: 12; " +
                "-fx-effect: dropshadow(gaussian, " + colorPanelShadow() + ", 18, 0.2, 0, 4); " +
                        "-fx-padding: 20;"
        );

        // Title
        Label titleLabel = new Label("Start");
        titleLabel.setFont(Font.font("Segoe UI", 14));
        titleLabel.setTextFill(Color.web(colorPrimary()));
        titleLabel.setStyle("-fx-font-weight: bold;");

        // Action buttons
        Button newProjectBtn = createActionButton("✚", "New Project", colorPrimary());
        newProjectBtn.setOnAction(e -> handleNewProject());

        Button openProjectBtn = createActionButton("🗂", "Open Project", colorAccent());
        openProjectBtn.setOnAction(e -> handleOpenProject());

        VBox primaryActionsBox = new VBox(10, newProjectBtn, openProjectBtn);
        primaryActionsBox.setAlignment(Pos.CENTER);

        Separator sep1 = new Separator();
        sep1.setStyle("-fx-text-fill: " + colorBorderSoft() + ";");
        sep1.setMaxWidth(250);

        // Recent projects section
        Label recentLabel = new Label("Continue");
        recentLabel.setFont(Font.font("Segoe UI", 12));
        recentLabel.setTextFill(Color.web(colorTextPrimary()));
        recentLabel.setStyle("-fx-font-weight: bold;");

        VBox recentList = createRecentProjectsList();
        VBox.setVgrow(recentList, Priority.ALWAYS);

        panel.getChildren().addAll(
            titleLabel, primaryActionsBox,
                sep1, recentLabel, recentList
        );

        return panel;
    }

    private VBox createRightInfoPanel() {
        VBox panel = new VBox(10);
        panel.setPrefWidth(260);
        panel.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, " + colorPanelGlassTop() + ", " + colorPanelGlassBottom() + "); " +
                "-fx-background-radius: 12; " +
                "-fx-border-color: " + colorGlassBorder() + "; " +
                "-fx-border-radius: 12; " +
                "-fx-effect: dropshadow(gaussian, " + colorPanelShadow() + ", 18, 0.2, 0, 4); " +
                        "-fx-padding: 14;"
        );

        Label clippingsLabel = new Label("Project Memory");
        clippingsLabel.setFont(Font.font("Segoe UI", 12));
        clippingsLabel.setTextFill(Color.web(colorPrimary()));
        clippingsLabel.setStyle("-fx-font-weight: bold;");

        ScrollPane clippingsPane = createClippingsPane();

        Separator separator = new Separator();
        separator.setStyle("-fx-text-fill: " + colorBorderSoft() + ";");

        Label helpLabel = new Label("Next Best Actions");
        helpLabel.setFont(Font.font("Segoe UI", 12));
        helpLabel.setTextFill(Color.web(colorPrimary()));
        helpLabel.setStyle("-fx-font-weight: bold;");

        ScrollPane helpPane = createGettingStartedPane();

        panel.getChildren().addAll(clippingsLabel, clippingsPane, separator, helpLabel, helpPane);
        return panel;
    }

    private VBox createRecentProjectsList() {
        VBox box = new VBox(8);
        box.setAlignment(Pos.TOP_CENTER);
        box.setStyle("-fx-padding: 5;");
        box.setStyle(
                "-fx-padding: 8; " +
                        "-fx-background-color: " + colorInnerGlassPanel() + "; " +
                        "-fx-background-radius: 10; " +
                        "-fx-border-color: " + colorGlassBorder() + "; " +
                        "-fx-border-radius: 10;"
        );

        List<ProjectManager.ProjectInfo> recentProjects = projectManager.getRecentProjects();
        if (recentProjects.isEmpty()) {
            Label emptyLabel = new Label("No recent projects");
            emptyLabel.setFont(Font.font("Segoe UI", 11));
            emptyLabel.setTextFill(Color.web(colorTextSecondary()));
            box.getChildren().add(emptyLabel);
        } else {
            for (ProjectManager.ProjectInfo project : recentProjects) {
                Button projectBtn = new Button(project.getDisplayName() + "\nLast opened: " + project.getFormattedDate());
                projectBtn.setPrefWidth(230);
                projectBtn.setPrefHeight(54);
                projectBtn.setFont(Font.font("Segoe UI", 11));
                projectBtn.setWrapText(true);
                projectBtn.setStyle(buildGlassButtonStyle(colorAccent(), false, false, 8));
                projectBtn.setOnMouseEntered(e -> projectBtn.setStyle(buildGlassButtonStyle(colorAccent(), true, false, 8)));
                projectBtn.setOnMouseExited(e -> projectBtn.setStyle(buildGlassButtonStyle(colorAccent(), false, false, 8)));
                projectBtn.setOnAction(e -> openRecentProject(project.directory()));
                box.getChildren().add(projectBtn);
            }
        }

        return box;
    }

    private ScrollPane createClippingsPane() {
        VBox box = new VBox(6);
        box.setStyle(
            "-fx-padding: 8; " +
                "-fx-background-color: " + colorInnerGlassPanel() + "; " +
                "-fx-background-radius: 10; " +
                "-fx-border-color: " + colorGlassBorder() + "; " +
                "-fx-border-radius: 10;"
        );

        List<ProjectManager.ProjectInfo> recentProjects = projectManager.getRecentProjects();
        if (recentProjects.isEmpty()) {
            box.getChildren().add(createHintLabel("No clippings yet. Open a project to continue where you left off."));
        } else {
            int count = Math.min(6, recentProjects.size());
            for (int i = 0; i < count; i++) {
                ProjectManager.ProjectInfo project = recentProjects.get(i);
                Label snippet = new Label(
                    project.getDisplayName() + "\n" +
                        "Created: " + project.getFormattedCreatedDate() + "\n" +
                        "Last touched: " + project.getFormattedDate() + "\n" +
                        "Path: " + project.directory().getFileName()
                );
                snippet.setWrapText(true);
                snippet.setFont(Font.font("Segoe UI", 10.5));
                snippet.setTextFill(Color.web(colorTextSecondary()));
                snippet.setStyle(
                    "-fx-background-color: " + colorCardBackground() + "; " +
                        "-fx-background-radius: 8; " +
                        "-fx-border-color: " + colorGlassBorder() + "; " +
                        "-fx-border-radius: 8; " +
                        "-fx-padding: 6 8 6 8;"
                );
                box.getChildren().add(snippet);
            }
        }

        ScrollPane scrollPane = new ScrollPane(box);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(320);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        return scrollPane;
    }

    private ScrollPane createGettingStartedPane() {
        VBox box = new VBox(6);
        box.setStyle(
            "-fx-padding: 8; " +
                "-fx-background-color: " + colorInnerGlassPanel() + "; " +
                "-fx-background-radius: 10; " +
                "-fx-border-color: " + colorGlassBorder() + "; " +
                "-fx-border-radius: 10;"
        );

        box.getChildren().addAll(
                createHintLabel("Open the latest project"),
                createHintLabel("Import vector or raster context"),
                createHintLabel("Review features and attributes"),
                createHintLabel("Export the map when the view is ready")
        );

        ScrollPane scrollPane = new ScrollPane(box);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(110);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        return scrollPane;
    }

    private Label createHintLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Segoe UI", 11));
        label.setTextFill(Color.web(colorTextSecondary()));
        label.setWrapText(true);
        return label;
    }

    private VBox createAllProjectsPanel() {
        VBox panel = new VBox(10);
        panel.setPrefWidth(760);
        panel.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, " + colorPanelGlassTop() + ", " + colorPanelGlassBottom() + "); " +
                "-fx-background-radius: 12; " +
                "-fx-border-color: " + colorGlassBorder() + "; " +
                "-fx-border-radius: 12; " +
                "-fx-effect: dropshadow(gaussian, " + colorPanelShadow() + ", 18, 0.2, 0, 4); " +
                        "-fx-padding: 20;"
        );

        Label titleLabel = new Label("Command Center");
        titleLabel.setFont(Font.font("Segoe UI", 14));
        titleLabel.setTextFill(Color.web(colorPrimary()));
        titleLabel.setStyle("-fx-font-weight: bold;");

        ScrollPane snippetsPane = createProjectSnippetsPane();
        VBox.setVgrow(snippetsPane, Priority.ALWAYS);

        panel.getChildren().addAll(titleLabel, snippetsPane);
        return panel;
    }

    private ScrollPane createProjectSnippetsPane() {
        VBox content = new VBox(12);
        content.setStyle("-fx-padding: 2;");

        List<ProjectManager.ProjectInfo> recentProjects = projectManager.getRecentProjects();
        if (recentProjects.isEmpty()) {
            Label emptyLabel = new Label("No projects yet. Create one to start building a spatial workspace TerraGIS can resume later.");
            emptyLabel.setWrapText(true);
            emptyLabel.setFont(Font.font("Segoe UI", 12));
            emptyLabel.setTextFill(Color.web(colorTextSecondary()));
            emptyLabel.setStyle(
                "-fx-padding: 14; " +
                    "-fx-background-color: " + colorInnerGlassPanel() + "; " +
                    "-fx-background-radius: 10; " +
                    "-fx-border-color: " + colorGlassBorder() + "; " +
                    "-fx-border-radius: 10;"
            );
            content.getChildren().add(emptyLabel);
        } else {
            int count = Math.min(3, recentProjects.size());
            for (int i = 0; i < count; i++) {
                content.getChildren().add(createProjectSnippetCard(recentProjects.get(i)));
            }
        }

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        return scrollPane;
    }

    private VBox createProjectSnippetCard(ProjectManager.ProjectInfo project) {
        VBox card = new VBox(10);
        card.setFillWidth(true);
        card.setStyle(
            "-fx-background-color: " + colorCardBackground() + "; " +
                "-fx-background-radius: 10; " +
                "-fx-border-color: " + colorGlassBorder() + "; " +
                "-fx-border-radius: 10; " +
                "-fx-padding: 10;"
        );

        StackPane preview = new StackPane();
        preview.setMinHeight(150);
        preview.setPrefHeight(150);
        preview.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, " + colorBackgroundMid() + ", " + colorBackgroundEnd() + "); " +
                "-fx-background-radius: 8; " +
                "-fx-border-color: " + colorGlassBorder() + "; " +
                "-fx-border-radius: 8;"
        );

        Path previewImagePath = project.directory().resolve(PROJECT_PREVIEW_FILE);
        if (Files.exists(previewImagePath)) {
            Image previewImage = new Image(previewImagePath.toUri().toString(), true);
            ImageView previewView = new ImageView(previewImage);
            previewView.setPreserveRatio(false);
            previewView.fitWidthProperty().bind(preview.widthProperty().subtract(2));
            previewView.fitHeightProperty().bind(preview.heightProperty().subtract(2));
            preview.getChildren().add(previewView);
        } else {
            Label previewLabel = new Label("Snapshot appears after save");
            previewLabel.setFont(Font.font("Segoe UI", 12));
            previewLabel.setTextFill(Color.web(colorTextSecondary()));
            preview.getChildren().add(previewLabel);
        }

        Label title = new Label(project.getDisplayName());
        title.setFont(Font.font("Segoe UI", 17));
        title.setTextFill(Color.web(colorTextPrimary()));
        title.setStyle("-fx-font-weight: bold;");

        Label meta = new Label(
            "Created: " + project.getFormattedCreatedDate() + "\n" +
                "Last opened: " + project.getFormattedDate()
        );
        meta.setFont(Font.font("Segoe UI", 11));
        meta.setTextFill(Color.web(colorTextSecondary()));

        Button openBtn = new Button("Continue");
        openBtn.setFont(Font.font("Segoe UI", 11));
        openBtn.setStyle(buildGlassButtonStyle(colorAccent(), false, false, 8));
        openBtn.setOnMouseEntered(e -> openBtn.setStyle(buildGlassButtonStyle(colorAccent(), true, false, 8)));
        openBtn.setOnMouseExited(e -> openBtn.setStyle(buildGlassButtonStyle(colorAccent(), false, false, 8)));
        openBtn.setOnAction(e -> openRecentProject(project.directory()));

        card.getChildren().addAll(preview, title, meta, openBtn);
        return card;
    }

    private Button createActionButton(String icon, String text, String bgColor) {
        Button btn = new Button(text);
        btn.setPrefWidth(250);
        btn.setPrefHeight(45);
        btn.setFont(Font.font("Segoe UI", 12));
        btn.setGraphic(new Label(icon));
        btn.setGraphicTextGap(10);
        btn.setContentDisplay(ContentDisplay.LEFT);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setStyle(buildGlassButtonStyle(bgColor, false, true, 10));
        btn.setOnMouseEntered(e -> btn.setStyle(buildGlassButtonStyle(bgColor, true, true, 10)));
        btn.setOnMouseExited(e -> btn.setStyle(buildGlassButtonStyle(bgColor, false, true, 10)));
        return btn;
    }

    private void handleNewProject() {
        TextInputDialog dialog = new TextInputDialog("My Project");
        dialog.setTitle("Create New Project");
        dialog.setHeaderText("New GIS Project");
        dialog.setContentText("Project Name:");

        var result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            String projectName = result.get().trim();
            Path projectPath = projectManager.createProject(projectName);
            if (projectPath != null) {
                log.info("Created new project: {}", projectName);
                openProject(projectPath);
            } else {
                showError("Failed to create project", "Could not create: " + projectName);
            }
        }
    }

    private void handleOpenProject() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open Project Directory");
        File projectsDir = new File(System.getProperty("user.home"), ".terragis-projects");
        if (projectsDir.exists()) {
            chooser.setInitialDirectory(projectsDir);
        }

        File selected = chooser.showDialog(this.getScene().getWindow());
        if (selected != null) {
            openProject(selected.toPath());
        }
    }

    private void openRecentProject(Path projectPath) {
        openProject(projectPath);
    }

    private void openProject(Path projectPath) {
        ProjectManager.ProjectMetadata metadata = projectManager.openProject(projectPath);
        if (metadata != null) {
            log.info("Opening project: {}", metadata.name());
            selectedProjectPath = projectPath;
            if (onProjectSelected != null) {
                onProjectSelected.run();
            }
        } else {
            showError("Invalid Project", "Could not open: " + projectPath);
        }
    }

    public Path getSelectedProjectPath() {
        return selectedProjectPath;
    }

    public void setOnProjectSelected(Runnable callback) {
        this.onProjectSelected = callback;
    }

    private void playPanelEntrance(HBox centerBox) {
        if (centerBox == null || centerBox.getChildren().size() < 3) {
            return;
        }

        List<Node> targets = new ArrayList<>(centerBox.getChildren());
        for (int i = 0; i < targets.size(); i++) {
            Node panel = targets.get(i);
            panel.setOpacity(0.0);
            panel.setTranslateY(16);

            FadeTransition fade = new FadeTransition(Duration.millis(420), panel);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            fade.setDelay(Duration.millis(i * 90L));

            TranslateTransition slide = new TranslateTransition(Duration.millis(420), panel);
            slide.setFromY(16);
            slide.setToY(0);
            slide.setDelay(Duration.millis(i * 90L));

            new ParallelTransition(fade, slide).play();
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String lighten(String hexColor, double factor) {
        String hex = hexColor.replace("#", "");
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);

        r = Math.min(255, (int)(r + (255 - r) * factor));
        g = Math.min(255, (int)(g + (255 - g) * factor));
        b = Math.min(255, (int)(b + (255 - b) * factor));

        return String.format("#%02X%02X%02X", r, g, b);
    }

    private String withAlpha(String hexColor, double alpha) {
        String hex = hexColor.replace("#", "");
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);

        double clampedAlpha = Math.max(0.0, Math.min(1.0, alpha));
        return String.format("rgba(%d, %d, %d, %.3f)", r, g, b, clampedAlpha);
    }

    private String buildGlassButtonStyle(String tintColor, boolean hover, boolean bold, int radius) {
        String tintedTop = withAlpha(lighten(tintColor, hover ? 0.12 : 0.04), hover ? 0.45 : 0.30);
        String tintedBottom = withAlpha(tintColor, hover ? 0.22 : 0.16);
        String borderColor = hover ? colorGlassBorderActive() : withAlpha(tintColor, 0.55);
        String frostedLayer = hover ? colorButtonGlassHover() : colorButtonGlassBase();

        return "-fx-background-color: linear-gradient(to bottom, " + tintedTop + ", " + tintedBottom + "), " + frostedLayer + "; " +
                "-fx-background-insets: 0, 1; " +
                "-fx-background-radius: " + radius + ", " + (radius - 1) + "; " +
                "-fx-border-color: " + borderColor + "; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: " + radius + "; " +
                "-fx-text-fill: " + colorTextPrimary() + "; " +
                "-fx-cursor: hand; " +
                "-fx-padding: 10 14 10 14; " +
                "-fx-font-weight: " + (bold ? "bold" : "normal") + "; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.32), " + (hover ? "14" : "10") + ", 0.2, 0, " + (hover ? "4" : "2") + ");";
    }

    private String buildThemeToggleStyle(boolean hover) {
        String tint = themeMode == ThemeMode.NEO_DARK ? "#48B0E5" : "#0C7FA8";
        return buildGlassButtonStyle(tint, hover, false, 9) + "-fx-padding: 8 12 8 12;";
    }

    private String colorPrimary() {
        return themeMode == ThemeMode.NEO_DARK ? "#56B68A" : "#0E7358";
    }

    private String colorAccent() {
        return themeMode == ThemeMode.NEO_DARK ? "#3D9ACF" : "#1A82B8";
    }

    private String colorBackgroundStart() {
        return themeMode == ThemeMode.NEO_DARK ? "#0D1418" : "#EAF3F8";
    }

    private String colorBackgroundMid() {
        return themeMode == ThemeMode.NEO_DARK ? "#101A20" : "#F0F7FB";
    }

    private String colorBackgroundEnd() {
        return themeMode == ThemeMode.NEO_DARK ? "#0E171C" : "#E6F0F4";
    }

    private String colorHeaderBackground() {
        return themeMode == ThemeMode.NEO_DARK ? "#0A1115" : "#DDECF3";
    }

    private String colorCardBackground() {
        return themeMode == ThemeMode.NEO_DARK ? "#182229" : "#F7FCFF";
    }

    private String colorTextPrimary() {
        return themeMode == ThemeMode.NEO_DARK ? "#F4FAFD" : "#1A2A33";
    }

    private String colorTextSecondary() {
        return themeMode == ThemeMode.NEO_DARK ? "#A9C1CD" : "#4C6674";
    }

    private String colorBorderSoft() {
        return themeMode == ThemeMode.NEO_DARK ? "#2F424C" : "#B6D0DD";
    }

    private String colorPanelGlassTop() {
        return themeMode == ThemeMode.NEO_DARK ? "rgba(255, 255, 255, 0.08)" : "rgba(255, 255, 255, 0.78)";
    }

    private String colorPanelGlassBottom() {
        return themeMode == ThemeMode.NEO_DARK ? "rgba(255, 255, 255, 0.03)" : "rgba(214, 233, 242, 0.45)";
    }

    private String colorButtonGlassBase() {
        return themeMode == ThemeMode.NEO_DARK ? "rgba(255, 255, 255, 0.12)" : "rgba(255, 255, 255, 0.80)";
    }

    private String colorButtonGlassHover() {
        return themeMode == ThemeMode.NEO_DARK ? "rgba(255, 255, 255, 0.22)" : "rgba(241, 250, 255, 0.92)";
    }

    private String colorInnerGlassPanel() {
        return themeMode == ThemeMode.NEO_DARK ? "rgba(255, 255, 255, 0.05)" : "rgba(255, 255, 255, 0.75)";
    }

    private String colorGlassBorder() {
        return themeMode == ThemeMode.NEO_DARK ? "rgba(255, 255, 255, 0.30)" : "rgba(103, 143, 162, 0.55)";
    }

    private String colorGlassBorderActive() {
        return themeMode == ThemeMode.NEO_DARK ? "rgba(255, 255, 255, 0.55)" : "rgba(54, 112, 138, 0.75)";
    }

    private String colorPanelShadow() {
        return themeMode == ThemeMode.NEO_DARK ? "rgba(0,0,0,0.35)" : "rgba(26,60,78,0.18)";
    }
}
