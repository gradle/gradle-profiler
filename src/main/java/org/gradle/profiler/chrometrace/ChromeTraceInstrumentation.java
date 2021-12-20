/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.profiler.chrometrace;

import org.gradle.profiler.ScenarioSettings;
import org.gradle.profiler.instrument.GradleInstrumentation;

import java.io.File;
import java.io.PrintWriter;

public class ChromeTraceInstrumentation extends GradleInstrumentation {
    private final File traceFolder;
    private final String traceFileBaseName;

    public ChromeTraceInstrumentation(ScenarioSettings scenarioSettings) {
        traceFolder = scenarioSettings.profilerOutputLocationFor("-trace");
        traceFolder.mkdirs();
        traceFileBaseName = scenarioSettings.getProfilerOutputBaseName();
    }

    @Override
    protected void generateInitScriptBody(PrintWriter writer) {
        writer.println("org.gradle.trace.GradleTracingPlugin.start(gradle, new File(new URI(\"" + traceFolder.toURI() + "\")), \"" + traceFileBaseName + "\")");
    }
}
