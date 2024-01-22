package org.gradle.profiler.ide;

import org.gradle.profiler.ide.idea.IDEA;
import org.gradle.profiler.ide.studio.AndroidStudio;

import java.io.File;
import java.nio.file.Path;

public class IdeProviderCompatibilityLayerImpl {

    private final IdeProvider<Ide> ideProvider = new DefaultIdeProvider();

    public File provideIde(String ideType, String ideVersion, Path homeDir, Path downloadsDir) {
        Ide ide;
        if (ideType.equals("IC")) {
            ide = new IDEA(ideVersion);
        } else if (ideType.equals("AI")) {
            ide = new AndroidStudio(ideVersion);
        } else {
            throw new IllegalArgumentException("Unknown IDE product was requested");
        }
        return ideProvider.provideIde(ide, homeDir, downloadsDir);
    }
}
