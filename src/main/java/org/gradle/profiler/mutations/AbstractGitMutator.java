package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.CommandExec;

import java.io.File;

public abstract class AbstractGitMutator implements BuildMutator {
	protected final File projectDir;

	public AbstractGitMutator(File projectDir) {
		this.projectDir = projectDir;
	}

	protected void resetGit() {
		System.out.println("> Resetting Git hard");
		new CommandExec().inDir(projectDir).run("git", "reset", "--hard", "HEAD");
		new CommandExec().inDir(projectDir).run("git", "status");
	}
}
