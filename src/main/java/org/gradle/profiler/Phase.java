package org.gradle.profiler;

public enum Phase {
	WARM_UP("warm-up"), MEASURE("measured");

	private final String name;

	Phase(String name) {
		this.name = name;
	}

	public String displayBuildNumber(int number) {
		return name + " build #" + number;
	}
}
