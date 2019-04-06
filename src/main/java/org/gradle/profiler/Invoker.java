package org.gradle.profiler;

public enum Invoker {
    ToolingApi() {
        @Override
        public String toString() {
            return "Tooling API";
        }

        @Override
        public boolean isGradle() {
            return true;
        }

        @Override
        public boolean isReuseDaemon() {
            return true;
        }
    },
    ToolingApiColdDaemon() {
        @Override
        public String toString() {
            return "Tooling API with cold daemon";
        }

        @Override
        public boolean isGradle() {
            return true;
        }

        @Override
        public boolean isColdDaemon() {
            return true;
        }
    },
    Cli() {
        @Override
        public String toString() {
            return "`gradle` command";
        }

        @Override
        public boolean isGradle() {
            return true;
        }

        @Override
        public boolean isReuseDaemon() {
            return true;
        }
    },
    CliNoDaemon() {
        @Override
        public String toString() {
            return "`gradle` command with --no-daemon";
        }

        @Override
        public boolean isGradle() {
            return true;
        }
    },
    CliColdDaemon() {
        @Override
        public String toString() {
            return "`gradle` command with cold daemon";
        }

        @Override
        public boolean isGradle() {
            return true;
        }

        @Override
        public boolean isColdDaemon() {
            return true;
        }
    }, Bazel, Buck, Maven;

    public boolean isGradle() {
        return false;
    }

    public boolean isReuseDaemon() {
        return false;
    }

    public boolean isColdDaemon() {
        return false;
    }

    public int benchmarkWarmUps() {
        if (isGradle() && !isReuseDaemon()) {
            // Do not warm up the daemon if it is not being reused
            return 1;
        }
        return 6;
    }

    public int profileWarmUps() {
        if (isGradle() && !isReuseDaemon()) {
            // Do not warm up the daemon if it is not being reused
            return 1;
        }
        return 2;
    }
}
