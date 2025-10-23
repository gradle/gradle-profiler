package org.gradle.profiler.asyncprofiler;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.gradle.profiler.Logging;
import org.gradle.profiler.OperatingSystem;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Enumeration;
import java.util.Set;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Downloads a version of async profiler and installs into ~/.gradle-profiler-dist.
 */
public class AsyncProfilerDownload {
    private static final String ASYNC_PROFILER_VERSION = "4.2";

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
        } else if (OperatingSystem.isLinuxAarch64()) {
            platformName = "linux-arm64";
            extension = "tar.gz";
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
            URL download = new URL(String.format(
                "https://github.com/async-profiler/async-profiler/releases/download/v%1$s/async-profiler-%1$s-%2$s.%3$s",
                ASYNC_PROFILER_VERSION,
                platformName,
                extension
            ));
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
        // NOTE: streaming zip files prevent to read unix file permissions, one need to use the ZipFile API instead
        try (final ZipFile zipFile = ZipFile.builder().setFile(source).get()) {
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                try (InputStream entryInputStream = zipFile.getInputStream(entry)) {
                    extractEntry(destDir.toPath(), entryInputStream, entry);
                }
            }
        }
    }

    private static void untarTo(File source, File destDir) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(source.toPath()));
             GzipCompressorInputStream gzipIs = new GzipCompressorInputStream(bis);
             TarArchiveInputStream tarStream = new TarArchiveInputStream(gzipIs)) {
            ArchiveEntry entry;
            while ((entry = tarStream.getNextEntry()) != null) {
                extractEntry(destDir.toPath(), tarStream, entry);
            }
        }
    }

    private static void extractEntry(Path destDir, InputStream inputStream, ArchiveEntry entry) throws IOException {
        if (entry.isDirectory()) {
            return;
        }

        Path file = destDir.resolve(entry.getName()).normalize();
        if (!file.startsWith(destDir)) {
            // Ignore files outside destination dir
            return;
        }
        Files.createDirectories(file.getParent());
        Files.copy(inputStream, file, StandardCopyOption.REPLACE_EXISTING);

        applyExecutablePermissions(getUnixMode(entry), file);
    }

    @SuppressWarnings("OctalInteger")
    private static void applyExecutablePermissions(int mode, Path file) throws IOException {
        if (mode != 0) {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
            ImmutableSet.Builder<PosixFilePermission> withExecute = ImmutableSet.builder();
            withExecute.addAll(permissions);
            if ((mode & 0100) != 0) {
                withExecute.add(PosixFilePermission.OWNER_EXECUTE);
            }
            if ((mode & 0010) != 0) {
                withExecute.add(PosixFilePermission.GROUP_EXECUTE);
            }
            if ((mode & 0001) != 0) {
                withExecute.add(PosixFilePermission.OTHERS_EXECUTE);
            }
            Files.setPosixFilePermissions(file, withExecute.build());
        }
    }

    private static int getUnixMode(ArchiveEntry entry) {
        if (entry instanceof TarArchiveEntry) {
            return ((TarArchiveEntry) entry).getMode();
        } else if (entry instanceof ZipArchiveEntry) {
            return ((ZipArchiveEntry) entry).getUnixMode();
        } else {
            return 0;
        }
    }

    private static void copyTo(URL source, File dest) throws IOException {
        try (InputStream inputStream = new BufferedInputStream(source.openConnection().getInputStream())) {
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
