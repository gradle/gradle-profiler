package org.gradle.profiler.jfr;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.api.JavaVersion;
import org.gradle.profiler.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;

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
    public List<String> summarizeResultFile(File resultFile) {
        if (resultFile.getName().endsWith(".jfr")) {
            return Collections.singletonList("JFR recording: " + resultFile.getAbsolutePath());
        }
        if (resultFile.getName().endsWith(".jfr-flamegraphs")) {
            return Collections.singletonList("JFR Flame Graphs: " + resultFile.getAbsolutePath());
        }
        return null;
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
    protected ProfilerController doNewController(String pid, ScenarioSettings settings) {
        File jfrFile = getJfrFile(settings);
        return new JFRControl(jfrArgs, pid, jfrFile);
    }

    @Override
    protected JvmArgsCalculator jvmArgsWithInstrumentation(ScenarioSettings settings, boolean startRecordingOnProcessStart, boolean captureSnapshotOnProcessExit) {
        File jfrFile = getJfrFile(settings);
        return new JFRJvmArgsCalculator(jfrArgs, startRecordingOnProcessStart, captureSnapshotOnProcessExit, jfrFile);
    }

    @Override
    public void addOptions(final OptionParser parser) {
        parser.accepts("jfr-settings", "JFR settings - Either a .jfc file or the name of a template known to your JFR installation")
                .availableIf("profile")
                .withOptionalArg()
                .defaultsTo(defaultConfig.getAbsolutePath());
    }

    private File getJfrFile(ScenarioSettings settings) {
        return new File(settings.getScenario().getOutputDir(), settings.getScenario().getProfileName() + PROFILE_JFR_SUFFIX);
    }
}
