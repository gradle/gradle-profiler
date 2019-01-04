package org.gradle.profiler.bs;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.profiler.GradleArgsCalculator;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class BuildScanProfiler extends Profiler {

    public final static String VERSION = "1.11";

    private final String buildScanVersion;
    /*
    buildScanAdditionalRepo may be needed if users run gradle-profiler in an environment that
    only has access to intranet (internal repo or artifacts) but does not have access to
    internet (plugins.gradle.org, the default repo)
     */
    private final String buildScanAdditionalRepo;

    public BuildScanProfiler() {
        this(null, null);
    }

    private BuildScanProfiler(String buildScanVersion, String buildScanAdditionalRepo) {
        this.buildScanVersion = buildScanVersion == null ? VERSION : buildScanVersion;
        this.buildScanAdditionalRepo = buildScanAdditionalRepo == null ? null : buildScanAdditionalRepo;
    }

    @Override
    public String toString() {
        return "buildscan";
    }

    private static class LogParser implements Consumer<String> {
		private static final Pattern RUNNING_SCENARIO = Pattern.compile("\\* Running scenario (.*) \\(scenario \\d+/\\d+\\)");
		private static final Pattern RUNNING_TASKS = Pattern.compile("\\* Running (.*) with (.*) tasks \\[(.*)]");
		private boolean nextLineIsBuildScanUrl;
		private String build = "UNKNOWN";
		private String step = "UNKNOWN";
		private String tasks = "UNKNOWN";
		private final List<String> results;

		public LogParser(List<String> results) {
			this.results = results;
		}

		@Override
		public void accept(String line) {
			if (nextLineIsBuildScanUrl) {
				results.add(String.format("- Build scan for '%s' %s [%s]: %s", build, step, tasks, line));
				nextLineIsBuildScanUrl = false;
			} else {
				Matcher tasksMatcher = RUNNING_TASKS.matcher(line);
				if (tasksMatcher.matches()) {
					build = tasksMatcher.group(1);
					step = tasksMatcher.group(2);
					tasks = tasksMatcher.group(3);
				} else if (line.equals("Publishing build scan...")) {
					nextLineIsBuildScanUrl = true;
				} else {
					Matcher scenarioMatcher = RUNNING_SCENARIO.matcher(line);
					if (scenarioMatcher.matches()) {
						String scenario = scenarioMatcher.group(1);
						if (!results.isEmpty()) {
							results.add("");
						}
						results.add(String.format("Scenario %s", scenario));
					}
				}
			}
		}
	}

    @Override
    public List<String> summarizeResultFile(File resultFile) {
        List<String> results = new ArrayList<>();
        if (resultFile.getName().equals("profile.log")) {
			LogParser logParser = new LogParser(results);
			try (Stream<String> logStream = Files.lines(resultFile.toPath())) {
                logStream.forEach(logParser);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return results;
    }

    @Override
    public GradleArgsCalculator newGradleArgsCalculator(ScenarioSettings settings) {
        return new GradleArgsCalculator() {
            @Override
            public void calculateGradleArgs(List<String> gradleArgs) {
                gradleArgs.addAll(new BuildScanInitScript(buildScanVersion, buildScanAdditionalRepo)
                    .getArgs());
            }
        };
    }

    @Override
    public GradleArgsCalculator newInstrumentedBuildsGradleArgsCalculator(ScenarioSettings settings) {
        return new GradleArgsCalculator() {
            @Override
            public void calculateGradleArgs(List<String> gradleArgs) {
                System.out.println("Using build scan profiler version " + buildScanVersion);
                gradleArgs.add("-Dscan");
            }
        };
    }

    @Override
    public Profiler withConfig(OptionSet parsedOptions) {
        return new BuildScanProfiler((String) parsedOptions.valueOf("buildscan-version"),
            (String) parsedOptions.valueOf("buildscan-additional-repo"));
    }

    @Override
    public void addOptions(final OptionParser parser) {
        parser.accepts("buildscan-version", "Version of the Build Scan plugin")
                .availableIf("profile")
                .withOptionalArg();
        parser.accepts("buildscan-additional-repo", "Additional repository for Build Scan")
            .availableIf("profile")
            .withOptionalArg();
    }
}
