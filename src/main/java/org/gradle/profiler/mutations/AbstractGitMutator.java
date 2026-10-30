package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.CommandExec;

import java.io.File;

public abstract class AbstractGitMutator implements BuildMutator {
	protected final File projectDir;

	public AbstractGitMutator(File projectDir) {
		this.projectDir = projectDir;
	}

	/**
	 * Fails when tracked files have staged or unstaged changes, so that profiling never discards local work.
	 * Untracked files are not considered.
	 */
	protected void requireNoLocalChanges(String operation) {
		int exitCode = new CommandExec().inDir(projectDir)
			.start("git", "diff", "--quiet", "--ignore-submodules=none", "HEAD", "--")
			.waitForExitCode();
		if (exitCode == 1) {
			throw new IllegalStateException("Cannot start Git " + operation + " profiling with local changes. Commit, stash, or deliberately reset your changes and retry.");
		}
		if (exitCode != 0) {
			throw new IllegalStateException("Could not check " + projectDir + " for local Git changes: git diff exited with code " + exitCode + ".");
		}
	}

	protected void resetGit() {
		System.out.println("> Resetting Git hard");
		new CommandExec().inDir(projectDir).run("git", "reset", "--hard", "HEAD");
		new CommandExec().inDir(projectDir).run("git", "status");
	}
}
