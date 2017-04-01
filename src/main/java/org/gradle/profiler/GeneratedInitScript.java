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
import java.util.Arrays;
import java.util.List;

public abstract class GeneratedInitScript {
    private final File initScript;

    public GeneratedInitScript() {
        try {
            initScript = File.createTempFile("gradleProfiler" + getClass().getSimpleName(), ".gradle");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        initScript.deleteOnExit();
    }

    protected abstract void writeContents(PrintWriter writer);

    private void generateInitScript(){
        try (PrintWriter writer = new PrintWriter(new FileWriter(initScript))) {
            writeContents(writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public final List<String> getArgs() {
        generateInitScript();
        return Arrays.asList("-I", initScript.getAbsolutePath());
    }
}
