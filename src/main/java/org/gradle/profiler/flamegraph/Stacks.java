package org.gradle.profiler.flamegraph;

import java.io.File;

public class Stacks {
    private final File file;
    private final EventType type;
    private final DetailLevel level;
    private final String fileBaseName;

    public Stacks(File file, EventType type, DetailLevel level, String fileBaseName) {
        this.file = file;
        this.type = type;
        this.level = level;
        this.fileBaseName = fileBaseName;
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
}
