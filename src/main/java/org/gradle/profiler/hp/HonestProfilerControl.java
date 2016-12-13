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

import org.gradle.profiler.ProfilerController;
import org.gradle.profiler.fg.FlameGraphSanitizer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class HonestProfilerControl implements ProfilerController {
    private static final String PROFILE_HPL = "profile.hpl";
    private static final String PROFILE_TXT = "profile.txt";
    private static final String PROFILE_SANITIZED_TXT = "profile-sanitized.txt";

    private static final Map<Pattern, String> DEFAULT_REPLACEMENTS = Collections.unmodifiableMap(
            new LinkedHashMap<Pattern, String>() { {
                put(Pattern.compile("build_([a-z0-9]+)"), "build_");
                put(Pattern.compile("settings_([a-z0-9]+)"), "settings_");
                put(Pattern.compile("org[.]gradle[.]"), "");
                put(Pattern.compile("sun[.]reflect[.]GeneratedMethodAccessor[0-9]+"), "GeneratedMethodAccessor");
            }}
    );

    private final HonestProfilerArgs args;
    private final File outputDir;

    public HonestProfilerControl(final HonestProfilerArgs args, final File outputDir) {
        this.args = args;
        this.outputDir = outputDir;
    }

    @Override
    public void start() throws IOException, InterruptedException {
        System.out.println("Starting profiling with Honest Profiler on port " + args.getPort());
        sendCommand("start");
    }

    @Override
    public void stop() throws IOException, InterruptedException {
        System.out.println("Stopping profiling with Honest Profiler on port " + args.getPort());
        sendCommand("stop");
        File hplFile = new File(outputDir, PROFILE_HPL);
        File txtFile = new File(outputDir, PROFILE_TXT);
        File sanitizedTxtFile = new File(outputDir, PROFILE_SANITIZED_TXT);
        Files.copy(args.getLogPath().toPath(), hplFile.toPath());
        convertToFlameGraphTxtFile(hplFile, txtFile);
        sanitizeFlameGraphTxtFile(txtFile, sanitizedTxtFile);
    }

    private void convertToFlameGraphTxtFile(final File hplFile, final File txtFile) throws IOException, InterruptedException {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) {
            throw new IllegalArgumentException("Please set the JAVA_HOME environment variable to your Java installation");
        }
        ProcessBuilder processBuilder = new ProcessBuilder(
                javaHome + File.separatorChar + "bin" + File.separatorChar + "java",
                "-cp",
                javaHome + File.separatorChar + "lib" + File.separatorChar + "tools.jar" + ":" + args.getHpHomeDir() + File.separatorChar + "honest-profiler.jar",
                "com.insightfullogic.honest_profiler.ports.console.FlameGraphDumperApplication",
                hplFile.getAbsolutePath(),
                txtFile.getAbsolutePath()
        );
        Process process = processBuilder.start();
        int result = process.waitFor();
        if (result != 0) {
            throw new RuntimeException("Unable to generate stack traces txt file");
        }
    }

    private void sanitizeFlameGraphTxtFile(final File txtFile, final File sanitizedTxtFile) {
        FlameGraphSanitizer sanitizer = new FlameGraphSanitizer(new FlameGraphSanitizer.RegexBasedSanitizerFunction(DEFAULT_REPLACEMENTS));
        // todo: add a way to provide custom patterns
        sanitizer.sanitize(txtFile, sanitizedTxtFile);
    }

    private void sendCommand(String command) throws IOException {
        Socket socket = new Socket("localhost", args.getPort());
        try (OutputStream out = socket.getOutputStream()) {
            out.write(commandBytes(command));
        }
    }

    private byte[] commandBytes(final String command) {
        return (command + "\r\n").getBytes();
    }

}
