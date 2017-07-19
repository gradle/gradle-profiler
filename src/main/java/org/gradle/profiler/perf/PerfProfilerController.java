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
package org.gradle.profiler.perf;

import org.apache.ant.compress.taskdefs.Unzip;
import org.apache.tools.ant.types.mappers.CutDirsMapper;
import org.gradle.profiler.CommandExec;
import org.gradle.profiler.ProfilerController;
import org.gradle.profiler.ScenarioSettings;
import org.gradle.profiler.fg.FlameGraphGenerator;
import org.gradle.profiler.fg.FlameGraphSanitizer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

public class PerfProfilerController implements ProfilerController {
    private static final String PROFILE_DATA_SUFFIX = ".data";
    private static final String PROFILE_SCRIPT_SUFFIX = "-perf-script.txt";
    private static final String PROFILE_FOLDED_SUFFIX = "-perf-folded.txt";
    private static final String PROFILE_FOLDED_NOIDLE_SUFFIX = "-perf-folded-noidle.txt";
    private static final String PROFILE_FOLDED_JAVA_SUFFIX = "-perf-folded-java.txt";
    private static final String PROFILE_PKGSPLIT_SUFFIX = "-perf-pkgsplit.txt";
    private static final String PROFILE_SANITIZED_TXT_SUFFIX = "-perf-sanitized.txt";
    private static final String FLAMES_SVG_SUFFIX = "-perf-flames.svg";
    private static final String FLAMES_PACKAGE_SVG_SUFFIX = "-perf-flames-package.svg";
    private static final String ICICLES_SVG_SUFFIX = "-perf-icicles.svg";

    private static final String TOOL_FLAMEGRAPH = "brendangregg/FlameGraph";
    private static final String TOOL_MISC = "brendangregg/Misc";
    private static final String TOOL_PERF_MAP_AGENT = "jvm-profiling-tools/perf-map-agent";
    private static final List<String> TOOL_GITHUB_REPOS = Arrays.asList(TOOL_FLAMEGRAPH, TOOL_MISC, TOOL_PERF_MAP_AGENT);

    private static final String CMD_FLAMEGRAPH = "flamegraph.pl";
    private static final String CMD_JMAPS = "java/jmaps";
    private static final String CMD_PKGSPLIT = "pkgsplit-perf.pl";
    private static final String CMD_STACKCOLLAPSE = "stackcollapse-perf.pl";

    private final PerfProfilerArgs args;
    private final ScenarioSettings scenarioSettings;
    private final CommandExec commandExec;
    private CommandExec.RunHandle perfHandle;

    PerfProfilerController(final PerfProfilerArgs args, ScenarioSettings scenarioSettings) {
        this.args = args;
        this.scenarioSettings = scenarioSettings;
        this.commandExec = new CommandExec();
    }

    @Override
    public void start() throws IOException, InterruptedException {
        System.out.println("Starting profiling with Perf");

        checkPrerequisites();
        prepareTools();

        File dataFile = profileFileForSuffix(PROFILE_DATA_SUFFIX);
        perfHandle = commandExec.runBackgrounded("perf", "record", "-F", String.valueOf(args.getFrequency()), "-o", dataFile.getAbsolutePath(), "-g", "-a");
    }

    @Override
    public void stop() throws IOException, InterruptedException {
        println("Stopping profiling with Perf");

        perfHandle.interrupt();
        generateJmaps();
        generateFlameGraph();
        // Disabled for now, I need to talk to Brendan about the pkgsplit script
        /*
        generatePackageFlameGraph();
        */
    }

    private void println(String message) {
        System.out.println(message);
    }

    private File profileFileForSuffix(String suffix) {
        return new File(getOutputDir(), getProfileName() + suffix);
    }

    private void checkPrerequisites() {
        checkCommand("perf", "--version");
        checkCommand("cmake", "--version");
        if (!System.getProperty("user.name").equals("root")) {
            throw new IllegalStateException("You must be 'root' to profile with perf");
        }
        String javaHome = System.getProperty("java.home");
        if (!new File(javaHome, "../lib/tools.jar").exists()) {
            throw new IllegalStateException("You must use the JDK to run this profiler (tools.jar is required). Current java.home is " + javaHome);
        }
        String maxStack = commandExec.runAndCollectOutput("sysctl", "kernel.perf_event_max_stack", "-b");
        if (Integer.valueOf(maxStack) < args.getMaxStack()) {
            throw new IllegalStateException("Kernel parameter kernel.perf_event_max_stack must be greater or equal to the desired stack depth limit of " + args.getMaxStack() + ". Currently set to " + maxStack);
        }
    }

    private void checkCommand(String... commandLine) {
        try {
            commandExec.run(commandLine);
        } catch (Exception e) {
            throw new IllegalStateException("Could not execute '" + commandLine[0] + "'. Is it installed?", e);
        }
    }

    private void generateJmaps() {
        File jmapsFile = new File(getToolDir(TOOL_MISC), CMD_JMAPS);
        File jmapsUpdated = new File(getToolDir(TOOL_MISC), CMD_JMAPS + "-updated");
        try (PrintWriter writer = new PrintWriter(jmapsUpdated)) {
            Files.lines(jmapsFile.toPath()).forEach((line) -> {
                if (line.startsWith("JAVA_HOME=")) {
                    String jdkHome = System.getProperty("java.home").replace("/jre", "");
                    writer.println("JAVA_HOME=" + jdkHome);
                } else if (line.startsWith("AGENT_HOME=")) {
                    File agentHome = getToolDir(TOOL_PERF_MAP_AGENT);
                    writer.println("AGENT_HOME=" + agentHome.getAbsolutePath());
                } else {
                    writer.println(line);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        setAndCheckExecutable(jmapsUpdated);
        commandExec.run(jmapsUpdated.getAbsolutePath(), "-u");
    }

    private void generateFlameGraph() throws IOException, InterruptedException {
        File dataFile = profileFileForSuffix(PROFILE_DATA_SUFFIX);
        File scriptFile = profileFileForSuffix(PROFILE_SCRIPT_SUFFIX);
        File foldedFile = profileFileForSuffix(PROFILE_FOLDED_SUFFIX);
        File foldedNoIdleFile = profileFileForSuffix(PROFILE_FOLDED_NOIDLE_SUFFIX);
        File foldedJavaFile = profileFileForSuffix(PROFILE_FOLDED_JAVA_SUFFIX);

        File sanitizedFile = profileFileForSuffix(PROFILE_SANITIZED_TXT_SUFFIX);
        File fgFile = profileFileForSuffix(FLAMES_SVG_SUFFIX);
        File icicleFile = profileFileForSuffix(ICICLES_SVG_SUFFIX);

        String stackcollapseCmd = new File(getToolDir(TOOL_FLAMEGRAPH), CMD_STACKCOLLAPSE).getAbsolutePath();

        commandExec.runAndCollectOutput(scriptFile, "perf", "script", "-F", "comm,pid,tid,cpu,time,event,ip,sym,dso,trace", "-i", dataFile.getAbsolutePath(), "--max-stack", String.valueOf(args.getMaxStack()));
        commandExec.runAndCollectOutput(foldedFile, stackcollapseCmd, "--inline", "--pid", "--tid", scriptFile.getAbsolutePath());

        sanitizeFlameGraphFile(new IdleCpuSanitizeFunction(), foldedFile, foldedNoIdleFile);
        sanitizeFlameGraphFile(new JavaProcessSanitizeFunction(), foldedNoIdleFile, foldedJavaFile);
        sanitizeFlameGraphFile(FlameGraphSanitizer.DEFAULT_SANITIZE_FUNCTION, foldedJavaFile, sanitizedFile);
        generateFlameGraph(sanitizedFile, fgFile, false);
        generateFlameGraph(sanitizedFile, icicleFile, true);
    }

    /**
     * Generate package flamegraphs per http://www.brendangregg.com/blog/2017-06-30/package-flame-graph.html
     */
    private void generatePackageFlameGraph() throws IOException, InterruptedException {
        File scriptFile = profileFileForSuffix(PROFILE_SCRIPT_SUFFIX);
        File pkgSplitFile = profileFileForSuffix(PROFILE_PKGSPLIT_SUFFIX);

        String pkgsplitCmd = new File(getToolDir(TOOL_FLAMEGRAPH), CMD_PKGSPLIT).getAbsolutePath();

        File sanitizedFile = profileFileForSuffix(PROFILE_SANITIZED_TXT_SUFFIX);
        File fgFile = profileFileForSuffix(FLAMES_PACKAGE_SVG_SUFFIX);

        commandExec.runAndCollectOutput(pkgSplitFile, scriptFile, pkgsplitCmd);
        sanitizeFlameGraphFile(new JavaPackageSanitizeFunction(), pkgSplitFile, sanitizedFile);
        generateFlameGraph(sanitizedFile, fgFile, false);
    }

    private void sanitizeFlameGraphFile(FlameGraphSanitizer.SanitizeFunction sanitizeFunction, final File txtFile, final File sanitizedTxtFile) {
        new FlameGraphSanitizer(sanitizeFunction).sanitize(txtFile, sanitizedTxtFile);
    }

    private void generateFlameGraph(final File sanitizedTxtFile, final File fgFile, boolean icicle) throws IOException, InterruptedException {
        new FlameGraphGenerator(getToolDir(TOOL_FLAMEGRAPH)).generateFlameGraph(sanitizedTxtFile, fgFile, icicle);
    }

    private File getToolsDir() {
        return new File(scenarioSettings.getInvocationSettings().getGradleUserHome(), "tools");
    }

    private File getToolDir(String tool) {
        return new File(getToolsDir(), tool);
    }

    private File getOutputDir() {
        return scenarioSettings.getScenario().getOutputDir();
    }

    private String getProfileName() {
        return scenarioSettings.getScenario().getProfileName();
    }

    private void prepareTools() {
        println("Preparing support tools");

        for (String repo : TOOL_GITHUB_REPOS) {
            try {
                downloadTool(repo);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        File perfMapAgentDir = getToolDir(TOOL_PERF_MAP_AGENT);
        if (!new File(perfMapAgentDir, "out").exists()) {
            build(perfMapAgentDir, "cmake", ".");
            build(perfMapAgentDir, "make");
        }

        File fgDir = getToolDir(TOOL_FLAMEGRAPH);
        setAndCheckExecutable(new File(fgDir, CMD_FLAMEGRAPH));
        setAndCheckExecutable(new File(fgDir, CMD_STACKCOLLAPSE));
        setAndCheckExecutable(new File(fgDir, CMD_PKGSPLIT));
    }

    private void build(File directory, String... commandLine) {
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        processBuilder.directory(directory);
        commandExec.run(processBuilder);
    }

    private void downloadTool(String repo) throws IOException {
        File toolsDir = getToolsDir();
        URL url = new URL(String.format("https://github.com/%s/archive/master.zip", repo));
        File toolDir = new File(toolsDir, repo);
        if (toolDir.mkdirs()) {
            File zipFile = new File(toolDir, "master.zip");
            try (InputStream in = url.openStream()) {
                Files.copy(in, zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            Unzip unzip = new Unzip();

            CutDirsMapper mapper = new CutDirsMapper();
            mapper.setDirs(1);
            unzip.add(mapper);
            unzip.setSrc(zipFile);
            unzip.setDest(toolDir);
            unzip.execute();
        }
    }

    private void setAndCheckExecutable(File file) {
        if (!file.setExecutable(true)) {
            throw new IllegalStateException("Could not make " + file + " executable!");
        }
    }

    private static class IdleCpuSanitizeFunction implements FlameGraphSanitizer.SanitizeFunction {
        @Override
        public boolean skipLine(String line) {
            return line.contains("cpu_idle");
        }

        @Override
        public String map(String entry) {
            return entry;
        }
    }

    private static class JavaProcessSanitizeFunction implements FlameGraphSanitizer.SanitizeFunction {
        private String currentProcess;

        private JavaProcessSanitizeFunction() {
            String[] split = ManagementFactory.getRuntimeMXBean().getName().split("@");
            this.currentProcess = "java=" + split[0];
        }

        @Override
        public boolean skipLine(String line) {
            return !line.startsWith("java-") || line.startsWith(currentProcess);
        }

        @Override
        public String map(String entry) {
            return entry;
        }
    }

    private static class JavaPackageSanitizeFunction implements FlameGraphSanitizer.SanitizeFunction {
        @Override
        public boolean skipLine(String line) {
            return !line.startsWith("java");
        }

        @Override
        public String map(String entry) {
            return entry;
        }
    }
}
