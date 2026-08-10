package com.terra.gis.ui;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BrandImageLoader {

    private static final String BRAND_LOGO_CLASSPATH = "/images/dupahar_logo.png";

    private BrandImageLoader() {
    }

    public static Image loadTrimmedBrandImage(Class<?> anchorClass, Logger log) {
        Image image = loadFromClasspath(anchorClass, BRAND_LOGO_CLASSPATH, log);
        if (image == null) {
            image = loadFromClasspath(anchorClass, "images/dupahar_logo.png", log);
        }
        if (image == null) {
            image = loadFromWorkspace(log);
        }
        if (image == null) {
            return null;
        }

        return trimTransparentPadding(image);
    }

    private static Image loadFromClasspath(Class<?> anchorClass, String resourcePath, Logger log) {
        try (InputStream stream = anchorClass.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return null;
            }
            Image image = new Image(stream);
            if (image.isError() || image.getPixelReader() == null) {
                log.warn("Brand logo resource exists but could not be decoded: {}", resourcePath);
                return null;
            }
            return image;
        } catch (Exception ex) {
            log.warn("Unable to load brand logo from classpath resource: {}", resourcePath, ex);
            return null;
        }
    }

    private static Image loadFromWorkspace(Logger log) {
        try {
            Path projectRoot = Path.of(System.getProperty("user.dir"));
            Path logoPath = projectRoot.resolve("src").resolve("main").resolve("resources").resolve("images").resolve("dupahar_logo.png");
            if (!Files.exists(logoPath)) {
                return null;
            }
            Image image = new Image(logoPath.toUri().toString(), false);
            if (image.isError() || image.getPixelReader() == null) {
                log.warn("Brand logo exists on disk but failed to decode: {}", logoPath);
                return null;
            }
            return image;
        } catch (Exception ex) {
            log.warn("Unable to load brand logo from workspace path", ex);
            return null;
        }
    }

    private static Image trimTransparentPadding(Image image) {
        PixelReader reader = image.getPixelReader();
        if (reader == null) {
            return image;
        }

        int width = (int) Math.round(image.getWidth());
        int height = (int) Math.round(image.getHeight());
        if (width <= 1 || height <= 1) {
            return image;
        }

        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (reader.getColor(x, y).getOpacity() > 0.02) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            return image;
        }

        int padX = Math.max(1, (maxX - minX + 1) / 20);
        int padY = Math.max(1, (maxY - minY + 1) / 20);

        minX = Math.max(0, minX - padX);
        minY = Math.max(0, minY - padY);
        maxX = Math.min(width - 1, maxX + padX);
        maxY = Math.min(height - 1, maxY + padY);

        int cropW = maxX - minX + 1;
        int cropH = maxY - minY + 1;
        if (cropW <= 0 || cropH <= 0 || (cropW == width && cropH == height)) {
            return image;
        }

        return new WritableImage(reader, minX, minY, cropW, cropH);
    }
}