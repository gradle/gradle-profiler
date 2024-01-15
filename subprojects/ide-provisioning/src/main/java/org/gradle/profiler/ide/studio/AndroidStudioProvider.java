package org.gradle.profiler.ide.studio;

import com.intellij.ide.starter.community.IdeByLinkDownloader;
import com.intellij.ide.starter.ide.IdeArchiveExtractor;
import com.intellij.ide.starter.ide.IdeDownloader;
import com.intellij.ide.starter.ide.installer.IdeInstallerFile;
import com.intellij.ide.starter.models.IdeInfo;
import com.intellij.ide.starter.utils.HttpClient;
import org.gradle.profiler.ide.IdeProvider;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;

public class AndroidStudioProvider implements IdeProvider<AndroidStudio> {
    private final IdeArchiveExtractor ideArchiveExtractor = IdeArchiveExtractor.INSTANCE;

    @Override
    public File provideIde(AndroidStudio ide, Path homeDir, Path downloadsDir) {
        IdeInfo ideInfo = new IdeInfo(
            "",
            "",
            "",
            "",
            Collections.emptyList(),
            "build",
            ide.getVersion(),
            "",
            getStudioDownloadUrl(ide),


        );

        String version = ide.getVersion().isEmpty()
            ? "latest"
            : ide.getVersion();

        File unpackDir = homeDir
            .resolve("androidStudio")
            .resolve(version)
            .toFile();

        File unpackedIde = getUnpackedIde(unpackDir);

        if (unpackedIde != null) {
            System.out.println("Downloading is skipped, get AndroidStudio from cache");
            return unpackedIde;
        }

        IdeInstallerFile installerFile = ideDownloader.downloadIdeInstaller(ideInfo, downloadsDir);

//        val extension = when {
//            BuildEnvironment.isWindows -> "windows.zip"
//            BuildEnvironment.isMacOsX && BuildEnvironment.isIntel -> "mac.zip"
//            BuildEnvironment.isMacOsX && !BuildEnvironment.isIntel -> "mac_arm.zip"
//            BuildEnvironment.isLinux -> "linux.tar.gz"
//                    else -> throw IllegalStateException("Unsupported OS: ${OperatingSystem.current()}")
//        }


//        val downloadUrl = "https://redirector.gvt1.com/edgedl/android/studio/ide-zips/2021.1.1.11/android-studio-2021.1.1.11$ext"
//        IdeArchiveExtractor.unpackIdeIfNeeded(installerFile, installDir.toFile())
//        val installationPath = when (!SystemInfo.isMac) {
//            true -> installDir.resolve("android-studio")
//            false -> installDir
//        }

        return null;
    }

    private static String getStudioDownloadUrl(AndroidStudio studio) {
        String extension = "foo";
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
