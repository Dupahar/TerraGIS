package com.terra.gis.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectSessionManagerTest {

    @Test
    void saveAndLoadProjectSession_roundTripsEscapedContent(@TempDir Path tempDir) throws Exception {
        Path projectDir = Files.createDirectories(tempDir.resolve("project"));

        boolean saved = ProjectSessionManager.saveProjectSession(
                projectDir,
                "layers\\primary\"route",
                "digitized\\geometry\"set",
                "ai\\action\"state");

        assertTrue(saved);

        ProjectSessionManager.ProjectSession session = ProjectSessionManager.loadProjectSession(projectDir);

        assertNotNull(session);
        assertEquals("layers\\primary\"route", session.layerState);
        assertEquals("digitized\\geometry\"set", session.digitizedState);
        assertEquals("ai\\action\"state", session.aiActionState);
        assertTrue(session.lastSaved > 0L);
    }

    @Test
    void loadProjectSession_returnsNullWhenFileIsMissing(@TempDir Path tempDir) {
        assertNull(ProjectSessionManager.loadProjectSession(tempDir.resolve("missing-project")));
    }

    @Test
    void saveProjectSession_returnsFalseWhenProjectDirectoryDoesNotExist(@TempDir Path tempDir) {
        assertFalse(ProjectSessionManager.saveProjectSession(
                tempDir.resolve("missing-project"),
                "layers",
                "digitized"));
    }

    @Test
    void createBlankSessionInitializesEmptyValues(@TempDir Path tempDir) {
        ProjectSessionManager.ProjectSession session = ProjectSessionManager.createBlankSession(tempDir);

        assertEquals("", session.layerState);
        assertEquals("", session.digitizedState);
        assertEquals("", session.aiActionState);
        assertTrue(session.lastSaved > 0L);
    }
}