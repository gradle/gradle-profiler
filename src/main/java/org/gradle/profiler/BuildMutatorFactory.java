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

}
