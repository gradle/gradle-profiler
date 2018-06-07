package org.gradle.profiler.jfr;

import com.github.chrishantha.jfr.flamegraph.output.Application;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Converts JFR recordings to the collapsed stacks format used by the FlameGraph tool.
 */
class JfrToStacksConverter {
    public void convertToStacks(File jfrRecording, File stacks, Collection<String> options) {
        stacks.getParentFile().mkdirs();
        List<String> allargs = new ArrayList<>();
        allargs.add("--jfrdump");
        allargs.add(jfrRecording.getAbsolutePath());
        allargs.add("--output");
        allargs.add(stacks.getAbsolutePath());
        allargs.addAll(options);
        try {
            Application.main(allargs.toArray(new String[0]));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
