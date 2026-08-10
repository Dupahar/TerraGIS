package com.terra.gis.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Manages project-specific session state (layers, digitized features, viewport).
 * <p>
 * Each project has its own session file that persists:
 * <ul>
 *   <li>Layer stack (file paths, visibility, order)</li>
 *   <li>Digitized features (points, lines, polygons with attributes)</li>
 *   <li>Viewport bounds and zoom level</li>
 *   <li>Edit mode state</li>
 * </ul>
 * <p>
 * This keeps projects isolated from each other and from global preferences.
 */
public class ProjectSessionManager {

    private static final Logger log = LoggerFactory.getLogger(ProjectSessionManager.class);
    private static final String SESSION_FILE = "session.json";

    /**
     * Project session data container.
     */
    public static class ProjectSession {
        public String layerState;
        public String digitizedState;
        public String aiActionState;
        public long lastSaved;

        public ProjectSession(String layerState, String digitizedState) {
            this(layerState, digitizedState, "");
        }

        public ProjectSession(String layerState, String digitizedState, String aiActionState) {
            this.layerState = layerState != null ? layerState : "";
            this.digitizedState = digitizedState != null ? digitizedState : "";
            this.aiActionState = aiActionState != null ? aiActionState : "";
            this.lastSaved = System.currentTimeMillis();
        }
    }

    /**
     * Saves the project session to disk.
     *
     * @param projectPath Path to the project directory
     * @param layerState Encoded layer state
     * @param digitizedState Encoded digitized features
     * @return true if successful
     */
    public static boolean saveProjectSession(Path projectPath, String layerState, String digitizedState) {
        return saveProjectSession(projectPath, layerState, digitizedState, "");
    }

    /**
     * Saves the project session to disk.
     *
     * @param projectPath Path to the project directory
     * @param layerState Encoded layer state
     * @param digitizedState Encoded digitized features
     * @param aiActionState Encoded AI model/action state
     * @return true if successful
     */
    public static boolean saveProjectSession(Path projectPath, String layerState, String digitizedState, String aiActionState) {
        try {
            if (projectPath == null || !Files.isDirectory(projectPath)) {
                log.warn("Project path is not a directory: {}", projectPath);
                return false;
            }

            Path sessionFile = projectPath.resolve(SESSION_FILE);
            ProjectSession session = new ProjectSession(layerState, digitizedState, aiActionState);
            String json = sessionToJson(session);
            Path tempFile = projectPath.resolve(SESSION_FILE + ".tmp");
            Files.writeString(tempFile, json, StandardCharsets.UTF_8);
            try {
                Files.move(tempFile, sessionFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveEx) {
                Files.move(tempFile, sessionFile, StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("Saved project session to: {}", sessionFile);
            return true;
        } catch (IOException e) {
            log.error("Failed to save project session", e);
            return false;
        }
    }

    /**
     * Loads the project session from disk.
     *
     * @param projectPath Path to the project directory
     * @return ProjectSession if found, or null if not present
     */
    public static ProjectSession loadProjectSession(Path projectPath) {
        try {
            Path sessionFile = projectPath.resolve(SESSION_FILE);
            if (!Files.exists(sessionFile)) {
                log.debug("No existing session file for project: {}", projectPath);
                return null;
            }

            String json = Files.readString(sessionFile, StandardCharsets.UTF_8);
            ProjectSession session = jsonToSession(json);
            log.debug("Loaded project session from: {}", sessionFile);
            return session;
        } catch (IOException e) {
            log.error("Failed to load project session", e);
            return null;
        }
    }

    /**
     * Initializes a new blank project session.
     * 
     * @param projectPath Path to the project directory
     * @return Empty ProjectSession
     */
    public static ProjectSession createBlankSession(Path projectPath) {
        log.debug("Creating blank session for new project: {}", projectPath);
        return new ProjectSession("", "", "");
    }

    private static String sessionToJson(ProjectSession session) {
        // Simple JSON encoding
        String escapedLayerState = escapeJson(session.layerState);
        String escapedDigitizedState = escapeJson(session.digitizedState);
        String escapedAiActionState = escapeJson(session.aiActionState);
        
        return String.format(
            "{\"layerState\":\"%s\",\"digitizedState\":\"%s\",\"aiActionState\":\"%s\",\"lastSaved\":%d}",
                escapedLayerState,
                escapedDigitizedState,
            escapedAiActionState,
                session.lastSaved
        );
    }

    private static ProjectSession jsonToSession(String json) {
        try {
            String layerState = extractJsonString(json, "layerState");
            String digitizedState = extractJsonString(json, "digitizedState");
            String aiActionState = extractJsonString(json, "aiActionState");
            return new ProjectSession(layerState, digitizedState, aiActionState);
        } catch (Exception e) {
            log.error("Failed to parse session JSON", e);
            return null;
        }
    }

    private static String extractJsonString(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int startIdx = json.indexOf(searchKey);
        if (startIdx == -1) {
            return "";
        }
        startIdx += searchKey.length();
        
        StringBuilder sb = new StringBuilder();
        for (int i = startIdx; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (next == '"') {
                    sb.append('"');
                    i++;
                } else if (next == '\\') {
                    sb.append('\\');
                    i++;
                } else {
                    sb.append(c);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
