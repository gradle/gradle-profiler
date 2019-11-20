package org.gradle.profiler;

import java.util.List;
import java.util.stream.Collectors;

public class CompositeBuildMutator implements BuildMutator {
	private final List<BuildMutator> mutators;

	public CompositeBuildMutator(List<BuildMutator> mutators) {
		this.mutators = mutators;
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
		for (BuildMutator mutator : mutators) {
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
		for (BuildMutator mutator : mutators) {
			mutator.afterBuild(context, error);
		}
	}

	@Override
	public void afterScenario(ScenarioContext context) {
		for (BuildMutator mutator : mutators) {
			mutator.afterScenario(context);
		}
	}

	@Override
	public String toString() {
		return mutators.stream().map(Object::toString).collect(Collectors.joining(", "));
	}
}
