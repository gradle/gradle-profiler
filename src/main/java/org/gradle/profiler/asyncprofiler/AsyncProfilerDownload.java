package org.gradle.profiler.asyncprofiler;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
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
    private static final String ASYNC_PROFILER_VERSION = "2.7";

    /**
     * Attempts to locate a default install of async profiler. Uses a previously downloaded installation, or downloads if not available.
     *
     * @return null if not available.
     */
    static File defaultHome() {
        String platformName;
        String extension;
        if (OperatingSystem.isMacOS()) {
            platformName = "macos";
            extension = "zip";
        } else if (OperatingSystem.isLinuxX86()) {
            platformName = "linux-x64";
            extension = "tar.gz";
        } else {
            return null;
        }

        File installDist = new File(new File(System.getProperty("user.home")), ".gradle-profiler-dist/" + ASYNC_PROFILER_VERSION + "-" + platformName);
        File marker = new File(installDist, "ok");
        File homeDir = new File(installDist, String.format("async-profiler-%s-%s", ASYNC_PROFILER_VERSION, platformName));

        if (marker.isFile()) {
            return homeDir;
        }

        try {
            URL download = new URL(String.format("https://github.com/jvm-profiling-tools/async-profiler/releases/download/v%s/async-profiler-%s-%s.%s", ASYNC_PROFILER_VERSION, ASYNC_PROFILER_VERSION, platformName, extension));
            Logging.startOperation("Download and install " + download);

            Files.createDirectories(installDist.toPath());
            File bundle = new File(installDist, String.format("async-profiler.%s", extension));
            copyTo(download, bundle);

            deleteDir(homeDir);
            if (extension.equals("tar.gz")) {
                untarTo(bundle, installDist);
            } else {
                unzipTo(bundle, installDist);
            }

            marker.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException("Could not install async-profiler", e);
        }

        return homeDir;
    }

    private static void unzipTo(File source, File destDir) throws IOException {
        try (ZipArchiveInputStream zipStream = new ZipArchiveInputStream(new FileInputStream(source))) {
            extract(zipStream, destDir.toPath());
        }
    }

    private static void untarTo(File source, File destDir) throws IOException {
        try (TarArchiveInputStream tarStream = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(source)))) {
            extract(tarStream, destDir.toPath());
        }
    }

    private static void extract(ArchiveInputStream archiveStream, Path destDir) throws IOException {
        ArchiveEntry entry;
        while ((entry = archiveStream.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            Path file = destDir.resolve(name).normalize();
            if (!file.startsWith(destDir)) {
                // Ignore files outside destination dir
                continue;
            }
            Files.createDirectories(file.getParent());
            Files.copy(archiveStream, file, REPLACE_EXISTING);
            boolean executable = isExecutable(entry);
            if (executable) {
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
                ImmutableSet.Builder<PosixFilePermission> withExecute = ImmutableSet.builder();
                withExecute.addAll(permissions);
                withExecute.add(PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(file, withExecute.build());
            }
        }
    }

    private static boolean isExecutable(ArchiveEntry entry) {
        return entry.getName().endsWith(".sh")
            || entry.getName().endsWith(".so")
            || entry.getName().endsWith(".dylib")
            || entry.getName().endsWith("jattach");
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
