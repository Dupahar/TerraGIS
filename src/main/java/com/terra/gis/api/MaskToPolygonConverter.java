package com.terra.gis.api;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

public final class MaskToPolygonConverter {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private MaskToPolygonConverter() {
    }

    public static List<Polygon> convert(AiMaskResult maskResult) {
        return convert(maskResult, Integer.MAX_VALUE);
    }

    public static List<Polygon> convert(AiMaskResult maskResult, int maxPolygons) {
        return convert(maskResult, maxPolygons, 1, 0.0d);
    }

    public static List<Polygon> convert(
            AiMaskResult maskResult,
            int maxPolygons,
            int minComponentPixels,
            double minComponentFillRatio) {
        if (maskResult == null) {
            throw new IllegalArgumentException("maskResult cannot be null");
        }
        if (maxPolygons <= 0) {
            return List.of();
        }
        if (minComponentPixels <= 0) {
            minComponentPixels = 1;
        }
        if (!Double.isFinite(minComponentFillRatio)) {
            minComponentFillRatio = 0.0d;
        }
        minComponentFillRatio = Math.max(0.0d, Math.min(1.0d, minComponentFillRatio));

        List<Polygon> polygons = new ArrayList<>();
        int width = maskResult.maskWidth();
        int height = maskResult.maskHeight();
        boolean[] mask = maskResult.mask();
        int xOffset = maskResult.tile().x();
        int yOffset = maskResult.tile().y();
        boolean[] visited = new boolean[mask.length];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (!mask[idx] || visited[idx]) {
                    continue;
                }
                ComponentSummary component = componentSummary(mask, visited, width, height, x, y);
                int bboxW = component.maxX() - component.minX() + 1;
                int bboxH = component.maxY() - component.minY() + 1;
                int bboxArea = bboxW * bboxH;
                double fillRatio = bboxArea > 0 ? (double) component.pixelCount() / bboxArea : 0.0d;

                if (component.pixelCount() < minComponentPixels || fillRatio < minComponentFillRatio) {
                    continue;
                }

                polygons.add(cellBoundsPolygon(
                        xOffset + component.minX(),
                        yOffset + component.minY(),
                        xOffset + component.maxX() + 1,
                        yOffset + component.maxY() + 1));
                if (polygons.size() >= maxPolygons) {
                    return polygons;
                }
            }
        }
        return polygons;
    }

    private static ComponentSummary componentSummary(
            boolean[] mask,
            boolean[] visited,
            int width,
            int height,
            int startX,
            int startY) {
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] { startX, startY });
        visited[startY * width + startX] = true;

        int minX = startX;
        int maxX = startX;
        int minY = startY;
        int maxY = startY;
        int pixelCount = 0;

        final int[][] dirs = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        while (!queue.isEmpty()) {
            int[] p = queue.removeFirst();
            int x = p[0];
            int y = p[1];
            pixelCount++;

            if (x < minX) {
                minX = x;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (y > maxY) {
                maxY = y;
            }

            for (int[] d : dirs) {
                int nx = x + d[0];
                int ny = y + d[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                    continue;
                }

                int nIdx = ny * width + nx;
                if (!mask[nIdx] || visited[nIdx]) {
                    continue;
                }

                visited[nIdx] = true;
                queue.add(new int[] { nx, ny });
            }
        }

        return new ComponentSummary(minX, minY, maxX, maxY, pixelCount);
    }

    private record ComponentSummary(int minX, int minY, int maxX, int maxY, int pixelCount) {
    }

    private static Polygon cellBoundsPolygon(int minX, int minY, int maxX, int maxY) {
        Coordinate[] shell = new Coordinate[] {
                new Coordinate(minX, minY),
                new Coordinate(maxX, minY),
                new Coordinate(maxX, maxY),
                new Coordinate(minX, maxY),
                new Coordinate(minX, minY)
        };
        return GEOMETRY_FACTORY.createPolygon(shell);
    }
}
