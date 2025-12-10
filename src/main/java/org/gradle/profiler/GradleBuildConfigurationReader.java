package org.gradle.profiler;

import javax.annotation.Nullable;
import java.io.File;

public interface GradleBuildConfigurationReader {
	GradleBuildConfiguration readConfiguration(@Nullable File javaHome);
	GradleBuildConfiguration readConfiguration(String gradleVersion, @Nullable File javaHome);
}
