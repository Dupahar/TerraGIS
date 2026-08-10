package com.terra.gis.api;

public record RasterTile(int x, int y, int width, int height, int index, int total) {
}
