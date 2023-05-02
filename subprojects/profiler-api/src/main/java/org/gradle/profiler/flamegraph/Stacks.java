package org.gradle.profiler.flamegraph;

import java.io.File;
import java.util.Locale;

public class Stacks {
    public static final String STACKS_FILE_SUFFIX = "-stacks.txt";

    private final File file;
    private final EventType type;
    private final DetailLevel level;
    private final String fileBaseName;
    private final boolean negate;

    public static String postFixFor(EventType type, DetailLevel level) {
        return "-" + type.getId() + "-" + level.name().toLowerCase(Locale.ROOT);
    }

    public Stacks(File file, EventType type, DetailLevel level, String fileBaseName) {
        this(file, type, level, fileBaseName, false);
    }

    public Stacks(File file, EventType type, DetailLevel level, String fileBaseName, boolean negate) {
        this.file = file;
        this.type = type;
        this.level = level;
        this.fileBaseName = fileBaseName;
        this.negate = negate;
    }

    public File getFile() {
        return file;
    }

    public EventType getType() {
        return type;
    }

    public DetailLevel getLevel() {
        return level;
    }

    public boolean isEmpty() {
        return file.length() == 0;
    }

    public String getFileBaseName() {
        return fileBaseName;
    }

    public boolean isNegate() {
        return negate;
    }
}
