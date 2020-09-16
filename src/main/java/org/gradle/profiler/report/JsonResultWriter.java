package org.gradle.profiler.report;

import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.gradle.profiler.GradleScenarioDefinition;
import org.gradle.profiler.OperatingSystem;
import org.gradle.profiler.ScenarioDefinition;
import org.gradle.profiler.Version;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.io.Writer;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.Map;

public class JsonResultWriter {

    private final boolean pretty;

    public JsonResultWriter(boolean pretty) {
        this.pretty = pretty;
    }

    public void write(List<? extends BuildScenarioResult<?>> scenarios, Temporal reportDate, Writer writer) {
        GsonBuilder builder = new GsonBuilder();
        if (pretty) {
            builder.setPrettyPrinting();
        }
        Gson gson = builder
            .registerTypeHierarchyAdapter(BuildScenarioResult.class, (JsonSerializer<? extends BuildScenarioResult<?>>) this::serializeScenarioResult)
            .registerTypeHierarchyAdapter(ScenarioDefinition.class, new ScenarioSerializer<>())
            .registerTypeHierarchyAdapter(GradleScenarioDefinition.class, new GradleScenarioSerializer())
            .registerTypeHierarchyAdapter(Temporal.class, (JsonSerializer<Temporal>) (date, type, context) -> new JsonPrimitive(DateTimeFormatter.ISO_INSTANT.format(date)))
            .create();
        gson.toJson(new Output(new Environment(reportDate), scenarios), writer);
    }

    private static class Environment {
        final String profilerVersion;
        final String operatingSystem;
        final Temporal date;

        public Environment(Temporal date) {
            this.profilerVersion = Version.getVersion();
            this.operatingSystem = OperatingSystem.getId();
            this.date = date;
        }
    }

    private static class Output {
        final Environment environment;
        final List<? extends BuildScenarioResult<?>> scenarios;

        public Output(Environment environment, List<? extends BuildScenarioResult<?>> scenarios) {
            this.environment = environment;
            this.scenarios = scenarios;
        }
    }

    private <T extends BuildInvocationResult> JsonObject serializeScenarioResult(BuildScenarioResult<T> scenarioResult, Type type, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        List<T> results = scenarioResult.getResults();

        // TODO Expose this in a less awkward way
        JsonObject jsonDefinition = (JsonObject) context.serialize(scenarioResult.getScenarioDefinition());
        String scenarioId = results.isEmpty()
            ? null
            : results.get(0).getBuildContext().getUniqueScenarioId();
        jsonDefinition.addProperty("id", scenarioId);
        json.add("definition", jsonDefinition);

        JsonArray samplesJson = new JsonArray();
        List<Sample<? super T>> samples = scenarioResult.getSamples();
        for (Sample<? super T> sample : samples) {
            samplesJson.add(serializeSample(scenarioResult, sample));
        }
        json.add("samples", samplesJson);
        JsonArray iterationsJson = new JsonArray();
        for (T result : results) {
            iterationsJson.add(serializeIteration(result, samples));
        }
        json.add("iterations", iterationsJson);
        return json;
    }

    private JsonObject serializeSample(BuildScenarioResult<? extends BuildInvocationResult> scenarioResult, Sample<?> sample) {
        JsonObject json = new JsonObject();
        json.addProperty("name", sample.getName());
        json.addProperty("confidence", scenarioResult.getStatistics().get(sample).getConfidencePercent() / 100d);
        return json;
    }

    private <T extends BuildInvocationResult> JsonObject serializeIteration(T result, List<? extends Sample<? super T>> samples) {
        JsonObject json = new JsonObject();
        json.addProperty("id", result.getBuildContext().getUniqueBuildId());
        json.addProperty("phase", result.getBuildContext().getPhase().name());
        json.addProperty("iteration", result.getBuildContext().getIteration());
        json.addProperty("title", result.getBuildContext().getDisplayName());
        JsonObject valuesJson = new JsonObject();
        for (Sample<? super T> sample : samples) {
            Duration value = sample.extractFrom(result);
            valuesJson.addProperty(sample.getName(), value.toNanos() / 1000000d);
        }
        json.add("values", valuesJson);
        return json;
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
            json.addProperty("mutator", scenario.getBuildMutator().get().toString());
            json.add("args", toJson(scenario.getGradleArgs()));
            json.add("jvmArgs", toJson(Iterables.concat(scenario.getBuildConfiguration().getJvmArguments(), scenario.getJvmArgs())));
            json.add("systemProperties", toJson(scenario.getSystemProperties()));
            return json;
        }
    }

    private static JsonArray toJson(Iterable<String> array) {
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
