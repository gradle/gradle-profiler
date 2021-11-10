package org.gradle.profiler.mutations;

import com.typesafe.config.Config;
import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildInvoker;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.GradleBuildInvoker;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.ScenarioContext;
import org.gradle.profiler.ScenarioDefinition;
import org.gradle.profiler.mutations.support.FileSupport;
import org.gradle.profiler.mutations.support.ProjectCombinations;

import java.io.File;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.profiler.mutations.support.ProjectCombinationsSupport.createProjectCombinations;

public class ApplyDependencyGraphMutator extends AbstractFileChangeMutator {

    private final InvocationSettings settings;
    private final int mutatorIndex;
    private final File generatedProjectsDir;
    private final File settingsFile;
    private final String originalSettingsText;
    private ProjectCombinations projectCombinations;

    protected ApplyDependencyGraphMutator(File sourceFile, InvocationSettings settings, int mutatorIndex) {
        super(sourceFile);
        this.settings = settings;
        this.mutatorIndex = mutatorIndex;
        this.generatedProjectsDir = new File(settings.getProjectDir(), "gradle-profiler-generated-projects");
        this.settingsFile = getSettingsFile();
        this.originalSettingsText = readText(settingsFile);
    }

    private File getSettingsFile() {
        return Stream.of("settings.gradle", "settings.gradle.kts")
            .map(it -> new File(settings.getProjectDir(), it))
            .filter(File::exists)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No settings.gradle file found in " + settings.getProjectDir()));
    }

    @Override
    public void validate(BuildInvoker invoker) {
        if (!(invoker instanceof GradleBuildInvoker)) {
            throw new IllegalStateException("Only Gradle invoker is supported for " + this + ", but " + invoker + " was provided.");
        }
    }

    @Override
    public void beforeScenario(ScenarioContext context) {
        ScenarioDefinition scenario = context.getScenarioDefinition();
        int numberOfIterations = scenario.getWarmUpCount() + scenario.getBuildCount();
        projectCombinations = createProjectCombinations(mutatorIndex, numberOfIterations);

        createProjects(projectCombinations.getProjectNames());
        String includeProjects = projectCombinations.getProjectNames().stream()
            .map(it -> {
                String include = String.format("\ninclude(\"%s\")", it);
                String projectPath = new File(generatedProjectsDir.getName(), it).toString();
                String projectDir = String.format("\nproject(\":%s\").projectDir = file(\"%s\")", it, projectPath);
                return include + projectDir;
            })
            .collect(Collectors.joining());
        FileSupport.writeUnchecked(settingsFile.toPath(), includeProjects, StandardOpenOption.APPEND);
    }

    private void createProjects(List<String> projectNames) {
        for (String projectName : projectNames) {
            File projectDir = new File(generatedProjectsDir, projectName);
            projectDir.mkdirs();
            File buildGradle = new File(projectDir, "build.gradle");
            FileSupport.writeUnchecked(buildGradle.toPath(), "plugins { id 'java' }");
        }
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        text.append("\ndependencies {\n");
        Set<String> projects = projectCombinations.getCombinationsIterator().next();
        projects.forEach(it -> text.append("    project(\":").append(it).append("\")\n"));
        text.append("\n}");
    }

    @Override
    public void afterScenario(ScenarioContext context) {
        super.afterScenario(context);
        revert(settingsFile, originalSettingsText);
        FileUtils.deleteQuietly(generatedProjectsDir);
    }

    public static class Configurator extends FileChangeMutatorConfigurator {

        private final AtomicInteger createdMutatorsCounter;

        public Configurator() {
            super(ApplyDependencyGraphMutator.class);
            this.createdMutatorsCounter = new AtomicInteger();
        }

        @Override
        protected BuildMutator newBuildMutator(Config scenario, InvocationSettings settings, File sourceFileToChange) {
            return new ApplyDependencyGraphMutator(sourceFileToChange, settings, createdMutatorsCounter.getAndIncrement());
        }
    }

}
