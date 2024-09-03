package org.gradle.profiler.mutations;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.math.IntMath;
import com.typesafe.config.Config;
import org.apache.commons.io.FileUtils;
import org.gradle.profiler.*;
import org.gradle.profiler.mutations.support.FileSupport;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static org.gradle.profiler.mutations.ApplyProjectDependencyChangeMutator.Configurator.DEPENDENCY_COUNT_KEY;
import static org.gradle.profiler.mutations.ApplyProjectDependencyChangeMutator.ProjectCombinations.createProjectCombinations;
import static org.gradle.profiler.mutations.support.ScenarioSupport.sourceFiles;

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
        // TODO: This validation should be handled elsewhere.
//        if (!(invoker instanceof GradleBuildInvoker)) {
//            throw new IllegalStateException("Only Gradle invoker is supported for " + this + ", but " + invoker + " was provided.");
//        }
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

        @VisibleForTesting
        static ProjectCombinations createProjectCombinations(int numberOfRequiredCombinations, int appliedProjectsCount) {
            checkArgument(appliedProjectsCount > 0, String.format("Value '%s' should be greater than 0.", DEPENDENCY_COUNT_KEY));
            int projectsToGenerate = calculateNumberOfProjectsToGenerate(numberOfRequiredCombinations, appliedProjectsCount);
            List<String> projectNames = IntStream.range(0, projectsToGenerate)
                .mapToObj(index -> String.format("generated-dependency-%s", index))
                .collect(Collectors.toList());
            Set<Set<String>> combinations = Sets.combinations(new LinkedHashSet<>(projectNames), appliedProjectsCount);
            return new ProjectCombinations(projectNames, combinations);
        }

        private static int calculateNumberOfProjectsToGenerate(int numberOfRequiredCombinations, int appliedProjectsCount) {
            if (appliedProjectsCount == 1) {
                return numberOfRequiredCombinations;
            }
            int projectsToGenerate = appliedProjectsCount;
            while (IntMath.binomial(projectsToGenerate, appliedProjectsCount) < numberOfRequiredCombinations) {
                // We could be smarter but this is good enough
                // unless we have more billions of iterations
                projectsToGenerate++;
            }
            return projectsToGenerate;
        }
    }

    public static class Configurator implements BuildMutatorConfigurator {

        public static final String DEPENDENCY_COUNT_KEY = "dependency-count";
        private static final String FILES_KEY = "files";
        private static final Set<String> VALID_CONFIG_KEYS = ImmutableSet.of(DEPENDENCY_COUNT_KEY, FILES_KEY);
        private static final int DEFAULT_APPLIED_PROJECTS_COUNT = 3;

        @Override
        public BuildMutator configure(String key, BuildMutatorConfiguratorSpec spec) {
            Config config = spec.getScenario().getConfig(key);
            validateConfig(key, spec.getScenarioName(), spec.getScenarioFile(), config);
            int appliedProjectCount = getAppliedProjectCount(config);
            List<File> sourceFiles = sourceFiles(config, spec.getScenarioName(), spec.getProjectDir(), FILES_KEY);
            ProjectCombinations combinations = getProjectCombinations(spec, sourceFiles.size(), appliedProjectCount);

            AtomicInteger index = new AtomicInteger();
            List<BuildMutator> mutatorsForKey = sourceFiles.stream()
                .map(sourceFileToChange -> {
                    boolean shouldCreateProjects = index.getAndIncrement() == 0;
                    return new ApplyProjectDependencyChangeMutator(spec.getProjectDir(), sourceFileToChange, combinations, shouldCreateProjects);
                })
                .collect(Collectors.toList());

            return CompositeBuildMutator.from(mutatorsForKey);
        }

        private ProjectCombinations getProjectCombinations(BuildMutatorConfiguratorSpec spec, int numberOfProjects, int appliedProjectDependencies) {
            int numberOfIterations = spec.getWarmupCount() + spec.getBuildCount();
            int numberOfRequiredCombinations = numberOfIterations * numberOfProjects;
            return createProjectCombinations(numberOfRequiredCombinations, appliedProjectDependencies);
        }

        private void validateConfig(String key, String scenarioName, File scenarioFile, Config config) {
            Set<String> invalidKeys = config.entrySet().stream()
                .map(Map.Entry::getKey)
                .filter(entryKey -> !VALID_CONFIG_KEYS.contains(entryKey))
                .collect(Collectors.toSet());
            if (!invalidKeys.isEmpty()) {
                throw new IllegalArgumentException("Unrecognized keys " + invalidKeys + " found for '" + scenarioName + "." + key + "' defined in scenario file " + scenarioFile + ": " + invalidKeys);
            }
        }

        private int getAppliedProjectCount(Config config) {
            return config.hasPath(DEPENDENCY_COUNT_KEY)
                ? config.getInt(DEPENDENCY_COUNT_KEY)
                : DEFAULT_APPLIED_PROJECTS_COUNT;
        }
    }
}
