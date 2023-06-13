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
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public abstract class GeneratedInitScript implements GradleArgsCalculator {
    private final File initScript;
    private boolean generated;

    public GeneratedInitScript() {
        try {
            initScript = File.createTempFile("gradleProfiler" + getClass().getSimpleName(), ".gradle").getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        initScript.deleteOnExit();
    }

    protected abstract void writeContents(PrintWriter writer);

    private void maybeGenerateInitScript() {
        if (generated) {
            return;
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(initScript))) {
            writeContents(writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        generated = true;
    }

    @Override
    public void calculateGradleArgs(List<String> gradleArgs) {
        maybeGenerateInitScript();
        gradleArgs.add("-I");
        gradleArgs.add(initScript.getAbsolutePath());
    }
}
