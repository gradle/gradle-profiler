package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.ScenarioContext;

import java.io.File;
import java.text.MessageFormat;

public class ShowBuildCacheSizeMutator extends AbstractBuildMutator {

	private final File gradleUserHome;

	public ShowBuildCacheSizeMutator(File gradleUserHome) {
		this.gradleUserHome = gradleUserHome;
	}

	@Override
	public void beforeScenario(ScenarioContext context) {
		showCacheSize();
	}

	@Override
	public void afterCleanup(BuildContext context, Throwable error) {
		showCacheSize();
	}

	@Override
	public void afterBuild(BuildContext context, Throwable error) {
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
        @Override
        BuildMutator createBuildMutator(InvocationSettings settings) {
            return new ShowBuildCacheSizeMutator(settings.getGradleUserHome());
        }
	}
}
