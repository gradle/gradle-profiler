package org.gradle.profiler.mutations;

import java.util.List;

/**
 * Interface which provides a means to execute a command
 */
public interface CommandInvoker {

    /**
     * @param command the command to execute
     * @return the exit code of the result of executing the command
     */
    int execute(List<String> command);

}
