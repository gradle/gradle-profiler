package org.gradle.profiler.yourkit;

import org.gradle.profiler.Logging;
import org.gradle.profiler.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static org.gradle.profiler.OperatingSystem.MAC_OS_APPLICATIONS_PATH;
import static org.gradle.profiler.OperatingSystem.MAC_OS_RESOURCES_PATH;

public class YourKit {

    static final String ENVIRONMENT_VARIABLE = "YOURKIT_HOME";
    private static final String YOURKIT_HOME = System.getenv(ENVIRONMENT_VARIABLE);

    static final int PORT = Integer.getInteger("org.gradle.profiler.yourkit.port", 10021);

    /**
     * Locates the user's YourKit installation. Returns null when not found.
     */
    public static File findYourKitHome() {
        if (YOURKIT_HOME != null) {
            File ykHome = new File(YOURKIT_HOME);
            if (ykHome.exists()) {
                return ykHome;
            }
        }
        File applicationsDir = new File(MAC_OS_APPLICATIONS_PATH);
        if (!applicationsDir.isDirectory()) {
            return null;
        }
        for (File file : applicationsDir.listFiles()) {
            if (file.getName().matches("YourKit.*\\.app")) {
                return file;
            }
        }
        return null;
    }

    public static File findControllerJar() {
        File yourKitHome = findYourKitHome();
        return tryLocations(yourKitHome, MAC_OS_RESOURCES_PATH + "/lib/yjp-controller-api-redist.jar", "lib/yjp-controller-api-redist.jar");
    }

    public static File findJniLib() {
        File yourKitHome = findYourKitHome();
        if (OperatingSystem.isWindows()) {
            return tryLocations(yourKitHome, "bin/win64/yjpagent.dll", "bin/windows-x86-64/yjpagent.dll");
        }
        String macLibLocationPrefix = MAC_OS_RESOURCES_PATH +"/bin/mac/libyjpagent.";
        return tryLocations(yourKitHome, macLibLocationPrefix + "jnilib", macLibLocationPrefix + "dylib", "bin/linux-x86-64/libyjpagent.so");
    }

    /**
     * Determines whether the YourKit installation supports the HTTP API v2 (2024.9+).
     *
     * <p>Detection is based on the controller JAR's manifest: YourKit 2024.9+ removed the
     * {@code Main-Class} attribute from {@code yjp-controller-api-redist.jar}, making it
     * a library-only JAR rather than an executable one.
     *
     * @return true if the installation uses HTTP API v2 (no Main-Class in manifest, or JAR missing),
     *         false if the installation uses the legacy CLI (Main-Class present)
     */
    public static boolean isHttpApiSupported() {
        File controllerJar = findControllerJar();
        if (controllerJar == null || !controllerJar.isFile()) {
            return true;
        }
        try (JarFile jar = new JarFile(controllerJar)) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) {
                return true;
            }
            String mainClass = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            return mainClass == null || mainClass.isEmpty();
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Blocks until the given port is free, so a new YourKit agent can bind to it.
     */
    static void waitForPortAvailable(int port) {
        long timeoutMs = 30_000;
        long pollIntervalMs = 500;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isPortAvailable(port)) {
                return;
            }
            Logging.detailed().println("Waiting for YourKit agent port " + port + " to become available...");
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        Logging.detailed().println("Warning: YourKit agent port " + port + " is still in use after waiting " + timeoutMs + "ms");
    }

    private static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress("localhost", port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static File tryLocations(File baseDir, String... candidates) {
        for (String candidate : candidates) {
            File location = new File(baseDir, candidate);
            if (location.exists()) {
                return location;
            }
        }
        return null;
    }
}
