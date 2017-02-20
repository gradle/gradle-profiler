package org.gradle.profiler;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigValue;
import org.gradle.profiler.mutations.*;

import java.io.File;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class ScenarioLoader {
    private static final String VERSIONS = "versions";
    private static final String TASKS = "tasks";
    private static final String CLEANUP_TASKS = "cleanup-tasks";
    private static final String GRADLE_ARGS = "gradle-args";
    private static final String RUN_USING = "run-using";
    private static final String SYSTEM_PROPERTIES = "system-properties";
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
    private static final String TARGETS = "targets";
    private static final String TYPE = "type";

    private static final List<String> ALL_SCENARIO_KEYS = Arrays.asList(
        VERSIONS, TASKS, CLEANUP_TASKS, GRADLE_ARGS, RUN_USING, SYSTEM_PROPERTIES, WARM_UP_COUNT, APPLY_ABI_CHANGE_TO, APPLY_NON_ABI_CHANGE_TO, APPLY_ANDROID_RESOURCE_CHANGE_TO, APPLY_ANDROID_RESOURCE_VALUE_CHANGE_TO, APPLY_ANDROID_MANIFEST_CHANGE_TO, APPLY_PROPERTY_RESOURCE_CHANGE_TO, APPLY_CPP_SOURCE_CHANGE_TO, APPLY_H_SOURCE_CHANGE_TO, BUCK, MAVEN
    );
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

    private List<ScenarioDefinition> loadScenarios(File scenarioFile, InvocationSettings settings, GradleVersionInspector inspector) {
        List<ScenarioDefinition> definitions = new ArrayList<>();
        Config config = ConfigFactory.parseFile(scenarioFile, ConfigParseOptions.defaults().setAllowMissing(false));
        Set<String> selectedScenarios = new TreeSet<>(config.root().keySet());
        if (!settings.getTargets().isEmpty()) {
            for (String target : settings.getTargets()) {
                if (!selectedScenarios.contains(target)) {
                    throw new IllegalArgumentException("Unknown scenario '" + target + "' requested. Available scenarios are: " + selectedScenarios.stream() .collect(Collectors.joining(", "))); }
            }
            selectedScenarios = new TreeSet<>(settings.getTargets());
        }
        for (String scenarioName : selectedScenarios) {
            Config scenario = config.getConfig(scenarioName);
            for (String key : config.getObject(scenarioName).keySet()) {
                if (!ALL_SCENARIO_KEYS.contains(key)) {
                    throw new IllegalArgumentException("Unrecognized key '" + scenarioName + "." + key + "' defined in scenario file " + scenarioFile);
                }
            }

            int warmUpCount = integer(scenario, WARM_UP_COUNT, settings.getWarmUpCount());

            List<Supplier<BuildMutator>> mutators = new ArrayList<>();
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), APPLY_ABI_CHANGE_TO, ApplyAbiChangeToJavaSourceFileMutator.class, mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), APPLY_NON_ABI_CHANGE_TO, ApplyNonAbiChangeToJavaSourceFileMutator.class, mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), APPLY_ANDROID_RESOURCE_CHANGE_TO, ApplyChangeToAndroidResourceFileMutator.class, mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), APPLY_ANDROID_RESOURCE_VALUE_CHANGE_TO, ApplyValueChangeToAndroidResourceFileMutator.class, mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), APPLY_ANDROID_MANIFEST_CHANGE_TO, ApplyChangeToAndroidManifestFileMutator.class, mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), APPLY_PROPERTY_RESOURCE_CHANGE_TO, ApplyChangeToPropertyResourceFileMutator.class, mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), APPLY_CPP_SOURCE_CHANGE_TO, ApplyChangeToNativeSourceFileMutator.class, mutators);
            maybeAddMutator(scenario, scenarioName, settings.getProjectDir(), APPLY_H_SOURCE_CHANGE_TO, ApplyChangeToNativeSourceFileMutator.class, mutators);

            BuildMutatorFactory buildMutatorFactory = new BuildMutatorFactory(mutators);

            if (scenario.hasPath(BUCK) && settings.isBuck()) {
                if (settings.isProfile()) {
                    throw new IllegalArgumentException("Can only profile scenario '" + scenarioName + "' when building using Gradle.");
                }
                Config executionInstructions = scenario.getConfig(BUCK);
                for (String key : scenario.getObject(BUCK).keySet()) {
                    if (!BUCK_KEYS.contains(key)) {
                        throw new IllegalArgumentException("Unrecognized key '" + scenarioName + ".buck." + key + "' defined in scenario file " + scenarioFile);
                    }
                }
                List<String> targets = strings(executionInstructions, TARGETS, Collections.emptyList());
                String type = string(executionInstructions, TYPE, null);
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
                List<String> targets = strings(executionInstructions, TARGETS, Collections.emptyList());
                File outputDir = new File(settings.getOutputDir(), scenarioName + "-maven");
                definitions.add(new MavenScenarioDefinition(scenarioName, targets, buildMutatorFactory, warmUpCount, settings.getBuildCount(), outputDir));
            } else if (!settings.isBuck() && !settings.isMaven()) {
                List<GradleVersion> versions = strings(scenario, VERSIONS, settings.getVersions()).stream().map(v -> inspector.resolve(v)).collect(
                        Collectors.toList());
                if (versions.isEmpty()) {
                    versions.add(gradleVersionInspector.defaultVersion());
                }

                List<String> tasks = strings(scenario, TASKS, settings.getTargets());
                List<String> cleanupTasks = strings(scenario, CLEANUP_TASKS, Collections.emptyList());
                List<String> gradleArgs = strings(scenario, GRADLE_ARGS, Collections.emptyList());
                Invoker invoker = invoker(scenario, RUN_USING, settings.getInvoker());
                Map<String, String> systemProperties = map(scenario, SYSTEM_PROPERTIES, settings.getSystemProperties());
                for (GradleVersion version : versions) {
                    File outputDir = versions.size() == 1 ? new File(settings.getOutputDir(), scenarioName) : new File(settings.getOutputDir(), scenarioName + "/" + version.getVersion());
                    definitions.add(new GradleScenarioDefinition(scenarioName, invoker, version, tasks, cleanupTasks, gradleArgs, systemProperties,
                            buildMutatorFactory, warmUpCount, settings.getBuildCount(), outputDir));
                }
            }
        }

        return definitions;
    }

    private void maybeAddMutator(Config scenario, String scenarioName, File projectDir, String key, Class<? extends AbstractFileChangeMutator> mutatorClass, List<Supplier<BuildMutator>> mutators) {
        File sourceFileToChange = sourceFile(scenario, key, scenarioName, projectDir);
        if (sourceFileToChange != null) {
            mutators.add(() -> {
                try {
                    return mutatorClass.getConstructor(File.class).newInstance(sourceFileToChange);
                } catch (Exception e) {
                    throw new RuntimeException("Could not create instance of mutator " + mutatorClass.getSimpleName(), e);
                }
            });
        }
    }

    File sourceFile(Config config, String key, String scenarioName, File projectDir) {
        String sourceFileName = string(config, key, null);
        if (sourceFileName == null) {
            return null;
        }
        File sourceFile = new File(projectDir, sourceFileName);
        if (!sourceFile.isFile()) {
            throw new IllegalArgumentException("Source file " + sourceFile + " specified for scenario " + scenarioName + " does not exist.");
        }
        return sourceFile;
    }

    private Map<String, String> map(Config config, String key, Map<String, String> defaultValues) {
        if (config.hasPath(key)) {
            Map<String, String> props = new LinkedHashMap<>();
            for (Map.Entry<String, ConfigValue> entry : config.getConfig(key).entrySet()) {
                props.put(entry.getKey(), entry.getValue().unwrapped().toString());
            }
            return props;
        } else {
            return defaultValues;
        }
    }

    private static Invoker invoker(Config config, String key, Invoker defaultValue) {
        if (config.hasPath(key)) {
            String value = config.getAnyRef(key).toString();
            if (value.equals("no-daemon")) {
                return Invoker.NoDaemon;
            }
            if (value.equals("tooling-api")) {
                return Invoker.ToolingApi;
            }
            throw new IllegalArgumentException("Unexpected value for '" + key + "' provided: " + value);
        } else {
            return defaultValue;
        }
    }

    private static String string(Config config, String key, String defaultValue) {
        if (config.hasPath(key)) {
            return config.getString(key);
        } else {
            return defaultValue;
        }
    }

    private static int integer(Config config, String key, int defaultValue) {
        if (config.hasPath(key)) {
            return Integer.valueOf(config.getString(key));
        } else {
            return defaultValue;
        }
    }

    private static List<String> strings(Config config, String key, List<String> defaults) {
        if (config.hasPath(key)) {
            Object value = config.getAnyRef(key);
            if (value instanceof List) {
                List<?> list = (List) value;
                return list.stream().map(v -> v.toString()).collect(Collectors.toList());
            } else if (value.toString().length() > 0) {
                return Collections.singletonList(value.toString());
            }
        }
        return defaults;
    }
}
