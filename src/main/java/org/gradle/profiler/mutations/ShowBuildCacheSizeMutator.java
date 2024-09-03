package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.ScenarioContext;

import java.io.File;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.stream.Stream;

public class ShowBuildCacheSizeMutator implements BuildMutator {

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
        Stream.of(new File(gradleUserHome, "caches"))
            .map(File::listFiles)
            .filter(Objects::nonNull)
            .flatMap(Stream::of)
            .filter(File::isDirectory)
            .filter(cacheDir -> cacheDir.getName().startsWith("build-cache-"))
            .sorted()
            .forEach(ShowBuildCacheSizeMutator::showCacheSize);
    }

    private static void showCacheSize(File cacheDir) {
        File[] cacheFiles = cacheDir.listFiles();
        if (cacheFiles == null) {
            return;
        }
        long size = Stream.of(cacheFiles)
            .map(File::length)
            .reduce(Long::sum)
            .orElse(0L);
        System.out.println(MessageFormat.format("> Build cache size: {0,number} bytes in {1,number} file(s) ({2})", size, cacheFiles.length, cacheDir.getName()));
    }

    public static class Configurator extends AbstractBuildMutatorWithoutOptionsConfigurator {
        @Override
        BuildMutator createBuildMutator(BuildMutatorConfiguratorSpec spec) {
            return new ShowBuildCacheSizeMutator(spec.getGradleUserHome());
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
