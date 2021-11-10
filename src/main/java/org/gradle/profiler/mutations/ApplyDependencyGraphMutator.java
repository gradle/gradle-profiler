package org.gradle.profiler.mutations;

import com.google.common.collect.Sets;
import com.google.common.math.IntMath;
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

import java.io.File;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ApplyDependencyGraphMutator extends AbstractFileChangeMutator {

    /**
     * From 34 choose 17 = 2333606220 that is more than an integer
     * From 33 choose 16 = 1166803110 that is less than an integer, so max n is 33
     */
    private static final int MAX_N = 33;

    private final InvocationSettings settings;
    private final long mutatorIndex;
    private final File generatedProjectsDir;
    private final File settingsFile;
    private final String originalSettingsText;
    private ProjectCombinations projectCombinations;

    protected ApplyDependencyGraphMutator(File sourceFile, InvocationSettings settings, long mutatorIndex) {
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
        projectCombinations = createProjectCombinations(numberOfIterations);

        createProjects(projectCombinations.getProjectNames());
        String additionalProjects = projectCombinations.getProjectNames().stream()
            .map(it -> "\ninclude(\"" + it + "\")")
            .collect(Collectors.joining());
        FileSupport.writeUnchecked(settingsFile.toPath(), additionalProjects, StandardOpenOption.APPEND);
    }

    /**
     * We use combination of projects. We don't try all combinations, but just combinations that satisfies:
     * "from n choose n / 2". For example if we have 15 projects, we will do combinations of 7 projects. So from
     * 15 we will choose 7 that results in 6435 combinations.
     */
    public ProjectCombinations createProjectCombinations(int numberOfIterations) {
        int numberOfProjects = findNumberOfProjects(numberOfIterations);
        List<String> projectNames = IntStream.range(0, numberOfProjects)
            .mapToObj(it -> String.format("project-%d-%d", mutatorIndex, it))
            .collect(Collectors.toList());
        int numberOfProjectsInOneCombination = getNumberOfProjectsInOneCombination(projectNames.size());
        Set<Set<String>> combinations = Sets.combinations(new LinkedHashSet<>(projectNames), numberOfProjectsInOneCombination);
        return new ProjectCombinations(projectNames, combinations);
    }

    private int findNumberOfProjects(int numberOfIterations) {
        for (int n = 1; n <= MAX_N; n++) {
            int k = getNumberOfProjectsInOneCombination(n);
            int combinations = IntMath.binomial(n, k);
            if (combinations >= numberOfIterations) {
                return n;
            }
        }
        throw new IllegalStateException("Too many warm up count and build count set");
    }

    private int getNumberOfProjectsInOneCombination(int n) {
        return n <= 1 ? 1 : n / 2;
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
        projectCombinations.getCombinationsIterator().next()
            .forEach(it -> text.append("    project(\":").append(it).append("\")\n"));
        text.append("\n}");
    }

    @Override
    public void afterScenario(ScenarioContext context) {
        super.afterScenario(context);
        revert(settingsFile, originalSettingsText);
        FileUtils.deleteQuietly(generatedProjectsDir);
    }

    private static class ProjectCombinations {

        private final List<String> projectNames;
        private final Iterator<Set<String>> combinations;

        public ProjectCombinations(List<String> projectNames, Set<Set<String>> combinations) {
            this.projectNames = projectNames;
            this.combinations = combinations.iterator();
        }

        public List<String> getProjectNames() {
            return projectNames;
        }

        public Iterator<Set<String>> getCombinationsIterator() {
            return combinations;
        }
    }

    public static class Configurator extends FileChangeMutatorConfigurator {

        private final AtomicLong createdMutatorsCounter;

        public Configurator() {
            super(ApplyDependencyGraphMutator.class);
            this.createdMutatorsCounter = new AtomicLong();
        }

        @Override
        protected BuildMutator newBuildMutator(Config scenario, InvocationSettings settings, File sourceFileToChange) {
            return new ApplyDependencyGraphMutator(sourceFileToChange, settings, createdMutatorsCounter.getAndIncrement());
        }
    }

}
