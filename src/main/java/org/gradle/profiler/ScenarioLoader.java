package org.gradle.profiler;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.gradle.profiler.mutations.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class ScenarioLoader {
    private static final String VERSIONS = "versions";
    private static final String TASKS = "tasks";
    private static final String CLEANUP_TASKS = "cleanup-tasks";
    private static final String GRADLE_ARGS = "gradle-args";
    private static final String RUN_USING = "run-using";
    private static final String SYSTEM_PROPERTIES = "system-properties";
    private static final String BAZEL = "bazel";
    private static final String BUCK = "buck";
    private static final String MAVEN = "maven";
    private static final String WARM_UP_COUNT = "warm-ups";
    private static final String APPLY_ABI_CHANGE_TO = "apply-abi-change-to";
    private static final String APPLY_NON_ABI_CHANGE_TO = "apply-non-abi-change-to";
    private static final String APPLY_ANDROID_RESOURCE_CHANGE_TO = "apply-android-resource-change-to";
    private static final String APPLY_ANDROID_RESOURCE_VALUE_CHANGE_TO = "apply-android-resource-value-change-to";
    private static final String APPLY_PROPERTY_RESOURCE_CHANGE_TO = "apply-property-resource-change-to";
    private static final String APPLY_ANDROID_MANIFEST_CHANGE_TO = "apply-android-manifest-change-to";
    private static final String APPLY_CPP_SOURCE_CHANGE_TO = "apply-cpp-change-to";
    private static final String APPLY_H_SOURCE_CHANGE_TO = "apply-h-change-to";
    private static final String CLEAR_BUILD_CACHE_BEFORE = "clear-build-cache-before";
    private static final String SHOW_BUILD_CACHE_SIZE = "show-build-cache-size";
    private static final String GIT_CHECKOUT = "git-checkout";
    private static final String CLEAN_BUILD_BEFORE = "clean-build-before";
    private static final String GIT_REVERT = "git-revert";
    private static final String TARGETS = "targets";
    private static final String COMMANDS = "commands";
    private static final String TYPE = "type";

    private static final List<String> ALL_SCENARIO_KEYS = Arrays.asList(
            CLEAN_BUILD_BEFORE, VERSIONS, TASKS, CLEANUP_TASKS, GRADLE_ARGS, RUN_USING, SYSTEM_PROPERTIES, WARM_UP_COUNT, APPLY_ABI_CHANGE_TO, APPLY_NON_ABI_CHANGE_TO, APPLY_ANDROID_RESOURCE_CHANGE_TO, APPLY_ANDROID_RESOURCE_VALUE_CHANGE_TO, APPLY_ANDROID_MANIFEST_CHANGE_TO, APPLY_PROPERTY_RESOURCE_CHANGE_TO, APPLY_CPP_SOURCE_CHANGE_TO, APPLY_H_SOURCE_CHANGE_TO, CLEAR_BUILD_CACHE_BEFORE, SHOW_BUILD_CACHE_SIZE, GIT_CHECKOUT, GIT_REVERT, BAZEL, BUCK, MAVEN
    );
    private static final List<String> BAZEL_KEYS = Arrays.asList(TARGETS, CLEANUP_TASKS, CLEAN_BUILD_BEFORE, COMMANDS);
    private static final List<String> BUCK_KEYS = Arrays.asList(TARGETS, TYPE);
    private static final List<String> MAVEN_KEYS = Collections.singletonList(TARGETS);

    private final GradleVersionInspector gradleVersionInspector;

    public ScenarioLoader(GradleVersionInspector gradleVersionInspector) {
        this.gradleVersionInspector = gradleVersionInspector;
    }

    public List<ScenarioDefinition> loadScenarios(InvocationSettings settings) {
        if (settings.getScenarioFile() != null) {
            return loadScenarios(settings.getScenarioFile(), settings, gradleVersionInspector);
        } else {
            return adhocScenarios(settings);
        }
    }

    private List<ScenarioDefinition> adhocScenarios(InvocationSettings settings) {
        List<ScenarioDefinition> scenarios = new ArrayList<>();
        List<GradleVersion> versions = new ArrayList<>();
        for (String v : settings.getVersions()) {
            versions.add(gradleVersionInspector.resolve(v));
        }
        if (versions.isEmpty()) {
            versions.add(gradleVersionInspector.defaultVersion());
        }
        for (GradleVersion version : versions) {
            File outputDir = versions.size() == 1 ? settings.getOutputDir() : new File(settings.getOutputDir(), version.getVersion());
            scenarios.add(new AdhocGradleScenarioDefinition(version, settings.getInvoker(), settings.getTargets(), settings.getSystemProperties(), new BuildMutatorFactory(Collections.emptyList()), settings.getWarmUpCount(), settings.getBuildCount(), outputDir));
        }
        return scenarios;
    }

    static List<ScenarioDefinition> loadScenarios(File scenarioFile, InvocationSettings settings, GradleVersionInspector inspector) {
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

            int warmUpCount = ConfigUtil.integer(scenario, WARM_UP_COUNT, settings.getWarmUpCount());

            List<Supplier<BuildMutator>> mutators = new ArrayList<>();
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), APPLY_ABI_CHANGE_TO, ApplyAbiChangeToJavaSourceFileMutator.class, mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), APPLY_NON_ABI_CHANGE_TO, ApplyNonAbiChangeToJavaSourceFileMutator.class, mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), APPLY_ANDROID_RESOURCE_CHANGE_TO, ApplyChangeToAndroidResourceFileMutator.class, mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), APPLY_ANDROID_RESOURCE_VALUE_CHANGE_TO, ApplyValueChangeToAndroidResourceFileMutator.class, mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), APPLY_ANDROID_MANIFEST_CHANGE_TO, ApplyChangeToAndroidManifestFileMutator.class, mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), APPLY_PROPERTY_RESOURCE_CHANGE_TO, ApplyChangeToPropertyResourceFileMutator.class, mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), APPLY_CPP_SOURCE_CHANGE_TO, ApplyChangeToNativeSourceFileMutator.class, mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), APPLY_H_SOURCE_CHANGE_TO, ApplyChangeToNativeSourceFileMutator.class, mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), CLEAR_BUILD_CACHE_BEFORE, new ClearBuildCacheMutator.Configurator(settings.getGradleUserHome()), mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), SHOW_BUILD_CACHE_SIZE, new ShowBuildCacheSizeMutator.Configurator(settings.getGradleUserHome()), mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), GIT_CHECKOUT, new GitCheckoutMutator.Configurator(), mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), GIT_REVERT, new GitRevertMutator.Configurator(), mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), "bazel." + CLEAN_BUILD_BEFORE, new BazelCleanBuildMutator.Configurator(), mutators);

            BuildMutatorFactory buildMutatorFactory = new BuildMutatorFactory(mutators);

            if (scenario.hasPath(BAZEL) && settings.isBazel()) {
                if (settings.isProfile()) {
                    throw new IllegalArgumentException("Can only profile scenario '" + scenarioName + "' when building using Gradle.");
                }
                Config executionInstructions = scenario.getConfig(BAZEL);
                for (String key : scenario.getObject(BAZEL).keySet()) {
                    if (!BAZEL_KEYS.contains(key)) {
                        throw new IllegalArgumentException("Unrecognized key '" + scenarioName + ".bazel." + key + "' defined in scenario file " + scenarioFile);
                    }
                }
                List<String> targets = ConfigUtil.strings(executionInstructions, TARGETS, Collections.emptyList());
                File outputDir = new File(settings.getOutputDir(), scenarioName + "-bazel");
                List<String> commands = ConfigUtil.strings(executionInstructions, COMMANDS, Collections.singletonList("build"));
                definitions.add(new BazelScenarioDefinition(scenarioName, targets, commands, buildMutatorFactory, warmUpCount, settings.getBuildCount(), outputDir));
            } else if (scenario.hasPath(BUCK) && settings.isBuck()) {
                if (settings.isProfile()) {
                    throw new IllegalArgumentException("Can only profile scenario '" + scenarioName + "' when building using Gradle.");
                }
                Config executionInstructions = scenario.getConfig(BUCK);
                for (String key : scenario.getObject(BUCK).keySet()) {
                    if (!BUCK_KEYS.contains(key)) {
                        throw new IllegalArgumentException("Unrecognized key '" + scenarioName + ".buck." + key + "' defined in scenario file " + scenarioFile);
                    }
                }
                List<String> targets = ConfigUtil.strings(executionInstructions, TARGETS, Collections.emptyList());
                String type = ConfigUtil.string(executionInstructions, TYPE, null);
                File outputDir = new File(settings.getOutputDir(), scenarioName + "-buck");
                definitions.add(new BuckScenarioDefinition(scenarioName, targets, type, buildMutatorFactory, warmUpCount, settings.getBuildCount(), outputDir));
            } else if (scenario.hasPath(MAVEN) && settings.isMaven()) {
                if (settings.isProfile()) {
                    throw new IllegalArgumentException("Can only profile scenario '" + scenarioName + "' when building using Gradle.");
                }
                Config executionInstructions = scenario.getConfig(MAVEN);
                for (String key : scenario.getObject(MAVEN).keySet()) {
                    if (!MAVEN_KEYS.contains(key)) {
                        throw new IllegalArgumentException("Unrecognized key '" + scenarioName + ".maven." + key + "' defined in scenario file " + scenarioFile);
                    }
                }
                List<String> targets = ConfigUtil.strings(executionInstructions, TARGETS, Collections.emptyList());
                File outputDir = new File(settings.getOutputDir(), scenarioName + "-maven");
                definitions.add(new MavenScenarioDefinition(scenarioName, targets, buildMutatorFactory, warmUpCount, settings.getBuildCount(), outputDir));
            } else if (!settings.isBazel() && !settings.isBuck() && !settings.isMaven()) {
                List<GradleVersion> versions = ConfigUtil.strings(scenario, VERSIONS, settings.getVersions()).stream().map(inspector::resolve).collect(
                        Collectors.toList());
                if (versions.isEmpty()) {
                    versions.add(inspector.defaultVersion());
                }

                List<String> tasks = ConfigUtil.strings(scenario, TASKS, settings.getTargets());
                List<String> cleanupTasks = ConfigUtil.strings(scenario, CLEANUP_TASKS, Collections.emptyList());
                List<String> gradleArgs = ConfigUtil.strings(scenario, GRADLE_ARGS, Collections.emptyList());
                Invoker invoker = ConfigUtil.invoker(scenario, RUN_USING, settings.getInvoker());
                Map<String, String> systemProperties = ConfigUtil.map(scenario, SYSTEM_PROPERTIES, settings.getSystemProperties());
                for (GradleVersion version : versions) {
                    File outputDir = versions.size() == 1 ? new File(settings.getOutputDir(), scenarioName) : new File(settings.getOutputDir(), scenarioName + "/" + version.getVersion());
                    definitions.add(new GradleScenarioDefinition(scenarioName, invoker, version, tasks, cleanupTasks, gradleArgs, systemProperties,
                            buildMutatorFactory, warmUpCount, settings.getBuildCount(), outputDir));
                }
            }
        }

        return definitions;
    }

    private static void maybeAddMutator(Config scenario, String scenarioName, File projectDir, String key, Class<? extends AbstractFileChangeMutator> mutatorClass, List<Supplier<BuildMutator>> mutators) {
        maybeAddMutator(scenario, scenarioName, projectDir, key, new FileChangeMutatorConfigurator(mutatorClass), mutators);
    }

    private static void maybeAddMutator(Config scenario, String scenarioName, File projectDir, String key, BuildMutatorConfigurator configurator, List<Supplier<BuildMutator>> mutators) {
        String[] pathKeys = key.split("\\.");
        String leafKey = pathKeys[pathKeys.length - 1];
        Config config = scenario;
        for (int i = 0; i < pathKeys.length; i++) {
            String keyToCheck = pathKeys[i];
            if (config.hasPath(keyToCheck)) {
                if (i < pathKeys.length - 1) {
                    config = config.getObject(keyToCheck).toConfig();
                } else {
                    mutators.add(configurator.configure(config, scenarioName, projectDir, keyToCheck));
                    break;
                }
            } else {
                break;
            }
        }

    }


}
