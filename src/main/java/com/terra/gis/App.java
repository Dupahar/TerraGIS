package com.terra.gis;

import atlantafx.base.theme.PrimerDark;
import javafx.application.Application;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import com.terra.gis.ui.MainView;
import com.terra.gis.ui.WelcomeView;
import com.terra.gis.ui.BrandImageLoader;
import com.terra.gis.diagnostics.CrashDiagnosticsManager;
import com.terra.gis.licensing.LicenseEvaluation;
import com.terra.gis.licensing.LicenseMode;
import com.terra.gis.licensing.LicenseService;
import java.nio.file.Path;
import javafx.concurrent.Task;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.util.Duration;

/**
 * TerraGIS application entry point and JavaFX application lifecycle manager.
 * <p>
 * This class handles:
 * <ul>
 *   <li>JavaFX application initialization and stage setup</li>
 *   <li>AtlantaFX PrimerDark theme application for modern UI aesthetics</li>
 *   <li>Configuration loading via {@link AppConfig}</li>
 *   <li>Main window creation with {@link MainView}</li>
 *   <li>Application logging initialization</li>
 * </ul>
 * 
 * <p><strong>Launch Configuration:</strong></p>
 * <ul>
 *   <li><strong>Default Window Size:</strong> 1024x768 pixels</li>
 *   <li><strong>Theme:</strong> AtlantaFX PrimerDark (dark mode)</li>
 *   <li><strong>JavaFX Version:</strong> 21.0.2+</li>
 * </ul>
 * 
 * <p><strong>Startup Sequence:</strong></p>
 * <ol>
 *   <li>Load application configuration from application.properties</li>
 *   <li>Apply AtlantaFX theme stylesheet</li>
 *   <li>Initialize MainView with map canvas and toolbars</li>
 *   <li>Display primary stage window</li>
 * </ol>
 * 
 * @see MainView
 * @see AppConfig
 * @see javafx.application.Application
 */
public class App extends Application {

    private static final Logger log = LoggerFactory.getLogger(App.class);
    private static final long LICENSE_RECHECK_HOURS = 24;

    private final LicenseService licenseService = new LicenseService();
    private volatile LicenseEvaluation licenseEvaluation = LicenseEvaluation.active("License active");
    private ScheduledExecutorService licenseScheduler;
    private MainView activeMainView;

    private record StartupContext(AppConfig config, LicenseEvaluation licenseEvaluation) {
    }

    @Override
    public void start(Stage stage) {
        log.info("Starting TerraGIS application...");

        // Apply AtlantaFX PrimerDark theme
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        log.debug("Applied AtlantaFX PrimerDark theme");

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double initialWidth = Math.min(1280, visualBounds.getWidth() * 0.9);
        double initialHeight = Math.min(900, visualBounds.getHeight() * 0.9);
        ProgressBar splashProgress = new ProgressBar(0);
        Label loadingText = new Label("Starting TerraGIS...");
        Scene scene = new Scene(createSplashView(splashProgress, loadingText), initialWidth, initialHeight);

        stage.setScene(scene);
        stage.setTitle("TerraGIS");
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setResizable(true);

        stage.setOnCloseRequest(event -> handleAppClose());

        stage.show();

        Timeline pulseAnimation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(splashProgress.progressProperty(), 0.08)),
                new KeyFrame(Duration.seconds(1.1), new KeyValue(splashProgress.progressProperty(), 0.82))
        );
        pulseAnimation.setAutoReverse(true);
        pulseAnimation.setCycleCount(Timeline.INDEFINITE);
        pulseAnimation.play();

        Task<StartupContext> startupTask = new Task<>() {
            @Override
            protected StartupContext call() {
                updateMessage("Loading configuration...");
                AppConfig config = AppConfig.getInstance();
                log.debug("Configuration loaded: {} v{}", config.getAppName(), config.getAppVersion());

                updateMessage("Preparing diagnostics...");
                CrashDiagnosticsManager.install(config.getAppVersion());

                updateMessage("Validating license...");
                LicenseEvaluation evaluation = licenseService.evaluateNow();

                updateMessage("Preparing workspace...");
                return new StartupContext(config, evaluation);
            }
        };

        loadingText.textProperty().bind(startupTask.messageProperty());

        startupTask.setOnSucceeded(event -> {
            pulseAnimation.stop();
            loadingText.textProperty().unbind();

            try {
                StartupContext context = startupTask.getValue();
                if (context == null) {
                    log.error("Startup task returned null context - this should not happen");
                    showStartupErrorFallback(stage, scene, "Startup Context Error", 
                        "Failed to initialize application context.\nPlease restart the application.");
                    return;
                }

                licenseEvaluation = context.licenseEvaluation();
                stage.setTitle(context.config().getAppTitle());

                Timeline completionAnimation = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(splashProgress.progressProperty(), splashProgress.getProgress())),
                        new KeyFrame(Duration.seconds(0.45), new KeyValue(splashProgress.progressProperty(), 1))
                );
                completionAnimation.setOnFinished(done -> {
                    try {
                        showWelcomeScreen(stage, scene, context.config());
                        log.info("TerraGIS splash screen finished; welcome screen displayed");
                        showLicenseNoticeIfNeeded();
                        startLicenseRecheckLoop(stage, scene, context.config());
                    } catch (Exception ex) {
                        log.error("Failed to display welcome screen", ex);
                        showStartupErrorFallback(stage, scene, "Display Error", 
                            "Failed to initialize user interface.\nPlease restart the application.");
                    }
                });
                completionAnimation.play();
            } catch (Exception ex) {
                log.error("Unexpected error in startup success handler", ex);
                showStartupErrorFallback(stage, scene, "Startup Error", 
                    "An unexpected error occurred during startup.\nPlease restart the application.");
            }
        });

        startupTask.setOnFailed(event -> {
            pulseAnimation.stop();
            loadingText.textProperty().unbind();
            splashProgress.setProgress(0);
            Throwable error = startupTask.getException();
            String message = (error != null && error.getMessage() != null)
                    ? error.getMessage()
                    : "Unknown startup error";
            String fullMessage = "Application startup failed: " + message;
            log.error("Application startup failed", error);

            // Show error in UI instead of just alert
            showStartupErrorFallback(stage, scene, "Startup Error", fullMessage);
            
            // Also show alert for additional visibility
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("TerraGIS Startup Error");
            alert.setHeaderText("TerraGIS could not finish startup");
            alert.setContentText(message);
            alert.show();
        });

        Thread startupThread = new Thread(startupTask, "terragis-startup");
        startupThread.setDaemon(true);
        startupThread.start();
    }

    private void handleAppClose() {
        if (activeMainView != null) {
            activeMainView.shutdown();
        }
        if (licenseScheduler != null) {
            licenseScheduler.shutdownNow();
        }
        log.info("TerraGIS application closing");
    }

    private StackPane createSplashView(ProgressBar progressBar, Label loadingText) {
        Node logo = createSplashLogo();

        Label appName = new Label("TerraGIS");
        appName.setFont(Font.font("Segoe UI", 72));
        appName.setTextFill(Color.web("#dff3ff"));
        appName.setStyle("-fx-font-weight: 700;");

        Label subtitle = new Label("Professional GIS Mapping & Analysis");
        subtitle.setFont(Font.font("Segoe UI", 18));
        subtitle.setTextFill(Color.web("#9fd3ea"));

        progressBar.setPrefWidth(420);
        progressBar.setMinHeight(12);
        progressBar.setStyle("-fx-accent: #52d5bf;");

        loadingText.setFont(Font.font("Segoe UI", 13));
        loadingText.setTextFill(Color.web("#9ac6dc"));

        VBox content = new VBox(18, logo, appName, subtitle, progressBar, loadingText);
        content.setAlignment(Pos.CENTER);

        StackPane root = new StackPane(content);
        root.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #07111b, #0d2538, #16364d);"
        );

        return root;
    }

    private Node createSplashLogo() {
        StackPane logo = new StackPane();
        logo.setMinSize(160, 160);
        logo.setPrefSize(160, 160);
        logo.setMaxSize(160, 160);
        logo.setStyle(
                "-fx-background-color: rgba(255, 255, 255, 0.06); " +
                "-fx-background-radius: 30; " +
                        "-fx-border-color: rgba(202, 238, 255, 0.35); " +
                "-fx-border-radius: 30; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.35), 20, 0.25, 0, 6);"
        );

        Image brandImage = loadBrandImage();
        if (brandImage != null) {
            ImageView logoView = new ImageView(brandImage);
            logoView.setFitWidth(150);
            logoView.setFitHeight(150);
            logoView.setPreserveRatio(true);
            logo.getChildren().add(logoView);
        } else {
            Label fallback = new Label("TG");
            fallback.setStyle(
                    "-fx-text-fill: #e9f7ff; " +
                            "-fx-font-size: 34px; " +
                            "-fx-font-weight: bold;"
            );
            logo.getChildren().add(fallback);
        }

        return logo;
    }

    private Image loadBrandImage() {
        Image image = BrandImageLoader.loadTrimmedBrandImage(getClass(), log);
        if (image == null) {
            log.warn("Brand logo not available from classpath or workspace fallback");
        }
        return image;
    }

    private void loadProjectAndSwitchToMain(Stage stage, Path projectPath, Scene scene, AppConfig config) {
        log.info("Loading project from: {}", projectPath);
        
        // Create MainView with project context
        MainView mainView = new MainView();
        activeMainView = mainView;
        mainView.setProjectPath(projectPath);
        mainView.setOnBackToMainMenu(() -> showWelcomeScreen(stage, scene, config));
        mainView.setReadOnlyMode(licenseEvaluation.mode() == LicenseMode.READ_ONLY, licenseEvaluation.message());

        // Switch to the main UI first, then restore session on the next pulse.
        // This avoids a visually blank/stalled transition on slower devices.
        scene.setRoot(mainView);
        stage.setOnCloseRequest(event -> handleAppClose());

        Platform.runLater(() -> {
            try {
                mainView.restoreProjectSession();
            } catch (Throwable ex) {
                log.error("Project session restore failed for {}", projectPath, ex);
            }
        });
        
        log.info("Switched to main editing interface for project: {}", projectPath);
    }

    private void showWelcomeScreen(Stage stage, Scene scene, AppConfig config) {
        activeMainView = null;
        WelcomeView welcomeView = new WelcomeView();
        welcomeView.setOnProjectSelected(() -> {
            Path projectPath = welcomeView.getSelectedProjectPath();
            if (projectPath != null) {
                loadProjectAndSwitchToMain(stage, projectPath, scene, config);
            }
        });

        scene.setRoot(welcomeView);
        stage.setOnCloseRequest(event -> handleAppClose());
        log.info("Returned to TerraGIS welcome screen");
    }

    private void showStartupErrorFallback(Stage stage, Scene scene, String title, String message) {
        log.error("Displaying startup error fallback: {} - {}", title, message);
        
        // Create error UI
        VBox errorBox = new VBox(20);
        errorBox.setAlignment(Pos.CENTER);
        errorBox.setPadding(new Insets(40));
        errorBox.setStyle("-fx-background-color: linear-gradient(to bottom right, #07111b, #0d2538, #16364d);");
        
        Label errorTitle = new Label(title);
        errorTitle.setFont(Font.font("Segoe UI", 24));
        errorTitle.setTextFill(Color.web("#ff6b6b"));
        errorTitle.setStyle("-fx-font-weight: bold;");
        
        Label errorMessage = new Label(message);
        errorMessage.setFont(Font.font("Segoe UI", 14));
        errorMessage.setTextFill(Color.web("#f8f9fa"));
        errorMessage.setWrapText(true);
        
        Button restartBtn = new Button("Restart Application");
        restartBtn.setFont(Font.font("Segoe UI", 12));
        restartBtn.setStyle(
            "-fx-padding: 10 20; " +
            "-fx-background-color: #ff6b6b; " +
            "-fx-text-fill: white; " +
            "-fx-font-weight: bold; " +
            "-fx-cursor: hand;"
        );
        restartBtn.setOnAction(e -> {
            log.info("Restart requested by user after startup error");
            stage.close();
        });
        
        Button exitBtn = new Button("Exit");
        exitBtn.setFont(Font.font("Segoe UI", 12));
        exitBtn.setStyle(
            "-fx-padding: 10 20; " +
            "-fx-background-color: #495057; " +
            "-fx-text-fill: white; " +
            "-fx-cursor: hand;"
        );
        exitBtn.setOnAction(e -> Platform.exit());
        
        HBox buttonBox = new HBox(15, restartBtn, exitBtn);
        buttonBox.setAlignment(Pos.CENTER);
        
        errorBox.getChildren().addAll(errorTitle, errorMessage, buttonBox);
        
        scene.setRoot(errorBox);
        stage.setOnCloseRequest(event -> handleAppClose());
    }

    private void startLicenseRecheckLoop(Stage stage, Scene scene, AppConfig config) {
        licenseScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "license-recheck");
            t.setDaemon(true);
            return t;
        });

        licenseScheduler.scheduleAtFixedRate(() -> {
            try {
                LicenseEvaluation latest = licenseService.evaluateNow();
                if (latest.mode() != licenseEvaluation.mode() || !latest.message().equals(licenseEvaluation.message())) {
                    licenseEvaluation = latest;
                    Platform.runLater(() -> {
                        if (activeMainView != null) {
                            activeMainView.setReadOnlyMode(latest.mode() == LicenseMode.READ_ONLY, latest.message());
                        }
                        if (latest.mode() != LicenseMode.ACTIVE) {
                            showLicenseNoticeIfNeeded();
                        }
                    });
                }
            } catch (Exception ex) {
                log.warn("License recheck failed", ex);
            }
        }, LICENSE_RECHECK_HOURS, LICENSE_RECHECK_HOURS, TimeUnit.HOURS);
    }

    private void showLicenseNoticeIfNeeded() {
        if (licenseEvaluation.mode() == LicenseMode.ACTIVE) {
            return;
        }

        Alert.AlertType type = licenseEvaluation.mode() == LicenseMode.READ_ONLY
                ? Alert.AlertType.WARNING
                : Alert.AlertType.INFORMATION;
        Alert alert = new Alert(type);
        alert.setTitle("Beta License Status");
        alert.setHeaderText(licenseEvaluation.mode() == LicenseMode.READ_ONLY
                ? "Read-only mode enabled"
                : "Grace mode enabled");
        alert.setContentText(licenseEvaluation.message());
        alert.show();
    }

    public static void main(String[] args) {
        log.info("Launching TerraGIS...");
        launch(args);
    }
}
