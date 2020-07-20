package org.gradle.profiler.asyncprofiler;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.gradle.profiler.Logging;
import org.gradle.profiler.OperatingSystem;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Downloads a version of async profiler and installs into ~/.gradle-profiler-dist.
 */
public class AsyncProfilerDownload {
    private static final String ASYNC_PROFILER_VERSION = "1.7.1";

    /**
     * Attempts to locate a default install of async profiler. Uses a previously downloaded installation, or downloads if not available.
     *
     * @return null if not available.
     */
    static File defaultHome() {
        String platformName;
        if (OperatingSystem.isMacOS()) {
            platformName = "macos-x64";
        } else if (OperatingSystem.isLinuxX86()) {
            platformName = "linux-x64";
        } else {
            return null;
        }

        File installDist = new File(new File(System.getProperty("user.home")), ".gradle-profiler-dist/" + ASYNC_PROFILER_VERSION + "/" + platformName);
        File marker = new File(installDist, "ok");
        File homeDir = new File(installDist, "home");

        if (marker.isFile()) {
            return homeDir;
        }

        try {
            URL download = new URL(String.format("https://github.com/jvm-profiling-tools/async-profiler/releases/download/v%s/async-profiler-%s-%s.tar.gz", ASYNC_PROFILER_VERSION, ASYNC_PROFILER_VERSION, platformName));
            Logging.startOperation("Download and install " + download);

            Files.createDirectories(installDist.toPath());
            File bundle = new File(installDist, "async-profiler.tar.gz");
            copyTo(download, bundle);

            deleteDir(homeDir);
            untarTo(bundle, homeDir);

            marker.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException("Could not install async-profiler", e);
        }

        return homeDir;
    }

    private static void untarTo(File source, File destDir) throws IOException {
        try (TarArchiveInputStream tarStream = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(source)))) {
            while (tarStream.getNextEntry() != null) {
                if (tarStream.getCurrentEntry().isDirectory()) {
                    continue;
                }
                String name = tarStream.getCurrentEntry().getName();
                File file = new File(destDir, name);
                Files.createDirectories(file.getParentFile().toPath());
                Files.copy(tarStream, file.toPath(), REPLACE_EXISTING);
                boolean executable = (tarStream.getCurrentEntry().getMode() & 0x40) != 0;
                if (executable) {
                    Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file.toPath());
                    ImmutableSet.Builder<PosixFilePermission> withExecute = ImmutableSet.builder();
                    withExecute.addAll(permissions);
                    withExecute.add(PosixFilePermission.OWNER_EXECUTE);
                    Files.setPosixFilePermissions(file.toPath(), withExecute.build());
                }
            }
        }
    }

    private static void copyTo(URL source, File dest) throws IOException {
        try (InputStream inputStream = source.openConnection().getInputStream()) {
            Files.copy(inputStream, dest.toPath(), REPLACE_EXISTING);
        }
    }

    private static void deleteDir(File homeDir) throws IOException {
        if (homeDir.exists()) {
            Files.walkFileTree(homeDir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
