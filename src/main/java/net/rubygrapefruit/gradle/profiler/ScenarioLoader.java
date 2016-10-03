package net.rubygrapefruit.gradle.profiler;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigValue;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

class ScenarioLoader {
    private final GradleVersionInspector gradleVersionInspector;

    public ScenarioLoader(GradleVersionInspector gradleVersionInspector) {
        this.gradleVersionInspector = gradleVersionInspector;
    }

    public List<ScenarioDefinition> loadScenarios(InvocationSettings settings) {
        List<ScenarioDefinition> scenarios = new ArrayList<>();
        if (settings.getConfigFile() != null) {
            scenarios = loadConfig(settings.getConfigFile(), settings, gradleVersionInspector);
        }
        if (scenarios.isEmpty()) {
            List<GradleVersion> versions = new ArrayList<>();
            for (String v : settings.getVersions()) {
                versions.add(gradleVersionInspector.resolve(v));
            }
            if (versions.isEmpty()) {
                versions.add(gradleVersionInspector.defaultVersion());
            }
            scenarios.add(new ScenarioDefinition("default", settings.getInvoker(), versions, settings.getTasks(), settings.getSystemProperties()));
        }
        return scenarios;
    }

    private List<ScenarioDefinition> loadConfig(File configFile, InvocationSettings settings, GradleVersionInspector inspector) {
        List<ScenarioDefinition> definitions = new ArrayList<>();
        Config config = ConfigFactory.parseFile(configFile, ConfigParseOptions.defaults().setAllowMissing(false));
        for (String scenarioName : new TreeSet<>(config.root().keySet())) {
            Config scenario = config.getConfig(scenarioName);
            List<GradleVersion> versions = strings(scenario, "versions", settings.getVersions()).stream().map(v -> inspector.resolve(v)).collect(
                    Collectors.toList());
            List<String> tasks = strings(scenario, "tasks", settings.getTasks());
            Invoker invoker = invoker(scenario, "run-using", settings.getInvoker());
            Map<String, String> systemProperties = map(scenario, "system-properties", settings.getSystemProperties());
            definitions.add(new ScenarioDefinition(scenarioName, invoker, versions, tasks, systemProperties));
        }
        return definitions;
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

    private static List<String> strings(Config config, String key, List<String> defaults) {
        if (config.hasPath(key)) {
            Object value = config.getAnyRef(key);
            if (value instanceof List) {
                List<?> list = (List) value;
                return list.stream().map(v -> v.toString()).collect(Collectors.toList());
            } else {
                return Collections.singletonList(value.toString());
            }
        } else {
            return defaults;
        }
    }
}
