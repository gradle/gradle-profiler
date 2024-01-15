package org.gradle.profiler.ide.studio;

import com.intellij.ide.starter.ide.IdeArchiveExtractor;
import com.intellij.ide.starter.utils.HttpClient;
import com.intellij.openapi.util.SystemInfo;
import org.gradle.profiler.ide.IdeProvider;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;

public class AndroidStudioProvider implements IdeProvider<AndroidStudio> {
    private final IdeArchiveExtractor ideArchiveExtractor = IdeArchiveExtractor.INSTANCE;
    private final HttpClient httpClient = HttpClient.INSTANCE;

    @Override
    public File provideIde(AndroidStudio ide, Path homeDir, Path downloadsDir) {
        String extension = getExtension();

        String version = ide.getVersion().isEmpty()
            ? "latest"
            : ide.getVersion();

        File unpackDir = homeDir
            .resolve("androidStudio")
            .resolve(version)
            .toFile();

        File unpackedIde = getUnpackedIde(unpackDir);

        if (unpackedIde != null) {
            System.out.println("Downloading is skipped, get Android Studio from cache");
            return unpackedIde;
        }

        File installer = downloadsDir
            .resolve("androidStudio" + version + extension)
            .toFile();

        httpClient.download(getStudioDownloadUrl(ide, extension), installer, 3);
        ideArchiveExtractor.unpackIdeIfNeeded(installer, unpackDir);

        return unpackDir.listFiles()[0];
    }

    private static String getExtension() {
        if (SystemInfo.isWindows) {
            return "windows.zip";
        } else if (SystemInfo.isLinux) {
            return "linux.tar.gz";
        } else if (SystemInfo.isMac && SystemInfo.OS_ARCH.equals("aarch64")) {
            return "mac_arm.zip";
        } else if (SystemInfo.isMac) {
            return "mac.zip";
        } else {
            throw new IllegalArgumentException("Unknown OS");
        }
    }

    private static String getStudioDownloadUrl(AndroidStudio studio, String extension) {
        return String.format("https://redirector.gvt1.com/edgedl/android/studio/ide-zips/%1$s/android-studio-%1$s-%2$s", studio.getVersion(), extension);
    }

    @Nullable
    private static File getUnpackedIde(File unpackDir) {
        File[] unpackedFiles = unpackDir.listFiles();
        if (unpackedFiles == null || unpackedFiles.length == 0) {
            return null;
        }
        if (unpackedFiles.length == 1) {
            return unpackedFiles[0];
        }
        throw new IllegalStateException("Unexpected content in " + unpackDir);
    }
}
