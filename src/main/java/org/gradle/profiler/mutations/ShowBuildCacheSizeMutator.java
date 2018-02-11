package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.text.MessageFormat;
import java.util.function.Supplier;

public class ShowBuildCacheSizeMutator implements BuildMutator {

	private final File gradleUserHome;
	private boolean enabled;

	public ShowBuildCacheSizeMutator(File gradleUserHome, boolean enabled) {
		this.gradleUserHome = gradleUserHome;
		this.enabled = enabled;
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
		if (!enabled) {
			return;
		}
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

	@Override
	public String toString() {
		return getClass().getSimpleName() + "(enabled = " + enabled + ")";
	}

	public static class Configurator implements BuildMutatorConfigurator {

		private final File gradleUserHome;

		public Configurator(File gradleUserHome) {
			this.gradleUserHome = gradleUserHome;
		}

		@Override
		public Supplier<BuildMutator> configure(Config scenario, String scenarioName, File projectDir, String key) {
			boolean enabled = scenario.getBoolean(key);
			return () -> new ShowBuildCacheSizeMutator(gradleUserHome, enabled);
		}
	}
}
