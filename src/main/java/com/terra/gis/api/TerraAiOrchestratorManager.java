package com.terra.gis.api;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Minimal orchestrator health manager used by the desktop UI.
 */
public final class TerraAiOrchestratorManager {

    private static volatile String lastStartupIssue;

    private TerraAiOrchestratorManager() {
    }

    public static boolean ensureOrchestratorRunning(String host, int port) {
        String resolvedHost = (host == null || host.isBlank()) ? "localhost" : host.trim();
        int resolvedPort = port > 0 ? port : 50051;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(resolvedHost, resolvedPort), 1200);
            lastStartupIssue = null;
            return true;
        } catch (IOException ex) {
            lastStartupIssue = "TerraAI orchestrator is not reachable at "
                    + resolvedHost + ":" + resolvedPort + " (" + ex.getMessage() + ")";
            return false;
        }
    }

    public static String getLastStartupIssue() {
        return lastStartupIssue;
    }
}
