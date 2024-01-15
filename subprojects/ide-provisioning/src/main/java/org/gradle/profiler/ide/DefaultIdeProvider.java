package org.gradle.profiler.ide;

import kotlin.io.FilesKt;
import org.gradle.profiler.ide.idea.IDEA;
import org.gradle.profiler.ide.idea.IDEAProvider;

import java.io.File;
import java.nio.file.Path;

public class DefaultIdeProvider implements IdeProvider<Ide> {

    private final IDEAProvider ideaProvider;

    public DefaultIdeProvider(IDEAProvider ideaProvider) {
        this.ideaProvider = ideaProvider;
    }

    @Override
    public File provideIde(Ide ide, Path homeDir, Path downloadsDir) {
        File result;
        if (ide instanceof IDEA) {
            result = ideaProvider.provideIde((IDEA) ide, homeDir, downloadsDir);
        } else {
            throw new IllegalArgumentException("Unknown IDE to provide");
        }

        cleanup(homeDir, downloadsDir);
        return result;
    }

    private void cleanup(Path homeDir, Path downloadsDir) {
        FilesKt.deleteRecursively(downloadsDir.toFile());
    }
}
