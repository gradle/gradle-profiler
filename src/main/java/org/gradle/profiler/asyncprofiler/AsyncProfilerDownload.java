package org.gradle.profiler.asyncprofiler;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.gradle.profiler.Logging;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Enumeration;
import java.util.Set;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Downloads and unpacks Async Profiler artifacts.
 */
public class AsyncProfilerDownload {

    /**
     * Downloads a distribution of Async Profiler and installs into {@code ~/.gradle-profiler-dist}.
     * <p>
     * Uses a previously downloaded installation, if available.
     *
     * @return the home directory of the unpacked Async Profiler distribution
     */
    static File forPlatform(AsyncProfilerPlatform platform, String version) {
        String platformName = platform.getPlatformName();
        String extension = platform.getDownloadExtension();

        Path installDist = Paths.get(System.getProperty("user.home"), ".gradle-profiler-dist", version + "-" + platformName);
        Path marker = installDist.resolve("ok");
        Path homeDir = installDist.resolve(String.format("async-profiler-%s-%s", version, platformName));

        if (Files.isReadable(marker)) {
            return homeDir.toFile();
        }

        try {
            URL download = new URL(String.format(
                "https://github.com/async-profiler/async-profiler/releases/download/v%1$s/async-profiler-%1$s-%2$s.%3$s",
                version,
                platformName,
                extension
            ));
            Logging.startOperation("Download and install " + download);

            Files.createDirectories(installDist);
            Path bundle = installDist.resolve(String.format("async-profiler.%s", extension));
            copyTo(download, bundle);

            deleteDir(homeDir);
            if (extension.equals("tar.gz")) {
                untarTo(bundle, installDist);
            } else {
                unzipTo(bundle, installDist);
            }

            Files.createFile(marker);
        } catch (IOException e) {
            throw new RuntimeException("Could not install async-profiler", e);
        }

        return homeDir.toFile();
    }

    private static void unzipTo(Path source, Path destDir) throws IOException {
        // NOTE: streaming zip files prevent to read unix file permissions, one need to use the ZipFile API instead
        try (final ZipFile zipFile = ZipFile.builder().setPath(source).get()) {
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                try (InputStream entryInputStream = zipFile.getInputStream(entry)) {
                    extractEntry(destDir, entryInputStream, entry);
                }
            }
        }
    }

    private static void untarTo(Path source, Path destDir) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(source));
             GzipCompressorInputStream gzipIs = new GzipCompressorInputStream(bis);
             TarArchiveInputStream tarStream = new TarArchiveInputStream(gzipIs)) {
            ArchiveEntry entry;
            while ((entry = tarStream.getNextEntry()) != null) {
                extractEntry(destDir, tarStream, entry);
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

    private static void copyTo(URL source, Path dest) throws IOException {
        try (InputStream inputStream = new BufferedInputStream(source.openConnection().getInputStream())) {
            Files.copy(inputStream, dest, REPLACE_EXISTING);
        }
    }

    private static void deleteDir(Path homeDir) throws IOException {
        if (Files.isDirectory(homeDir)) {
            Files.walkFileTree(homeDir, new SimpleFileVisitor<Path>() {
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
