package com.terra.gis.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class TerraAiOrchestratorManagerTest {

    @Test
    void ensureOrchestratorRunning_returnsTrueWhenSocketIsAcceptingConnections() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> acceptSingleConnection(serverSocket));

            assertTrue(TerraAiOrchestratorManager.ensureOrchestratorRunning("localhost", serverSocket.getLocalPort()));

            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void ensureOrchestratorRunning_returnsFalseWhenPortIsClosed() throws Exception {
        int closedPort;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            closedPort = serverSocket.getLocalPort();
        }

        assertFalse(TerraAiOrchestratorManager.ensureOrchestratorRunning("localhost", closedPort));
        assertTrue(TerraAiOrchestratorManager.getLastStartupIssue().contains("localhost:" + closedPort));
    }

    private void acceptSingleConnection(ServerSocket serverSocket) {
        try {
            serverSocket.accept().close();
        } catch (IOException ignored) {
            // The test closes the socket after the client check completes.
        }
    }
}