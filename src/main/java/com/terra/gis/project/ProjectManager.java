package com.terra.gis.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * Manages TerraGIS project files and metadata.
 * <p>
 * Provides functionality for:
 * <ul>
 *   <li>Creating new projects</li>
 *   <li>Loading/saving project state</li>
 *   <li>Tracking recent projects</li>
 *   <li>Managing project directory structure</li>
 * </ul>
 */
public class ProjectManager {

    private static final Logger log = LoggerFactory.getLogger(ProjectManager.class);
    private static final String PROJECTS_DIR = ".terragis-projects";
    private static final String PROJECT_FILE = "project.json";
    private static final String LAYERS_DIR = "layers";
    private static final String PREF_KEY_RECENT_PROJECTS = "projectManager.recentProjects";
    private static final int MAX_RECENT_PROJECTS = 10;

    private final Preferences prefs = Preferences.userNodeForPackage(ProjectManager.class);
    private Path projectsRootDir;

    public ProjectManager() {
        initializeProjectsDirectory();
    }

    private void initializeProjectsDirectory() {
        String userHome = System.getProperty("user.home");
        projectsRootDir = Paths.get(userHome, PROJECTS_DIR);
        try {
            Files.createDirectories(projectsRootDir);
            log.debug("Projects directory initialized at: {}", projectsRootDir);
        } catch (IOException e) {
            log.error("Failed to create projects directory", e);
        }
    }

    /**
     * Creates a new project with the given name.
     * 
     * @param projectName Name of the project
     * @return Path to the new project directory, or null if creation failed
     */
    public Path createProject(String projectName) {
        if (projectName == null || projectName.trim().isEmpty()) {
            throw new IllegalArgumentException("Project name cannot be empty");
        }

        Path projectDir = projectsRootDir.resolve(sanitizeProjectName(projectName));
        try {
            if (Files.exists(projectDir)) {
                throw new IOException("Project already exists: " + projectName);
            }

            Files.createDirectories(projectDir);
            Files.createDirectories(projectDir.resolve(LAYERS_DIR));

            // Create project metadata file
            ProjectMetadata metadata = new ProjectMetadata(
                    projectName,
                    Instant.now().getEpochSecond(),
                    Instant.now().getEpochSecond()
            );
            saveProjectMetadata(projectDir, metadata);

            addRecentProject(projectDir, projectName);
            log.info("Created new project: {} at {}", projectName, projectDir);
            return projectDir;
        } catch (IOException e) {
            log.error("Failed to create project: {}", projectName, e);
            return null;
        }
    }

    /**
     * Opens an existing project by directory.
     * 
     * @param projectPath Path to the project directory
     * @return ProjectMetadata if valid, or null if project is invalid
     */
    public ProjectMetadata openProject(Path projectPath) {
        try {
            if (!Files.isDirectory(projectPath)) {
                throw new IOException("Project path is not a directory: " + projectPath);
            }

            Path projectFile = projectPath.resolve(PROJECT_FILE);
            if (!Files.exists(projectFile)) {
                throw new IOException("Project metadata file not found: " + projectFile);
            }

            ProjectMetadata metadata = loadProjectMetadata(projectPath);
            if (metadata != null) {
                // Update lastModified timestamp to current time
                long nowSeconds = Instant.now().getEpochSecond();
                ProjectMetadata updatedMetadata = new ProjectMetadata(
                        metadata.name(),
                        metadata.created(),
                        nowSeconds
                );
                saveProjectMetadata(projectPath, updatedMetadata);
                addRecentProject(projectPath, metadata.name());
                log.info("Opened project: {} from {}", metadata.name(), projectPath);
                return updatedMetadata;
            }
            return metadata;
        } catch (IOException e) {
            log.error("Failed to open project: {}", projectPath, e);
            return null;
        }
    }

    /**
     * Lists all available projects.
     * 
     * @return List of ProjectInfo for all available projects
     */
    public List<ProjectInfo> listAllProjects() {
        List<ProjectInfo> projects = new ArrayList<>();
        try {
            if (!Files.exists(projectsRootDir)) {
                return projects;
            }

            Files.list(projectsRootDir)
                    .filter(Files::isDirectory)
                    .forEach(projectDir -> {
                        ProjectMetadata metadata = loadProjectMetadata(projectDir);
                        if (metadata != null) {
                            projects.add(new ProjectInfo(
                                    projectDir,
                                    metadata.name(),
                                    metadata.created(),
                                    metadata.lastModified()
                            ));
                        }
                    });

            projects.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        } catch (IOException e) {
            log.error("Failed to list projects", e);
        }
        return projects;
    }

    /**
     * Gets recently opened projects.
     * 
     * @return List of ProjectInfo sorted by recency
     */
    public List<ProjectInfo> getRecentProjects() {
        List<ProjectInfo> recent = new ArrayList<>();
        String encodedList = prefs.get(PREF_KEY_RECENT_PROJECTS, "");
        if (encodedList.isEmpty()) {
            return recent;
        }

        String[] paths = encodedList.split(";");
        for (String pathStr : paths) {
            if (pathStr.trim().isEmpty()) continue;
            try {
                Path projectPath = Paths.get(pathStr);
                if (Files.isDirectory(projectPath)) {
                    ProjectMetadata metadata = loadProjectMetadata(projectPath);
                    if (metadata != null) {
                        recent.add(new ProjectInfo(projectPath, metadata.name(), metadata.created(), metadata.lastModified()));
                    }
                }
            } catch (Exception e) {
                log.debug("Invalid project path in recent list: {}", pathStr);
            }
        }
        return recent;
    }

    /**
     * Gets the path for a specific layers directory within a project.
     * 
     * @param projectPath Path to the project
     * @return Path to the layers subdirectory
     */
    public Path getLayersDirectory(Path projectPath) {
        return projectPath.resolve(LAYERS_DIR);
    }

    /**
     * Saves project metadata.
     * 
     * @param projectPath Path to the project
     * @param metadata The metadata to save
     * @return true if successful, false otherwise
     */
    public boolean saveProjectMetadata(Path projectPath, ProjectMetadata metadata) {
        try {
            Path metadataFile = projectPath.resolve(PROJECT_FILE);
            String json = metadataToJson(metadata);
            Files.write(metadataFile, json.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            log.error("Failed to save project metadata", e);
            return false;
        }
    }

    /**
     * Updates the lastModified timestamp of a project to the current time.
     * 
     * @param projectPath Path to the project
     * @return true if successful, false otherwise
     */
    public boolean updateProjectLastModified(Path projectPath) {
        try {
            ProjectMetadata metadata = loadProjectMetadata(projectPath);
            if (metadata != null) {
                long nowSeconds = Instant.now().getEpochSecond();
                ProjectMetadata updated = new ProjectMetadata(
                        metadata.name(),
                        metadata.created(),
                        nowSeconds
                );
                return saveProjectMetadata(projectPath, updated);
            }
            return false;
        } catch (Exception e) {
            log.debug("Failed to update project last modified timestamp", e);
            return false;
        }
    }

    /**
     * Deletes a project directory.
     * 
     * @param projectPath Path to the project
     * @return true if successful, false otherwise
     */
    public boolean deleteProject(Path projectPath) {
        try {
            deleteDirectoryRecursive(projectPath);
            removeFromRecentProjects(projectPath);
            log.info("Deleted project: {}", projectPath);
            return true;
        } catch (IOException e) {
            log.error("Failed to delete project: {}", projectPath, e);
            return false;
        }
    }

    /**
     * Opens file browser to select a project directory.
     * This is a utility method; UI will implement the actual dialog.
     * 
     * @return Selected project path, or null if cancelled
     */
    public static Path browseForProject() {
        // This method is a placeholder for UI integration
        // The actual file browser will be implemented in the UI layer
        return null;
    }

    // Private helper methods

    private void addRecentProject(Path projectPath, String projectName) {
        String pathStr = projectPath.toString();
        String current = prefs.get(PREF_KEY_RECENT_PROJECTS, "");
        List<String> paths = new ArrayList<>();

        // Add the new project to the front
        paths.add(pathStr);

        // Add existing projects (excluding duplicates)
        if (!current.isEmpty()) {
            for (String p : current.split(";")) {
                if (!p.equals(pathStr) && !p.isEmpty()) {
                    paths.add(p);
                }
            }
        }

        // Keep only the most recent projects
        if (paths.size() > MAX_RECENT_PROJECTS) {
            paths = paths.subList(0, MAX_RECENT_PROJECTS);
        }

        prefs.put(PREF_KEY_RECENT_PROJECTS, String.join(";", paths));
    }

    private void removeFromRecentProjects(Path projectPath) {
        String pathStr = projectPath.toString();
        String current = prefs.get(PREF_KEY_RECENT_PROJECTS, "");
        List<String> paths = new ArrayList<>();

        if (!current.isEmpty()) {
            for (String p : current.split(";")) {
                if (!p.equals(pathStr) && !p.isEmpty()) {
                    paths.add(p);
                }
            }
        }

        prefs.put(PREF_KEY_RECENT_PROJECTS, String.join(";", paths));
    }

    private ProjectMetadata loadProjectMetadata(Path projectPath) {
        try {
            Path metadataFile = projectPath.resolve(PROJECT_FILE);
            if (!Files.exists(metadataFile)) {
                return null;
            }
            String json = Files.readString(metadataFile, StandardCharsets.UTF_8);
            return jsonToMetadata(json);
        } catch (IOException e) {
            log.debug("Failed to load project metadata", e);
            return null;
        }
    }

    private String sanitizeProjectName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
    }

    private void deleteDirectoryRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.list(path).forEach(child -> {
                try {
                    deleteDirectoryRecursive(child);
                } catch (IOException e) {
                    log.error("Failed to delete: {}", child, e);
                }
            });
        }
        Files.deleteIfExists(path);
    }

    private String metadataToJson(ProjectMetadata metadata) {
        return String.format(
                "{\"name\":\"%s\",\"created\":%d,\"lastModified\":%d}",
                escapeJson(metadata.name()),
                metadata.created(),
                metadata.lastModified()
        );
    }

    private ProjectMetadata jsonToMetadata(String json) {
        // Simple JSON parsing (could use a real JSON library)
        try {
            String nameStart = "\"name\":\"";
            int nameIdx = json.indexOf(nameStart) + nameStart.length();
            int nameEnd = json.indexOf("\"", nameIdx);
            String name = json.substring(nameIdx, nameEnd);

            String createdStr = "\"created\":";
            int createdIdx = json.indexOf(createdStr) + createdStr.length();
            int createdEnd = json.indexOf(",", createdIdx);
            long created = Long.parseLong(json.substring(createdIdx, createdEnd));

            String modifiedStr = "\"lastModified\":";
            int modifiedIdx = json.indexOf(modifiedStr) + modifiedStr.length();
            int modifiedEnd = json.indexOf("}", modifiedIdx);
            long modified = Long.parseLong(json.substring(modifiedIdx, modifiedEnd));

            return new ProjectMetadata(name, created, modified);
        } catch (Exception e) {
            log.error("Failed to parse project metadata JSON", e);
            return null;
        }
    }

    private String escapeJson(String str) {
        return str.replace("\"", "\\\"").replace("\\", "\\\\");
    }

    /**
     * Metadata for a TerraGIS project.
     */
    public record ProjectMetadata(
            String name,
            long created,
            long lastModified
    ) {
    }

    /**
     * Information about a project for display in the UI.
     */
    public record ProjectInfo(
            Path directory,
            String name,
            long created,
            long lastModified
    ) {
        public String getDisplayName() {
            return name;
        }

        public String getPath() {
            return directory.toString();
        }

        public String getFormattedCreatedDate() {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM d, yyyy h:mm a");
            return sdf.format(new java.util.Date(created * 1000));
        }

        public String getFormattedDate() {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM d, yyyy h:mm a");
            return sdf.format(new java.util.Date(lastModified * 1000));
        }
    }
}
