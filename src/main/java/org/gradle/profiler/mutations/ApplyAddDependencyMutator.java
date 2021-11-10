package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildInvoker;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.GradleBuildInvoker;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.ScenarioContext;
import org.gradle.profiler.ScenarioLoader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ApplyAddDependencyMutator extends AbstractFileChangeMutator {

    private final InvocationSettings settings;
    private final long mutatorIndex;
    private final File generatedProjectsDir;
    private final File settingsFile;
    private final String originalSettingsText;
    private final List<String> projects;
    private final int warmupCount;
    private final int buildCount;

    protected ApplyAddDependencyMutator(File sourceFile, Config scenario, InvocationSettings settings, long mutatorIndex) {
        super(sourceFile);
        this.settings = settings;
        this.mutatorIndex = mutatorIndex;
        this.generatedProjectsDir = new File(settings.getProjectDir(), "gradle-profiler-generated-projects");
        this.settingsFile = getSettingsFile();
        this.originalSettingsText = readText(settingsFile);
        this.projects = Collections.unmodifiableList(getProjectNames());
        this.warmupCount = ScenarioLoader.getWarmUpCount(settings, scenario);
        this.buildCount = ScenarioLoader.getBuildCount(settings, scenario);
    }

    private File getSettingsFile() {
        return Stream.of("settings.gradle", "settings.gradle.kts")
            .map(it -> new File(settings.getProjectDir(), it))
            .filter(File::exists)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No settings.gradle file found in " + settings.getProjectDir()));
    }

    private List<String> getProjectNames() {
        System.out.println(warmupCount);
        System.out.println(buildCount);
        int requiredProjects = warmupCount + buildCount;
        return IntStream.rangeClosed(0, requiredProjects)
            .mapToObj(it -> String.format("project-%d-%d", mutatorIndex, it))
            .collect(Collectors.toList());
    }

    @Override
    public void validate(BuildInvoker invoker) {
        if (!(invoker instanceof GradleBuildInvoker)) {
            throw new IllegalStateException("Only Gradle invoker is supported for " + this + ", but " + invoker + " was provided.");
        }
    }

    @Override
    public void beforeScenario(ScenarioContext context) {
        createProjects();
        try {
            String additionalProjects = projects.stream()
                .map(it -> "include(\"" + it + "\")")
                .collect(Collectors.joining("\n"));
            additionalProjects = "\n" + additionalProjects;
            Files.write(settingsFile.toPath(), additionalProjects.getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void createProjects() {
        for (String projectName : projects) {
            File projectDir = new File(generatedProjectsDir, projectName);
            projectDir.mkdirs();
            File buildGradle = new File(projectDir, "build.gradle");
            try (PrintWriter writer = new PrintWriter(buildGradle, UTF_8.toString())) {
                writer.write("plugins { id 'java' }");
            } catch (FileNotFoundException | UnsupportedEncodingException e) {
                throw new RuntimeException("Could not write to file: " + buildGradle, e);
            }
        }
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        int iteration;
        switch (context.getPhase()) {
            case WARM_UP:
                iteration = context.getIteration();
                break;
            case MEASURE:
                iteration = warmupCount + context.getIteration();
                break;
            default:
                throw new IllegalStateException("Phase: " + context.getPhase() + " is not handled for " + this.getClass());
        }
        String projectName = projects.get(iteration - 1);
        text.append("dependencies {\n")
            .append(String.format("    project(\":%s\")\n", projectName))
            .append("}");
    }

    @Override
    public void afterScenario(ScenarioContext context) {
        super.afterScenario(context);
        revert(settingsFile, originalSettingsText);
        FileUtils.deleteQuietly(generatedProjectsDir);
    }

    public static class Configurator extends FileChangeMutatorConfigurator {

        private final AtomicLong createdMutatorsCounter;

        public Configurator() {
            super(ApplyAddDependencyMutator.class);
            this.createdMutatorsCounter = new AtomicLong();
        }

        @Override
        protected BuildMutator newBuildMutator(Config scenario, InvocationSettings settings, File sourceFileToChange) {
            return new ApplyAddDependencyMutator(sourceFileToChange, scenario, settings, createdMutatorsCounter.getAndIncrement());
        }
    }

}
