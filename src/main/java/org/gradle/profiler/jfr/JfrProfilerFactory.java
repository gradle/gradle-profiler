package org.gradle.profiler.jfr;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.api.JavaVersion;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ProfilerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public class JfrProfilerFactory extends ProfilerFactory {
    private final File defaultConfig = createDefaultConfig();

    private File createDefaultConfig() {
        try {
            File jfcFile = File.createTempFile("gradle", ".jfc");
            String jfcTemplateName;
            if (isOracleVm()) {
                jfcTemplateName = JavaVersion.current().isJava9Compatible() ? "oracle-java9.jfc" : "oracle.jfc";
            } else if (JavaVersion.current().isJava8Compatible()) {
                jfcTemplateName = "openjdk.jfc";
            } else {
                throw new IllegalArgumentException("JFR is only supported on OpenJDK since Java 8 and Oracle JDK since Java 7");
            }
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
    public void addOptions(final OptionParser parser) {
        parser.accepts("jfr-settings", "JFR settings - Either a .jfc file or the name of a template known to your JFR installation")
            .availableIf("profile")
            .withOptionalArg()
            .defaultsTo(defaultConfig.getAbsolutePath());
    }

    @Override
    public Profiler createFromOptions(OptionSet parsedOptions) {
        return new JfrProfiler(newConfigObject(parsedOptions));
    }

    private JFRArgs newConfigObject(OptionSet parsedOptions) {
        String jfrSettings = (String) parsedOptions.valueOf("jfr-settings");
        if (jfrSettings.endsWith(".jfc")) {
            jfrSettings = new File(jfrSettings).getAbsolutePath();
        }
        return new JFRArgs(jfrSettings);
    }

    private boolean isOracleVm() {
        return System.getProperty("java.vendor").toLowerCase(Locale.ROOT).contains("oracle");
    }
}
