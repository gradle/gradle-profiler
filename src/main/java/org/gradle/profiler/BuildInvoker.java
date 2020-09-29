package org.gradle.profiler;

public class BuildInvoker {
    public static final BuildInvoker Bazel = new BuildInvoker() {
        @Override
        public String toString() {
            return "Bazel";
        }
    };
    public static final BuildInvoker Buck = new BuildInvoker() {
        @Override
        public String toString() {
            return "Buck";
        }
    };
    public static final BuildInvoker Maven = new BuildInvoker() {
        @Override
        public String toString() {
            return "Maven";
        }
    };

    public int benchmarkWarmUps() {
        return 6;
    }

    public int profileWarmUps() {
        return 2;
    }

    public boolean allowsCleanupBetweenBuilds() {
        return true;
    }
}
