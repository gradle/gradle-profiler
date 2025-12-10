package org.gradle.profiler.buildscan;

import org.gradle.profiler.BuildConfiguration;
import org.gradle.profiler.GeneratedInitScript;
import org.gradle.profiler.GradleArgsCalculator;
import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ScenarioSettings;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class BuildScanProfiler extends Profiler {

    private static final GradleVersion GRADLE_5 = GradleVersion.version("5.0");
    private static final GradleVersion GRADLE_6 = GradleVersion.version("6.0");

    /**
     * The effective version of the Build Scan or Develocity plugin to use.
     * <p>
     * Depending on the Gradle version, we return:
     * - Build Scan plugin version for Gradle &lt; 6.x
     * - Develocity plugin version for Gradle &gt;= 6.x
     */
    public static String defaultBuildScanVersion(GradleVersion gradleVersion) {
        if (gradleVersion.compareTo(GRADLE_5) < 0) {
            return "1.16";
        } else if (gradleVersion.compareTo(GRADLE_6) < 0) {
            return "2.4.2";
        } else {
            return "4.2.2";
        }
    }

    private final String buildScanVersion;

    BuildScanProfiler(String buildScanVersion) {
        this.buildScanVersion = buildScanVersion;
    }

    @Override
    public boolean requiresGradle() {
        return true;
    }

    @Override
    public String toString() {
        return "buildscan";
    }

    private static class LogParser implements Consumer<String> {
        private static final Pattern RUNNING_SCENARIO = Pattern.compile("\\* Running scenario (.*) \\(scenario \\d+/\\d+\\)");
        private static final Pattern RUNNING_BUILD = Pattern.compile("\\* Running measured build #(\\d+)");
        private boolean nextLineIsBuildScanUrl;
        private String measuredBuildNumber = null;
        private final Consumer<String> results;

        public LogParser(Consumer<String> results) {
            this.results = results;
        }

        @Override
        public void accept(String line) {
            if (nextLineIsBuildScanUrl) {
                if (measuredBuildNumber != null) {
                    results.accept(String.format("- Build scan for measured build #%s: %s", measuredBuildNumber, line));
                    measuredBuildNumber = null;
                }
                nextLineIsBuildScanUrl = false;
            } else {
                Matcher buildMatcher = RUNNING_BUILD.matcher(line);
                if (buildMatcher.matches()) {
                    measuredBuildNumber = buildMatcher.group(1);
                } else if (line.equals("Publishing build scan...") || line.equals("Publishing Build Scan...") || line.equals("Publishing Build Scan to Develocity...")) {
                    nextLineIsBuildScanUrl = true;
                } else {
                    Matcher scenarioMatcher = RUNNING_SCENARIO.matcher(line);
                    if (scenarioMatcher.matches()) {
                        String scenario = scenarioMatcher.group(1);
                        results.accept(String.format("Scenario %s", scenario));
                    }
                }
            }
        }
    }

    @Override
    public void summarizeResultFile(File resultFile, Consumer<String> consumer) {
        if (resultFile.getName().equals("profile.log")) {
            LogParser logParser = new LogParser(consumer);
            try (Stream<String> logStream = Files.lines(resultFile.toPath())) {
                logStream.forEach(logParser);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public GradleArgsCalculator newGradleArgsCalculator(ScenarioSettings settings) {
        return gradleArgs -> {
            BuildConfiguration buildConfiguration = settings.getScenario().getBuildConfiguration();
            Optional<GeneratedInitScript> initScript = getInitScript(buildConfiguration.as(GradleBuildConfiguration.class));
            initScript.ifPresent(script -> script.calculateGradleArgs(gradleArgs));
        };
    }

    private Optional<GeneratedInitScript> getInitScript(GradleBuildConfiguration buildConfiguration) {
        if (buildConfiguration.isUsesDevelocityPlugin()) {
            return Optional.of(new DevelocityAlreadyAppliedInitScript());
        } else if (buildConfiguration.isUsesScanPlugin() && buildConfiguration.getGradleVersion().compareTo(GRADLE_6) >= 0) {
            return Optional.of(new GradleEnterpriseAlreadyAppliedInitScript());
        } else if (!buildConfiguration.isUsesScanPlugin()) {
            String effectiveDevelocityPluginVersion = getEffectiveBuildScanOrDevelocityPluginVersion(buildConfiguration);
            return (buildConfiguration.getGradleVersion().compareTo(GRADLE_6) < 0)
                ? Optional.of(new BuildScanInitScript(effectiveDevelocityPluginVersion))
                : Optional.of(new DevelocityInitScript(effectiveDevelocityPluginVersion));
        }
        return Optional.empty();
    }

    @Override
    public GradleArgsCalculator newInstrumentedBuildsGradleArgsCalculator(ScenarioSettings settings) {
        return gradleArgs -> {
            GradleBuildConfiguration buildConfiguration = settings.getScenario().getBuildConfiguration().as(GradleBuildConfiguration.class);
            if (buildConfiguration.isUsesScanPlugin()) {
                System.out.println("Using build scan plugin specified in the build");
            } else {
                System.out.println("Using build scan plugin " + getEffectiveBuildScanOrDevelocityPluginVersion(buildConfiguration));
            }
            if (buildConfiguration.getGradleVersion().compareTo(GRADLE_5) < 0) {
                gradleArgs.add("-Dscan");
            } else if (buildConfiguration.getGradleVersion().compareTo(GRADLE_6) < 0) {
                gradleArgs.add("--scan");
            }
        };
    }

    /**
     * The effective version of the Build Scan or Develocity plugin to use.
     * <p>
     * Depending on the Gradle version, and whether the configuration specified a version, we return:
     * - Version specified in the configuration if available
     * - Build Scan plugin version for Gradle < 6.x
     * - Develocity plugin version for Gradle >= 6.x
     */
    private String getEffectiveBuildScanOrDevelocityPluginVersion(GradleBuildConfiguration buildConfiguration) {
        return buildScanVersion != null ? buildScanVersion : defaultBuildScanVersion(buildConfiguration.getGradleVersion());
    }
}
