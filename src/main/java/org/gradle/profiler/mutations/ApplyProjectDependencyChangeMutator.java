package org.gradle.profiler.mutations;

import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildInvoker;
import org.gradle.profiler.GradleBuildInvoker;
import org.gradle.profiler.ScenarioContext;
import org.gradle.profiler.mutations.support.FileSupport;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Adds a project dependency to the source file.
 *
 * Before the scenario it also generates projects that are later added as dependencies.
 */
public class ApplyProjectDependencyChangeMutator extends AbstractFileChangeMutator {

    private final ProjectCombinations projectCombinations;
    private final File projectDir;
    private final File settingsFile;
    private final File generatedProjectsDir;
    private final ProjectCombinations combinations;
    private final boolean shouldCreateProjects;
    private String originalSettingsText;

    public ApplyProjectDependencyChangeMutator(File projectDir, File sourceFile, ProjectCombinations projectCombinations, boolean shouldCreateProjects) {
        super(sourceFile);
        this.projectDir = projectDir;
        this.settingsFile = getSettingsFile();
        this.projectCombinations = projectCombinations;
        this.generatedProjectsDir = new File(projectDir, "gradle-profiler-generated-projects");
        this.combinations = projectCombinations;
        this.shouldCreateProjects = shouldCreateProjects;
    }

    private File getSettingsFile() {
        return Stream.of("settings.gradle", "settings.gradle.kts")
            .map(it -> new File(projectDir, it))
            .filter(File::exists)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No settings.gradle(.kts) file found in " + projectDir));
    }

    @Override
    public void beforeScenario(ScenarioContext context) {
        super.beforeScenario(context);
        if (shouldCreateProjects) {
            this.originalSettingsText = FileSupport.readUnchecked(settingsFile.toPath());
            FileUtils.deleteQuietly(generatedProjectsDir);
            createProjects(combinations.getProjectNames());
            String includeProjects = combinations.getProjectNames().stream()
                .map(it -> {
                    String include = String.format("\ninclude(\"%s\")", it);
                    String projectDir = String.format("\nproject(\":%s\").projectDir = file(\"%s/%s\")", it, generatedProjectsDir.getName(), it);
                    return include + projectDir;
                })
                .collect(Collectors.joining());
            FileSupport.writeUnchecked(settingsFile.toPath(), includeProjects, StandardOpenOption.APPEND);
        }
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
    public void afterScenario(ScenarioContext context) {
        super.afterScenario(context);
        if (shouldCreateProjects) {
            revert(settingsFile, originalSettingsText);
            try {
                FileUtils.forceDelete(generatedProjectsDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public void validate(BuildInvoker invoker) {
        if (!(invoker instanceof GradleBuildInvoker)) {
            throw new IllegalStateException("Only Gradle invoker is supported for " + this + ", but " + invoker + " was provided.");
        }
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        text.append("\ndependencies {\n");
        Set<String> projects = projectCombinations.getNextCombination();
        projects.forEach(it -> text.append("    project(\":").append(it).append("\")\n"));
        text.append("}");
    }

    public static class ProjectCombinations {

        private final List<String> projectNames;
        private final Iterator<Set<String>> combinations;

        public ProjectCombinations(List<String> projectNames, Set<Set<String>> combinations) {
            this.projectNames = Collections.unmodifiableList(projectNames);
            this.combinations = combinations.iterator();
        }

        public List<String> getProjectNames() {
            return projectNames;
        }

        public Set<String> getNextCombination() {
            return combinations.next();
        }
    }
}
