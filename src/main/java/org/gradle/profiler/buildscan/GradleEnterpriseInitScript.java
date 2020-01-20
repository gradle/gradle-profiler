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

public class GradleEnterpriseInitScript extends GeneratedInitScript {

    private final String version;

    public GradleEnterpriseInitScript(String version) {
        this.version = version;
    }

    @Override
    public void writeContents(final PrintWriter writer) {
        writer.write("initscript {\n");
        writer.write("    repositories {\n");
        writer.write("      gradlePluginPortal()\n");
        writer.write("    }\n");
        writer.write("    dependencies {\n");
        writer.write("        classpath \"com.gradle:gradle-enterprise-gradle-plugin:" + version + "\"\n");
        writer.write("    }\n");
        writer.write("}\n");
        writer.write("\n");
        writer.write("settingsEvaluated {\n");
        writer.write("    if (!it.pluginManager.hasPlugin(\"com.gradle.enterprise\")) {\n");
        writer.write("        it.pluginManager.apply(com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin)\n");
        writer.write("    }\n");
        writer.write("    it.extensions[\"gradleEnterprise\"].buildScan.with {\n");
        writer.write("        termsOfServiceUrl = 'https://gradle.com/terms-of-service'\n");
        writer.write("        termsOfServiceAgree = 'yes'\n");
        writer.write("    }\n");
        writer.write("}\n");
    }


}
