package org.gradle.profiler.mutations;

import org.apache.commons.io.FileUtils;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.ScenarioContext;
import org.gradle.profiler.mutations.support.FileSupport;
import org.gradle.profiler.mutations.support.ProjectCombinations;

import java.io.File;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ApplyDependencyChangeSetupMutator implements BuildMutator {

    private final String originalText;
    private final File projectDir;
    private final File settingsFile;
    private final ProjectCombinations combinations;
    private final File generatedProjectsDir;

    protected ApplyDependencyChangeSetupMutator(File projectDir, ProjectCombinations combinations) {
        this.projectDir = projectDir;
        this.settingsFile = getSettingsFile();
        this.originalText = FileSupport.readUnchecked(settingsFile.toPath());
        this.generatedProjectsDir = new File(projectDir, "gradle-profiler-generated-projects");
        this.combinations = combinations;
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
        createProjects(combinations.getProjectNames());
        String includeProjects = combinations.getProjectNames().stream()
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
    public void afterScenario(ScenarioContext context) {
        FileUtils.deleteQuietly(generatedProjectsDir);
        FileSupport.writeUnchecked(settingsFile.toPath(), originalText);
    }
}
