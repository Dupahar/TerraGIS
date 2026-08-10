package com.terra.gis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Central configuration manager for TerraGIS application settings.
 * <p>
 * This singleton class loads and provides access to all application configuration from
 * {@code application.properties} file located in the classpath resources.
 * 
 * <p><strong>Configuration Categories:</strong></p>
 * <ul>
 *   <li><strong>Application:</strong> app.name, app.version</li>
 *   <li><strong>UI Settings:</strong> map.canvas.width, map.canvas.height, available personas</li>
 *   <li><strong>Data Paths:</strong> Default directories for data import/export</li>
 *   <li><strong>Rendering:</strong> Map rendering defaults, style configurations</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * AppConfig config = AppConfig.getInstance();
 * String appName = config.getString("app.name", "TerraGIS");
 * int canvasWidth = config.getInt("map.canvas.width", 800);
 * double zoomFactor = config.getDouble("map.zoom.factor", 1.2);
 * }</pre>
 * 
 * <p><strong>Thread Safety:</strong> Singleton instance is lazily initialized with synchronized access.
 * Configuration properties are immutable after loading.</p>
 * 
 * <p><strong>Error Handling:</strong> If application.properties is missing or invalid, default values
 * are used and a warning is logged. The application continues with sensible defaults.</p>
 * 
 * @see java.util.Properties
 */
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static final String CONFIG_FILE = "application.properties";
    private static AppConfig instance;
    
    private final Properties properties;

    private AppConfig() {
        properties = new Properties();
        loadProperties();
    }

    /**
     * Gets the singleton instance of AppConfig.
     * 
     * @return AppConfig instance
     */
    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    /**
     * Loads properties from the application.properties file.
     */
    private void loadProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                log.warn("Unable to find {}, using defaults", CONFIG_FILE);
                return;
            }
            properties.load(input);
            log.info("Loaded configuration from {}", CONFIG_FILE);
        } catch (IOException e) {
            log.error("Error loading configuration file", e);
        }
    }

    /**
     * Gets a string property value.
     * 
     * @param key Property key
     * @param defaultValue Default value if key not found
     * @return Property value or default
     */
    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Gets an integer property value.
     * 
     * @param key Property key
     * @param defaultValue Default value if key not found or invalid
     * @return Property value or default
     */
    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for key {}: {}", key, value);
            return defaultValue;
        }
    }

    /**
     * Gets a double property value.
     * 
     * @param key Property key
     * @param defaultValue Default value if key not found or invalid
     * @return Property value or default
     */
    public double getDouble(String key, double defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid double value for key {}: {}", key, value);
            return defaultValue;
        }
    }

    /**
     * Gets a boolean property value.
     * 
     * @param key Property key
     * @param defaultValue Default value if key not found
     * @return Property value or default
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Gets a string array property value (comma-separated).
     * 
     * @param key Property key
     * @param defaultValue Default value if key not found
     * @return Array of string values
     */
    public String[] getStringArray(String key, String[] defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.split(",");
    }

    // Convenience methods for common properties

    public String getAppName() {
        return getString("app.name", "TerraGIS");
    }

    public String getAppVersion() {
        return getString("app.version", "1.0-SNAPSHOT");
    }

    public String getAppTitle() {
        return getString("app.title", "TerraGIS - Professional Java GIS Platform");
    }

    public int getMapCanvasDefaultWidth() {
        return getInt("map.canvas.default.width", 800);
    }

    public int getMapCanvasDefaultHeight() {
        return getInt("map.canvas.default.height", 600);
    }

    public String getDefaultCRS() {
        return getString("map.default.crs", "EPSG:4326");
    }

    public double getDefaultBufferDistance() {
        return getDouble("geometry.buffer.default.distance", 100.0);
    }

    public String[] getAvailablePersonas() {
        return getStringArray("persona.available", 
            new String[]{"Surveyor (Cadastral)", "Hydrologist (Flood)", "Auditor (Carbon MRV)"});
    }

    public String getDefaultPersona() {
        return getString("persona.default", "Surveyor (Cadastral)");
    }
}
