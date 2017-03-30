package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.io.IOException;

import static java.lang.ProcessBuilder.Redirect.INHERIT;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

public class ApplyPatchProjectMutator implements BuildMutator {
	private final File projectDir;
	private final File patchFile;
	private boolean applied;

	public ApplyPatchProjectMutator(File projectDir, File patchFile) {
		this.projectDir = projectDir;
		this.patchFile = patchFile;
	}

	@Override
	public void beforeBuild() throws IOException {
		if (!applied) {
			execute(projectDir, patchFile, "patch", "-p0");
			applied = true;
		}
	}

	@Override
	public void cleanup() throws IOException {
		if (applied) {
			execute(projectDir, patchFile, "patch", "-R", "-p0");
			applied = false;
		}
	}

	private static void execute(File directory, File patchFile, String... commandLine) throws IOException {
		try {
			int exitValue = new ProcessBuilder(commandLine)
					.directory(directory)
					.redirectInput(patchFile)
					.redirectOutput(INHERIT)
					.redirectError(INHERIT)
					.start()
					.waitFor();
			if (exitValue != 0) {
				throw new IOException(format("Command returned %d: %s", exitValue, stream(commandLine).collect(joining(" "))));
			}
		} catch (InterruptedException e) {
			throw new IOException(format("Could not execute %s", stream(commandLine).collect(joining(" "))));
		}
	}
}
