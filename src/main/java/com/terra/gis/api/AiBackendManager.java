package com.terra.gis.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts and monitors the Python AI backend process used by TerraGIS segmentation.
 */
public final class AiBackendManager {

    private static final Logger log = LoggerFactory.getLogger(AiBackendManager.class);
    private static final Object LOCK = new Object();
    private static final int PORT = 6565;
    private static final Duration START_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(5);

    private static Process backendProcess;
    private static String lastStartupIssue;

    private AiBackendManager() {
    }

    public static boolean ensureBackendRunning() {
        synchronized (LOCK) {
            lastStartupIssue = null;

            if (isBackendReachable()) {
                return true;
            }

            if (backendProcess != null && backendProcess.isAlive()) {
                return waitForBackend(RECONNECT_TIMEOUT);
            }

            return startBackendAndWait();
        }
    }

    public static String getLastStartupIssue() {
        synchronized (LOCK) {
            return lastStartupIssue;
        }
    }

    public static void shutdown() {
        synchronized (LOCK) {
            if (backendProcess != null && backendProcess.isAlive()) {
                log.info("Stopping AI backend process");
                backendProcess.destroy();
                backendProcess = null;
            }
        }
    }

    private static boolean startBackendAndWait() {
        Path repoRoot = Path.of(System.getProperty("user.dir"));
        Path backendDir = repoRoot.resolve("ai_backend");
        Path serverScript = backendDir.resolve("server.py");
        Path protoPb2 = backendDir.resolve("terragis_service_pb2.py");
        Path protoGrpcPb2 = backendDir.resolve("terragis_service_pb2_grpc.py");

        if (!Files.exists(serverScript)) {
            lastStartupIssue = "AI backend script not found at " + serverScript;
            log.warn(lastStartupIssue);
            return false;
        }

        if (!Files.exists(protoPb2) || !Files.exists(protoGrpcPb2)) {
            lastStartupIssue = "Python protobuf stubs are missing in ai_backend. Run ai_backend\\generate_proto.ps1 first.";
            log.warn(lastStartupIssue);
            return false;
        }

        String pythonExecutable = resolvePythonExecutable(backendDir);
        try {
            ProcessBuilder pb = new ProcessBuilder(pythonExecutable, serverScript.toString());
            pb.directory(backendDir.toFile());
            pb.redirectErrorStream(true);

            String modelPath = System.getenv("TERRAGIS_MODEL_PATH");
            if (modelPath == null || modelPath.isBlank()) {
                lastStartupIssue = "TERRAGIS_MODEL_PATH is not set; AI backend cannot start a real model";
                log.warn(lastStartupIssue);
                return false;
            }

            if (!Files.exists(Path.of(modelPath))) {
                lastStartupIssue = "TERRAGIS_MODEL_PATH does not exist: " + modelPath;
                log.warn(lastStartupIssue);
                return false;
            }

            pb.environment().put("TERRAGIS_MODEL_PATH", modelPath);
            pb.environment().putIfAbsent("TERRAGIS_PORT", String.valueOf(PORT));
            pb.environment().putIfAbsent("TERRAGIS_NUM_CLASSES", "8");
            pb.environment().putIfAbsent("TERRAGIS_TARGET_CLASS", "-1");

            backendProcess = pb.start();
            log.info("Started AI backend process using {}", pythonExecutable);
            pipeBackendLogs(backendProcess);

            boolean started = waitForBackend(START_TIMEOUT);
            if (!started && lastStartupIssue == null) {
                lastStartupIssue = "Backend did not become reachable on localhost:" + PORT
                        + "; verify Python dependencies are installed in the selected environment.";
            }
            return started;
        } catch (Exception ex) {
            log.error("Failed to start AI backend process", ex);
            lastStartupIssue = "Failed to launch Python backend: " + ex.getMessage();
            backendProcess = null;
            return false;
        }
    }

    private static String resolvePythonExecutable(Path backendDir) {
        String fromEnv = System.getenv("TERRAGIS_PYTHON_EXECUTABLE");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }

        String activeVenv = System.getenv("VIRTUAL_ENV");
        if (activeVenv != null && !activeVenv.isBlank()) {
            Path activeVenvPython = Path.of(activeVenv).resolve("Scripts").resolve("python.exe");
            if (Files.exists(activeVenvPython)) {
                return activeVenvPython.toString();
            }
        }

        Path venvPython = backendDir.resolve(".venv").resolve("Scripts").resolve("python.exe");
        if (Files.exists(venvPython)) {
            return venvPython.toString();
        }

        return "python";
    }

    private static boolean waitForBackend(Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (backendProcess != null && !backendProcess.isAlive()) {
                try {
                    int exitCode = backendProcess.exitValue();
                    lastStartupIssue = "AI backend process exited early with code " + exitCode
                            + ". Check ai_backend logs/imports (protobuf stubs, torch/rasterio/grpc).";
                    log.warn(lastStartupIssue);
                } catch (IllegalThreadStateException ignored) {
                    // Process is still running between checks.
                }
                return false;
            }

            if (isBackendReachable()) {
                return true;
            }

            try {
                Thread.sleep(350);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static boolean isBackendReachable() {
        try (TerraApiClient client = new TerraApiClient("localhost", PORT)) {
            client.ping("terragis-autostart");
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static void pipeBackendLogs(Process process) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[ai-backend] {}", line);
                }
            } catch (Exception ex) {
                log.debug("AI backend log stream ended", ex);
            }
        }, "ai-backend-log-reader");
        t.setDaemon(true);
        t.start();
    }
}
