package com.terra.gis.spatial;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

class VectorImporterTest {

    private final VectorImporter importer = new VectorImporter();

    @Test
    void readVectorFile_throwsForNullFile() {
        assertThrows(IllegalArgumentException.class, () -> importer.readVectorFile(null));
    }

    @Test
    void readVectorFile_throwsForMissingFile() {
        File missing = new File("target/does-not-exist-test-vector.shp");

        assertThrows(IOException.class, () -> importer.readVectorFile(missing));
    }

    @Test
    void printSchema_throwsForNullFeatureSource() {
        assertThrows(IllegalArgumentException.class, () -> importer.printSchema(null));
    }
}
