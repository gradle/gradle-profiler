package org.gradle.profiler.ide;

public interface RunIdeContext {
    RunIdeContext withSystemProperty(String key, String value);
    CommandChain withCommands();
}
