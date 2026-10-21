package org.gradle.trace.pid;

class Java9CurrentProcess {
    static long getPid() {
        return ProcessHandle.current().pid();
    }
}
