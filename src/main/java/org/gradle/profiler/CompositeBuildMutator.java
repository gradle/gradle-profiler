package org.gradle.profiler;

import java.util.List;
import java.util.stream.Collectors;

public class CompositeBuildMutator implements BuildMutator {
	private final List<BuildMutator> mutators;

	public CompositeBuildMutator(List<BuildMutator> mutators) {
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
	public String toString() {
		return mutators.stream().map(Object::toString).collect(Collectors.joining(", "));
	}
}
