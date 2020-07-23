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
package org.gradle.profiler;

import java.io.File;
import java.util.function.Consumer;

public class Profiler {

    public static final Profiler NONE = new Profiler() {
        @Override
        public String toString() {
            return "none";
        }
    };

    public void validate(ScenarioSettings settings, Consumer<String> reporter) {
    }

    public ProfilerController newController(String pid, ScenarioSettings settings) {
        return ProfilerController.EMPTY;
    }

    /**
     * Returns a calculator that provides JVM args that should be applied to all builds, including warm-up builds.
     */
    public JvmArgsCalculator newJvmArgsCalculator(ScenarioSettings settings) {
        return JvmArgsCalculator.DEFAULT;
    }

    /**
     * Returns a calculator that provides JVM args that should be applied to instrumented builds, but not warm-up builds.
     */
    public JvmArgsCalculator newInstrumentedBuildsJvmArgsCalculator(ScenarioSettings settings) {
        return JvmArgsCalculator.DEFAULT;
    }

    /**
     * Returns a calculator that provides Gradle args that should be applied to all builds, including warm-up builds.
     */
    public GradleArgsCalculator newGradleArgsCalculator(ScenarioSettings settings) {
        return GradleArgsCalculator.DEFAULT;
    }

    /**
     * Returns a calculator that provides Gradle args that should be applied to instrumented builds, but not warm-up builds.
     */
    public GradleArgsCalculator newInstrumentedBuildsGradleArgsCalculator(ScenarioSettings settings) {
        return GradleArgsCalculator.DEFAULT;
    }

    /**
     * Describe the given file, if recognized and should be reported to the user.
     */
    public void summarizeResultFile(File resultFile, Consumer<String> consumer) {
    }
}
