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
package org.gradle.profiler.hp;

import org.gradle.profiler.JvmArgsCalculator;

import java.util.List;

public class HonestProfilerJvmArgsCalculator extends JvmArgsCalculator {

    private final HonestProfilerArgs args;

    public HonestProfilerJvmArgsCalculator(final HonestProfilerArgs honestProfilerArgs) {
        this.args = honestProfilerArgs;
    }

    @Override
    public void calculateJvmArgs(final List<String> jvmArgs) {
        jvmArgs.add("-XX:+UnlockDiagnosticVMOptions");
        jvmArgs.add("-XX:+DebugNonSafepoints");
        jvmArgs.add("-agentpath:" + args.getHpHomeDir().getAbsolutePath() + "/liblagent.so=interval=" + args.getInterval() + ",logPath=" + args.getLogPath().getAbsolutePath() + ",port=" + args.getPort() + ",host=localhost,start=0'");
    }
}
