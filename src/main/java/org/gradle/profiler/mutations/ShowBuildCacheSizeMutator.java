package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.text.MessageFormat;

public class ShowBuildCacheSizeMutator extends AbstractBuildMutator {

	private final File gradleUserHome;

	public ShowBuildCacheSizeMutator(File gradleUserHome) {
		this.gradleUserHome = gradleUserHome;
	}

	@Override
	public void beforeScenario() {
		showCacheSize();
	}

	@Override
	public void afterCleanup(Throwable error) {
		showCacheSize();
	}

	@Override
	public void afterBuild(Throwable error) {
		showCacheSize();
	}

	private void showCacheSize() {
		File cacheDir = new File(new File(gradleUserHome, "caches"), "build-cache-1");
		File[] cacheFiles = cacheDir.listFiles((file) -> file.getName().length() == 32);
		if (cacheFiles == null) {
			System.out.println("> Cannot list cache directory " + cacheDir);
		} else {
			long size = 0;
			for (File cacheFile : cacheFiles) {
				size += cacheFile.length();
			}
			System.out.println(MessageFormat.format("> Build cache size: {0,number} bytes in {1,number} file(s)", size, cacheFiles.length));
		}
	}

	public static class Configurator extends AbstractBuildMutatorWithoutOptionsConfigurator {

		private final File gradleUserHome;

		public Configurator(File gradleUserHome) {
			this.gradleUserHome = gradleUserHome;
		}

        @Override
        BuildMutator createBuildMutator(File projectDir) {
            return new ShowBuildCacheSizeMutator(gradleUserHome);
        }
	}
}
