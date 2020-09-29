package org.gradle.profiler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.gradle.profiler.mutations.ApplyAbiChangeToSourceFileMutator;
import org.gradle.profiler.mutations.ApplyChangeToAndroidLayoutFileMutator;
import org.gradle.profiler.mutations.ApplyChangeToAndroidManifestFileMutator;
import org.gradle.profiler.mutations.ApplyChangeToAndroidResourceFileMutator;
import org.gradle.profiler.mutations.ApplyChangeToNativeSourceFileMutator;
import org.gradle.profiler.mutations.ApplyChangeToPropertyResourceFileMutator;
import org.gradle.profiler.mutations.ApplyNonAbiChangeToSourceFileMutator;
import org.gradle.profiler.mutations.ApplyValueChangeToAndroidResourceFileMutator;
import org.gradle.profiler.mutations.BuildMutatorConfigurator;
import org.gradle.profiler.mutations.ClearArtifactTransformCacheMutator;
import org.gradle.profiler.mutations.ClearBuildCacheMutator;
import org.gradle.profiler.mutations.ClearConfigurationCacheStateMutator;
import org.gradle.profiler.mutations.ClearGradleUserHomeMutator;
import org.gradle.profiler.mutations.ClearJarsCacheMutator;
import org.gradle.profiler.mutations.ClearProjectCacheMutator;
import org.gradle.profiler.mutations.FileChangeMutatorConfigurator;
import org.gradle.profiler.mutations.GitCheckoutMutator;
import org.gradle.profiler.mutations.GitRevertMutator;
import org.gradle.profiler.mutations.ShowBuildCacheSizeMutator;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

class ScenarioLoader {
    private static final String TITLE = "title";
    private static final String VERSIONS = "versions";
    private static final String TASKS = "tasks";
    private static final String CLEANUP_TASKS = "cleanup-tasks";
    private static final String GRADLE_ARGS = "gradle-args";
    private static final String RUN_USING = "run-using";
    private static final String DAEMON = "daemon";
    private static final String SYSTEM_PROPERTIES = "system-properties";
    private static final String BAZEL = "bazel";
    private static final String BUCK = "buck";
    private static final String MAVEN = "maven";
    private static final String TOOL_HOME = "home";
    private static final String WARM_UP_COUNT = "warm-ups";
    private static final String ITERATIONS = "iterations";
    private static final String MEASURED_BUILD_OPERATIONS = "measured-build-ops";
    private static final String APPLY_ABI_CHANGE_TO = "apply-abi-change-to";
    private static final String APPLY_NON_ABI_CHANGE_TO = "apply-non-abi-change-to";
    private static final String APPLY_ANDROID_RESOURCE_CHANGE_TO = "apply-android-resource-change-to";
    private static final String APPLY_ANDROID_RESOURCE_VALUE_CHANGE_TO = "apply-android-resource-value-change-to";
    private static final String APPLY_ANDROID_LAYOUT_CHANGE_TO = "apply-android-layout-change-to";
    private static final String APPLY_PROPERTY_RESOURCE_CHANGE_TO = "apply-property-resource-change-to";
    private static final String APPLY_ANDROID_MANIFEST_CHANGE_TO = "apply-android-manifest-change-to";
    private static final String APPLY_CPP_SOURCE_CHANGE_TO = "apply-cpp-change-to";
    private static final String APPLY_H_SOURCE_CHANGE_TO = "apply-h-change-to";
    private static final String CLEAR_BUILD_CACHE_BEFORE = "clear-build-cache-before";
    private static final String CLEAR_GRADLE_USER_HOME_BEFORE = "clear-gradle-user-home-before";
    private static final String CLEAR_INSTANT_EXECUTION_STATE_BEFORE = "clear-instant-execution-state-before";
    private static final String CLEAR_CONFIGURATION_CACHE_STATE_BEFORE = "clear-configuration-cache-state-before";
    private static final String CLEAR_PROJECT_CACHE_BEFORE = "clear-project-cache-before";
    private static final String CLEAR_TRANSFORM_CACHE_BEFORE = "clear-transform-cache-before";
    private static final String CLEAR_JARS_CACHE_BEFORE = "clear-jars-cache-before";
    private static final String SHOW_BUILD_CACHE_SIZE = "show-build-cache-size";
    private static final String GIT_CHECKOUT = "git-checkout";
    private static final String GIT_REVERT = "git-revert";
    private static final String TARGETS = "targets";
    private static final String TYPE = "type";
    private static final String MODEL = "model";
    private static final String ANDROID_STUDIO_SYNC = "android-studio-sync";
    private static final String JVM_ARGS = "jvm-args";

    private static final Map<String, BuildMutatorConfigurator> BUILD_MUTATOR_CONFIGURATORS = ImmutableMap.<String, BuildMutatorConfigurator>builder()
        .put(APPLY_ABI_CHANGE_TO, new FileChangeMutatorConfigurator(ApplyAbiChangeToSourceFileMutator.class))
        .put(APPLY_NON_ABI_CHANGE_TO, new FileChangeMutatorConfigurator(ApplyNonAbiChangeToSourceFileMutator.class))
        .put(APPLY_ANDROID_RESOURCE_CHANGE_TO, new FileChangeMutatorConfigurator(ApplyChangeToAndroidResourceFileMutator.class))
        .put(APPLY_ANDROID_RESOURCE_VALUE_CHANGE_TO, new FileChangeMutatorConfigurator(ApplyValueChangeToAndroidResourceFileMutator.class))
        .put(APPLY_ANDROID_LAYOUT_CHANGE_TO, new FileChangeMutatorConfigurator(ApplyChangeToAndroidLayoutFileMutator.class))
        .put(APPLY_ANDROID_MANIFEST_CHANGE_TO, new FileChangeMutatorConfigurator(ApplyChangeToAndroidManifestFileMutator.class))
        .put(APPLY_PROPERTY_RESOURCE_CHANGE_TO, new FileChangeMutatorConfigurator(ApplyChangeToPropertyResourceFileMutator.class))
        .put(APPLY_CPP_SOURCE_CHANGE_TO, new FileChangeMutatorConfigurator(ApplyChangeToNativeSourceFileMutator.class))
        .put(APPLY_H_SOURCE_CHANGE_TO, new FileChangeMutatorConfigurator(ApplyChangeToNativeSourceFileMutator.class))
        .put(CLEAR_BUILD_CACHE_BEFORE, new ClearBuildCacheMutator.Configurator())
        .put(CLEAR_GRADLE_USER_HOME_BEFORE, new ClearGradleUserHomeMutator.Configurator())
        .put(CLEAR_INSTANT_EXECUTION_STATE_BEFORE, new ClearConfigurationCacheStateMutator.Configurator())
        .put(CLEAR_CONFIGURATION_CACHE_STATE_BEFORE, new ClearConfigurationCacheStateMutator.Configurator())
        .put(CLEAR_PROJECT_CACHE_BEFORE, new ClearProjectCacheMutator.Configurator())
        .put(CLEAR_TRANSFORM_CACHE_BEFORE, new ClearArtifactTransformCacheMutator.Configurator())
        .put(CLEAR_JARS_CACHE_BEFORE, new ClearJarsCacheMutator.Configurator())
        .put(SHOW_BUILD_CACHE_SIZE, new ShowBuildCacheSizeMutator.Configurator())
        .put(GIT_CHECKOUT, new GitCheckoutMutator.Configurator())
        .put(GIT_REVERT, new GitRevertMutator.Configurator())
        .build();

    private static final List<String> ALL_SCENARIO_KEYS = ImmutableList.<String>builder()
        .addAll(BUILD_MUTATOR_CONFIGURATORS.keySet())
        .addAll(Arrays.asList(
            TITLE,
            VERSIONS,
            TASKS,
            CLEANUP_TASKS,
            GRADLE_ARGS,
            RUN_USING,
            SYSTEM_PROPERTIES,
            WARM_UP_COUNT,
            ITERATIONS,
            MEASURED_BUILD_OPERATIONS,
            BAZEL,
            BUCK,
            MAVEN,
            MODEL,
            TOOL_HOME,
            ANDROID_STUDIO_SYNC,
            DAEMON,
            JVM_ARGS
        ))
        .build();

    private static final List<String> BAZEL_KEYS = Arrays.asList(TARGETS, TOOL_HOME);
    private static final List<String> BUCK_KEYS = Arrays.asList(TARGETS, TYPE, TOOL_HOME);
    private static final List<String> MAVEN_KEYS = Arrays.asList(TARGETS, TOOL_HOME);

    private final GradleBuildConfigurationReader gradleBuildConfigurationReader;

    public ScenarioLoader(GradleBuildConfigurationReader gradleBuildConfigurationReader) {
        this.gradleBuildConfigurationReader = gradleBuildConfigurationReader;
    }

    public List<ScenarioDefinition> loadScenarios(InvocationSettings settings) {
        List<ScenarioDefinition> scenarios = doLoadScenarios(settings);
        List<String> problems = new ArrayList<>();
        for (ScenarioDefinition scenario : scenarios) {
            scenario.visitProblems(settings, message -> problems.add("- Scenario " + scenario.getDisplayName() + ": " + message));
        }
        if (!problems.isEmpty()) {
            System.out.println();
            System.out.println("There were some problems with the profiler configuration:");
            for (String problem : problems) {
                System.out.println(problem);
            }
            System.out.println();
            throw new IllegalArgumentException("There were some problems with the profiler configuration. Please see the log output for details.");
        }
        return scenarios;
    }

    private List<ScenarioDefinition> doLoadScenarios(InvocationSettings settings) {
        if (settings.getScenarioFile() != null) {
            return loadScenarios(settings.getScenarioFile(), settings, gradleBuildConfigurationReader);
        } else {
            return adhocScenarios(settings);
        }
    }

    private List<ScenarioDefinition> adhocScenarios(InvocationSettings settings) {
        List<ScenarioDefinition> scenarios = new ArrayList<>();
        List<GradleBuildConfiguration> versions = new ArrayList<>();
        for (String v : settings.getVersions()) {
            versions.add(gradleBuildConfigurationReader.readConfiguration(v));
        }
        if (versions.isEmpty()) {
            versions.add(gradleBuildConfigurationReader.readConfiguration());
        }

        for (GradleBuildConfiguration version : versions) {
            File outputDir = versions.size() == 1 ? settings.getOutputDir() : new File(settings.getOutputDir(), version.getGradleVersion().getVersion());
            scenarios.add(new AdhocGradleScenarioDefinition(
                version,
                (GradleBuildInvoker) settings.getInvoker(),
                new RunTasksAction(settings.getTargets()),
                settings.getSystemProperties(),
                getWarmUpCount(settings, settings.getInvoker(), settings.getWarmUpCount()),
                getBuildCount(settings),
                outputDir,
                settings.getMeasuredBuildOperations()
            ));
        }
        return scenarios;
    }

    static List<ScenarioDefinition> loadScenarios(File scenarioFile, InvocationSettings settings, GradleBuildConfigurationReader inspector) {
        List<ScenarioDefinition> definitions = new ArrayList<>();
        Config config = ConfigFactory.parseFile(scenarioFile, ConfigParseOptions.defaults().setAllowMissing(false)).resolve();
        Set<String> roots = config.root().keySet();
        Set<String> selectedScenarios;
        if (!settings.getTargets().isEmpty()) {
            for (String target : settings.getTargets()) {
                if (!roots.contains(target)) {
                    throw new IllegalArgumentException("Unknown scenario '" + target + "' requested. Available scenarios are: " + roots.stream().sorted().collect(Collectors.joining(", ")));
                }
            }
            selectedScenarios = new LinkedHashSet<>(settings.getTargets());
        } else if (roots.contains("default-scenarios")) {
            selectedScenarios = new LinkedHashSet<>(config.getStringList("default-scenarios"));
        } else {
            selectedScenarios = new TreeSet<>(roots);
        }
        for (String scenarioName : selectedScenarios) {
            Config scenario = config.getConfig(scenarioName);
            for (String key : config.getObject(scenarioName).keySet()) {
                if (!ALL_SCENARIO_KEYS.contains(key)) {
                    throw new IllegalArgumentException("Unrecognized key '" + scenarioName + "." + key + "' defined in scenario file " + scenarioFile);
                }
            }
            String title = scenario.hasPath(TITLE) ? scenario.getString(TITLE) : null;

            List<BuildMutator> mutators = BUILD_MUTATOR_CONFIGURATORS.entrySet().stream()
                .filter(entry -> scenario.hasPath(entry.getKey()))
                .map(entry -> entry.getValue().configure(scenario, scenarioName, settings, entry.getKey()))
                .filter(mutator -> mutator != BuildMutator.NOOP)
                .collect(Collectors.toList());

            int buildCount = getBuildCount(settings, scenario);
            File scenarioBaseDir = new File(settings.getOutputDir(), GradleScenarioDefinition.safeFileName(scenarioName));

            if (scenario.hasPath(BAZEL) && settings.isBazel()) {
                Config executionInstructions = getConfig(scenarioFile, settings, scenarioName, scenario, BAZEL, BAZEL_KEYS);

                List<String> targets = ConfigUtil.strings(executionInstructions, TARGETS);
                File bazelHome = getToolHome(executionInstructions);
                File outputDir = new File(scenarioBaseDir, "bazel");
                int warmUpCount = getWarmUpCount(settings, scenario);
                definitions.add(new BazelScenarioDefinition(scenarioName, title, targets, mutators, warmUpCount, buildCount, outputDir, bazelHome));
            } else if (scenario.hasPath(BUCK) && settings.isBuck()) {
                Config executionInstructions = getConfig(scenarioFile, settings, scenarioName, scenario, BUCK, BUCK_KEYS);
                List<String> targets = ConfigUtil.strings(executionInstructions, TARGETS);
                String type = ConfigUtil.string(executionInstructions, TYPE, null);
                File buckHome = getToolHome(executionInstructions);
                File outputDir = new File(scenarioBaseDir, "buck");
                int warmUpCount = getWarmUpCount(settings, scenario);
                definitions.add(new BuckScenarioDefinition(scenarioName, title, targets, type, mutators, warmUpCount, buildCount, outputDir, buckHome));
            } else if (scenario.hasPath(MAVEN) && settings.isMaven()) {
                Config executionInstructions = getConfig(scenarioFile, settings, scenarioName, scenario, MAVEN, MAVEN_KEYS);
                List<String> targets = ConfigUtil.strings(executionInstructions, TARGETS);
                File mavenHome = getToolHome(executionInstructions);
                File outputDir = new File(scenarioBaseDir, "maven");
                int warmUpCount = getWarmUpCount(settings, scenario);
                definitions.add(new MavenScenarioDefinition(scenarioName, title, targets, mutators, warmUpCount, buildCount, outputDir, mavenHome));
            } else if (!settings.isBazel() && !settings.isBuck() && !settings.isMaven()) {
                List<GradleBuildConfiguration> versions = ConfigUtil.strings(scenario, VERSIONS, settings.getVersions()).stream().map(inspector::readConfiguration).collect(
                    Collectors.toList());
                if (versions.isEmpty()) {
                    versions.add(inspector.readConfiguration());
                }

                List<String> gradleArgs = ConfigUtil.strings(scenario, GRADLE_ARGS);
                BuildAction buildAction = getBuildAction(scenario, scenarioFile, settings);
                GradleBuildInvoker invoker = invoker(scenario, (GradleBuildInvoker) settings.getInvoker(), buildAction);
                int warmUpCount = getWarmUpCount(settings, invoker, scenario);
                List<String> measuredBuildOperations = getMeasuredBuildOperations(settings, scenario);
                BuildAction cleanupAction = getCleanupAction(scenario);
                Map<String, String> systemProperties = ConfigUtil.map(scenario, SYSTEM_PROPERTIES, settings.getSystemProperties());
                List<String> jvmArgs = ConfigUtil.strings(scenario, JVM_ARGS);
                for (GradleBuildConfiguration version : versions) {
                    File outputDir = versions.size() == 1 ? scenarioBaseDir : new File(scenarioBaseDir, version.getGradleVersion().getVersion());
                    definitions.add(new GradleScenarioDefinition(
                        scenarioName,
                        title,
                        invoker,
                        version,
                        buildAction,
                        cleanupAction,
                        gradleArgs,
                        systemProperties,
                        mutators,
                        warmUpCount,
                        buildCount,
                        outputDir,
                        jvmArgs,
                        measuredBuildOperations
                    ));
                }
            }
        }

        definitions.forEach(ScenarioDefinition::validate);

        return definitions;
    }

    private static Config getConfig(File scenarioFile, InvocationSettings settings, String scenarioName, Config scenario, String toolName, List<String> toolKeys) {
        if (settings.isProfile()) {
            throw new IllegalArgumentException("Can only profile scenario '" + scenarioName + "' when building using Gradle.");
        }
        Config executionInstructions = scenario.getConfig(toolName);
        for (String key : scenario.getObject(toolName).keySet()) {
            if (!toolKeys.contains(key)) {
                throw new IllegalArgumentException("Unrecognized key '" + scenarioName + "." + toolName + "." + key + "' defined in scenario file " + scenarioFile);
            }
        }
        return executionInstructions;
    }

    private static ImmutableList<String> getMeasuredBuildOperations(InvocationSettings settings, Config scenario) {
        return ImmutableSet.<String>builder()
            .addAll(settings.getMeasuredBuildOperations())
            .addAll(ConfigUtil.strings(scenario, MEASURED_BUILD_OPERATIONS))
            .build()
            .asList();
    }

    private static int getBuildCount(InvocationSettings settings) {
        return getBuildCount(settings, (Integer) null);
    }

    private static int getBuildCount(InvocationSettings settings, Config scenario) {
        return getBuildCount(settings, ConfigUtil.optionalInteger(scenario, ITERATIONS));
    }

    private static int getBuildCount(InvocationSettings settings, Integer providedValue) {
        if (settings.isDryRun()) {
            return 1;
        }
        if (settings.getBuildCount() != null) {
            return settings.getBuildCount();
        }
        if (providedValue != null) {
            return providedValue;
        }
        if (settings.isBenchmark()) {
            return 10;
        } else {
            return 1;
        }
    }

    private static File getToolHome(Config config) {
        String homeString = ConfigUtil.string(config, TOOL_HOME, null);
        return homeString == null ? null : new File(homeString);
    }

    private static int getWarmUpCount(InvocationSettings settings, Config scenario) {
        return getWarmUpCount(settings, settings.getInvoker(), ConfigUtil.optionalInteger(scenario, WARM_UP_COUNT));
    }

    private static int getWarmUpCount(InvocationSettings settings, BuildInvoker invoker, Config scenario) {
        return getWarmUpCount(settings, invoker, ConfigUtil.optionalInteger(scenario, WARM_UP_COUNT));
    }

    private static int getWarmUpCount(InvocationSettings settings, BuildInvoker invoker, Integer providedValue) {
        if (settings.isDryRun()) {
            return 1;
        }
        if (settings.getWarmUpCount() != null) {
            return settings.getWarmUpCount();
        }
        if (providedValue != null) {
            return providedValue;
        }
        if (settings.isBenchmark()) {
            return invoker.benchmarkWarmUps();
        } else {
            return invoker.profileWarmUps();
        }
    }

    public static GradleBuildInvoker invoker(Config config, GradleBuildInvoker defaultValue, BuildAction buildAction) {
        GradleBuildInvoker invoker = defaultValue;
        boolean sync = buildAction instanceof AndroidStudioSyncAction;
        if (sync) {
            invoker = GradleBuildInvoker.AndroidStudio;
        }

        if (config.hasPath(RUN_USING)) {
            if (sync) {
                throw new IllegalArgumentException("Cannot specify '" + RUN_USING + "' when performing Android sync.");
            }
            String value = ConfigUtil.string(config, RUN_USING, null);
            if (value.equals("cli")) {
                invoker = GradleBuildInvoker.Cli;
            } else if (value.equals("tooling-api")) {
                invoker = GradleBuildInvoker.ToolingApi;
            } else {
                throw new IllegalArgumentException("Unexpected value for '" + RUN_USING + "' provided: " + value);
            }
        }
        if (config.hasPath(DAEMON)) {
            String value = ConfigUtil.string(config, DAEMON, null);
            if (value.equals("none")) {
                if (sync) {
                    throw new IllegalArgumentException("Cannot use no daemon when performing Android sync.");
                }
                invoker = GradleBuildInvoker.CliNoDaemon;
            } else if (value.equals("cold")) {
                invoker = invoker.withColdDaemon();
            } else if (!value.equals("warm")) {
                throw new IllegalArgumentException("Unexpected value for '" + DAEMON + "' provided: " + value);
            } // else, already warm
        }

        return invoker;
    }

    private static BuildAction getCleanupAction(Config scenario) {
        List<String> tasks = ConfigUtil.strings(scenario, CLEANUP_TASKS);
        if (tasks.isEmpty()) {
            return BuildAction.NO_OP;
        }
        return new RunTasksAction(tasks);
    }

    private static BuildAction getBuildAction(Config scenario, File scenarioFile, InvocationSettings invocationSettings) {
        Class<?> toolingModel = getToolingModelClass(scenario, scenarioFile);
        boolean sync = scenario.hasPath(ANDROID_STUDIO_SYNC);
        List<String> tasks = ConfigUtil.strings(scenario, TASKS);

        if (sync) {
            if (toolingModel != null) {
                throw new IllegalArgumentException("Cannot load tooling model and Android studio sync in same scenario.");
            }
            if (!tasks.isEmpty()) {
                throw new IllegalArgumentException("Cannot run tasks and Android studio sync in same scenario.");
            }
            if (invocationSettings.getStudioInstallDir() == null) {
                throw new IllegalArgumentException("Android Studio installation directory should be specified using --studio-install-dir when measuring Android studio sync.");
            }
            return new AndroidStudioSyncAction();
        }

        if (toolingModel != null) {
            return new LoadToolingModelAction(toolingModel, tasks);
        } else {
            return new RunTasksAction(tasks);
        }
    }

    private static Class<?> getToolingModelClass(Config scenario, File scenarioFile) {
        String toolingModelName = ConfigUtil.string(scenario, MODEL, null);
        if (toolingModelName == null) {
            return null;
        }
        try {
            return Class.forName(toolingModelName);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unrecognized tooling model '" + toolingModelName + "' defined in scenario file " + scenarioFile);
        }
    }
}
