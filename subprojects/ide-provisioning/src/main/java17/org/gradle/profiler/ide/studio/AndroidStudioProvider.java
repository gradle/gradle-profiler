package org.gradle.profiler.ide.studio;

import com.intellij.ide.starter.ide.IdeArchiveExtractor;
import com.intellij.ide.starter.utils.HttpClient;
import com.intellij.openapi.util.SystemInfo;
import org.gradle.profiler.ide.IdeProvider;
import org.gradle.profiler.ide.UnpackUtils;

import java.io.File;
import java.nio.file.Path;

public class AndroidStudioProvider implements IdeProvider<AndroidStudio> {
    private final IdeArchiveExtractor ideArchiveExtractor = IdeArchiveExtractor.INSTANCE;
    private final HttpClient httpClient = HttpClient.INSTANCE;

    @Override
    public File provideIde(AndroidStudio ide, Path homeDir, Path downloadsDir) {
        if (ide.getVersion().isEmpty()) {
            throw new IllegalArgumentException("Android Studio version must be specified");
        }

        String extension = getExtension();
        String version = ide.getVersion();
        File unpackDir = homeDir
            .resolve("androidStudio")
            .resolve(version)
            .toFile();

        File unpackedIde = UnpackUtils.getSingleFileFrom(unpackDir);

        if (unpackedIde != null) {
            System.out.println("Downloading is skipped, get Android Studio from cache");
            return unpackedIde;
        }

        File installer = downloadsDir
            .resolve("androidStudio" + version + extension)
            .toFile();

        httpClient.download(getStudioDownloadUrl(ide, extension), installer, 3);
        ideArchiveExtractor.unpackIdeIfNeeded(installer, unpackDir);

        return UnpackUtils.getSingleFileFrom(unpackDir);
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
}
