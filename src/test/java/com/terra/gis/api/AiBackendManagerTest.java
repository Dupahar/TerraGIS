package com.terra.gis.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiBackendManagerTest {

    @TempDir
    Path tempDir;

    private String originalUserDir;

    @AfterEach
    void tearDown() {
        AiBackendManager.shutdown();
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void ensureBackendRunning_reportsMissingScriptWhenBackendFolderIsAbsent() throws Exception {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());

        try (ServerSocket serverSocket = new ServerSocket(6565);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> acceptAndClose(serverSocket));

            assertFalse(AiBackendManager.ensureBackendRunning());
            assertNotNull(AiBackendManager.getLastStartupIssue());
            assertTrue(AiBackendManager.getLastStartupIssue().contains("AI backend script not found"));

            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void ensureBackendRunning_reportsMissingModelPathWhenSupportFilesExist() throws Exception {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());

        Path backendDir = Files.createDirectories(tempDir.resolve("ai_backend"));
        Files.writeString(backendDir.resolve("server.py"), "print('hello')\n", StandardCharsets.UTF_8);
        Files.writeString(backendDir.resolve("terragis_service_pb2.py"), "# stub\n", StandardCharsets.UTF_8);
        Files.writeString(backendDir.resolve("terragis_service_pb2_grpc.py"), "# stub\n", StandardCharsets.UTF_8);

        try (ServerSocket serverSocket = new ServerSocket(6565);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> acceptAndClose(serverSocket));

            assertFalse(AiBackendManager.ensureBackendRunning());
            assertNotNull(AiBackendManager.getLastStartupIssue());
            assertTrue(AiBackendManager.getLastStartupIssue().contains("TERRAGIS_MODEL_PATH is not set"));

            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void ensureBackendRunning_reportsMissingProtoStubsWhenServerScriptExists() throws Exception {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());

        Path backendDir = Files.createDirectories(tempDir.resolve("ai_backend"));
        Files.writeString(backendDir.resolve("server.py"), "print('hello')\n", StandardCharsets.UTF_8);

        assertFalse(AiBackendManager.ensureBackendRunning());
        assertNotNull(AiBackendManager.getLastStartupIssue());
        assertTrue(AiBackendManager.getLastStartupIssue().contains("protobuf stubs are missing"));
    }

    @Test
    void resolvePythonExecutable_prefersProjectVenvWhenPresent() throws Exception {
        Path backendDir = Files.createDirectories(tempDir.resolve("ai_backend"));
        Path venvPython = backendDir.resolve(".venv").resolve("Scripts").resolve("python.exe");
        Files.createDirectories(venvPython.getParent());
        Files.writeString(venvPython, "", StandardCharsets.UTF_8);

        Method m = AiBackendManager.class.getDeclaredMethod("resolvePythonExecutable", Path.class);
        m.setAccessible(true);
        String resolved = (String) m.invoke(null, backendDir);

        assertEquals(venvPython.toString(), resolved);
    }

    private void acceptAndClose(ServerSocket serverSocket) {
        try {
            serverSocket.accept().close();
        } catch (IOException ignored) {
            // The test closes the socket after the client probe completes.
        }
    }
}