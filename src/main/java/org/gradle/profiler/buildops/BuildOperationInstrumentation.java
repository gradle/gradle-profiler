package org.gradle.profiler.buildops;

import org.gradle.profiler.GradleInstrumentation;

import java.io.PrintWriter;

public class BuildOperationInstrumentation extends GradleInstrumentation {
    @Override
    protected String getJarBaseName() {
        return "build-operations";
    }

    @Override
    protected void generateInitScriptBody(PrintWriter writer) {
        writer.println("org.gradle.trace.buildops.BuildOperationTrace.start(gradle)");
    }
}
