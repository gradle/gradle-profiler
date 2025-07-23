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
package org.gradle.profiler.buildscan;

import org.gradle.profiler.GeneratedInitScript;

import java.io.PrintWriter;

/**
 * An init script to set up Gradle Enterprise plugin dependency and apply it, used for Gradle 6+.
 */
public class DevelocityInitScript extends GeneratedInitScript {

    static final String PUBLISH_AND_TAG = "" +
        "        background {\n" +
        "            publishing {\n" +
        "                onlyIf { System.getProperty('org.gradle.profiler.phase') == 'MEASURE' }\n" +
        "            }\n" +
        "        }\n" +
        "        tag('GRADLE_PROFILER')\n";

    private final String version;

    public DevelocityInitScript(String version) {
        this.version = version;
    }

    @Override
    public void writeContents(final PrintWriter writer) {
        writer.write("initscript {\n");
        writer.write("    repositories {\n");
        writer.write("      gradlePluginPortal()\n");
        writer.write("    }\n");
        writer.write("    dependencies {\n");
        writer.write("        classpath(\"com.gradle.develocity:com.gradle.develocity.gradle.plugin:" + version + "\")\n");
        writer.write("    }\n");
        writer.write("}\n");
        writer.write("\n");
        writer.write("settingsEvaluated {\n");
        writer.write("    if (!it.pluginManager.hasPlugin(\"com.gradle.develocity\")) {\n");
        writer.write("        it.pluginManager.apply(com.gradle.develocity.agent.gradle.DevelocityPlugin)\n");
        writer.write("    }\n");
        writer.write("    it.extensions[\"develocity\"].buildScan.with {\n");
        writer.write("        termsOfUseUrl = 'https://gradle.com/help/legal-terms-of-use'\n");
        writer.write("        termsOfUseAgree = 'yes'\n");
        writer.write(PUBLISH_AND_TAG);
        writer.write("    }\n");
        writer.write("}\n");
    }


}
