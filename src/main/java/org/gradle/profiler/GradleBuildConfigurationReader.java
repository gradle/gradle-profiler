package org.gradle.profiler;

public interface GradleBuildConfigurationReader {
	GradleBuildConfiguration readConfiguration();
	GradleBuildConfiguration readConfiguration(String gradleVersion);
}
