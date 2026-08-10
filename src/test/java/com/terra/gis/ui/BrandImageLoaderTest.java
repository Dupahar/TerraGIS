package com.terra.gis.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

class BrandImageLoaderTest {

    private static final Logger log = LoggerFactory.getLogger(BrandImageLoaderTest.class);

    @Test
    void loadTrimmedBrandImage_loadsClasspathImage() {
        Image image = BrandImageLoader.loadTrimmedBrandImage(BrandImageLoader.class, log);

        assertNotNull(image);
        assertTrue(image.getWidth() > 0.0);
        assertTrue(image.getHeight() > 0.0);
    }

    @Test
    void trimTransparentPadding_reducesTransparentBorder() throws Exception {
        WritableImage image = new WritableImage(6, 4);
        PixelWriter writer = image.getPixelWriter();

        writer.setColor(3, 2, Color.rgb(24, 96, 160, 1.0));

        Method method = BrandImageLoader.class.getDeclaredMethod("trimTransparentPadding", Image.class);
        method.setAccessible(true);

        Image trimmed = (Image) method.invoke(null, image);

        assertNotNull(trimmed);
        assertEquals(3, (int) Math.round(trimmed.getWidth()));
        assertEquals(3, (int) Math.round(trimmed.getHeight()));
    }

    @Test
    void trimTransparentPadding_returnsOriginalImageWhenFullyTransparent() throws Exception {
        WritableImage image = new WritableImage(4, 4);

        Method method = BrandImageLoader.class.getDeclaredMethod("trimTransparentPadding", Image.class);
        method.setAccessible(true);

        Image trimmed = (Image) method.invoke(null, image);

        assertSame(image, trimmed);
    }

    @Test
    void loadFromWorkspace_returnsNullWhenWorkspaceImageIsMissing(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());

        try {
            Method method = BrandImageLoader.class.getDeclaredMethod("loadFromWorkspace", Logger.class);
            method.setAccessible(true);

            Image image = (Image) method.invoke(null, log);
            assertNull(image);
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }
}