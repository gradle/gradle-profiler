package org.gradle.profiler;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionInspector {

    public final static VersionInspector BAZEL = new VersionInspector("bazel", "bazel", "Build label: (.+)", "bazel version");
    public final static VersionInspector BUCK = new VersionInspector("buck", "buck", "buck version v(.+)", "buck", "--version");

    private Version cachedVersion;
    private final String name;
    private final String defaultVersion;
    private final String[] commands;
    private final String regex;

    private VersionInspector(String name, String defaultVersion, String regex, String... versionCommands) {
        this.name = name;
        this.defaultVersion = defaultVersion;
        this.regex = regex;
        this.commands = versionCommands;
    }

    public Version getVersion() {
        if (cachedVersion == null) {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(commands);
                processBuilder.redirectError();
                Process process = processBuilder.start();
                process.waitFor();
                String output = IOUtils.toString(process.getInputStream(), "UTF-8");
                // parse the output
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(output);
                if (matcher.find()) {
                    String version = matcher.group(1);
                    cachedVersion = new Version(version);
                } else {
                    cachedVersion = new Version(defaultVersion);
                }

            } catch (InterruptedException | IOException e) {
                System.err.println("Unable to obtain the " + name + " version");
                e.printStackTrace();
                cachedVersion = new Version(defaultVersion);
            }

        }
        return cachedVersion;
    }
}
