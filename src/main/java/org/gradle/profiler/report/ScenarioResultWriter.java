package org.gradle.profiler.report;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.gradle.profiler.Phase;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Serializes the result of a single scenario, including its definition, the statistics
 * of each measured sample and the values of every iteration.
 */
public class ScenarioResultWriter {

    private ScenarioResultWriter() {
    }

    public static <T extends BuildInvocationResult> JsonObject serialize(BuildScenarioResult<T> scenarioResult, @Nullable BuildScenarioResult<?> baseline, Gson gson) {
        JsonObject json = new JsonObject();
        List<T> results = scenarioResult.getResults();

        // TODO Expose this in a less awkward way
        JsonObject jsonDefinition = (JsonObject) gson.toJsonTree(scenarioResult.getScenarioDefinition());
        String scenarioId = results.isEmpty()
            ? null
            : results.get(0).getBuildContext().getUniqueScenarioId();
        jsonDefinition.addProperty("id", scenarioId);
        json.add("definition", jsonDefinition);

        JsonArray samplesJson = new JsonArray();
        List<Sample<? super T>> samples = scenarioResult.getSamples();
        for (Sample<? super T> sample : samples) {
            samplesJson.add(serializeSample(scenarioResult, sample, baseline, gson));
        }
        json.add("samples", samplesJson);
        JsonArray iterationsJson = new JsonArray();
        for (T result : results) {
            iterationsJson.add(serializeIteration(result, samples));
        }
        json.add("iterations", iterationsJson);
        return json;
    }

    private static <T extends BuildInvocationResult> JsonObject serializeSample(BuildScenarioResult<T> scenarioResult, Sample<? super T> sample, @Nullable BuildScenarioResult<?> baseline, Gson gson) {
        JsonObject json = new JsonObject();
        json.addProperty("name", sample.getName());
        json.addProperty("unit", sample.getUnit());
        double[] values = measuredValues(scenarioResult, sample);
        if (values.length > 0) {
            json.add("stats", gson.toJsonTree(SampleStatistics.from(values)));
            double[] baselineValues = baseline == null ? new double[0] : measuredValues(baseline, sample.getName());
            if (baselineValues.length > 0) {
                double confidence = SampleStatistics.confidenceOfDifference(baselineValues, values);
                if (!Double.isNaN(confidence)) {
                    JsonObject confidenceJson = new JsonObject();
                    confidenceJson.addProperty("baseline", baseline.getResults().get(0).getBuildContext().getUniqueScenarioId());
                    confidenceJson.addProperty("value", confidence);
                    json.add("confidence", confidenceJson);
                }
            }
        }
        return json;
    }

    private static <T extends BuildInvocationResult> double[] measuredValues(BuildScenarioResult<T> scenarioResult, String sampleName) {
        return scenarioResult.getSamples().stream()
            .filter(sample -> sample.getName().equals(sampleName))
            .findFirst()
            .map(sample -> measuredValues(scenarioResult, sample))
            .orElse(new double[0]);
    }

    private static <T extends BuildInvocationResult> double[] measuredValues(BuildScenarioResult<T> scenarioResult, Sample<? super T> sample) {
        return scenarioResult.getResults().stream()
            .filter(result -> result.getBuildContext().getPhase() == Phase.MEASURE)
            .mapToDouble(sample::extractValue)
            .toArray();
    }

    private static <T extends BuildInvocationResult> JsonObject serializeIteration(T result, List<? extends Sample<? super T>> samples) {
        JsonObject json = new JsonObject();
        json.addProperty("id", result.getBuildContext().getUniqueBuildId());
        json.addProperty("phase", result.getBuildContext().getPhase().name());
        json.addProperty("iteration", result.getBuildContext().getIteration());
        json.addProperty("title", result.getBuildContext().getDisplayName());
        JsonObject valuesJson = new JsonObject();
        for (Sample<? super T> sample : samples) {
            valuesJson.addProperty(sample.getName(), sample.extractValue(result));
        }
        json.add("values", valuesJson);
        return json;
    }
}
