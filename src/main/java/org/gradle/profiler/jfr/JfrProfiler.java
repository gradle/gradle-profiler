package org.gradle.profiler.jfr;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.api.JavaVersion;
import org.gradle.profiler.InstrumentingProfiler;
import org.gradle.profiler.JvmArgsCalculator;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ScenarioSettings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

public class JfrProfiler extends InstrumentingProfiler {
    private static final String PROFILE_JFR_SUFFIX = ".jfr";

    private final JFRArgs jfrArgs;
    private final File defaultConfig;

    public JfrProfiler() {
        this(null);
    }

    private JfrProfiler(JFRArgs jfrArgs) {
        this.jfrArgs = jfrArgs;
        this.defaultConfig = createDefaultConfig();
    }

    private static File createDefaultConfig() {
        try {
            File jfcFile = File.createTempFile("gradle", ".jfc");
            String jfcTemplateName = JavaVersion.current().isJava9Compatible() ? "gradle-java9.jfc" : "gradle.jfc";
            URL jfcResource = JfrProfiler.class.getResource(jfcTemplateName);
            try (InputStream stream = jfcResource.openStream()) {
                Files.copy(stream, jfcFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            jfcFile.deleteOnExit();
            return jfcFile;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "JFR";
    }

    @Override
    public void summarizeResultFile(File resultFile, Consumer<String> consumer) {
        if (resultFile.getName().endsWith(".jfr")) {
            consumer.accept("JFR recording: " + resultFile.getAbsolutePath());
        } else if (resultFile.getName().endsWith(".jfr-flamegraphs")) {
            consumer.accept("JFR Flame Graphs: " + resultFile.getAbsolutePath());
        }
    }

    @Override
    public Profiler withConfig(OptionSet parsedOptions) {
        return new JfrProfiler(newConfigObject(parsedOptions));
    }

    private JFRArgs newConfigObject(OptionSet parsedOptions) {
        String jfrSettings = (String) parsedOptions.valueOf("jfr-settings");
        if (jfrSettings.endsWith(".jfc")) {
            jfrSettings = new File(jfrSettings).getAbsolutePath();
        }
        return new JFRArgs(jfrSettings);
    }

    @Override
    protected SnapshotCapturingProfilerController doNewController(ScenarioSettings settings) {
        File jfrFile = getJfrFile(settings);
        return new JFRControl(getJfrArgs(), jfrFile);
    }

    @Override
    protected JvmArgsCalculator jvmArgsWithInstrumentation(ScenarioSettings settings, boolean startRecordingOnProcessStart, boolean captureSnapshotOnProcessExit) {
        File jfrFile = getJfrFile(settings);
        return new JFRJvmArgsCalculator(getJfrArgs(), startRecordingOnProcessStart, captureSnapshotOnProcessExit, jfrFile);
    }

    @Override
    public void addOptions(final OptionParser parser) {
        parser.accepts("jfr-settings", "JFR settings - Either a .jfc file or the name of a template known to your JFR installation")
                .availableIf("profile")
                .withOptionalArg()
                .defaultsTo(defaultConfig.getAbsolutePath());
    }

    private JFRArgs getJfrArgs() {
        return jfrArgs == null
            ? new JFRArgs(defaultConfig.getAbsolutePath())
            : jfrArgs;
    }

    private File getJfrFile(ScenarioSettings settings) {
        return new File(settings.getScenario().getOutputDir(), settings.getScenario().getProfileName() + PROFILE_JFR_SUFFIX);
    }
}
