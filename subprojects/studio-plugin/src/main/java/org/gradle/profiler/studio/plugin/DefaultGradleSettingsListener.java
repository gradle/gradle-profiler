package org.gradle.profiler.studio.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener;
import org.jetbrains.plugins.gradle.settings.TestRunner;

import java.util.Collection;
import java.util.Set;

/**
 * A default implementation of GradleSettingsListener that does nothing.
 */
public class DefaultGradleSettingsListener implements GradleSettingsListener {
    @Override
    public void onProjectsLinked(@NotNull Collection<GradleProjectSettings> settings) {
    }

    @Override
    public void onGradleHomeChange(@Nullable String oldPath, @Nullable String newPath, @NotNull String linkedProjectPath) {
    }

    @Override
    public void onGradleDistributionTypeChange(DistributionType currentValue, @NotNull String linkedProjectPath) {
    }

    @Override
    public void onServiceDirectoryPathChange(@Nullable String oldPath, @Nullable String newPath) {
    }

    @Override
    public void onGradleVmOptionsChange(@Nullable String oldOptions, @Nullable String newOptions) {
    }

    @Override
    public void onBuildDelegationChange(boolean delegatedBuild, @NotNull String linkedProjectPath) {
    }

    @Override
    public void onTestRunnerChange(@NotNull TestRunner currentTestRunner, @NotNull String linkedProjectPath) {
    }

    @Override
    public void onProjectRenamed(@NotNull String oldName, @NotNull String newName) {
    }

    @Override
    public void onProjectsUnlinked(@NotNull Set<String> linkedProjectPaths) {
    }

    @Override
    public void onBulkChangeStart() {
    }

    @Override
    public void onBulkChangeEnd() {
    }
}
