package org.gradle.profiler.ide.idea;

import com.intellij.icons.ExpUiIcons;
import com.intellij.ide.starter.community.PublicIdeDownloader;
import com.intellij.ide.starter.community.model.BuildType;
import com.intellij.ide.starter.ide.IdeArchiveExtractor;
import com.intellij.ide.starter.ide.IdeDownloader;
import com.intellij.ide.starter.ide.installer.IdeInstallerFile;
import com.intellij.ide.starter.models.IdeInfo;
import org.gradle.profiler.ide.IdeProvider;
import org.gradle.profiler.ide.UnpackUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;

public class IDEAProvider implements IdeProvider<IDEA> {
    private final IdeDownloader ideDownloader;
    private final IdeArchiveExtractor ideArchiveExtractor;

    public IDEAProvider() {
        this.ideDownloader = PublicIdeDownloader.INSTANCE;
        this.ideArchiveExtractor = IdeArchiveExtractor.INSTANCE;
    }

    @Override
    public File provideIde(IDEA ide, Path homeDir, Path downloadsDir) {
        IdeInfo ideInfo = new IdeInfo(
            "IC",
            "Idea",
            "idea",
            ide.getBuildType(),
            Collections.emptyList(),
            "", // latest
            ide.getVersion(),
            null,
            null,
            "IDEA Community",
            info -> null
        );

        String version = ideInfo.getVersion().isEmpty()
            ? "latest"
            : ideInfo.getVersion();

        File unpackDir = homeDir
            .resolve(ideInfo.getInstallerFilePrefix())
            .resolve(version)
            .toFile();

        File unpackedIde = UnpackUtils.getSingleFileFrom(unpackDir);

        if (unpackedIde != null) {
            System.out.println("Downloading is skipped, get " + ideInfo.getFullName() + " from cache");
            return unpackedIde;
        }

        IdeInstallerFile installerFile = ideDownloader.downloadIdeInstaller(ideInfo, downloadsDir);
        ideArchiveExtractor.unpackIdeIfNeeded(installerFile.getInstallerFile().toFile(), unpackDir);

        return UnpackUtils.getSingleFileFrom(unpackDir);
    }
}
