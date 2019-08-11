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
        List<BuildMutator> mutators = factories.stream()
            .map(Supplier::get)
            .filter(it -> it != BuildMutator.NOOP)
            .collect(Collectors.toList());
        if (mutators.isEmpty()) {
            return BuildMutator.NOOP;
        }
        if (mutators.size() == 1) {
            return mutators.get(0);
        }
        return new CompositeBuildMutator(mutators);
    }

    @Override
    public String toString()
    {
        return get().toString();
    }
}
