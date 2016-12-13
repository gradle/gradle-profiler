package org.gradle.profiler;

import org.eclipse.jgit.api.ApplyCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.*;

public class GitRepository {
    private final File projectDir;

    public GitRepository(File projectDir) {
        this.projectDir = projectDir;
    }

    public void add(String path) {
        try {
            withRepo(git -> git.add().addFilepattern(path).call());
        } catch (Exception e) {
            throw new RuntimeException("Could not add file " + path, e);
        }
    }

    public void commit() {
        try {
            withRepo(git -> git.commit().setMessage("commit").call());
        } catch (Exception e) {
            throw new RuntimeException("Could not commit", e);
        }
    }

    public void diff(File patchFile) {
        try {
            withRepo(git -> {
                ByteArrayOutputStream diff = new ByteArrayOutputStream();
                git.diff().setOutputStream(diff).call();
                try (FileOutputStream outputStream = new FileOutputStream(patchFile)) {
                    outputStream.write(diff.toByteArray());
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Could not create patch file " + patchFile, e);
        }
    }

    public void apply(File patchFile) {
        try {
            withRepo(git -> {
                try (FileInputStream patchInputStream = new FileInputStream(patchFile)) {
                    ApplyCommand apply = git.apply();
                    apply.setPatch(patchInputStream);
                    apply.call();
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Could not apply patch file " + patchFile, e);
        }
    }

    public void reset() {
        try {
            withRepo(git -> git.reset().setMode(ResetCommand.ResetType.HARD).call());
        } catch (Exception e) {
            throw new RuntimeException("Could not revert changes", e);
        }
    }

    private void withRepo(GitAction action) throws IOException, GitAPIException {
        try (Git git = Git.init().setDirectory(projectDir).call()) {
            action.run(git);
        }
    }

    interface GitAction {
        void run(Git git) throws GitAPIException, IOException;
    }
}
