package com.terra.gis.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectManagerTest {

    private static final String RECENT_PROJECTS_KEY = "projectManager.recentProjects";

    @TempDir
    Path tempDir;

    private String originalUserHome;

    @BeforeEach
    void setUp() throws BackingStoreException {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        clearRecentProjects();
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        clearRecentProjects();
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void createProject_createsSanitizedProjectStructureAndMetadata() throws Exception {
        ProjectManager manager = new ProjectManager();

        Path projectDir = manager.createProject("My Project #1");

        assertNotNull(projectDir);
        assertEquals(tempDir.resolve(".terragis-projects").resolve("my_project__1"), projectDir);
        assertTrue(Files.isDirectory(projectDir));
        assertTrue(Files.isDirectory(projectDir.resolve("layers")));

        Path metadataFile = projectDir.resolve("project.json");
        assertTrue(Files.exists(metadataFile));
        String json = Files.readString(metadataFile, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"name\":\"My Project #1\""));

        List<ProjectManager.ProjectInfo> recentProjects = manager.getRecentProjects();
        assertEquals(1, recentProjects.size());
        ProjectManager.ProjectInfo info = recentProjects.get(0);
        assertEquals(projectDir, info.directory());
        assertEquals("My Project #1", info.getDisplayName());
        assertEquals(projectDir.toString(), info.getPath());
        assertFalse(info.getFormattedCreatedDate().isBlank());
        assertFalse(info.getFormattedDate().isBlank());
    }

    @Test
    void createProject_rejectsBlankNames() {
        ProjectManager manager = new ProjectManager();

        assertThrows(IllegalArgumentException.class, () -> manager.createProject("   "));
    }

    @Test
    void openProject_updatesMetadataAndRecentProjects() throws Exception {
        ProjectManager manager = new ProjectManager();
        Path projectDir = manager.createProject("Open Project");

        long createdLastModified = readLastModified(projectDir.resolve("project.json"));
        Thread.sleep(1100L);

        ProjectManager.ProjectMetadata opened = manager.openProject(projectDir);

        assertNotNull(opened);
        assertEquals("Open Project", opened.name());
        assertTrue(opened.lastModified() >= createdLastModified);
        assertEquals(1, manager.getRecentProjects().size());
        assertEquals(projectDir, manager.getRecentProjects().get(0).directory());
    }

    @Test
    void openProject_returnsNullForInvalidDirectory() {
        ProjectManager manager = new ProjectManager();

        assertNull(manager.openProject(tempDir.resolve("missing-project")));
    }

    @Test
    void listAllProjects_sortsByLastModifiedDescending() throws Exception {
        ProjectManager manager = new ProjectManager();
        Path firstProject = manager.createProject("First Project");
        Thread.sleep(1100L);
        Path secondProject = manager.createProject("Second Project");

        List<ProjectManager.ProjectInfo> projects = manager.listAllProjects();

        assertEquals(2, projects.size());
        assertEquals(secondProject, projects.get(0).directory());
        assertEquals(firstProject, projects.get(1).directory());
    }

    @Test
    void updateProjectLastModified_returnsFalseForMissingProject() {
        ProjectManager manager = new ProjectManager();

        assertFalse(manager.updateProjectLastModified(tempDir.resolve("missing-project")));
    }

    @Test
    void deleteProject_removesProjectAndRecentEntry() throws Exception {
        ProjectManager manager = new ProjectManager();
        Path projectDir = manager.createProject("Delete Project");

        assertTrue(manager.deleteProject(projectDir));
        assertFalse(Files.exists(projectDir));
        assertTrue(manager.getRecentProjects().isEmpty());
    }

    @Test
    void saveProjectMetadata_writesExpectedJson() throws IOException {
        ProjectManager manager = new ProjectManager();
        Path projectDir = Files.createDirectories(tempDir.resolve("manual-project"));

        boolean saved = manager.saveProjectMetadata(projectDir, new ProjectManager.ProjectMetadata("Manual Project", 11L, 22L));

        assertTrue(saved);
        String json = Files.readString(projectDir.resolve("project.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"name\":\"Manual Project\""));
        assertTrue(json.contains("\"created\":11"));
        assertTrue(json.contains("\"lastModified\":22"));
    }

    @Test
    void browseForProject_returnsNull() {
        assertNull(ProjectManager.browseForProject());
    }

    private void clearRecentProjects() throws BackingStoreException {
        Preferences prefs = Preferences.userNodeForPackage(ProjectManager.class);
        prefs.remove(RECENT_PROJECTS_KEY);
        prefs.flush();
    }

    private long readLastModified(Path metadataFile) throws IOException {
        String json = Files.readString(metadataFile, StandardCharsets.UTF_8);
        String marker = "\"lastModified\":";
        int start = json.indexOf(marker);
        int end = json.indexOf('}', start);
        return Long.parseLong(json.substring(start + marker.length(), end));
    }
}