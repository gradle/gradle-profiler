package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildMutator;

import java.io.File;
import java.util.List;

import static java.util.Collections.singletonList;

public class ClearGradleUserHomeMutator extends ClearDirectoryMutator {

    // Don't delete the wrapper dir, since this is where the Gradle distribution we are going to run is located
    private static final List<String> keepFiles = singletonList("wrapper");

    public ClearGradleUserHomeMutator(File gradleUserHome, Schedule schedule) {
        super("Gradle User Home directory", gradleUserHome, schedule, keepFiles);
    }

    public static class Configurator extends AbstractScheduledMutator.Configurator {
        @Override
        protected BuildMutator newInstance(BuildMutatorConfiguratorSpec spec, String key, Schedule schedule) {
            return new ClearGradleUserHomeMutator(spec.getGradleUserHome(), schedule);
        }
    }
}
