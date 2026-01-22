package org.gradle.profiler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.stream.Collectors;

public class CompositeBuildMutator implements BuildMutator {
	private final List<BuildMutator> mutators;

	private CompositeBuildMutator(List<BuildMutator> mutators) {
		this.mutators = ImmutableList.copyOf(mutators);
	}

    public static BuildMutator from(List<BuildMutator> mutators) {
        switch (mutators.size()) {
            case 0:
                return BuildMutator.NOOP;
            case 1:
                return mutators.get(0);
            default:
                return new CompositeBuildMutator(mutators);
        }
    }

    @Override
	public void beforeScenario(ScenarioContext context) {
		for (BuildMutator mutator : mutators) {
			mutator.beforeScenario(context);
		}
	}

	@Override
	public void beforeCleanup(BuildContext context) {
		for (BuildMutator mutator : mutators) {
			mutator.beforeCleanup(context);
		}
	}

	@Override
	public void afterCleanup(BuildContext context, Throwable error) {
		for (BuildMutator mutator : Lists.reverse(mutators)) {
			mutator.afterCleanup(context, error);
		}
	}

	@Override
	public void beforeBuild(BuildContext context) {
		for (BuildMutator mutator : mutators) {
			mutator.beforeBuild(context);
		}
	}

	@Override
	public void afterBuild(BuildContext context, Throwable error) {
		for (BuildMutator mutator : Lists.reverse(mutators)) {
			mutator.afterBuild(context, error);
		}
	}

	@Override
	public void afterScenario(ScenarioContext context) {
		for (BuildMutator mutator : Lists.reverse(mutators)) {
			mutator.afterScenario(context);
		}
	}

	@Override
	public String toString() {
		return mutators.stream().map(Object::toString).collect(Collectors.joining(", "));
	}
}
