package org.gradle.profiler;

public interface GradleVersionInspector {
	GradleVersion resolve(String versionString);
	GradleVersion defaultVersion();
}
