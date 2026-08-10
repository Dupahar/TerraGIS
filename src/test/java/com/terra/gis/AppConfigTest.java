package com.terra.gis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class AppConfigTest {

    @Test
    void getInstance_returnsSingletonWithDefaultValues() {
        AppConfig first = AppConfig.getInstance();
        AppConfig second = AppConfig.getInstance();

        assertSame(first, second);
        assertEquals("TerraGIS", first.getAppName());
        assertEquals("1.0-SNAPSHOT", first.getAppVersion());
        assertEquals("TerraGIS - Professional Java GIS Platform", first.getAppTitle());
        assertEquals(800, first.getMapCanvasDefaultWidth());
        assertEquals(600, first.getMapCanvasDefaultHeight());
        assertEquals("EPSG:4326", first.getDefaultCRS());
        assertEquals(100.0, first.getDefaultBufferDistance(), 0.0);
        assertArrayEquals(
                new String[] {"Surveyor (Cadastral)", "Hydrologist (Flood)", "Auditor (Carbon MRV)"},
                first.getAvailablePersonas());
        assertEquals("Surveyor (Cadastral)", first.getDefaultPersona());
    }

    @Test
    void gettersUseConfiguredValuesAndFallbacks() throws Exception {
        AppConfig config = AppConfig.getInstance();
        Properties properties = extractProperties(config);

        properties.setProperty("app.name", "TerraGIS Pro");
        properties.setProperty("app.version", "2.0");
        properties.setProperty("app.title", "TerraGIS for Field Ops");
        properties.setProperty("map.canvas.default.width", "1280");
        properties.setProperty("map.canvas.default.height", "720");
        properties.setProperty("map.default.crs", "EPSG:3857");
        properties.setProperty("geometry.buffer.default.distance", "250.5");
        properties.setProperty("persona.available", "Surveyor,Analyst,Auditor");
        properties.setProperty("persona.default", "Analyst");
        properties.setProperty("custom.int", "42");
        properties.setProperty("custom.double", "12.75");
        properties.setProperty("custom.boolean", "true");
        properties.setProperty("custom.list", "a,b,c");
        properties.setProperty("custom.invalid.int", "not-a-number");
        properties.setProperty("custom.invalid.double", "not-a-number");

        assertEquals("TerraGIS Pro", config.getAppName());
        assertEquals("2.0", config.getAppVersion());
        assertEquals("TerraGIS for Field Ops", config.getAppTitle());
        assertEquals(1280, config.getMapCanvasDefaultWidth());
        assertEquals(720, config.getMapCanvasDefaultHeight());
        assertEquals("EPSG:3857", config.getDefaultCRS());
        assertEquals(250.5, config.getDefaultBufferDistance(), 0.0);
        assertArrayEquals(new String[] {"Surveyor", "Analyst", "Auditor"}, config.getAvailablePersonas());
        assertEquals("Analyst", config.getDefaultPersona());
        assertEquals(42, config.getInt("custom.int", 0));
        assertEquals(12.75, config.getDouble("custom.double", 0.0), 0.0);
        assertEquals(true, config.getBoolean("custom.boolean", false));
        assertArrayEquals(new String[] {"a", "b", "c"}, config.getStringArray("custom.list", new String[0]));
        assertEquals(99, config.getInt("custom.invalid.int", 99));
        assertEquals(3.5, config.getDouble("custom.invalid.double", 3.5), 0.0);
    }

    private Properties extractProperties(AppConfig config) throws Exception {
        Field field = AppConfig.class.getDeclaredField("properties");
        field.setAccessible(true);
        return (Properties) field.get(config);
    }
}