package org.gradle.profiler;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class SudoCommandExec extends CommandExec {
    private static final List<String> SUDO_PREFIX = Collections.unmodifiableList(Arrays.asList("sudo", "-n")); // -n = --non-interactive, don't prompt for password
    private static final Pattern PREFIXED_WITH_SUDO = Pattern.compile("^sudo($| .*)");
    private final boolean useSudo;

    public SudoCommandExec() {
        this(null);
    }

    protected SudoCommandExec(File dir) {
        super(dir);
        useSudo = shouldUseSudo();
    }

    @Override
    public CommandExec inDir(File directory) {
        return new SudoCommandExec(directory);
    }

    protected boolean shouldUseSudo() {
        return !System.getProperty("user.name").equals("root");
    }

    @Override
    protected RunHandle run(ProcessBuilder processBuilder, OutputStream outputStream, OutputStream errorStream, InputStream inputStream) {
        maybeAddSudo(processBuilder);
        return super.run(processBuilder, outputStream, errorStream, inputStream);
    }

    void maybeAddSudo(ProcessBuilder processBuilder) {
        if (useSudo) {
            List<String> commandLine = new ArrayList<>(processBuilder.command());
            String firstCommand = commandLine.get(0);
            if (!PREFIXED_WITH_SUDO.matcher(firstCommand).matches()) {
                commandLine.addAll(0, SUDO_PREFIX);
            }
            processBuilder.command(commandLine);
        }
    }
}
