package org.gradle.profiler;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BuildMutatorFactory implements Supplier<BuildMutator> {
    private final List<? extends Supplier<BuildMutator>> factories;

    public BuildMutatorFactory(List<? extends Supplier<BuildMutator>> factories) {
        this.factories = factories;
    }

    @Override
    public BuildMutator get() {
        if (factories.isEmpty()) {
            return new NoOpMutator();
        }
        if (factories.size() == 1) {
            return factories.get(0).get();
        }
        List<BuildMutator> mutators = factories.stream().map(s -> s.get()).collect(Collectors.toList());
        return new CompositeBuildMutator(mutators);
    }

    @Override
    public String toString()
    {
        return get().toString();
    }

    private static class NoOpMutator implements BuildMutator {
        @Override
        public void beforeBuild() throws IOException {
        }

        @Override
        public void cleanup() throws IOException {
        }

        @Override
        public String toString()
        {
            return "NONE";
        }
    }

    private static class CompositeBuildMutator implements BuildMutator {
        private final List<BuildMutator> mutators;

        CompositeBuildMutator(List<BuildMutator> mutators) {
            this.mutators = mutators;
        }

        @Override
        public void beforeBuild() throws IOException {
            for (BuildMutator mutator : mutators) {
                mutator.beforeBuild();
            }
        }

        @Override
        public void cleanup() throws IOException {
            for (BuildMutator mutator : mutators) {
                mutator.cleanup();
            }
        }

        @Override
        public String toString()
        {
            return mutators.toString();
        }
    }
}
