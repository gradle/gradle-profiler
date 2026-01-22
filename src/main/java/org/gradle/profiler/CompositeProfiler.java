package org.gradle.profiler;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

class CompositeProfiler extends Profiler {
    private final List<Profiler> delegates;

    CompositeProfiler(final List<Profiler> delegates) {
        this.delegates = delegates;
    }

    @Override
    public boolean requiresGradle() {
        return delegates.stream().anyMatch(Profiler::requiresGradle);
    }

    @Override
    public String toString() {
        return delegates.stream().map(Object::toString).collect(Collectors.joining(", "));
    }

    @Override
    public void validate(ScenarioSettings settings, Consumer<String> reporter) {
        for (Profiler delegate : delegates) {
            delegate.validate(settings, reporter);
        }
    }

    @Override
    public void summarizeResultFile(File resultFile, Consumer<String> consumer) {
        for (Profiler delegate : delegates) {
            delegate.summarizeResultFile(resultFile, consumer);
        }
    }

    @Override
    public ProfilerController newController(final String pid, final ScenarioSettings settings) {
        List<ProfilerController> controllers = delegates.stream()
            .map((Profiler prof) -> prof.newController(pid,
                settingsFor(prof, settings)))
            .collect(Collectors.toList());
        return new ProfilerController() {
            @Override
            public void startSession() throws IOException, InterruptedException {
                for (ProfilerController controller : controllers) {
                    controller.startSession();
                }
            }

            @Override
            public void startRecording() throws IOException, InterruptedException {
                for (ProfilerController controller : controllers) {
                    controller.startRecording();
                }
            }

            @Override
            public void stopRecording(String pid) throws IOException, InterruptedException {
                for (ProfilerController controller : controllers) {
                    controller.stopRecording(pid);
                }
            }

            @Override
            public void stopSession() throws IOException, InterruptedException {
                for (ProfilerController controller : controllers) {
                    controller.stopSession();
                }
            }
        };
    }

    private ScenarioSettings settingsFor(final Profiler prof, final ScenarioSettings scenarioSettings) {
        InvocationSettings settings = scenarioSettings.getInvocationSettings();
        InvocationSettings newSettings = settings.newBuilder()
            .setProfiler(prof)
            .build();
        return new ScenarioSettings(newSettings, scenarioSettings.getScenario());
    }

    @Override
    public JvmArgsCalculator newJvmArgsCalculator(final ScenarioSettings settings) {
        return jvmArgs -> delegates.forEach(prof -> prof.newJvmArgsCalculator(settingsFor(prof, settings)).calculateJvmArgs(jvmArgs));
    }

    @Override
    public JvmArgsCalculator newInstrumentedBuildsJvmArgsCalculator(ScenarioSettings settings) {
        return jvmArgs -> delegates.forEach(prof -> prof.newInstrumentedBuildsJvmArgsCalculator(settingsFor(prof, settings)).calculateJvmArgs(jvmArgs));
    }

    @Override
    public GradleArgsCalculator newGradleArgsCalculator(ScenarioSettings settings) {
        return new GradleArgsCalculator() {
            @Override
            public void calculateGradleArgs(List<String> gradleArgs) {
                delegates.forEach(prof -> prof.newGradleArgsCalculator(settingsFor(prof, settings)).calculateGradleArgs(gradleArgs));
            }
        };
    }

    @Override
    public GradleArgsCalculator newInstrumentedBuildsGradleArgsCalculator(ScenarioSettings settings) {
        return new GradleArgsCalculator() {
            @Override
            public void calculateGradleArgs(List<String> gradleArgs) {
                delegates.forEach(prof -> prof.newInstrumentedBuildsGradleArgsCalculator(settingsFor(prof, settings)).calculateGradleArgs(gradleArgs));
            }
        };
    }

    @Override
    public boolean isCreatesStacksFiles() {
        return delegates.stream().anyMatch(Profiler::isCreatesStacksFiles);
    }
}
