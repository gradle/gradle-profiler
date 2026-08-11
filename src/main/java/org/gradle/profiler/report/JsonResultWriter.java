package org.gradle.profiler.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.gradle.profiler.gradle.GradleScenarioDefinition;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.ScenarioDefinition;
import org.gradle.profiler.Version;
import org.gradle.profiler.maven.MavenScenarioDefinition;

import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.io.Writer;
import java.lang.reflect.Type;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class JsonResultWriter {

    private final boolean pretty;

    public JsonResultWriter(boolean pretty) {
        this.pretty = pretty;
    }

    public void write(@Nullable String title, Temporal reportDate, List<? extends BuildScenarioResult<?>> scenarios, Writer writer) {
        GsonBuilder builder = new GsonBuilder();
        if (pretty) {
            builder.setPrettyPrinting();
        }
        Gson gson = builder
            .registerTypeHierarchyAdapter(ScenarioDefinition.class, new ScenarioSerializer<>())
            .registerTypeHierarchyAdapter(GradleScenarioDefinition.class, new GradleScenarioSerializer())
            .registerTypeHierarchyAdapter(MavenScenarioDefinition.class, new MavenScenarioSerializer())
            .registerTypeHierarchyAdapter(Temporal.class, (JsonSerializer<Temporal>) (date, type, context) -> new JsonPrimitive(DateTimeFormatter.ISO_INSTANT.format(date)))
            .create();

        JsonObject json = new JsonObject();
        if (title != null) {
            json.addProperty("title", title);
        }
        json.add("date", gson.toJsonTree(reportDate));
        json.add("environment", gson.toJsonTree(new Environment()));
        writeScenarioResults(scenarios, gson, json);
        gson.toJson(json, writer);
    }

    private static class Environment {
        final String profilerVersion;
        final String operatingSystem;

        public Environment() {
            this.profilerVersion = Version.getVersion();
            this.operatingSystem = OperatingSystem.getId();
        }
    }

    private static void writeScenarioResults(List<? extends BuildScenarioResult<?>> results, Gson gson, JsonObject json) {
        JsonArray scenariosJson = new JsonArray();
        for (BuildScenarioResult<?> scenario : results) {
            scenariosJson.add(ScenarioResultWriter.serialize(scenario, gson));
        }
        json.add("scenarios", scenariosJson);
    }

    private static class ScenarioSerializer<T extends ScenarioDefinition> implements JsonSerializer<T> {
        @Override
        @OverridingMethodsMustInvokeSuper
        public JsonObject serialize(T scenario, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject json = new JsonObject();
            json.addProperty("name", scenario.getName());
            json.addProperty("title", scenario.getTitle());
            json.addProperty("displayName", scenario.getDisplayName());
            json.addProperty("buildTool", scenario.getBuildToolDisplayName());
            json.addProperty("tasks", scenario.getTasksDisplayName());
            return json;
        }
    }

    private static class GradleScenarioSerializer extends ScenarioSerializer<GradleScenarioDefinition> {
        @Override
        public JsonObject serialize(GradleScenarioDefinition scenario, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject json = super.serialize(scenario, typeOfSrc, context);
            json.addProperty("version", scenario.getBuildConfiguration().getGradleVersion().getVersion());
            json.addProperty("gradleHome", scenario.getBuildConfiguration().getGradleHome().getAbsolutePath());
            json.addProperty("javaHome", scenario.getBuildConfiguration().getJavaHome().getAbsolutePath());
            json.addProperty("usesScanPlugin", scenario.getBuildConfiguration().isUsesScanPlugin());
            json.addProperty("action", scenario.getAction().getDisplayName());
            json.addProperty("cleanup", scenario.getCleanupAction().getDisplayName());
            json.addProperty("invoker", scenario.getInvoker().toString());
            json.add("mutators", toJson(scenario.getBuildMutators().stream().map(Object::toString)));
            json.add("args", toJson(scenario.getGradleArgs().stream()));
            json.add("jvmArgs", toJson(Stream.concat(scenario.getBuildConfiguration().getJvmArguments().stream(), scenario.getJvmArgs().stream())));
            json.add("systemProperties", toJson(scenario.getSystemProperties()));
            return json;
        }
    }

    private static class MavenScenarioSerializer extends ScenarioSerializer<MavenScenarioDefinition> {
        @Override
        public JsonObject serialize(MavenScenarioDefinition scenario, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject json = super.serialize(scenario, typeOfSrc, context);
            json.add("systemProperties", toJson(scenario.getSystemProperties()));
            return json;
        }
    }

    private static JsonArray toJson(Stream<String> array) {
        JsonArray json = new JsonArray();
        array.forEach(json::add);
        return json;
    }

    private static JsonObject toJson(Map<String, String> values) {
        JsonObject json = new JsonObject();
        values.forEach(json::addProperty);
        return json;
    }
}
