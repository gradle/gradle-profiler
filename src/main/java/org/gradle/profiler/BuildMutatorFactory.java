package org.gradle.profiler;

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
        List<BuildMutator> mutators = factories.stream().map(Supplier::get).collect(Collectors.toList());
        return new CompositeBuildMutator(mutators);
    }

    @Override
    public String toString()
    {
        return get().toString();
    }

    private static class NoOpMutator implements BuildMutator {
        @Override
        public String toString() {
            return "none";
        }
    }

    private static class CompositeBuildMutator implements BuildMutator {
        private final List<BuildMutator> mutators;

        CompositeBuildMutator(List<BuildMutator> mutators) {
            this.mutators = mutators;
        }

        @Override
        public void beforeScenario() {
            for (BuildMutator mutator : mutators) {
                mutator.beforeScenario();
            }
        }

        @Override
        public void beforeCleanup() {
            for (BuildMutator mutator : mutators) {
                mutator.beforeCleanup();
            }
        }

        @Override
        public void afterCleanup(Throwable error) {
            for (BuildMutator mutator : mutators) {
                mutator.afterCleanup(error);
            }
        }

        @Override
        public void beforeBuild() {
            for (BuildMutator mutator : mutators) {
                mutator.beforeBuild();
            }
        }

        @Override
        public void afterBuild(Throwable error) {
            for (BuildMutator mutator : mutators) {
                mutator.afterBuild(error);
            }
        }

        @Override
        public void afterScenario() {
            for (BuildMutator mutator : mutators) {
                mutator.afterScenario();
            }
        }

        @Override
        public String toString()
        {
            return mutators.stream().map(Object::toString).collect(Collectors.joining(", "));
        }
    }
}
