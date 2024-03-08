package org.gradle.profiler.ide.studio;

import com.intellij.ide.starter.ide.IdeArchiveExtractor;
import com.intellij.ide.starter.utils.HttpClient;
import com.intellij.openapi.util.SystemInfo;
import org.gradle.profiler.ide.IdeProvider;
import org.gradle.profiler.ide.UnpackUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static com.intellij.ide.starter.di.DiContainerKt.getDi;

public class AndroidStudioProvider implements IdeProvider<AndroidStudio> {
    private final IdeArchiveExtractor ideArchiveExtractor = IdeArchiveExtractor.INSTANCE;
    private final HttpClient httpClient = HttpClient.INSTANCE;

    @Override
    public File provideIde(AndroidStudio ide, Path homeDir, Path downloadsDir) {
        var v = getDi();
        if (ide.getVersion().isEmpty()) {
            throw new IllegalArgumentException("Android Studio version must be specified");
        }

        String extension = getExtension();
        String version = ide.getVersion();
        File unpackDir = homeDir
            .resolve("androidStudio")
            .resolve(version)
            .toFile();

        if (UnpackUtils.isDirNotEmpty(unpackDir)) {
            System.out.println("Downloading is skipped, get Android Studio from cache");
            return unpackDir;
        }

        File installer = downloadsDir
            .resolve("androidStudio" + version + extension)
            .toFile();

        httpClient.download(getStudioDownloadUrl(ide, extension), installer, 3);
        ideArchiveExtractor.unpackIdeIfNeeded(installer, unpackDir);

        Path contents = UnpackUtils.getSingleFileFrom(unpackDir)
            .toPath()
            .resolve("Contents");

        removeLastPathSegment(contents);
        return unpackDir;
    }

    private static void removeLastPathSegment(Path path) {
        Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Can't remove last path segment for " + path);
        }

        Path target = parent.getParent().resolve(path.getFileName());
        try {
            Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
            Files.delete(parent);
        } catch (IOException e) {
            throw new IllegalArgumentException("Can't remove last path segment for " + path);
        }
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
