package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.ConfigUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

public class ClearBuildCacheMutator implements BuildMutator {

	private final File gradleUserHome;
	private CleanupSchedule schedule;

	public ClearBuildCacheMutator(File gradleUserHome, CleanupSchedule schedule) {
		this.gradleUserHome = gradleUserHome;
		this.schedule = schedule;
	}

	@Override
	public void beforeBuild() {
		if (schedule == CleanupSchedule.BUILD) {
			cleanCacheDirs();
		}
	}

	@Override
	public void beforeScenario() throws IOException {
		if (schedule == CleanupSchedule.SCENARIO) {
			cleanCacheDirs();
		}
	}

	@Override
	public void beforeCleanup() throws IOException {
		if (schedule == CleanupSchedule.CLEANUP) {
			cleanCacheDirs();
		}
	}

	private void cleanCacheDirs() {
		System.out.println("> Cleaning build caches in " + gradleUserHome);
		File cachesDir = new File(gradleUserHome, "caches");
		if (cachesDir.isDirectory()) {
			File[] buildCacheDirs = cachesDir.listFiles((File file) -> file.getName().startsWith("build-cache-"));
			if (buildCacheDirs == null) {
				throw new IllegalStateException("Cannot find build cache directories in " + gradleUserHome);
			}
			for (File buildCacheDir : buildCacheDirs) {
                Arrays.stream(Objects.requireNonNull(buildCacheDir.listFiles((file) -> file.getName().length() == 32))).forEach(FileUtils::deleteQuietly);
			}
		}
	}

	public static class Configurator implements BuildMutatorConfigurator {
		private final File gradleUserHome;

		public Configurator(File gradleUserHome) {
			this.gradleUserHome = gradleUserHome;
		}

		@Override
		public Supplier<BuildMutator> configure(Config scenario, String scenarioName, File projectDir, String key) {
			CleanupSchedule schedule = ConfigUtil.enumValue(scenario, key, CleanupSchedule.class, null);
			if (schedule == null) {
				throw new IllegalArgumentException("Schedule for cleanup is not specified");
			}
			return () -> new ClearBuildCacheMutator(gradleUserHome, schedule);
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "(" + schedule + ")";
	}

	public enum CleanupSchedule {
		SCENARIO, CLEANUP, BUILD
	}
}
