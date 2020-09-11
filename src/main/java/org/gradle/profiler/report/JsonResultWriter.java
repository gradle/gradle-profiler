package org.gradle.profiler.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.gradle.profiler.ScenarioDefinition;
import org.gradle.profiler.Version;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import java.io.Writer;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;

public class JsonResultWriter {

    private final boolean pretty;

    public JsonResultWriter(boolean pretty) {
        this.pretty = pretty;
    }

    public void write(List<? extends BuildScenarioResult<?>> scenarios, Writer writer) {
        GsonBuilder builder = new GsonBuilder();
        if (pretty) {
            builder.setPrettyPrinting();
        }
        Gson gson = builder
            .registerTypeHierarchyAdapter(BuildScenarioResult.class, (JsonSerializer<? extends BuildScenarioResult<?>>) this::serializeScenarioResult)
            .registerTypeHierarchyAdapter(ScenarioDefinition.class, (JsonSerializer<ScenarioDefinition>) this::serializeScenarioDefinition)
            .create();
        gson.toJson(new Output(new Environment(), scenarios), writer);
    }

    private static class Environment {
        final String profilerVersion;

        public Environment() {
            this.profilerVersion = Version.getVersion();
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
        json.add("definition", context.serialize(scenarioResult.getScenarioDefinition()));
        JsonArray samplesJson = new JsonArray();
        List<Sample<? super T>> samples = scenarioResult.getSamples();
        for (Sample<? super T> sample : samples) {
            samplesJson.add(serializeSample(scenarioResult, sample));
        }
        json.add("samples", samplesJson);
        JsonArray iterationsJson = new JsonArray();
        for (T result : scenarioResult.getResults()) {
            iterationsJson.add(serializeIteration(result, samples));
        }
        json.add("iterations", iterationsJson);
        return json;
    }

    private JsonObject serializeScenarioDefinition(ScenarioDefinition scenario, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.addProperty("name", scenario.getName());
        json.addProperty("title", scenario.getTitle());
        json.addProperty("displayName", scenario.getDisplayName());
        json.addProperty("buildTool", scenario.getBuildToolDisplayName());
        json.addProperty("tasks", scenario.getTasksDisplayName());
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
            valuesJson.addProperty(sample.getName(), value.toNanos() / 1000000d );
        }
        json.add("values", valuesJson);
        return json;
    }
}
